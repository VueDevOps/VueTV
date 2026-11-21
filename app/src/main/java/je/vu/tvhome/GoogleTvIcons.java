package je.vu.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

/** Reuses the launcher's own installed artwork, without bundling its assets. */
final class GoogleTvIcons {
    static Drawable load(Context context,String kind){
        int id=kind.equals("search")?0x7f080705:0x7f08070b;
        String expected=kind.equals("search")?"res/xX.png":"res/jv.png";
        try{
            Resources google=context.getPackageManager().getResourcesForApplication("com.google.android.apps.tv.launcherx");
            TypedValue value=new TypedValue();google.getValue(id,value,true);
            // Obfuscated IDs may change with a launcher update. Never display an
            // unrelated resource; the view has a native drawing fallback.
            if(value.string==null||!expected.equals(value.string.toString()))return null;
            Drawable icon=google.getDrawable(id,null).mutate();android.util.Log.d("VueIcons","Using installed Google TV "+kind+" icon");return icon;
        }catch(Exception unavailable){return null;}
    }
}
