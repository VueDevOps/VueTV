package je.vu.tvhome;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class TwitchSessionCheck {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static class Store implements TwitchSession.Store {
        TwitchSession.Credentials value=new TwitchSession.Credentials("client","a0","r0",0);
        boolean deleted;
        public synchronized TwitchSession.Credentials read(){return value;}
        public synchronized boolean save(TwitchSession.Credentials expected,TwitchSession.Credentials next){
            if(deleted||!value.equals(expected))return false;value=next;return true;
        }
        synchronized void disconnect(){value=new TwitchSession.Credentials("client","","",value.revision()+1);}
    }
    static class Network implements TwitchSession.Network {
        int refreshes,validations,requests,rotation,feedFailures;
        boolean expired,revoked,offline,wrongClient,missingScope,incomplete;
        long expiresIn=14400;
        Runnable duringRefresh=()->{};
        public TwitchSession.Validation validate(String access)throws Exception {
            validations++;
            if(expired&&access.equals("a"+rotation))throw new TwitchSession.HttpFailure(401,"HTTP 401");
            return new TwitchSession.Validation("user",wrongClient?"other-client":"client",!missingScope,expiresIn);
        }
        public TwitchSession.TokenPair refresh(String client,String refresh)throws Exception {
            refreshes++;
            if(offline)throw new IOException("Offline");
            if(revoked)throw new TwitchSession.HttpFailure(400,"HTTP 400");
            check(refresh.equals("r"+rotation),"Never reuse a rotated refresh token");
            rotation++;expired=false;duringRefresh.run();
            return new TwitchSession.TokenPair("a"+rotation,incomplete?"":"r"+rotation);
        }
        public String followed(String access,String client,String user)throws Exception {
            requests++;if(feedFailures>0){feedFailures--;throw new TwitchSession.HttpFailure(401,"HTTP 401");}
            check(user.equals("user"),"Use validated user's follows");return "streams";
        }
    }
    static TwitchSession session(String name,Store store,Network network,AtomicLong now){return new TwitchSession(name,store,network,now::get);}
    static void fails(Class<? extends Exception> type,Callable<?> work)throws Exception {
        try{work.call();throw new AssertionError("Expected "+type.getSimpleName());}
        catch(Exception error){if(!type.isInstance(error))throw error;}
    }
    public static void main(String[] args)throws Exception {
        Store store=new Store();Network net=new Network();AtomicLong now=new AtomicLong();
        TwitchSession session=session("rotation",store,net,now);
        check(session.followed().equals("streams"),"Valid initial session");
        now.set(3599000);session.followed();check(net.validations==1,"Validate at most hourly for valid tokens");
        now.set(3600000);session.followed();check(net.validations==2,"Hourly validation");
        for(int day=1;day<=35;day++){
            now.addAndGet(86400000);net.expired=true;session.followed();
            check(store.read().refresh().equals("r"+day),"Persist each rotated refresh token across days");
        }
        check(net.refreshes==35,"Sessions can continue beyond 30 days with regular use");
        net.expired=true;now.addAndGet(86400000);
        session("restart",store,net,now).followed();check(net.refreshes==36,"Stored token pair survives process restart");

        Store legacy=new Store();legacy.value=new TwitchSession.Credentials("client","a0","",0);
        Network old=new Network();old.expired=true;
        fails(TwitchSession.ReconnectRequired.class,()->session("legacy",legacy,old,now).followed());
        check(old.refreshes==0,"Legacy sign-ins need one reconnect instead of refreshing without a token");

        Network feed=new Network();feed.feedFailures=1;Store feedStore=new Store();
        session("api401",feedStore,feed,now).followed();check(feed.refreshes==1&&feed.requests==2,"API 401 refreshes and retries once");
        Network rejected=new Network();rejected.feedFailures=2;
        fails(TwitchSession.ReconnectRequired.class,()->session("bounded",new Store(),rejected,now).followed());
        check(rejected.refreshes==1&&rejected.requests==2,"Do not loop on persistent API rejection");

        Network outage=new Network();outage.expired=true;outage.offline=true;Store offlineStore=new Store();
        TwitchSession offline=session("offline",offlineStore,outage,now);
        fails(IOException.class,offline::followed);check(offlineStore.read().refresh().equals("r0"),"Network failures preserve sign-in");
        outage.offline=false;offline.followed();check(offlineStore.read().refresh().equals("r1"),"Automatic recovery on next attempt");
        Network revoked=new Network();revoked.expired=true;revoked.revoked=true;
        fails(TwitchSession.ReconnectRequired.class,()->session("revoked",new Store(),revoked,now).followed());
        check(revoked.refreshes==1,"Revoked or inactive refresh token requires reconnect");

        for(boolean wrongClient:new boolean[]{true,false}){
            Network mismatch=new Network();mismatch.wrongClient=wrongClient;mismatch.missingScope=!wrongClient;
            fails(TwitchSession.ReconnectRequired.class,()->session("mismatch",new Store(),mismatch,now).followed());
            check(mismatch.refreshes==0&&mismatch.requests==0,"Reject wrong client or missing scope without an unnecessary refresh");
        }
        Network shortToken=new Network();shortToken.expiresIn=10;
        AtomicLong shortTime=new AtomicLong();TwitchSession shortSession=session("short",new Store(),shortToken,shortTime);
        shortSession.followed();shortTime.set(10000);shortSession.followed();check(shortToken.validations==2,"Validation cache never exceeds token lifetime");

        for(boolean delete:new boolean[]{false,true}){
            Store changed=new Store();Network slow=new Network();slow.expired=true;
            slow.duringRefresh=()->{if(delete)changed.deleted=true;else changed.disconnect();};
            fails(IOException.class,()->session("changed"+delete,changed,slow,now).followed());
            check(!changed.read().access().equals("a1"),"In-flight refresh cannot restore disconnected/deleted profile credentials");
        }
        Network malformed=new Network();malformed.expired=true;malformed.incomplete=true;Store unchanged=new Store();
        fails(IOException.class,()->session("malformed",unchanged,malformed,now).followed());
        check(unchanged.read().access().equals("a0"),"Incomplete refresh must not overwrite saved token pair");

        Store shared=new Store();Network parallel=new Network();parallel.expired=true;
        CountDownLatch refreshing=new CountDownLatch(1),release=new CountDownLatch(1),secondEntered=new CountDownLatch(1);
        parallel.duringRefresh=()->{refreshing.countDown();try{check(release.await(2,TimeUnit.SECONDS),"Release refresh gate");}catch(InterruptedException error){throw new AssertionError(error);}};
        TwitchSession a=session("shared-profile",shared,parallel,now),b=session("shared-profile",shared,parallel,now);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try{
            Future<String> first=workers.submit(a::followed);
            check(refreshing.await(1,TimeUnit.SECONDS),"First refresh started");
            Future<String> second=workers.submit(()->{secondEntered.countDown();return b.followed();});
            check(secondEntered.await(1,TimeUnit.SECONDS),"Concurrent activity started loading");
            try{second.get(100,TimeUnit.MILLISECONDS);throw new AssertionError("Second load must await in-flight refresh");}catch(TimeoutException expected){}
            release.countDown();
            check(first.get(3,TimeUnit.SECONDS).equals("streams")&&second.get(3,TimeUnit.SECONDS).equals("streams"),"Concurrent loads succeed");
            check(parallel.refreshes==1,"Activities sharing a profile serialize single-use refresh tokens");
        }finally{release.countDown();workers.shutdownNow();}
        System.out.println("PASS: 35-day renewal, token rotation/restart, 401 retry, hourly validation, legacy migration, offline recovery, revocation, scopes, disconnect/deletion races and concurrent refresh");
    }
}
