package je.vu.tvhome;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** JVM checks for expiry, safe replacement, account isolation and bounded storage. */
public class CacheCheck {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("vue-cache-check");
        try{
            check(TimedDiskCache.hash("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"Stable SHA-256 cache keys");
            TimedDiskCache cache=new TimedDiskCache(root.toFile(),64);
            cache.put("account-a/image",new byte[]{1,2,3});
            check(Arrays.equals(cache.get("account-a/image",10000),new byte[]{1,2,3}),"Fresh cache hit");
            check(cache.get("account-b/image",10000)==null,"Account namespaces must not cross");
            cache.file("account-a/image").setLastModified(System.currentTimeMillis()-20000);
            check(cache.get("account-a/image",10000)==null,"Expired data must miss");
            check(cache.get("account-a/image",30000)!=null,"Longer TTL permits cached data");
            cache.put("account-a/image",new byte[]{4,5});
            check(Arrays.equals(cache.get("account-a/image",10000),new byte[]{4,5}),"Atomic replacement");
            check(cache.file("account-a/image").getName().matches("[a-f0-9]{64}\\.cache"),"Keys must not expose URLs or credentials");
            cache.put("oversized",new byte[65]);check(cache.get("oversized",1000)==null,"Reject oversized entry");
            ExecutorService workers=Executors.newFixedThreadPool(4);
            List<Future<?>> writes=new ArrayList<>();
            for(int i=0;i<20;i++){final int key=i;writes.add(workers.submit(()->cache.put("entry-"+key,new byte[8])));}
            for(Future<?> write:writes)write.get();workers.shutdown();
            long total=0;for(File file:root.toFile().listFiles())total+=file.length();check(total<=64,"Storage must stay bounded");
            cache.clear();check(root.toFile().listFiles().length==0,"Clear removes cached data");
            System.out.println("PASS: freshness, expiry, TTL change, account isolation, hashed names, atomic replacement, concurrent writes, size bound and clear");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});}}
    }
}
