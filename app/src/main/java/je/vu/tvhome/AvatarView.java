package je.vu.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Local vector artwork: crisp at header and preview sizes, with no image downloads. */
final class AvatarView extends View {
    static final int[][] PALETTES={{0xff174869,0xff65bed2},{0xff45386a,0xffb19fe8},{0xff215c58,0xff86d5ad},{0xff804e3d,0xffe5ad83},{0xff6e5c26,0xffe8cc71},{0xff6b3b59,0xffdfa4c7}};
    static final int[] SKINS={0xfff6ddcd,0xffedbea2,0xffdba57e,0xffc38b60,0xff996347,0xff654435};
    static final int[] HAIR={0xff25313c,0xff483329,0xff94563e,0xffd9b86f,0xffc2c9ce,0xff704c79};
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path=new Path();
    String name;
    AvatarStyle style;
    Shader backdrop;
    int backgroundIndex=-1;
    AvatarView(Context context,ProfileStore.User user){super(context);setAvatar(user.name,user.avatar);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setAvatar(String label,AvatarStyle choices){name=label;style=choices.copy();int next=style.background(name);if(next!=backgroundIndex){backgroundIndex=next;backdrop=new LinearGradient(0,0,100,100,PALETTES[next][1],PALETTES[next][0],Shader.TileMode.CLAMP);}invalidate();}
    void fill(Canvas canvas,int color,float left,float top,float right,float bottom,float radius){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);canvas.drawRoundRect(left,top,right,bottom,radius,radius,paint);}
    void oval(Canvas canvas,int color,float left,float top,float right,float bottom){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);canvas.drawOval(left,top,right,bottom,paint);}
    void stroke(Canvas canvas,int color,float width){paint.setShader(null);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float side=Math.min(getWidth(),getHeight());if(side<=0)return;canvas.save();canvas.translate((getWidth()-side)/2,(getHeight()-side)/2);canvas.scale(side/100,side/100);
        path.reset();path.addCircle(50,50,50,Path.Direction.CW);canvas.clipPath(path);paint.setStyle(Paint.Style.FILL);paint.setShader(backdrop);canvas.drawRect(0,0,100,100,paint);paint.setShader(null);
        oval(canvas,0x1cffffff,-16,-20,54,46);oval(canvas,0x12ffffff,69,62,119,112);
        int skin=SKINS[style.skin(name)],hair=HAIR[style.hairColor(name)],cut=style.hair(name),accessory=style.accessory(name);
        if(cut==3){oval(canvas,hair,24,16,76,70);oval(canvas,hair,39,4,61,26);}else if(cut!=4)oval(canvas,hair,25,15,75,61);
        oval(canvas,skin,23,43,35,59);oval(canvas,skin,65,43,77,59);
        fill(canvas,skin,42,64,58,86,7);fill(canvas,PALETTES[backgroundIndex][1],15,78,85,120,28);oval(canvas,skin,39,72,61,86);
        stroke(canvas,0x40ffffff,2);canvas.drawLine(26,91,26,104,paint);canvas.drawLine(74,91,74,104,paint);
        oval(canvas,skin,28,20,72,76);
        if(cut==0){fill(canvas,hair,28,18,72,33,12);fill(canvas,hair,27,27,33,44,3);fill(canvas,hair,67,27,73,44,3);}
        else if(cut==1){path.reset();path.moveTo(26,41);path.cubicTo(22,8,59,7,75,25);path.cubicTo(68,43,48,36,39,29);path.cubicTo(34,38,29,41,26,41);paint.setStyle(Paint.Style.FILL);paint.setColor(hair);canvas.drawPath(path,paint);}
        else if(cut==2){for(int i=0;i<6;i++){float x=30+i*8;oval(canvas,hair,x-7,13+(i%2)*3,x+7,31+(i%2)*3);}fill(canvas,hair,26,27,32,43,3);fill(canvas,hair,68,27,74,43,3);}
        else if(cut==3){stroke(canvas,hair,7);canvas.drawArc(new RectF(28,17,72,49),180,180,false,paint);}
        stroke(canvas,hair,1.8f);canvas.drawLine(34,40,42,39,paint);canvas.drawLine(58,39,66,40,paint);
        int eyes=style.eyes(name);stroke(canvas,0xff23303a,2.3f);
        if(eyes==1){canvas.drawArc(new RectF(34,45,43,53),185,170,false,paint);canvas.drawArc(new RectF(57,45,66,53),185,170,false,paint);}
        else if(eyes==2){canvas.drawLine(35,49,42,49,paint);canvas.drawLine(58,49,65,49,paint);}
        else{oval(canvas,0xff23303a,36,45,41,52);oval(canvas,0xbfffffff,37,46,38.5f,48);if(eyes==3){stroke(canvas,0xff23303a,2.3f);canvas.drawArc(new RectF(57,45,66,53),185,170,false,paint);}else{oval(canvas,0xff23303a,59,45,64,52);oval(canvas,0xbfffffff,60,46,61.5f,48);}}
        oval(canvas,0x25cf6172,30,54,40,61);oval(canvas,0x25cf6172,60,54,70,61);
        stroke(canvas,0x557d483c,1.4f);path.reset();path.moveTo(50,49);path.lineTo(48,56);path.quadTo(50,58,53,56);canvas.drawPath(path,paint);
        stroke(canvas,0xff874e4b,2);canvas.drawArc(new RectF(41,56,59,68),12,156,false,paint);
        if(accessory==1){stroke(canvas,0xff263646,2);canvas.drawRoundRect(30,42,46,55,4,4,paint);canvas.drawRoundRect(54,42,70,55,4,4,paint);canvas.drawLine(46,47,54,47,paint);canvas.drawLine(26,46,30,46,paint);canvas.drawLine(70,46,74,46,paint);}
        else if(accessory==2){stroke(canvas,0xffdde8f0,4);canvas.drawArc(new RectF(22,19,78,77),180,180,false,paint);fill(canvas,0xff33495e,20,43,29,63,4);fill(canvas,0xff33495e,71,43,80,63,4);fill(canvas,0xffd3e3ef,21,46,25,60,2);fill(canvas,0xffd3e3ef,75,46,79,60,2);}
        else if(accessory==3){stroke(canvas,0xffffdf82,2);canvas.drawLine(77,24,77,36,paint);canvas.drawLine(71,30,83,30,paint);canvas.drawLine(73,26,81,34,paint);canvas.drawLine(73,34,81,26,paint);}
        paint.setStyle(Paint.Style.FILL);canvas.restore();
    }
}
