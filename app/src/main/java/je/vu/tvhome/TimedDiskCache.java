package je.vu.tvhome;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Private bounded cache. File timestamps describe freshness, not access time. */
final class TimedDiskCache {
    final File directory;
    final long maxBytes;
    TimedDiskCache(File directory,long maxBytes){this.directory=directory;this.maxBytes=maxBytes;}
    static String hash(String key){
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));char[] name=new char[digest.length*2];char[] hex="0123456789abcdef".toCharArray();for(int i=0;i<digest.length;i++){int b=digest[i]&255;name[i*2]=hex[b>>>4];name[i*2+1]=hex[b&15];}return new String(name);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    File file(String key){return new File(directory,hash(key)+".cache");}
    synchronized byte[] get(String key,long ttl){
        File file=file(key);long age=System.currentTimeMillis()-file.lastModified();
        if(!file.isFile()||age<0||age>=ttl||file.length()>maxBytes)return null;
        try{return Files.readAllBytes(file.toPath());}catch(IOException e){return null;}
    }
    synchronized long writtenAt(String key){return file(key).lastModified();}
    synchronized void put(String key,byte[] bytes){
        if(bytes.length>maxBytes||(!directory.isDirectory()&&!directory.mkdirs()))return;
        File target=file(key),temporary=null;
        try{
            temporary=File.createTempFile("write-",".tmp",directory);
            try(FileOutputStream out=new FileOutputStream(temporary)){out.write(bytes);}
            try{Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            File[] files=directory.listFiles((dir,name)->name.endsWith(".cache"));
            if(files==null)return;
            long total=0;for(File file:files)total+=file.length();
            if(total>maxBytes){Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(File file:files){if(total<=maxBytes)break;long size=file.length();if(file.delete())total-=size;}}
        }catch(IOException ignored){}finally{if(temporary!=null&&temporary.exists())temporary.delete();}
    }
    synchronized void clear(){File[] files=directory.listFiles();if(files!=null)for(File file:files)if(file.isFile())file.delete();}
}
