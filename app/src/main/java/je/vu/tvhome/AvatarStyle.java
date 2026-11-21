package je.vu.tvhome;

import java.util.Locale;

/** Stable username-based defaults plus explicit, independently editable choices. */
final class AvatarStyle {
    static final String[] BACKGROUNDS={"Ocean","Orchid","Mint","Sunset","Gold","Rose"};
    static final String[] SKINS={"Porcelain","Peach","Warm","Golden","Brown","Deep"};
    static final String[] HAIRS={"Crop","Swoop","Curls","Bun","Bald"};
    static final String[] HAIR_COLORS={"Midnight","Chocolate","Chestnut","Blond","Silver","Plum"};
    static final String[] EYES={"Friendly","Happy","Calm","Wink"};
    static final String[] ACCESSORIES={"None","Glasses","Headphones","Sparkle"};
    int variant=0,background=-1,skin=-1,hair=-1,hairColor=-1,eyes=-1,accessory=-1;
    AvatarStyle copy(){AvatarStyle copy=new AvatarStyle();copy.variant=variant;copy.background=background;copy.skin=skin;copy.hair=hair;copy.hairColor=hairColor;copy.eyes=eyes;copy.accessory=accessory;return copy;}
    void reset(){variant=0;clearChoices();}
    void shuffle(){variant++;clearChoices();}
    void clearChoices(){background=skin=hair=hairColor=eyes=accessory=-1;}
    int seed(String name){return mix((name==null?"":name.trim().toLowerCase(Locale.ROOT)).hashCode()^variant*0x9e3779b9);}
    static int mix(int value){value^=value>>>16;value*=0x7feb352d;value^=value>>>15;value*=0x846ca68b;return value^(value>>>16);}
    int choice(String name,int selected,int salt,int count){return selected>=0&&selected<count?selected:Math.floorMod(mix(seed(name)^salt),count);}
    int background(String name){return choice(name,background,11,BACKGROUNDS.length);}
    int skin(String name){return choice(name,skin,29,SKINS.length);}
    int hair(String name){return choice(name,hair,47,HAIRS.length);}
    int hairColor(String name){return choice(name,hairColor,71,HAIR_COLORS.length);}
    int eyes(String name){return choice(name,eyes,97,EYES.length);}
    int accessory(String name){return choice(name,accessory,137,ACCESSORIES.length);}
}
