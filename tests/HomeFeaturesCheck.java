package je.vu.tvhome;

import java.util.*;

public class HomeFeaturesCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(AppOrder.arrange(Arrays.asList("b","a","b","removed"),Arrays.asList("a","b","new")).equals(Arrays.asList("b","a","new")),"Preserve saved order, remove missing apps, append new apps, remove duplicates");
        check(AppOrder.arrange(Collections.emptyList(),Arrays.asList("a","b")).equals(Arrays.asList("a","b")),"Default order");
        int red=ArtworkColors.accent(new int[]{0xffff2222,0xffdd1111,0xffeeeeee,0xff111111});
        check(((red>>>16)&255)>(red&255)*2,"Retain colorful image hue despite neutral pixels");
        int blue=ArtworkColors.accent(new int[]{0xff2222ff,0xff1111dd});check((blue&255)>((blue>>>16)&255)*2,"Blue artwork produces blue accent");
        int dark=ArtworkColors.darken(red,.2);check(((dark>>>16)&255)<70,"Background stays dark enough for text");
        check(ArtworkColors.accent(new int[]{0x00ffffff})==0xff46515a,"Transparent icon fallback");
        System.out.println("PASS: persisted app ordering reconciliation and dark artwork-derived color selection");
    }
}
