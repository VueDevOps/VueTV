package je.vu.tvhome;

import android.app.AlertDialog;
import android.app.Dialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Draft-only avatar edits: Cancel discards changes, Save updates the profile roster. */
final class ProfileEditor {
    final MainActivity host;
    final ProfileStore.User user;
    final AvatarStyle draft;
    final Dialog dialog;
    final AvatarView preview;
    final EditText name;
    final List<Runnable> captions=new ArrayList<>();
    ProfileEditor(MainActivity activity,ProfileStore.User profile){
        host=activity;user=profile;draft=user.avatar.copy();dialog=new Dialog(host);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(host);root.setOrientation(LinearLayout.VERTICAL);root.setClipChildren(false);root.setClipToPadding(false);root.setPadding(host.dp(26),host.dp(20),host.dp(26),host.dp(20));root.setBackground(host.background("#14212C",22));root.addView(host.text("Edit profile",28,"#E1F2FF"));
        TextView subtitle=host.text("Make this little face your own.",13,"#A8BAC8");LinearLayout.LayoutParams sub=new LinearLayout.LayoutParams(-1,-2);sub.topMargin=host.dp(6);sub.bottomMargin=host.dp(12);root.addView(subtitle,sub);
        LinearLayout body=new LinearLayout(host);body.setClipChildren(false);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout left=new LinearLayout(host);left.setOrientation(LinearLayout.VERTICAL);left.setGravity(Gravity.CENTER_HORIZONTAL);left.setPadding(0,host.dp(8),host.dp(20),0);body.addView(left,new LinearLayout.LayoutParams(host.dp(210),-1));
        preview=new AvatarView(host,user);left.addView(preview,new LinearLayout.LayoutParams(host.dp(160),host.dp(160)));
        TextView shuffle=button(left,"Shuffle avatar",()->{draft.shuffle();refresh();});LinearLayout.LayoutParams shuffleP=(LinearLayout.LayoutParams)shuffle.getLayoutParams();shuffleP.topMargin=host.dp(15);shuffle.setLayoutParams(shuffleP);
        button(left,"Reset to username",()->{draft.reset();refresh();});
        ScrollView scroll=new ScrollView(host);scroll.setVerticalScrollBarEnabled(false);scroll.setClipChildren(false);body.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));LinearLayout controls=new LinearLayout(host);controls.setOrientation(LinearLayout.VERTICAL);controls.setClipChildren(false);controls.setPadding(host.dp(4),0,host.dp(4),host.dp(8));scroll.addView(controls);
        name=host.field(controls,"Profile name",user.name,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);name.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh();}public void afterTextChanged(Editable text){}});
        LinearLayout row=controlRow(controls);
        choice(row,"Background",AvatarStyle.BACKGROUNDS,()->draft.background,()->draft.background(label()),value->draft.background=value);
        choice(row,"Skin tone",AvatarStyle.SKINS,()->draft.skin,()->draft.skin(label()),value->draft.skin=value);
        row=controlRow(controls);
        choice(row,"Hair",AvatarStyle.HAIRS,()->draft.hair,()->draft.hair(label()),value->draft.hair=value);
        choice(row,"Hair color",AvatarStyle.HAIR_COLORS,()->draft.hairColor,()->draft.hairColor(label()),value->draft.hairColor=value);
        row=controlRow(controls);
        choice(row,"Eyes",AvatarStyle.EYES,()->draft.eyes,()->draft.eyes(label()),value->draft.eyes=value);
        choice(row,"Accessory",AvatarStyle.ACCESSORIES,()->draft.accessory,()->draft.accessory(label()),value->draft.accessory=value);
        LinearLayout footer=new LinearLayout(host);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setClipChildren(false);LinearLayout.LayoutParams foot=new LinearLayout.LayoutParams(-1,host.dp(48));foot.topMargin=host.dp(12);root.addView(footer,foot);
        TextView save=button(footer,"Save changes",()->{try{host.profiles.update(user.id,name.getText().toString(),draft);close();host.recreate();}catch(IllegalArgumentException failure){name.setError(failure.getMessage());name.requestFocus();}});
        button(footer,"Cancel",this::close);TextView delete=button(footer,"Delete profile",this::delete);delete.setTextColor(host.color("#F3B9BD"));
        dialog.setContentView(root);Window window=dialog.getWindow();if(window!=null)window.setBackgroundDrawableResource(android.R.color.transparent);dialog.show();KeyboardInput.configure(dialog,save,false);if(window!=null)window.setLayout(Math.min(host.dp(820),host.getResources().getDisplayMetrics().widthPixels-host.dp(40)),Math.min(host.dp(500),host.getResources().getDisplayMetrics().heightPixels-host.dp(32)));save.post(()->save.requestFocusFromTouch());refresh();
    }
    String label(){String value=name==null?user.name:name.getText().toString().trim();return value.isEmpty()?user.name:value;}
    LinearLayout controlRow(LinearLayout parent){LinearLayout row=new LinearLayout(host);row.setClipChildren(false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,host.dp(62));p.topMargin=host.dp(8);parent.addView(row,p);return row;}
    TextView button(LinearLayout row,String label,Runnable click){TextView button=host.button(label,click);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,13);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,host.dp(42));p.setMargins(0,0,host.dp(10),host.dp(8));row.addView(button,p);return button;}
    void choice(LinearLayout row,String title,String[] names,java.util.function.IntSupplier selected,java.util.function.IntSupplier resolved,java.util.function.IntConsumer write){
        TextView button=host.button("",()->{String[] values=new String[names.length+1];values[0]="From username";System.arraycopy(names,0,values,1,names.length);int current=selected.getAsInt();host.requests.dialog(new AlertDialog.Builder(host).setTitle(title).setSingleChoiceItems(values,current>=0&&current<names.length?current+1:0,(d,index)->{write.accept(index-1);d.dismiss();refresh();}).setNegativeButton("Cancel",null));});button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,12);button.setMaxLines(2);button.setPadding(host.dp(10),0,host.dp(10),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,host.dp(54),1);p.rightMargin=host.dp(10);row.addView(button,p);captions.add(()->button.setText(title+"\n"+names[resolved.getAsInt()]+(selected.getAsInt()<0?" · Auto":"")));
    }
    void refresh(){if(preview!=null)preview.setAvatar(label(),draft);for(Runnable caption:captions)caption.run();}
    void delete(){
        if(host.profiles.users().size()<=1){host.toast("Keep at least one profile. Create another first.");return;}
        host.requests.dialog(new AlertDialog.Builder(host).setTitle("Delete “"+user.name+"”?").setMessage("This removes this profile’s sign-ins, preferences and cached content from this streamer. Your accounts and media on the servers are kept.").setNegativeButton("Cancel",null).setPositiveButton("Delete profile",(d,w)->{close();host.profiles.remove(user.id);host.recreate();}));
    }
    void close(){dialog.dismiss();}
}
