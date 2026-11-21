package je.vu.tvhome;

import java.util.HashSet;
import java.util.Set;

public class AvatarCheck {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static String signature(AvatarStyle style,String name){return style.background(name)+":"+style.skin(name)+":"+style.hair(name)+":"+style.hairColor(name)+":"+style.eyes(name)+":"+style.accessory(name);}
    public static void main(String[] args){
        AvatarStyle original=new AvatarStyle();check(signature(original,"Sam").equals(signature(new AvatarStyle()," sam ")),"Username defaults remain stable and normalized");
        AvatarStyle draft=original.copy();draft.shuffle();check(!signature(draft,"Sam").equals(signature(original,"Sam")),"Shuffle produces a different avatar");check(original.variant==0,"Editing a draft leaves saved avatar untouched");
        draft.background=4;draft.skin=5;draft.hair=2;draft.hairColor=1;draft.eyes=3;draft.accessory=1;check(signature(draft,"Sam").equals(signature(draft,"Riley")),"Explicit choices survive a name change");
        AvatarStyle saved=draft.copy();draft.reset();check(saved.variant==1&&saved.background==4,"Saved style owns its choices");check(signature(draft,"Sam").equals(signature(original,"Sam")),"Reset restores username defaults");
        Set<String> variety=new HashSet<>();for(int i=0;i<1000;i++){String name="User "+i;variety.add(signature(original,name));check(original.background(name)>=0&&original.background(name)<6&&original.skin(name)>=0&&original.skin(name)<6&&original.hair(name)>=0&&original.hair(name)<5&&original.hairColor(name)>=0&&original.hairColor(name)<6&&original.eyes(name)>=0&&original.eyes(name)<4&&original.accessory(name)>=0&&original.accessory(name)<4,"All generated choices remain in bounds");}check(variety.size()>900,"Usernames produce varied avatars");
        draft.variant=Integer.MIN_VALUE;draft.background=999;draft.skin=-99;signature(draft,"💜 Åsa");check(draft.background("💜 Åsa")<6&&draft.skin("💜 Åsa")>=0,"Invalid stored choices fall back safely");
        System.out.println("PASS: stable username avatars, variation, draft isolation, overrides, reset and valid ranges");
    }
}
