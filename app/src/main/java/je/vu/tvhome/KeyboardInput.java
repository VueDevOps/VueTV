package je.vu.tvhome;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;

/** Explicit IME completion and focus handoff for TV text-entry dialogs. */
final class KeyboardInput {
    static void configure(AlertDialog dialog,boolean search){
        configure(dialog,dialog.getButton(AlertDialog.BUTTON_POSITIVE),search);
    }
    static void configure(Dialog dialog,View doneTarget,boolean search){
        if(dialog.getWindow()==null)return;
        View root=dialog.getWindow().getDecorView();
        List<EditText> fields=new ArrayList<>();collect(root,fields);
        if(fields.isEmpty())return;
        IBinder token=root.getWindowToken();
        // Keep the dialog token: its field can already be detached on dismissal.
        dialog.setOnDismissListener(ignored->hide(root,token));
        for(int i=0;i<fields.size();i++){
            EditText field=fields.get(i);EditText next=i+1<fields.size()?fields.get(i+1):null;
            field.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN|
                    (next!=null?EditorInfo.IME_ACTION_NEXT:search?EditorInfo.IME_ACTION_SEARCH:EditorInfo.IME_ACTION_DONE));
            field.setOnEditorActionListener((text,action,event)->{
                boolean enter=event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER;
                if(!enter&&action!=EditorInfo.IME_ACTION_NEXT&&action!=EditorInfo.IME_ACTION_DONE&&
                        action!=EditorInfo.IME_ACTION_SEARCH&&action!=EditorInfo.IME_ACTION_GO)return false;
                if(enter&&event.getAction()!=KeyEvent.ACTION_UP)return true;
                if(next!=null){next.requestFocus();return true;}
                hide(field,field.getWindowToken());
                if(doneTarget!=null){
                    doneTarget.requestFocusFromTouch();
                    if(search&&doneTarget.isEnabled())doneTarget.performClick();
                }else field.clearFocus();
                return true;
            });
        }
    }
    static void hide(Dialog dialog){
        if(dialog.getWindow()!=null){View root=dialog.getWindow().getDecorView();hide(root,root.getWindowToken());}
    }
    private static void hide(View view,IBinder token){
        InputMethodManager keyboard=(InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if(keyboard!=null&&token!=null)keyboard.hideSoftInputFromWindow(token,0);
    }
    private static void collect(View view,List<EditText> fields){
        if(view instanceof EditText)fields.add((EditText)view);
        else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),fields);}
    }
}
