package je.vu.tvhome;

import java.util.*;
import java.util.function.Function;

/** Stable within a half-hour window, with fresh titles entering each window. */
final class PickRotation {
    static <T> List<T> select(List<T> pool,Function<T,String> key,String profile,long slot,int count){
        Map<String,T> unique=new LinkedHashMap<>();
        for(T item:pool)unique.putIfAbsent(key.apply(item),item);
        List<T> sorted=new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparingLong((T item)->Integer.toUnsignedLong((profile+":"+key.apply(item)).hashCode()))
                .thenComparing(key));
        List<T> result=new ArrayList<>();if(sorted.isEmpty())return result;
        int step=Math.max(1,Math.min(5,sorted.size()/3));
        int start=(int)Math.floorMod(slot*step,sorted.size());
        for(int i=0;i<Math.min(count,sorted.size());i++)result.add(sorted.get((start+i)%sorted.size()));
        return result;
    }
}
