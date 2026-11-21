package je.vu.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

/** Keeps Home working on TVs that force their system launcher above the HOME role. */
public final class HomeButtonService extends AccessibilityService {
    final Handler main=new Handler(Looper.getMainLooper());
    boolean held;
    long lastHome;
    final Runnable hold=()->{held=true;quickSettings();};
    @Override protected void onServiceConnected(){android.util.Log.i("VueHome","Home button service connected");}
    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event.getKeyCode()!=KeyEvent.KEYCODE_HOME)return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN){if(event.getRepeatCount()==0){held=false;main.removeCallbacks(hold);main.postDelayed(hold,ViewConfiguration.getLongPressTimeout());}return true;}
        if(event.getAction()==KeyEvent.ACTION_UP){main.removeCallbacks(hold);if(!event.isCanceled()&&!held)openHome();return true;}
        return true;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        // Startup and system-generated HOME intents may bypass remote key events.
        // Match only the original home activity; never redirect its dashboard.
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&"com.google.android.apps.tv.launcherx".contentEquals(event.getPackageName()==null?"":event.getPackageName())&&"com.google.android.apps.tv.launcherx.home.HomeActivity".contentEquals(event.getClassName()==null?"":event.getClassName()))openHome();
    }
    void openHome(){
        long now=android.os.SystemClock.elapsedRealtime();if(now-lastHome<250)return;lastHome=now;
        startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
        android.util.Log.i("VueHome","Opened Vue TV home");
    }
    void quickSettings(){
        try{Object bar=getSystemService(Class.forName("android.app.StatusBarManager"));if(bar!=null){bar.getClass().getMethod("expandNotificationsPanel").invoke(bar);return;}}catch(ReflectiveOperationException|SecurityException ignored){}
        startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    @Override public void onInterrupt(){main.removeCallbacks(hold);}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
}
