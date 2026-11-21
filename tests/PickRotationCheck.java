package je.vu.tvhome;

import java.util.*;

public class PickRotationCheck {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        List<String> pool=new ArrayList<>();for(int i=0;i<120;i++)pool.add("title-"+i);
        List<String> first=PickRotation.select(pool,x->x,"profile",100,12);
        Collections.reverse(pool);
        check(first.equals(PickRotation.select(pool,x->x,"profile",100,12)),"Stable across refreshes, restarts and server ordering");
        List<String> next=PickRotation.select(pool,x->x,"profile",101,12);
        check(!new HashSet<>(first).equals(new HashSet<>(next)),"New titles enter the next rotation");
        check(next.size()==12&&new HashSet<>(next).size()==12,"No duplicate cards");
        check(PickRotation.select(Arrays.asList("a","a","b"),x->x,"profile",100,12).size()==2,"Small libraries are deduplicated without repetition");
        check(PickRotation.select(Collections.<String>emptyList(),x->x,"profile",100,12).isEmpty(),"Empty library");
        Set<String> seen=new HashSet<>();for(int i=0;i<120;i++)seen.addAll(PickRotation.select(pool,x->x,"profile",i,12));
        check(seen.size()==120,"Entire candidate library gets exposure");
        System.out.println("PASS: stable picks, timed variety, full coverage, deduplication and empty libraries");
    }
}
