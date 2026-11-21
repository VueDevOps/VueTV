package je.vu.tvhome;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Remote-friendly account picker, separate from connection settings. */
final class ProfilePicker {
    final MainActivity host;
    Dialog picker;
    ProfileEditor editor;
    ProfilePicker(MainActivity owner){host=owner;}
    void show(){
        close();picker=new Dialog(host);picker.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content=new LinearLayout(host);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(host.dp(28),host.dp(28),host.dp(28),host.dp(24));content.setBackground(host.background("#14212C",22));
        TextView title=host.text("Who’s watching?",30,"#E1F2FF");title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));content.addView(title);
        TextView subtitle=host.text("Choose your user. Hold a user to edit their profile.",13,"#A8BAC8");LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=host.dp(8);content.addView(subtitle,sp);
        HorizontalScrollView scroll=new HorizontalScrollView(host);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipChildren(false);scroll.setClipToPadding(false);scroll.setPadding(host.dp(8),host.dp(20),host.dp(8),host.dp(16));LinearLayout choices=new LinearLayout(host);choices.setClipChildren(false);scroll.addView(choices);content.addView(scroll,new LinearLayout.LayoutParams(-1,host.dp(180)));
        View current=null;
        for(ProfileStore.User user:host.profiles.users()){
            boolean selected=user.id.equals(host.profileId);LinearLayout tile=new LinearLayout(host);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(host.dp(8),host.dp(10),host.dp(8),host.dp(8));tile.setFocusableInTouchMode(true);tile.setFocusable(true);tile.setClickable(true);tile.setDefaultFocusHighlightEnabled(false);tile.setContentDescription(user.name+(selected?", current user":""));tile.setBackground(tileBackground(selected,false));
            AvatarView avatar=new AvatarView(host,user);tile.addView(avatar,new LinearLayout.LayoutParams(host.dp(68),host.dp(68)));
            TextView name=host.text(user.name,16,"#E1F2FF");name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,host.dp(26));np.topMargin=host.dp(9);tile.addView(name,np);
            TextView state=host.text(selected?"Current user":"",10,"#A8BAC8");state.setGravity(Gravity.CENTER);tile.addView(state,new LinearLayout.LayoutParams(-1,host.dp(16)));
            tile.setOnFocusChangeListener((v,focused)->{tile.setBackground(tileBackground(selected,focused));host.lift(tile,focused,1.035f);});tile.setOnClickListener(v->{close();if(!selected)switchUser(user.id);});tile.setOnLongClickListener(v->{edit(user);return true;});
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(host.dp(132),-1);tp.rightMargin=host.dp(12);choices.addView(tile,tp);if(selected)current=tile;
        }
        LinearLayout actions=new LinearLayout(host);actions.setGravity(Gravity.CENTER_VERTICAL);content.addView(actions);
        button(actions,"+ Create user",()->{close();create();});button(actions,"Edit profile",()->edit(host.profiles.active()));button(actions,"VueSettings",()->{close();host.connections();});button(actions,"Close",this::close);
        picker.setContentView(content);Window window=picker.getWindow();if(window!=null)window.setBackgroundDrawableResource(android.R.color.transparent);picker.show();if(window!=null)window.setLayout(Math.min(host.dp(820),host.getResources().getDisplayMetrics().widthPixels-host.dp(64)),-2);if(current!=null){View selected=current;selected.post(()->selected.requestFocusFromTouch());}
    }
    GradientDrawable tileBackground(boolean selected,boolean focused){GradientDrawable shape=host.background(focused?"#314B60":selected?"#223747":"#0014212C",16);if(focused||selected)shape.setStroke(host.dp(focused?2:1),host.color(focused?"#D3E3EF":"#617D91"));return shape;}
    static String avatarColor(String id){String[] colors={"#405E7A","#665584","#3A716D","#855C48","#6B6B40","#704A63"};return colors[Math.floorMod(id.hashCode(),colors.length)];}
    void button(LinearLayout row,String label,Runnable action){TextView button=host.button(label,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,host.dp(46));p.rightMargin=host.dp(12);row.addView(button,p);}
    void create(){nameDialog("Create user","",name->{ProfileStore.User user=host.profiles.create(name,host.prefs);switchUser(user.id);});}
    void nameDialog(String title,String initial,java.util.function.Consumer<String> save){
        LinearLayout form=new LinearLayout(host);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(host.dp(24),host.dp(4),host.dp(24),host.dp(12));EditText name=host.field(form,"User name",initial,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        AlertDialog dialog=new AlertDialog.Builder(host).setTitle(title).setView(form).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();dialog.setOnShowListener(d->{host.requests.style(dialog);dialog.getButton(-1).setOnClickListener(v->{try{save.accept(name.getText().toString());dialog.dismiss();}catch(IllegalArgumentException failure){name.setError(failure.getMessage());}});});dialog.show();name.requestFocus();
    }
    void edit(ProfileStore.User user){close();editor=new ProfileEditor(host,user);}
    void switchUser(String id){host.finishAppMove(true);host.profiles.activate(id);host.recreate();}
    void close(){if(picker!=null){picker.dismiss();picker=null;}if(editor!=null){editor.close();editor=null;}}
}
