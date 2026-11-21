package je.vu.tvhome;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Public-client device OAuth. Refresh tokens rotate after every successful use. */
final class TwitchSession {
    record Credentials(String client,String access,String refresh,long revision) {}
    record TokenPair(String access,String refresh) {}
    record Validation(String user,String client,boolean follows,long expiresIn) {}
    interface Store {
        Credentials read();
        boolean save(Credentials expected,Credentials replacement);
    }
    interface Network {
        Validation validate(String access)throws Exception;
        TokenPair refresh(String client,String refresh)throws Exception;
        String followed(String access,String client,String user)throws Exception;
    }
    static class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status,String message){super(message);this.status=status;}
    }
    static class ReconnectRequired extends IOException {
        ReconnectRequired(){super("Reconnect Twitch once in Connections to renew this sign-in.");}
    }
    private static final ConcurrentHashMap<String,Object> locks=new ConcurrentHashMap<>();
    private final Object lock;
    private final Store store;
    private final Network network;
    private final LongSupplier clock;
    private String validatedToken="";
    private Validation validation;
    private long validatedUntil;

    TwitchSession(String profile,Store store,Network network,LongSupplier clock){
        lock=locks.computeIfAbsent(profile,key->new Object());this.store=store;this.network=network;this.clock=clock;
    }
    String followed()throws Exception {
        // Shared across activity recreations: a single-use refresh token must never race itself.
        synchronized(lock){
            Credentials current=store.read();
            if(current.client().isEmpty()||current.access().isEmpty())throw new ReconnectRequired();
            boolean refreshed=false;
            Validation valid;
            try{valid=validate(current);}
            catch(HttpFailure failure){
                if(failure.status!=401)throw failure;
                current=renew(current);refreshed=true;valid=validateRenewed(current);
            }
            String result;
            try{result=network.followed(current.access(),current.client(),valid.user());}
            catch(HttpFailure failure){
                if(failure.status!=401)throw failure;
                if(refreshed)throw new ReconnectRequired();
                current=renew(current);valid=validateRenewed(current);
                try{result=network.followed(current.access(),current.client(),valid.user());}
                catch(HttpFailure retry){if(retry.status==401)throw new ReconnectRequired();throw retry;}
            }
            if(!current.equals(store.read()))throw new IOException("Twitch connection changed. Refresh again.");
            return result;
        }
    }
    private Validation validateRenewed(Credentials credentials)throws Exception {
        try{return validate(credentials);}
        catch(HttpFailure failure){if(failure.status==401)throw new ReconnectRequired();throw failure;}
    }
    private Validation validate(Credentials credentials)throws Exception {
        long now=clock.getAsLong();
        Validation result;
        if(credentials.access().equals(validatedToken)&&now<validatedUntil)result=validation;
        else{
            result=network.validate(credentials.access());
            validatedToken=credentials.access();validation=result;
            validatedUntil=now+Math.min(3600,Math.max(0,result.expiresIn()))*1000L;
        }
        if(!credentials.client().equals(result.client())||result.user().isEmpty()||!result.follows())throw new ReconnectRequired();
        return result;
    }
    private Credentials renew(Credentials previous)throws Exception {
        if(previous.refresh().isEmpty())throw new ReconnectRequired();
        TokenPair pair;
        try{pair=network.refresh(previous.client(),previous.refresh());}
        catch(HttpFailure failure){
            if(failure.status==400||failure.status==401)throw new ReconnectRequired();
            throw failure;
        }
        if(pair.access().isEmpty()||pair.refresh().isEmpty())throw new IOException("Twitch returned an incomplete sign-in. Reconnect in Connections.");
        Credentials next=new Credentials(previous.client(),pair.access(),pair.refresh(),previous.revision()+1);
        // Never restore credentials after disconnect, profile deletion, or a new sign-in.
        if(!store.save(previous,next))throw new IOException("Twitch connection changed. Refresh again.");
        return next;
    }
}
