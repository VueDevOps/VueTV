package je.vu.tvhome;

import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** A single muted player, kept below the launcher UI with artwork as its fallback. */
final class LivePreview {
    static final String PAGE="https://vuedevops.github.io/VueTV/live-preview.html";
    static String pageUrl(String channel){return PAGE+"?v=2&channel="+Uri.encode(channel);}
    final MainActivity host;
    WebView view;
    Runnable pending,poll,timeout;
    String channel="";
    long ticket,started;
    boolean playing;
    LivePreview(MainActivity host){this.host=host;}

    void show(MainActivity.Media media){
        if(!host.active||!host.currentTab.equals("Live")||!media.provider.equals("Twitch")||!host.prefs.getBoolean("livePreview",true)){stop();return;}
        String next=media.id.toLowerCase(Locale.ROOT);
        if(!next.matches("[a-z0-9_]{1,25}")){stop();return;}
        if(next.equals(channel))return;
        cancelCallbacks();channel=next;playing=false;final long run=++ticket;
        if(view!=null){view.animate().cancel();view.setAlpha(0);}
        pending=()->{pending=null;if(run==ticket&&host.active)start(next,run);};
        host.main.postDelayed(pending,180);
    }
    void start(String next,long run){
        try{
            if(view==null){
                if((host.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)WebView.setWebContentsDebuggingEnabled(true);
                view=new WebView(host);
                view.setBackgroundColor(Color.BLACK);view.setFocusable(false);view.setFocusableInTouchMode(false);
                view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                view.setOnTouchListener((v,event)->true);
                WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
                settings.setMediaPlaybackRequiresUserGesture(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                view.setWebChromeClient(new android.webkit.WebChromeClient());
                view.setWebViewClient(new WebViewClient(){
                    public boolean shouldOverrideUrlLoading(WebView web,WebResourceRequest request){
                        Uri uri=request.getUrl();return request.isForMainFrame()&&!("https".equals(uri.getScheme())&&"vuedevops.github.io".equals(uri.getHost())&&"/VueTV/live-preview.html".equals(uri.getPath()));
                    }
                    public void onReceivedError(WebView web,WebResourceRequest request,WebResourceError error){if(web==view&&request.isForMainFrame()&&request.getUrl().toString().startsWith(PAGE))fail("page unavailable");}
                    public boolean onRenderProcessGone(WebView web,android.webkit.RenderProcessGoneDetail detail){
                        if(view==web){view=null;stop();}if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);web.destroy();return true;
                    }
                });
                // Images stay underneath as a fallback; the existing shade and UI stay above the player.
                host.screen.addView(view,2,new FrameLayout.LayoutParams(-1,-1));
            }
            view.animate().cancel();view.setAlpha(0);view.onResume();
            if(view.getUrl()!=null&&view.getUrl().startsWith(PAGE)){
                view.evaluateJavascript("!!(window.vuePreviewSelect&&window.vuePreviewSelect("+JSONObject.quote(next)+"))",reused->{
                    if(run!=ticket||view==null||!host.active)return;
                    if(!"true".equals(reused))view.loadUrl(pageUrl(next));
                    beginPolling(next,run);
                });
            }else{view.loadUrl(pageUrl(next));beginPolling(next,run);}
        }catch(RuntimeException unavailable){fail("WebView unavailable");}
    }
    void beginPolling(String next,long run){
        started=SystemClock.elapsedRealtime();poll=()->check(next,run);host.main.postDelayed(poll,200);
        timeout=()->{if(run==ticket&&!playing)fail("playback timed out");};host.main.postDelayed(timeout,45000);
    }
    void check(String next,long run){
        if(run!=ticket||view==null||!host.active)return;
        try{view.evaluateJavascript("JSON.stringify(window.vuePreviewStatus||{})",encoded->{
            if(run!=ticket||view==null||!host.active)return;
            try{
                String raw=new JSONArray("["+encoded+"]").optString(0,"{}");JSONObject state=new JSONObject(raw);
                String status=state.optString("status");
                if(next.equals(state.optString("channel"))&&status.equals("playing")&&state.optBoolean("muted")&&state.optDouble("volume",1)==0){
                    if(!playing){playing=true;view.animate().alpha(1).setDuration(host.motionEnabled()?180:0).start();android.util.Log.i("VueLivePreview","Muted live preview playing after "+(SystemClock.elapsedRealtime()-started)+" ms");}
                }else if(status.equals("blocked")||status.equals("offline")||status.equals("error")||status.equals("ended")||(playing&&(status.equals("paused")||!state.optBoolean("muted")||state.optDouble("volume",1)!=0))){fail("playback unavailable");return;}
            }catch(Exception loading){/* The page may still be booting or changing documents. */}
            if(!playing&&SystemClock.elapsedRealtime()-started>45000){fail("playback timed out");return;}
            if(run==ticket)host.main.postDelayed(poll,playing?1000:200);
        });}catch(RuntimeException unavailable){fail("player unavailable");}
    }
    void fail(String reason){android.util.Log.i("VueLivePreview","Artwork fallback: "+reason);stop();}
    void stop(){
        cancelCallbacks();channel="";playing=false;
        if(view!=null){view.animate().cancel();view.setAlpha(0);view.stopLoading();view.loadUrl("about:blank");view.onPause();}
    }
    void cancelCallbacks(){
        ticket++;
        if(pending!=null)host.main.removeCallbacks(pending);pending=null;
        if(poll!=null)host.main.removeCallbacks(poll);poll=null;
        if(timeout!=null)host.main.removeCallbacks(timeout);timeout=null;
    }
    void destroy(){
        stop();if(view!=null){if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);view.destroy();view=null;}
    }
}
