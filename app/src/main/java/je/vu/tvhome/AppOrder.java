package je.vu.tvhome;

import java.util.*;

final class AppOrder {
    static List<String> arrange(List<String> saved,List<String> available){
        Set<String> installed=new LinkedHashSet<>(available),result=new LinkedHashSet<>();
        for(String id:saved)if(installed.contains(id))result.add(id);
        result.addAll(installed);return new ArrayList<>(result);
    }
}
