package je.vu.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.LruCache;
import android.view.View;

/** A cached soft halo: no live blur or bitmap allocation during focus animation. */
final class FocusGlow extends View {
    static final LruCache<String,Bitmap> masks=new LruCache<>(4);
    final boolean circle;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    Bitmap mask;
    FocusGlow(Context context,boolean round){super(context);circle=round;setAlpha(0);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
        super.onSizeChanged(width,height,oldWidth,oldHeight);if(width<=0||height<=0)return;
        float density=getResources().getDisplayMetrics().density;String key=width+":"+height+":"+density+":"+circle;mask=masks.get(key);if(mask!=null)return;
        mask=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(mask);Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setColor(0xffc7dce8);glow.setShadowLayer(10*density,0,2*density,0x95c7dce8);
        float inset=20*density;RectF shape=new RectF(inset,inset,width-inset,height-inset);float radius=circle?shape.width()/2:12*density;
        canvas.drawRoundRect(shape,radius,radius,glow);canvas.setBitmap(null);masks.put(key,mask);
    }
    @Override protected void onDraw(Canvas canvas){if(mask!=null)canvas.drawBitmap(mask,0,0,paint);}
}
