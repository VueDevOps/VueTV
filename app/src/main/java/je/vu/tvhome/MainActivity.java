package je.vu.tvhome;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** A TV-first preview. All media rows are backed by actual account data. */
public class MainActivity extends Activity {
    final ExecutorService io = Executors.newFixedThreadPool(4);
    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService imageIo = Executors.newFixedThreadPool(3);
    final ExecutorService backdropIo=Executors.newSingleThreadExecutor();
    static final android.util.LruCache<String,Bitmap> backdrops=new android.util.LruCache<>(12*1024*1024){protected int sizeOf(String key,Bitmap value){return value.getByteCount();}};
    final Map<View,ScrollMotion> scrollMotions=new IdentityHashMap<>();
    final android.view.animation.Interpolator motionEase=new android.view.animation.PathInterpolator(.2f,0,0,1);
    float motionScale=1;
    ViewTreeObserver.OnPreDrawListener pendingAlignment;
    View alignmentTarget;
    Runnable pendingFeatured;
    int sceneTicket,backdropTicket;
    ImageView previousBackdrop;
    Bitmap lastBackdropSource;
    String lastBackdropKey="";
    static final android.util.LruCache<String, Bitmap> images = new android.util.LruCache<>(48 * 1024 * 1024) {
        protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    static final ConcurrentHashMap<String,Long> imageTimes=new ConcurrentHashMap<>();
    final Map<String,List<ImageView>> imageRequests=new HashMap<>();
    TimedDiskCache imageDisk, feedDisk;
    static final long DAY=24*60*60*1000L, MINUTE=60*1000L;
    long imageTtl;
    boolean forceFeedRefresh;
    String renderedFeed="";
    final ThreadLocal<Boolean> bypassFeedCache=ThreadLocal.withInitial(()->false);
    final Set<String> refreshingFeeds=ConcurrentHashMap.newKeySet();
    final ThreadLocal<Boolean> refreshRecentRequests=ThreadLocal.withInitial(()->false);
    boolean tintBackdrop;
    Media bannerMedia;
    LinearLayout picksRail;
    TextView picksHeading;
    String picksFingerprint="";
    List<Media> pendingPicks;
    LinearLayout latestRail;
    TextView latestHeading;
    String latestFingerprint="";
    List<Media> pendingLatest;
    String pendingLatestMessage;
    View movingApp;
    LinearLayout movingAppsRow;
    List<String> moveOriginalOrder;
    TextView moveHint;
    boolean rearrangingAppViews;
    Requests requests;
    TwitchSession twitchSession;
    LivePreview livePreview;
    android.content.SharedPreferences prefs;
    ProfileStore profiles;
    ProfilePicker profilePicker;
    String profileId;
    LinearLayout page, rows;
    FrameLayout hero;
    TextView heroTitle, heroSubtitle;
    ImageView heroImage;
    View backdropShade;
    static Bitmap backdropShading;
    FrameLayout screen;
    LinearLayout navigation;
    ScrollView homeScroll;
    TextView heroProvider, heroDescription, heroMeta;
    TextView watchButton;
    Media featured;
    int presentation;
    boolean rebuildingRows;
    final List<Media> featuredItems = new ArrayList<>();
    int featuredIndex;
    View carouselDots;
    String currentTab = "For you";
    boolean active;
    int generation;
    String deviceCode = "";
    long deviceExpiry;
    int pollInterval = 5;
    AlertDialog pairing;
    final Runnable refresh = new Runnable() {
        public void run() { if (active) { loadFeeds(true); main.postDelayed(this, MINUTE); } }
    };
    static class Media {
        String title, detail, image, provider, id;
        String requestType="",broadcasterId="";
        boolean topPick,metadataLoading,metadataLoaded;
        String tmdbId="",libraryMeta="",mediaType="Movie";
        double communityRating=-1,criticRating=-1;
        String meta="", overview="";
        Media(String t, String d, String i, String p, String key) { title=t; detail=d; image=i; provider=p; id=key; }
    }
    static class AppEntry {final String name,pkg;AppEntry(String name,String pkg){this.name=name;this.pkg=pkg;}}
    public void onCreate(Bundle state) {
        super.onCreate(state);
        profiles=new ProfileStore(this);profileId=profiles.active().id;prefs=profiles.preferences(profileId);profilePicker=new ProfilePicker(this);
        requests=new Requests(this);
        twitchSession=createTwitchSession();livePreview=new LivePreview(this);
        imageDisk=new TimedDiskCache(new File(getCacheDir(),profiles.cacheFolder("artwork",profileId)),128*1024*1024L);
        feedDisk=new TimedDiskCache(new File(getCacheDir(),profiles.cacheFolder("feeds",profileId)),4*1024*1024L);
        imageTtl=prefs.getLong("artworkTtl",DAY);
        if (!prefs.contains("device")) prefs.edit().putString("device", UUID.randomUUID().toString()).apply();
        getWindow().setFlags(1024,1024);
        draw();
    }
    protected void onResume() { super.onResume(); active=true;motionScale=android.provider.Settings.Global.getFloat(getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1); main.removeCallbacks(refresh);main.post(this::loadFeeds);main.postDelayed(refresh,MINUTE);main.post(()->{View focus=getCurrentFocus();if(focus!=null){restoreFocusedBackground(focus);alignFocus(focus);}}); }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasCategory(Intent.CATEGORY_HOME)){requests.closeDetails();currentTab="For you";draw();loadFeeds();}}
    protected void onPause() { super.onPause();finishAppMove(true);active=false;livePreview.pause(); main.removeCallbacks(refresh);stopMotion(); cancelPairing(); }
    protected void onDestroy() { if(livePreview!=null)livePreview.destroy();if(profilePicker!=null)profilePicker.close();if(requests!=null)requests.closeDetails();super.onDestroy(); generation++;stopMotion(); io.shutdownNow(); imageIo.shutdownNow();backdropIo.shutdownNow(); imageRequests.clear(); }
    int dp(float x) { return (int)(x*getResources().getDisplayMetrics().density+.5f); }
    int color(String s) { return Color.parseColor(s); }
    TextView text(String value, int size, String c) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,size); t.setTextColor(color(c)); t.setFontFeatureSettings("kern"); t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); t.setIncludeFontPadding(false); return t;
    }
    GradientDrawable background(String c, int radius) { GradientDrawable b=new GradientDrawable(); b.setColor(color(c)); b.setCornerRadius(dp(radius)); return b; }
    void focusable(View v, String base) {
        v.setFocusable(true); v.setClickable(true); v.setBackground(background(base,12));
        v.setOnFocusChangeListener((view, focused)-> {
            GradientDrawable b=background(base,12);
            if(focused) b.setStroke(dp(2),color("#D3E3EF"));
            view.setBackground(b);
            lift(view,focused,1.055f);
        });
    }
    TextView button(String label, Runnable click) {
        TextView b=text(label,16,"#F1F5F3"); b.setGravity(Gravity.CENTER); b.setPadding(dp(18),dp(11),dp(18),dp(11));
        focusable(b,"#23312F"); b.setOnClickListener(v->click.run()); return b;
    }
    void draw() {
        if(livePreview!=null)livePreview.stop();
        finishAppMove(true);stopMotion();generation++; presentation=0;renderedFeed="";lastBackdropKey="";lastBackdropSource=null;tintBackdrop=false;picksRail=null;picksHeading=null;picksFingerprint="";pendingPicks=null;latestRail=null;latestHeading=null;latestFingerprint="";pendingLatest=null;
        if(screen==null){screen=new FrameLayout(this);screen.setBackgroundColor(color("#0B0B0B"));setContentView(screen);}
        else for(int i=screen.getChildCount()-1;i>=0;i--)if(screen.getChildAt(i)!=livePreview.view)screen.removeViewAt(i);
        previousBackdrop=new BackdropImage();previousBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);screen.addView(previousBackdrop,0,new FrameLayout.LayoutParams(-1,-1));
        heroImage=new BackdropImage();heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);screen.addView(heroImage,1,new FrameLayout.LayoutParams(-1,-1));
        backdropShade=new View(this);
        if(backdropShading==null){
            backdropShading=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(backdropShading);Paint paint=new Paint();
            paint.setShader(new LinearGradient(0,0,1280,0,new int[]{color("#F20B0B0B"),color("#800B0B0B"),color("#180B0B0B")},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,1280,720,paint);
            paint.setShader(new LinearGradient(0,0,0,720,new int[]{Color.TRANSPARENT,color("#000B0B0B"),color("#F00B0B0B")},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,1280,720,paint);canvas.setBitmap(null);
        }
        android.graphics.drawable.BitmapDrawable shading=new android.graphics.drawable.BitmapDrawable(getResources(),backdropShading);shading.setGravity(Gravity.FILL);backdropShade.setBackground(shading);screen.addView(backdropShade,new FrameLayout.LayoutParams(-1,-1));
        homeScroll=new ScrollView(this){protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect){return 0;}};homeScroll.setBackgroundColor(Color.TRANSPARENT);homeScroll.setFillViewport(true);homeScroll.setClipChildren(false);homeScroll.setVerticalScrollBarEnabled(false);homeScroll.setSmoothScrollingEnabled(true);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setClipChildren(false);page.setPadding(0,dp(32),0,dp(50));homeScroll.addView(page);screen.addView(homeScroll,new FrameLayout.LayoutParams(-1,-1));
        navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER_VERTICAL);navigation.setClipChildren(false);navigation.setPadding(dp(58),0,dp(58),0);page.addView(navigation,new LinearLayout.LayoutParams(-1,dp(44)));
        FrameLayout avatar=new FrameLayout(this);avatar.setBackground(background(ProfilePicker.avatarColor(profileId),40));avatar.setClipToOutline(true);avatar.setContentDescription("Choose user: "+profiles.active().name);
        avatar.addView(new AvatarView(this,profiles.active()),new FrameLayout.LayoutParams(-1,-1));
        focusCircle(avatar);avatar.setOnClickListener(v->profilePicker.show());navigation.addView(avatar,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout pill=new LinearLayout(this);pill.setGravity(Gravity.CENTER_VERTICAL);pill.setClipChildren(false);pill.setClipToPadding(false);pill.setPadding(dp(4),dp(4),dp(4),dp(4));pill.setBackground(background("#553F474D",22));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(345),dp(44));pp.setMargins(dp(12),0,0,0);navigation.addView(pill,pp);
        View search=new HeaderIcon("search");search.setContentDescription("Search");focusCircle(search);search.setOnClickListener(v->{if(currentTab.equals("Requests"))requests.search();else search();});pill.addView(search,new LinearLayout.LayoutParams(dp(48),dp(36)));
        TextView home=navTab("Home",currentTab.equals("For you"),()->switchTab("For you"));pill.addView(home,new LinearLayout.LayoutParams(dp(68),dp(36)));
        TextView apps=navTab("Apps",currentTab.equals("Your apps"),()->switchTab("Your apps"));pill.addView(apps,new LinearLayout.LayoutParams(dp(63),dp(36)));
        TextView liveTab=navTab("Live",currentTab.equals("Live"),()->switchTab("Live"));liveTab.setContentDescription("Live: your followed Twitch channels");liveTab.setTooltipText("Your followed Twitch channels that are live");pill.addView(liveTab,new LinearLayout.LayoutParams(dp(58),dp(36)));
        TextView requestTab=navTab("Requests",currentTab.equals("Requests"),()->switchTab("Requests"));pill.addView(requestTab,new LinearLayout.LayoutParams(0,dp(36),1));
        LinearLayout quick=new LinearLayout(this);quick.setGravity(Gravity.CENTER_VERTICAL);quick.setClipChildren(false);quick.setClipToPadding(false);quick.setPadding(dp(4),dp(4),dp(4),dp(4));quick.setBackground(background("#55424B50",22));
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(dp(44),dp(44));qp.setMargins(dp(12),0,0,0);navigation.addView(quick,qp);
        View settings=new HeaderIcon("settings");settings.setContentDescription("Google TV quick settings");settings.setTooltipText("Google TV quick settings");focusCircle(settings);settings.setOnClickListener(v->openDeviceQuickSettings());quick.addView(settings,new LinearLayout.LayoutParams(dp(36),dp(36)));
        navigation.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        TextClock clock=new TextClock(this);clock.setFormat24Hour("HH:mm");clock.setFormat12Hour("HH:mm");clock.setTextColor(color("#C3CED2"));clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,16);clock.setIncludeFontPadding(false);navigation.addView(clock);
        TextView brand=text("  |  Vue TV",18,"#C3CED2");brand.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);navigation.addView(brand,new LinearLayout.LayoutParams(dp(102),-1));
        hero=new FrameLayout(this);hero.setClipChildren(false);hero.setContentDescription("Featured recommendation");hero.setFocusable(true);hero.setClickable(true);hero.setOnClickListener(v->{if(featured!=null)open(featured);else if(currentTab.equals("Live"))twitchSetup();else embyLogin();});
        hero.setId(View.generateViewId());home.setNextFocusDownId(hero.getId());hero.setNextFocusUpId(home.getId());
        hero.setOnFocusChangeListener((v,f)->{if(f){setPresentation(1);showBannerBackground();}});
        hero.setOnKeyListener((v,key,e)->{if(e.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_LEFT)&&!featuredItems.isEmpty()){int previous=featuredIndex;featuredIndex=(featuredIndex+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:featuredItems.size()-1))%featuredItems.size();bannerMedia=featuredItems.get(featuredIndex);carouselDots.invalidate();queueFeatured(bannerMedia,true);if(previous!=featuredIndex)playNavigationSound(v,key);}return e.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_LEFT);});
        page.addView(hero,new LinearLayout.LayoutParams(-1,dp(220)));
        heroProvider=text("EMBY",22,"#E1F2FD");heroProvider.setTypeface(null,Typeface.BOLD);place(hero,heroProvider,58,65,440,30);
        heroTitle=text("Your next great watch",52,"#FFFFFF");heroTitle.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));heroTitle.setMaxLines(2);heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);place(hero,heroTitle,56,100,620,80);
        heroSubtitle=text("Recommended for you",14,"#E1F2FD");place(hero,heroSubtitle,58,175,490,25);
        heroDescription=text("Connect your library to discover something to watch.",12,"#BFC2C5");heroDescription.setMaxLines(2);heroDescription.setEllipsize(android.text.TextUtils.TruncateAt.END);heroDescription.setVisibility(View.GONE);hero.addView(heroDescription);
        heroMeta=text("",12,"#E2E2E2");heroMeta.setVisibility(View.GONE);hero.addView(heroMeta);
        watchButton=text("Watch now",16,"#18212A");watchButton.setGravity(Gravity.CENTER);watchButton.setBackground(background("#E1F2FF",40));watchButton.setVisibility(View.GONE);hero.addView(watchButton);
        carouselDots=new View(this){final Paint ink=new Paint(3);protected void onDraw(Canvas c){for(int i=0;i<Math.min(3,featuredItems.size());i++){ink.setColor(i==featuredIndex?color("#F0F3F5"):color("#55E3E5E8"));c.drawCircle(dp(8+i*16),dp(9),dp(4),ink);}}};FrameLayout.LayoutParams dotsP=new FrameLayout.LayoutParams(dp(50),dp(18),Gravity.RIGHT|Gravity.BOTTOM);dotsP.setMargins(0,0,dp(58),dp(23));hero.addView(carouselDots,dotsP);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setClipChildren(false);page.addView(rows);
        if(currentTab.equals("Requests")){hero.setVisibility(View.GONE);showBannerBackground();requests.draw();}
        else if(currentTab.equals("Live"))placeholder("Live from your follows","Loading your followed channels...",this::twitchSetup);
        else if(currentTab.equals("Your apps")){hero.setVisibility(View.GONE);appShowcase();appRow(true);TextView appsTitle=(TextView)rows.getChildAt(0);appsTitle.setPadding(dp(58),dp(1),0,dp(10));appsTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,18);FrameLayout searchBar=new FrameLayout(this);TextView find=button("Search for apps and games",this::storeSearch);find.setGravity(Gravity.CENTER_VERTICAL);find.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,18);find.setPadding(dp(64),0,dp(20),0);find.setBackground(background("#13191E",40));searchBar.addView(find,new FrameLayout.LayoutParams(-1,-1));View glass=new HeaderIcon("search");FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(dp(40),dp(40));gp.leftMargin=dp(12);gp.topMargin=dp(10);searchBar.addView(glass,gp);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(60));fp.setMargins(dp(48),dp(2),dp(48),0);rows.addView(searchBar,fp);}
        else {placeholder("Top picks for you",hasEmby()?"Loading your library...":"Connect Emby for your recommendations",this::embyLogin);appRow(false);}
        if(bannerMedia!=null&&!currentTab.equals("Your apps")&&!currentTab.equals("Requests"))showFeatured(bannerMedia,false);
        (currentTab.equals("Requests")?requestTab:currentTab.equals("Your apps")?apps:currentTab.equals("Live")?liveTab:home).requestFocus();
        if(livePreview.view!=null)livePreview.ensureView();
    }
    void switchTab(String tab){currentTab=tab;featuredItems.clear();featuredIndex=0;bannerMedia=null;featured=null;draw();loadFeeds();}
    boolean motionEnabled(){return motionScale>0;}
    void lift(View view,boolean focused,float scale){if(!motionEnabled()){view.animate().cancel();view.setScaleX(focused?scale:1);view.setScaleY(focused?scale:1);return;}view.animate().scaleX(focused?scale:1).scaleY(focused?scale:1).setDuration(320).setInterpolator(motionEase).start();}
    void fade(View view,float alpha){if(!motionEnabled()){view.setAlpha(alpha);return;}view.animate().alpha(alpha).setDuration(320).setInterpolator(motionEase).start();}
    void motionVisibility(View view,boolean show){
        view.animate().cancel();
        if(!motionEnabled()){view.setVisibility(show?View.VISIBLE:View.INVISIBLE);view.setAlpha(show?1:0);return;}
        if(show){if(view.getVisibility()!=View.VISIBLE)view.setAlpha(0);view.setVisibility(View.VISIBLE);}
        view.animate().alpha(show?1:0).setDuration(260).setInterpolator(motionEase).withEndAction(()->{if(!show)view.setVisibility(View.INVISIBLE);}).start();
    }
    void stopMotion(){
        if(heroImage instanceof BackdropImage)((BackdropImage)heroImage).stopDrift();
        for(ScrollMotion motion:scrollMotions.values())motion.stop();scrollMotions.clear();sceneTicket++;backdropTicket++;lastBackdropKey="";lastBackdropSource=null;
        if(pendingFeatured!=null)main.removeCallbacks(pendingFeatured);pendingFeatured=null;
        if(homeScroll!=null&&pendingAlignment!=null&&homeScroll.getViewTreeObserver().isAlive())homeScroll.getViewTreeObserver().removeOnPreDrawListener(pendingAlignment);
        pendingAlignment=null;alignmentTarget=null;
    }
    class ScrollMotion implements Choreographer.FrameCallback {
        final View viewport;final boolean horizontal;final MotionSpring spring;boolean running;long lastFrame;
        ScrollMotion(View view,boolean horizontal){viewport=view;this.horizontal=horizontal;spring=new MotionSpring(horizontal?view.getScrollX():view.getScrollY());}
        int maximum(){ViewGroup group=(ViewGroup)viewport;if(group.getChildCount()==0)return 0;View content=group.getChildAt(0);return Math.max(0,horizontal?content.getWidth()+viewport.getPaddingLeft()+viewport.getPaddingRight()-viewport.getWidth():content.getHeight()+viewport.getPaddingTop()+viewport.getPaddingBottom()-viewport.getHeight());}
        void to(int value){int target=Math.max(0,Math.min(maximum(),value));if(!motionEnabled()){stop();spring.snap(target);apply(target);return;}if(!running)spring.snap(horizontal?viewport.getScrollX():viewport.getScrollY());spring.target(target);if(!running&&Math.abs(spring.position-target)<.5)return;if(!running){running=true;lastFrame=0;Choreographer.getInstance().postFrameCallback(this);}}
        void apply(int value){if(horizontal)viewport.scrollTo(value,viewport.getScrollY());else viewport.scrollTo(viewport.getScrollX(),value);}
        void stop(){running=false;lastFrame=0;Choreographer.getInstance().removeFrameCallback(this);}
        public void doFrame(long time){if(!running||!viewport.isAttachedToWindow()){stop();return;}double dt=lastFrame==0?1/60.0:(time-lastFrame)/1_000_000_000.0;lastFrame=time;spring.target(Math.max(0,Math.min(maximum(),spring.target)));boolean moving=spring.step(dt,22/Math.max(.75,motionScale));double bounded=Math.max(0,Math.min(maximum(),spring.position));if(bounded!=spring.position){spring.position=bounded;spring.velocity=0;}apply((int)Math.round(spring.position));if(moving)Choreographer.getInstance().postFrameCallback(this);else running=false;}
    }
    ScrollMotion scrolling(View view,boolean horizontal){ScrollMotion motion=scrollMotions.get(view);if(motion==null){motion=new ScrollMotion(view,horizontal);scrollMotions.put(view,motion);}return motion;}
    class SmoothRail extends HorizontalScrollView {
        SmoothRail(){super(MainActivity.this);}
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect rectangle){return 0;}
    }
    class CardRail extends LinearLayout {
        CardRail(){super(MainActivity.this);}
        void moveChild(View child,int position){int from=indexOfChild(child);if(from<0||from==position)return;ViewGroup.LayoutParams params=child.getLayoutParams();rearrangingAppViews=true;try{removeView(child);addView(child,position,params);}finally{rearrangingAppViews=false;}requestLayout();invalidate();}
    }
    int layoutPosition(View view,View ancestor,boolean horizontal){int position=0;View node=view;while(node!=ancestor){position+=horizontal?node.getLeft():node.getTop();if(!(node.getParent() instanceof View))break;View parent=(View)node.getParent();if(parent!=ancestor)position-=horizontal?parent.getScrollX():parent.getScrollY();node=parent;}return position;}
    boolean fullArtwork(Media media){return media.topPick||(currentTab.equals("Live")&&media.provider.equals("Twitch"));}
    void queueFeatured(Media media){queueFeatured(media,false);}
    void queueFeatured(Media media,boolean banner){if(pendingFeatured!=null)main.removeCallbacks(pendingFeatured);pendingFeatured=()->{pendingFeatured=null;if(active&&!isDestroyed()&&(banner||presentation==2||currentTab.equals("Requests"))){setBackdropTint(!banner&&!fullArtwork(media));showFeatured(media,false);if(media.topPick&&!banner)enrichTopPick(media);}};main.postDelayed(pendingFeatured,motionEnabled()?140:0);}
    void setBackdropTint(boolean tint){if(tintBackdrop!=tint){tintBackdrop=tint;if(heroImage instanceof BackdropImage)((BackdropImage)heroImage).stopDrift();backdropTicket++;lastBackdropKey="";lastBackdropSource=null;}}
    void showBannerBackground(){if(pendingFeatured!=null)main.removeCallbacks(pendingFeatured);pendingFeatured=null;setBackdropTint(false);if(currentTab.equals("Your apps")||currentTab.equals("Requests")){backdropTicket++;heroImage.setTag("neutral");heroImage.animate().cancel();heroImage.setImageDrawable(null);backdropShade.setAlpha(0);heroImage.setAlpha(1);previousBackdrop.setImageDrawable(null);}else if(bannerMedia!=null)showFeatured(bannerMedia,false);}
    void restoreFocusedBackground(View focus){if(focus.getTag() instanceof AppEntry){AppEntry app=(AppEntry)focus.getTag();try{showAppBackground(app.pkg,getPackageManager().getApplicationIcon(app.pkg));}catch(Exception ignored){}}else if(focus.getTag() instanceof Media){Media media=(Media)focus.getTag();setBackdropTint(!fullArtwork(media));showFeatured(media,false);}else showBannerBackground();}
    Map<View,Integer> scenePositions(){Map<View,Integer> result=new IdentityHashMap<>();if(!motionEnabled()||!screen.isLaidOut())return result;for(View view:new View[]{rows,heroTitle,heroProvider,heroSubtitle,heroDescription,heroMeta,watchButton})if(view.getVisibility()==View.VISIBLE){int[] location=new int[2];view.getLocationInWindow(location);result.put(view,location[1]);}return result;}
    void animateScene(Map<View,Integer> before){
        final int ticket=++sceneTicket;if(before.isEmpty())return;final FrameLayout root=screen;
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnPreDrawListener(this);if(ticket!=sceneTicket||isDestroyed())return true;for(Map.Entry<View,Integer> entry:before.entrySet()){View view=entry.getKey();if(view.getVisibility()!=View.VISIBLE)continue;int[] location=new int[2];view.getLocationInWindow(location);float start=view.getTranslationY()+entry.getValue()-location[1];if(Math.abs(start)>.5){view.setTranslationY(start);view.animate().translationY(0).setDuration(420).setInterpolator(motionEase).start();}}return true;}});
    }
    class BackdropImage extends ImageView {
        final Paint imagePaint=new Paint(Paint.FILTER_BITMAP_FLAG);final RectF destination=new RectF();
        android.animation.ValueAnimator drift;
        boolean artwork;float phase;
        BackdropImage(){super(MainActivity.this);}
        void stopDrift(){if(drift!=null){drift.cancel();drift=null;}}
        void transform(float phase){
            boolean moving=artwork&&motionEnabled();
            setScaleX(moving?1.055f+phase*.015f:1);setScaleY(moving?1.055f+phase*.015f:1);
            setTranslationX(moving?getWidth()*(phase-.5f)*.018f:0);setTranslationY(moving?getHeight()*(phase-.5f)*.008f:0);
            setCameraDistance(Math.max(getWidth(),getHeight())*8f);setRotationY(moving?(phase-.5f)*.6f:0);
        }
        void startDrift(){
            stopDrift();phase=0;transform(phase);
            if(!artwork||!active||!motionEnabled())return;
            // View transforms reuse Android's cached display list; the image is not redrawn each frame.
            drift=android.animation.ValueAnimator.ofFloat(0,1);drift.setDuration(24000);
            drift.setRepeatCount(android.animation.ValueAnimator.INFINITE);drift.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            drift.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            drift.addUpdateListener(value->{phase=(float)value.getAnimatedValue();transform(phase);});drift.start();
        }
        protected void onDetachedFromWindow(){stopDrift();super.onDetachedFromWindow();}
        protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);transform(phase);}
        protected void onDraw(Canvas canvas){Drawable drawable=getDrawable();if(drawable instanceof BitmapDrawable){destination.set(0,0,getWidth(),getHeight());canvas.drawBitmap(((BitmapDrawable)drawable).getBitmap(),null,destination,imagePaint);}}
    }
    void applyImage(ImageView view,Bitmap bitmap,String key){
        if(view!=heroImage){if(!(view.getDrawable() instanceof BitmapDrawable)||((BitmapDrawable)view.getDrawable()).getBitmap()!=bitmap)view.setImageBitmap(bitmap);return;}
        final boolean wash=tintBackdrop;String styledKey=key+(wash?":wash":":art-depth");
        if(styledKey.equals(lastBackdropKey)&&bitmap==lastBackdropSource)return;lastBackdropKey=styledKey;lastBackdropSource=bitmap;
        final int ticket=++backdropTicket;final ImageView target=heroImage;final String composedKey=styledKey+":"+System.identityHashCode(bitmap);
        Bitmap ready=backdrops.get(composedKey);if(ready!=null){revealBackdrop(target,ready,ticket);return;}
        backdropIo.execute(()->{try{
            int width=1280,height=720;Bitmap composed=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(composed);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            if(wash)paintColorWash(canvas,paint,bitmap,width,height);else{
            float scale=Math.max(width/(float)bitmap.getWidth(),height/(float)bitmap.getHeight());float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;canvas.drawBitmap(bitmap,null,new RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),paint);
            }
            canvas.setBitmap(null);composed.setHasAlpha(false);backdrops.put(composedKey,composed);
            main.post(()->{if(!isDestroyed()&&ticket==backdropTicket&&target==heroImage)revealBackdrop(target,composed,ticket);});
        }catch(Exception failure){android.util.Log.e("VueMotion","Backdrop composition failed",failure);}});
    }
    void paintColorWash(Canvas canvas,Paint paint,Bitmap source,int width,int height){int[] samples=new int[24*16];for(int y=0;y<16;y++)for(int x=0;x<24;x++)samples[y*24+x]=source.getPixel(Math.min(source.getWidth()-1,x*source.getWidth()/24),Math.min(source.getHeight()-1,y*source.getHeight()/16));int accent=ArtworkColors.accent(samples);paint.setShader(new LinearGradient(0,0,width,height,new int[]{ArtworkColors.darken(accent,.18),ArtworkColors.darken(accent,.055)},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,width,height,paint);paint.setShader(new RadialGradient(width*.23f,height*.32f,width*.8f,new int[]{ArtworkColors.darken(accent,.26),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,width,height,paint);}
    void showAppBackground(String pkg,Drawable icon){
        if(pendingFeatured!=null)main.removeCallbacks(pendingFeatured);pendingFeatured=null;setBackdropTint(true);String key="app-wash:"+pkg;if(key.equals(lastBackdropKey))return;lastBackdropKey=key;lastBackdropSource=null;heroImage.setTag(key);final int ticket=++backdropTicket;final ImageView target=heroImage;Bitmap cached=backdrops.get(key);if(cached!=null){revealBackdrop(target,cached,ticket);return;}
        Drawable copy=icon.getConstantState()==null?icon:icon.getConstantState().newDrawable(getResources()).mutate();backdropIo.execute(()->{try{Bitmap sample=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);Canvas sampleCanvas=new Canvas(sample);copy.setBounds(0,0,64,64);copy.draw(sampleCanvas);sampleCanvas.setBitmap(null);Bitmap composed=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(composed);paintColorWash(canvas,new Paint(Paint.ANTI_ALIAS_FLAG),sample,1280,720);canvas.setBitmap(null);composed.setHasAlpha(false);backdrops.put(key,composed);main.post(()->{if(!isDestroyed()&&ticket==backdropTicket&&target==heroImage)revealBackdrop(target,composed,ticket);});}catch(Exception ignored){}});
    }
    void revealBackdrop(ImageView target,Bitmap bitmap,int ticket){
        if(ticket!=backdropTicket||target!=heroImage)return;
        backdropShade.animate().cancel();backdropShade.setAlpha(tintBackdrop?0:1);
        BackdropImage moving=(BackdropImage)target;
        if(target.getDrawable() instanceof BitmapDrawable&&((BitmapDrawable)target.getDrawable()).getBitmap()==bitmap){moving.artwork=!tintBackdrop;moving.startDrift();target.animate().cancel();target.setAlpha(1);previousBackdrop.setImageDrawable(null);return;}
        target.animate().cancel();boolean fade=motionEnabled()&&target.getDrawable()!=null;
        ((BackdropImage)previousBackdrop).artwork=moving.artwork;((BackdropImage)previousBackdrop).phase=moving.phase;((BackdropImage)previousBackdrop).transform(moving.phase);
        previousBackdrop.setImageDrawable(fade?target.getDrawable():null);moving.artwork=!tintBackdrop;target.setImageBitmap(bitmap);moving.startDrift();target.setAlpha(fade?0:1);
        if(fade)target.animate().alpha(1).setDuration(360).setInterpolator(motionEase).withEndAction(()->{if(ticket==backdropTicket)previousBackdrop.setImageDrawable(null);}).start();
    }
    void collectFocusable(View view,List<View> result){
        if(view.getVisibility()!=View.VISIBLE||!view.isEnabled())return;
        // Scroll containers can report focusable; the actionable cards form the row.
        if(view.isFocusable()&&(!(view instanceof ViewGroup)||view.isClickable())){result.add(view);return;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectFocusable(group.getChildAt(i),result);}
    }
    void addFocusGroup(View view,List<List<View>> groups){List<View> items=new ArrayList<>();collectFocusable(view,items);if(!items.isEmpty())groups.add(items);}
    List<List<View>> focusGroups(){
        List<List<View>> result=new ArrayList<>();addFocusGroup(navigation,result);
        if(hero.getVisibility()==View.VISIBLE)addFocusGroup(hero,result);
        for(int i=0;i<page.getChildCount();i++){
            View section=page.getChildAt(i);if(section==navigation||section==hero)continue;
            if(section==rows){for(int j=0;j<rows.getChildCount();j++)addFocusGroup(rows.getChildAt(j),result);}
            else addFocusGroup(section,result);
        }return result;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(movingApp!=null){
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN)moveAppBy(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(event.getAction()==KeyEvent.ACTION_UP)finishAppMove(true);return true;}
            if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)finishAppMove(false);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)&&navigation!=null){
            View focused=getCurrentFocus();List<List<View>> groups=focusGroups();
            for(int row=1;row<groups.size();row++){int column=groups.get(row).indexOf(focused);if(column<0||focused==hero)continue;int next=column+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);if(next>=0&&next<groups.get(row).size()){View target=groups.get(row).get(next);if(!target.requestFocus())target.requestFocusFromTouch();if(target.hasFocus())playNavigationSound(target,key);alignFocus(target);}return true;}
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_UP)&&navigation!=null){
            View focused=getCurrentFocus();List<List<View>> groups=focusGroups();
            for(int row=0;row<groups.size();row++){
                int column=groups.get(row).indexOf(focused);if(column<0)continue;
                int next=row+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);
                if(next<0||next>=groups.size())return true;
                List<View> targetRow=groups.get(next);int targetColumn=0;
                if(next==0){for(int i=0;i<targetRow.size();i++)if(targetRow.get(i) instanceof TextView&&((TextView)targetRow.get(i)).getText().toString().equals(currentTab.equals("Requests")?"Requests":currentTab.equals("Your apps")?"Apps":currentTab.equals("Live")?"Live":"Home"))targetColumn=i;}
                View target=targetRow.get(targetColumn);
                if(next>0&&target!=hero&&!currentTab.equals("Your apps"))setPresentation(2);
                if(!target.requestFocus())target.requestFocusFromTouch();
                if(target.hasFocus())playNavigationSound(target,key);
                alignFocus(target);android.util.Log.d("VueNav","focus row="+next+" column="+targetColumn);return true;
            }
        }return super.dispatchKeyEvent(event);
    }
    // Custom DPAD handling bypasses ViewRootImpl's focus sound. Use the same
    // device effects as Google TV; framework-handled navigation plays its own.
    void playNavigationSound(View target,int key){
        int direction=key==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:key==KeyEvent.KEYCODE_DPAD_RIGHT?View.FOCUS_RIGHT:key==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:View.FOCUS_DOWN;
        target.playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
        android.util.Log.d("VueSound","Navigation effect direction="+direction);
    }
    void alignFocus(View target){
        alignmentTarget=target;if(pendingAlignment!=null)return;
        final ScrollView viewport=homeScroll;
        pendingAlignment=new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(viewport.getViewTreeObserver().isAlive())viewport.getViewTreeObserver().removeOnPreDrawListener(this);pendingAlignment=null;View selected=alignmentTarget;if(isDestroyed()||viewport!=homeScroll||selected==null||!selected.hasFocus())return true;
            if(selected==hero||isDescendant(selected,navigation)){scrolling(viewport,false).to(0);return true;}
            scrolling(viewport,false).to(layoutPosition(selected,viewport,false)-dp(358));
            for(ViewParent parent=selected.getParent();parent instanceof View;parent=parent.getParent())if(parent instanceof SmoothRail){SmoothRail rail=(SmoothRail)parent;int left=layoutPosition(selected,rail,true),right=left+selected.getWidth(),position=rail.getScrollX();if(left<position+rail.getPaddingLeft())position=left-rail.getPaddingLeft();else if(right>position+rail.getWidth()-rail.getPaddingRight())position=right-rail.getWidth()+rail.getPaddingRight();scrolling(rail,true).to(position);break;}
            return true;}};
        viewport.getViewTreeObserver().addOnPreDrawListener(pendingAlignment);viewport.invalidate();
    }
    void appShowcase(){
        heroImage.setImageDrawable(null);screen.setBackgroundColor(color("#0D0F12"));
        HorizontalScrollView showcase=new SmoothRail();showcase.setHorizontalScrollBarEnabled(false);showcase.setClipToPadding(false);showcase.setClipChildren(false);showcase.setPadding(dp(58),dp(20),dp(58),dp(24));
        LinearLayout cards=new LinearLayout(this);cards.setClipChildren(false);showcase.addView(cards);page.addView(showcase,page.indexOfChild(rows),new LinearLayout.LayoutParams(-1,dp(236)));
        String[][] apps={{"Emby","tv.emby.embyatv"},{"Netflix","com.netflix.ninja"},{"YouTube","com.google.android.youtube.tv"},{"Twitch","tv.twitch.android.app"}};
        for(String[] app:apps){FrameLayout card=new FrameLayout(this);card.setBackground(background("#1D242D",24));card.setClipToOutline(true);card.setContentDescription("Open "+app[0]);card.setFocusable(true);card.setClickable(true);
            ImageView banner=new ImageView(this);banner.setScaleType(ImageView.ScaleType.CENTER_CROP);banner.setBackgroundColor(color("#18202B"));
            try{Drawable art=getPackageManager().getApplicationBanner(app[1]);if(art==null)art=getPackageManager().getApplicationIcon(app[1]);banner.setImageDrawable(art);}catch(Exception ignored){}
            card.addView(banner,new FrameLayout.LayoutParams(-1,-1));card.setOnClickListener(v->launch(app[1]));card.setOnFocusChangeListener((v,f)->{lift(card,f,1.025f);if(f)alignFocus(card);});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(412),dp(192));p.setMargins(0,0,dp(20),0);cards.addView(card,p);
        }
    }
    void storeSearch(){EditText field=new EditText(this);field.setSingleLine(true);field.setHint("App or game name");new AlertDialog.Builder(this).setTitle("Search for apps and games").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Search",(d,n)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://search?q="+Uri.encode(field.getText().toString()))));}catch(ActivityNotFoundException e){toast("Play Store is unavailable");}}).show();}
    void place(FrameLayout parent,View child,int x,int y,int width,int height){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(width),dp(height));p.leftMargin=dp(x);p.topMargin=dp(y);if(child.getParent()==null)parent.addView(child,p);else child.setLayoutParams(p);}
    TextView navTab(String name,boolean selected,Runnable action){
        TextView t=text(name,14,"#E1E5E9");t.setId(View.generateViewId());t.setGravity(Gravity.CENTER);t.setDefaultFocusHighlightEnabled(false);t.setFocusableInTouchMode(true);t.setFocusable(true);t.setClickable(true);t.setBackground(background(selected?"#334A555D":"#004A555D",18));
        t.setOnClickListener(v->action.run());t.setOnFocusChangeListener((v,f)->{t.setBackground(background(f?"#D3E3EF":selected?"#334A555D":"#004A555D",18));t.setTextColor(color(f?"#101820":"#E1E5E9"));if(f&&!rearrangingAppViews){setPresentation(0);showBannerBackground();scrolling(homeScroll,false).to(0);}});return t;
    }
    void focusCircle(View view){view.setDefaultFocusHighlightEnabled(false);view.setBackground(background("#174B555D",view instanceof HeaderIcon?18:20));view.setFocusable(true);view.setClickable(true);view.setOnFocusChangeListener((v,f)->{v.setBackground(background(f?"#D3E3EF":"#174B555D",v instanceof HeaderIcon?18:20));if(v instanceof HeaderIcon)((HeaderIcon)v).focused=f;v.invalidate();if(f&&!rearrangingAppViews){setPresentation(0);showBannerBackground();scrolling(homeScroll,false).to(0);}});}
    class HeaderIcon extends View {
        final String kind;boolean focused;final Paint ink=new Paint(3);final Drawable googleIcon;
        HeaderIcon(String kind){super(MainActivity.this);this.kind=kind;googleIcon=GoogleTvIcons.load(MainActivity.this,kind);}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);if(googleIcon!=null){int size=dp(kind.equals("search")?22:18),x=(getWidth()-size)/2,y=(getHeight()-size)/2;googleIcon.setBounds(x,y,x+size,y+size);googleIcon.setTint(color(focused?"#17222A":"#E1EAF2"));googleIcon.draw(canvas);return;}canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);canvas.scale(dp(1),dp(1));ink.setColor(color(focused?"#17222A":"#E1EAF2"));ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.6f);ink.setStrokeCap(Paint.Cap.ROUND);
            if(kind.equals("search")){canvas.drawCircle(-1.5f,-1.5f,4.5f,ink);canvas.drawLine(2,2,6,6,ink);}
            else {canvas.drawCircle(0,0,5,ink);canvas.drawCircle(0,0,1.7f,ink);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine((float)Math.cos(a)*5,(float)Math.sin(a)*5,(float)Math.cos(a)*7,(float)Math.sin(a)*7,ink);}}
            canvas.restore();}
    }
    void setPresentation(int mode){
        if(hero==null||presentation==mode||currentTab.equals("Your apps")||currentTab.equals("Requests"))return;Map<View,Integer> before=scenePositions();presentation=mode;
        fade(navigation,mode==2?0:mode==1?.65f:1);
        LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)hero.getLayoutParams();p.height=dp(mode==1?387:mode==2?247:220);hero.setLayoutParams(p);
        carouselDots.setVisibility(mode==2?View.GONE:View.VISIBLE);FrameLayout.LayoutParams dotParams=(FrameLayout.LayoutParams)carouselDots.getLayoutParams();dotParams.bottomMargin=dp(mode==1?30:23);carouselDots.setLayoutParams(dotParams);
        for(int i=0;i<rows.getChildCount();i++)if(rows.getChildAt(i) instanceof TextView){TextView heading=(TextView)rows.getChildAt(i);heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,mode==0?12:18);heading.setPadding(dp(58),dp(i==0?4:mode==2?18:7),0,dp(10));LinearLayout.LayoutParams hp=(LinearLayout.LayoutParams)heading.getLayoutParams();hp.height=dp(i==0?(mode==2?34:31):(mode==2?46:28));heading.setLayoutParams(hp);}
        motionVisibility(heroDescription,mode!=0);motionVisibility(watchButton,mode==1);motionVisibility(heroMeta,mode==2);
        if(mode==2){place(hero,heroTitle,58,47,620,75);place(hero,heroProvider,58,130,430,22);heroProvider.setText(featured==null?"Emby":featured.provider);heroProvider.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,14);place(hero,heroDescription,58,162,432,44);place(hero,heroMeta,58,207,430,25);heroSubtitle.setVisibility(View.GONE);}
        else {heroProvider.setText(featured==null?"EMBY":featured.provider.toUpperCase(Locale.ROOT));heroProvider.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,22);place(hero,heroProvider,58,mode==1?115:65,440,30);place(hero,heroTitle,56,mode==1?150:100,620,80);place(hero,heroSubtitle,58,mode==1?225:175,490,25);heroSubtitle.setVisibility(View.VISIBLE);place(hero,heroDescription,58,258,420,44);place(hero,watchButton,53,310,120,44);}
        animateScene(before);if(mode!=2)homeScroll.post(()->{if(presentation==mode)scrolling(homeScroll,false).to(0);});
    }
    void showFeatured(Media media,boolean fromCard){
        featured=media;livePreview.show(media);watchButton.setText(media.requestType.isEmpty()?"Watch now":"View details");heroTitle.setText(media.title);heroTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,media.title.length()>32?34:media.title.length()>22?42:52);heroDescription.setText(media.overview.isEmpty()?media.detail:media.overview);heroMeta.setText(media.provider.equals("Twitch")?"Live now":styledHeroMetadata(media));heroSubtitle.setText(media.provider.equals("Twitch")?"Live from your follows":"Recommended for you");heroProvider.setText(presentation==2?media.provider:media.provider.toUpperCase(Locale.ROOT));loadImage(heroImage,media.image.replace("MaxWidth=960",tintBackdrop?"MaxWidth=480":"MaxWidth=1920"),media.provider.equals("Emby"));
        carouselDots.invalidate();if(fromCard)setPresentation(2);
    }
    void search(){EditText field=new EditText(this);field.setSingleLine(true);field.setHint("Search your Emby library");new AlertDialog.Builder(this).setTitle("Search").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Search",(d,n)->{String query=field.getText().toString().trim();if(query.isEmpty())return;io.execute(()->{try{List<Media> found=embyItems("/Users/"+prefs.getString("embyUser","")+"/Items?Recursive=true&IncludeItemTypes=Movie,Episode&SearchTerm="+Uri.encode(query)+"&Limit=24&Fields=Overview",true);main.post(()->{rows.removeAllViews();mediaRow("Search results","",found,"No matches in your library");});}catch(Exception e){main.post(()->toast("Search unavailable. Connect Emby first."));}});}).show();}
    void title(String name, String subtitle) {
        TextView h=text(name,12,"#A8B0B5");h.setPadding(dp(58),dp(rows.getChildCount()==0?4:7),0,dp(10));rows.addView(h,new LinearLayout.LayoutParams(-1,dp(rows.getChildCount()==0?31:28)));
    }
    LinearLayout rail() {
        HorizontalScrollView scroll=new SmoothRail();scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setHorizontalScrollBarEnabled(false);scroll.setPadding(dp(58),dp(1),dp(58),dp(6));
        LinearLayout row=new CardRail();row.setClipChildren(false);scroll.addView(row);rows.addView(scroll);return row;
    }
    void appRow(boolean all) {
        title("Your apps",""); LinearLayout row=rail();
        String[] defaults={"com.google.android.youtube.tv","tv.emby.embyatv","com.netflix.ninja","com.esaba.downloader","tv.twitch.android.app","com.s0und.s0undtv","com.kick.mobile","com.viaplay.android","com.internet.tvbrowser","com.spocky.projengmenu"};
        List<ResolveInfo> installed=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0);installed.sort(Comparator.comparing(info->info.loadLabel(getPackageManager()).toString()));
        Map<String,AppEntry> entries=new LinkedHashMap<>();for(ResolveInfo info:installed)if(!info.activityInfo.packageName.equals(getPackageName()))entries.putIfAbsent(info.activityInfo.packageName,new AppEntry(info.loadLabel(getPackageManager()).toString(),info.activityInfo.packageName));
        List<String> available=new ArrayList<>();for(String pkg:defaults)if(entries.containsKey(pkg))available.add(pkg);for(String pkg:entries.keySet())if(!available.contains(pkg))available.add(pkg);
        List<String> saved=new ArrayList<>();try{JSONArray json=new JSONArray(prefs.getString("appOrder","[]"));for(int i=0;i<json.length();i++)saved.add(json.getString(i));}catch(JSONException ignored){}
        for(String pkg:AppOrder.arrange(saved,available)){AppEntry app=entries.get(pkg);appCard(row,app.name,app.pkg,"#AAAAAA");}
    }
    void appCard(LinearLayout row,String name,String pkg,String base) {
        FrameLayout card=new FrameLayout(this);card.setClipChildren(false);card.setFocusable(true);card.setClickable(true);card.setContentDescription(name);card.setBackground(background("#00000000",60));
        FocusGlow glow=new FocusGlow(this,true);FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(dp(116),dp(116),Gravity.TOP|Gravity.CENTER_HORIZONTAL);gp.topMargin=dp(-16);card.addView(glow,gp);
        AppEntry entry=new AppEntry(name,pkg);card.setTag(entry);card.setOnLongClickListener(v->{showAppActions(row,card,entry);return true;});
        ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setBackground(background(base,60));icon.setClipToOutline(true);
        icon.setAlpha(currentTab.equals("Your apps")?1:.68f);
        try{Drawable appIcon=getPackageManager().getApplicationIcon(pkg);icon.setImageDrawable(appIcon);}catch(Exception e){icon.setImageResource(android.R.drawable.sym_def_app_icon);}
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(76),dp(76),Gravity.TOP|Gravity.CENTER_HORIZONTAL);ip.topMargin=dp(4);card.addView(icon,ip);
        TextView label=text(name,12,"#E0E6EA");label.setGravity(Gravity.CENTER);label.setMaxLines(1);label.setVisibility(View.INVISIBLE);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(22),Gravity.BOTTOM);card.addView(label,lp);
        card.setOnClickListener(v->{if(movingApp!=null)finishAppMove(true);else launch(pkg);});card.setOnFocusChangeListener((v,f)->{fade(glow,f?1:0);motionVisibility(label,f);fade(icon,f||currentTab.equals("Your apps")?1:.68f);lift(icon,f,1.08f);if(f&&!rearrangingAppViews){if(!currentTab.equals("Your apps"))setPresentation(2);showAppBackground(pkg,icon.getDrawable());fade(navigation,.55f);alignFocus(card);}});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(76),dp(102));p.setMargins(0,0,dp(15),0);row.addView(card,p);
    }
    void showAppActions(LinearLayout row,View card,AppEntry app){new AlertDialog.Builder(this).setTitle(app.name).setItems(new String[]{"Move","Open","App info"},(dialog,choice)->{if(choice==0)beginAppMove(row,card,app);if(choice==1)launch(app.pkg);if(choice==2)startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.pkg)));}).setNegativeButton("Cancel",null).show();}
    List<String> appPackages(LinearLayout row){List<String> result=new ArrayList<>();for(int i=0;i<row.getChildCount();i++)if(row.getChildAt(i).getTag() instanceof AppEntry)result.add(((AppEntry)row.getChildAt(i).getTag()).pkg);return result;}
    void beginAppMove(LinearLayout row,View card,AppEntry app){
        movingApp=card;movingAppsRow=row;moveOriginalOrder=appPackages(row);card.requestFocusFromTouch();android.util.Log.d("VueApps","Move started");GradientDrawable highlight=background("#243A4B",14);highlight.setStroke(dp(2),color("#D3E3EF"));card.setBackground(highlight);
        moveHint=text("Move "+app.name+"  ·  Left / Right  ·  OK to save  ·  Back to cancel",14,"#E1F2FF");moveHint.setGravity(Gravity.CENTER);moveHint.setPadding(dp(16),dp(10),dp(16),dp(10));moveHint.setBackground(background("#E51B2832",12));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);p.bottomMargin=dp(22);screen.addView(moveHint,p);alignFocus(card);
    }
    void moveAppBy(int direction){
        if(!(movingAppsRow instanceof CardRail)||movingApp==null)return;int old=movingAppsRow.indexOfChild(movingApp),next=old+direction;if(next<0||next>=movingAppsRow.getChildCount())return;
        Map<View,Float> positions=new IdentityHashMap<>();for(int i=0;i<movingAppsRow.getChildCount();i++){View child=movingAppsRow.getChildAt(i);positions.put(child,child.getX());}
        ((CardRail)movingAppsRow).moveChild(movingApp,next);movingApp.requestFocusFromTouch();playNavigationSound(movingApp,direction>0?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT);android.util.Log.d("VueApps","Moved app "+old+" -> "+next);final LinearLayout row=movingAppsRow;
        row.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(row.getViewTreeObserver().isAlive())row.getViewTreeObserver().removeOnPreDrawListener(this);for(Map.Entry<View,Float> item:positions.entrySet()){View view=item.getKey();if(view.getParent()!=row)continue;float delta=item.getValue()-view.getLeft();if(motionEnabled()&&Math.abs(delta)>.5){view.setTranslationX(delta);view.animate().translationX(0).setDuration(320).setInterpolator(motionEase).start();}else view.setTranslationX(0);}return true;}});alignFocus(movingApp);
    }
    void finishAppMove(boolean save){
        if(movingApp==null)return;View selected=movingApp;LinearLayout row=movingAppsRow;
        if(save)prefs.edit().putString("appOrder",new JSONArray(appPackages(row)).toString()).apply();
        else if(row instanceof CardRail){for(int i=0;i<moveOriginalOrder.size();i++){String pkg=moveOriginalOrder.get(i);for(int j=0;j<row.getChildCount();j++){View child=row.getChildAt(j);if(child.getTag() instanceof AppEntry&&((AppEntry)child.getTag()).pkg.equals(pkg)){((CardRail)row).moveChild(child,i);break;}}}}
        movingApp=null;movingAppsRow=null;moveOriginalOrder=null;selected.setBackground(background("#00000000",60));if(moveHint!=null&&moveHint.getParent() instanceof ViewGroup)((ViewGroup)moveHint.getParent()).removeView(moveHint);moveHint=null;selected.requestFocusFromTouch();alignFocus(selected);android.util.Log.d("VueApps",save?"App order saved":"Move cancelled");if(active)toast(save?"App order saved":"Move cancelled");
    }
    void emptyFeeds() {
        placeholder("Continue watching",hasEmby()?"Loading your Emby library…":"Connect Emby for your actual watch progress.",this::embyLogin);
        placeholder("Top picks for you",hasEmby()?"Finding unwatched films in your library…":"Picks will come from your Emby library. Netflix and YouTube recommendations stay inside their apps.",this::embyLogin);
        placeholder("Live from your follows",hasTwitch()?"Checking Twitch…":"Connect Twitch to see only the people you follow, when they are live.",this::twitchSetup);
    }
    void placeholder(String name,String message,Runnable setup) {
        title(name,"");LinearLayout row=rail();TextView t=button(message,setup);t.setGravity(Gravity.CENTER_VERTICAL);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,13);row.addView(t,new LinearLayout.LayoutParams(dp(500),dp(86)));
    }
    void launch(String pkg) {
        Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if(i==null)i=getPackageManager().getLaunchIntentForPackage(pkg);
        if(i==null&&pkg.equals("tv.emby.embyatv")) {launch("com.mb.android");return;}
        if(i==null) {toast("App is not installed");return;} startActivity(i);
    }
    boolean openLink(String pkg,String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));return true;}
        catch(ActivityNotFoundException|SecurityException unavailable){return false;}
    }
    void open(Media m) {
        if(!m.requestType.isEmpty()){requests.details(m);return;}
        if(m.provider.equals("Twitch")) {
            // The installed S0undTV beta handles numeric channel links on cold/warm starts; category 5 is Followed.
            String link=m.broadcasterId.matches("[0-9]+")?"channel://com.s0und.s0undtv/startstream/"+m.broadcasterId+"/5":"s0undtv://stream/"+Uri.encode(m.id);
            if(!openLink("com.s0und.s0undtv",link))
                toast("Install or update S0undTV to open this channel.");
        }else{
            openEmby(m);
        }
    }
    void openEmby(Media media){
        // Android TV and the universal Android client use different URI contracts.
        if(openLink("tv.emby.embyatv","embyatv://tv.emby.embyatv/displayitem/"+Uri.encode(media.id)))return;
        io.execute(()->{try{
            JSONObject info=new JSONObject(cachedGet(server()+"/emby/System/Info/Public",Collections.emptyMap(),DAY));
            String id=info.getString("Id");
            main.post(()->{if(active&&!isDestroyed()&&!openLink("com.mb.android","emby://items/"+Uri.encode(id)+"/"+Uri.encode(media.id)))toast("No compatible Emby app is installed.");});
        }catch(Exception unavailable){main.post(()->toast("Unable to open this title in Emby. Check your connection."));}});
    }
    List<Media> selectPicks(List<Media> library,List<Media> discoveries){
        Set<String> owned=new HashSet<>();for(Media item:library)if(!item.tmdbId.isEmpty())owned.add((item.mediaType.equals("Series")?"tv":"movie")+":"+item.tmdbId);
        List<Media> fresh=new ArrayList<>();for(Media item:discoveries)if(!owned.contains(item.requestType+":"+item.id))fresh.add(item);
        long slot=System.currentTimeMillis()/(30*MINUTE);
        List<Media> a=PickRotation.select(library,m->m.id,profileId,slot,12);
        List<Media> b=PickRotation.select(fresh,m->m.requestType+":"+m.id,profileId,slot,library.isEmpty()?12:3);
        if(!b.isEmpty()&&a.size()>9)a=new ArrayList<>(a.subList(0,9));
        List<Media> result=new ArrayList<>();int i=0,j=0;
        while(i<a.size()||j<b.size()){for(int k=0;k<3&&i<a.size();k++)result.add(a.get(i++));if(j<b.size())result.add(b.get(j++));}
        return result;
    }
    void mediaRow(String name,String detail,List<Media> items,String empty) {
        title(name,"");TextView heading=(TextView)rows.getChildAt(rows.getChildCount()-1);LinearLayout row=rail();
        if(name.equals("Top picks for you")){picksRail=row;picksHeading=heading;picksFingerprint=feedFingerprint(items);}
        if(name.equals("Recently added")){latestRail=row;latestHeading=heading;latestFingerprint=feedFingerprint(items);}
        populateMediaCards(row,heading,name,items,empty);
    }
    void populateMediaCards(LinearLayout row,TextView heading,String name,List<Media> items,String empty){
        if(items.isEmpty()){TextView message=button(empty,name.startsWith("Live")?this::twitchSetup:this::embyLogin);message.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,13);row.addView(message,new LinearLayout.LayoutParams(dp(500),dp(86)));return;}
        for(Media m:items) {
            m.topPick=name.equals("Top picks for you");
            FrameLayout card=new FrameLayout(this);card.setContentDescription(m.title+", "+m.provider);card.setClipChildren(false);focusable(card,"#29313D");
            FocusGlow glow=m.topPick?null:new FocusGlow(this,false);if(glow!=null){FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(-1,-1);gp.setMargins(dp(-20),dp(-20),dp(-20),dp(-20));card.addView(glow,gp);}
            card.setTag(m);
            card.setId(View.generateViewId());if(row.getChildCount()==0&&(name.equals("Top picks for you")||currentTab.equals("Live"))){hero.setNextFocusDownId(card.getId());card.setNextFocusUpId(hero.getId());}
            if(name.equals("Top picks for you"))card.setNextFocusUpId(hero.getId());
            FrameLayout artwork=new FrameLayout(this);artwork.setBackground(background("#3E4B6A",12));artwork.setClipToOutline(true);card.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
            TextView fallback=text(m.title,16,"#D4E0ED");fallback.setGravity(Gravity.CENTER_VERTICAL);fallback.setMaxLines(2);fallback.setPadding(dp(10),0,dp(10),0);artwork.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.addView(image,new FrameLayout.LayoutParams(-1,-1));loadImage(image,m.image.replace("MaxWidth=960","MaxWidth=480"),m.provider.equals("Emby"));
            View dim=new View(this);dim.setBackgroundColor(color("#48000000"));dim.setTag("dim");artwork.addView(dim,new FrameLayout.LayoutParams(-1,-1));
            if(m.provider.equals("Twitch")){
                TextView badge=text("LIVE",9,"#FFFFFF");badge.setPadding(dp(4),dp(2),dp(4),dp(2));badge.setBackground(background("#D02020",3));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);bp.setMargins(dp(6),dp(6),0,0);artwork.addView(badge,bp);
                TextView streamer=text(m.title,11,"#FFFFFF");streamer.setSingleLine(true);streamer.setEllipsize(android.text.TextUtils.TruncateAt.END);streamer.setPadding(dp(6),dp(3),dp(6),dp(3));streamer.setBackground(background("#D9161820",4));streamer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);np.setMargins(dp(6),0,dp(6),dp(6));streamer.setMaxWidth(dp(141));artwork.addView(streamer,np);
            }
            View outline=new View(this);GradientDrawable border=background("#00000000",13);border.setStroke(dp(2),color("#B5C1CA"));outline.setBackground(border);outline.setAlpha(0);card.addView(outline,new FrameLayout.LayoutParams(-1,-1));
            FrameLayout slot=new FrameLayout(this);slot.setClipChildren(false);slot.addView(card,new FrameLayout.LayoutParams(-1,dp(86)));
            LinearLayout caption=new LinearLayout(this);caption.setOrientation(LinearLayout.VERTICAL);caption.setTag("caption");caption.setAlpha(0);caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            TextView captionTitle=text("",11,"#D3E3EF");captionTitle.setSingleLine(true);captionTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(captionTitle,new LinearLayout.LayoutParams(-1,dp(16)));
            String info=m.provider.equals("Twitch")?m.detail:m.meta;
            TextView captionDetail=text(info,10,"#A8B0B5");captionDetail.setSingleLine(true);captionDetail.setEllipsize(android.text.TextUtils.TruncateAt.END);captionDetail.setVisibility(View.INVISIBLE);caption.addView(captionDetail,new LinearLayout.LayoutParams(-1,dp(15)));
            FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(31));cp.topMargin=dp(94);slot.addView(caption,cp);
            card.setOnFocusChangeListener((v,f)->{if(glow!=null)fade(glow,f?1:0);captionTitle.setText(f?m.title:"");captionDetail.setVisibility(f&&!info.isEmpty()?View.VISIBLE:View.INVISIBLE);fade(outline,f?1:0);lift(card,f,1.055f);updateRowFocus(row,heading);if(f&&!rebuildingRows&&!rearrangingAppViews){setPresentation(2);queueFeatured(m);alignFocus(card);}});
            card.setOnClickListener(v->open(m));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(153),dp(125));p.setMargins(0,0,dp(20),0);row.addView(slot,p);
        }
    }
    void updateRowFocus(LinearLayout row,TextView heading){row.postOnAnimation(()->{boolean selected=row.hasFocus();if(!selected&&row==picksRail&&pendingPicks!=null){List<Media> waiting=pendingPicks;pendingPicks=null;updatePicksRow(waiting);}if(!selected&&row==latestRail&&pendingLatest!=null){List<Media> waiting=pendingLatest;String message=pendingLatestMessage;pendingLatest=null;updateRecentRow(waiting,message);}if(Boolean.valueOf(selected).equals(row.getTag()))return;row.setTag(selected);heading.setTextColor(color(selected?"#D3E3EF":"#A8B0B5"));for(int i=0;i<row.getChildCount();i++){View shade=row.getChildAt(i).findViewWithTag("dim");if(shade!=null)fade(shade,selected?0:1);View caption=row.getChildAt(i).findViewWithTag("caption");if(caption!=null)fade(caption,selected?1:0);}});}
    void updatePicksRow(List<Media> picks){
        if(picksRail==null||picksHeading==null||picks.isEmpty())return;
        String fingerprint=feedFingerprint(picks);if(fingerprint.equals(picksFingerprint))return;
        if(picksRail.hasFocus()){pendingPicks=new ArrayList<>(picks);return;}
        pendingPicks=null;picksFingerprint=fingerprint;picksRail.removeAllViews();
        populateMediaCards(picksRail,picksHeading,"Top picks for you",picks,"");
    }
    void updateRecentRow(List<Media> latest,String message){if(latestRail==null||latestHeading==null||message.startsWith("Emby:"))return;String fingerprint=feedFingerprint(latest);if(fingerprint.equals(latestFingerprint))return;if(latestRail.hasFocus()){pendingLatest=new ArrayList<>(latest);pendingLatestMessage=message;return;}latestFingerprint=fingerprint;latestRail.removeAllViews();populateMediaCards(latestRail,latestHeading,"Recently added",latest,message);android.util.Log.d("VueRecent","Recently added row updated without moving focus");}
    void loadImage(ImageView view,String url,boolean emby) {
        String key=imageKey(url,emby);view.setTag(key);if(url.isEmpty())return;
        final long ttl=emby?imageTtl:MINUTE;
        Bitmap cached=images.get(key);
        if(cached!=null){applyImage(view,cached,key);if(System.currentTimeMillis()-imageTimes.getOrDefault(key,0L)<ttl){android.util.Log.d("VueCache","image memory hit");return;}}
        else if(view==heroImage){Bitmap thumb=images.get(imageKey(url.replace("MaxWidth=1920","MaxWidth=480"),emby));if(thumb!=null)applyImage(view,thumb,key);}
        List<ImageView> pending=imageRequests.get(key);if(pending!=null){pending.add(view);return;}
        List<ImageView> waiting=new ArrayList<>();waiting.add(view);imageRequests.put(key,waiting);
        final String token=emby?prefs.getString("embyToken",""):"";
        imageIo.execute(()->{
            Bitmap bitmap=null;long written=0;
            try{
                byte[] bytes=imageDisk.get(key,ttl);
                if(bytes!=null){bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);written=imageDisk.writtenAt(key);if(bitmap!=null)android.util.Log.d("VueCache","image disk hit");}
                if(bitmap==null){
                    HttpURLConnection c=connection(url);if(emby)c.setRequestProperty("X-Emby-Token",token);
                    try(InputStream in=c.getInputStream()){bytes=readBytes(in,12*1024*1024);}finally{c.disconnect();}
                    bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                    if(bitmap!=null){imageDisk.put(key,bytes);written=System.currentTimeMillis();android.util.Log.d("VueCache","image network fetch");}
                }
            }catch(Exception e){byte[] stale=imageDisk.get(key,7*DAY);if(stale!=null){bitmap=BitmapFactory.decodeByteArray(stale,0,stale.length);written=imageDisk.writtenAt(key);}}
            final Bitmap result=bitmap;
            if(result!=null){images.put(key,result);imageTimes.put(key,written);}
            main.post(()->{List<ImageView> listeners=imageRequests.remove(key);if(isDestroyed()||listeners==null)return;for(ImageView target:listeners)if(result!=null&&key.equals(target.getTag()))applyImage(target,result,key);});
        });
    }
    String imageKey(String url,boolean emby){String scope=profileId.equals(ProfileStore.ORIGINAL)?"":profileId+"|";return TimedDiskCache.hash(scope+(emby?server()+"|"+prefs.getString("embyUser","")+"|"+prefs.getString("embyToken",""):"twitch|"+prefs.getString("twitchToken",""))+"|"+url);}
    byte[] readBytes(InputStream in,int limit)throws IOException{try(ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){out.write(buffer,0,count);if(out.size()>limit)throw new IOException("Response too large");}return out.toByteArray();}}
    String cachedGet(String url,Map<String,String> headers,long ttl)throws Exception{
        return cachedGet(url,headers,ttl,false);
    }
    String cachedGet(String url,Map<String,String> headers,long ttl,boolean force)throws Exception{
        String key=url+"|"+new TreeMap<>(headers).toString();
        boolean bypass=force||bypassFeedCache.get();
        if(!bypass){byte[] saved=feedDisk.get(key,ttl);if(saved!=null){android.util.Log.d("VueCache","feed disk hit");return new String(saved,StandardCharsets.UTF_8);}}
        // Home can draw the last snapshot immediately while expired lists refresh.
        boolean emby=url.startsWith(server()+"/emby/");
        if(!bypass&&emby){
            byte[] stale=feedDisk.get(key,emby?DAY:5*MINUTE);
            if(stale!=null){
                if(refreshingFeeds.add(key))io.execute(()->{try{
                    String updated=request(url,"GET",null,headers);feedDisk.put(key,updated.getBytes(StandardCharsets.UTF_8));android.util.Log.d("VueCache","feed background refresh");
                    if(!updated.equals(new String(stale,StandardCharsets.UTF_8)))main.post(()->{if(active&&!isDestroyed())loadFeeds();});
                }catch(Exception ignored){}finally{refreshingFeeds.remove(key);}});
                android.util.Log.d("VueCache","feed stale snapshot; refreshing in background");return new String(stale,StandardCharsets.UTF_8);
            }
        }
        String result=request(url,"GET",null,headers);feedDisk.put(key,result.getBytes(StandardCharsets.UTF_8));android.util.Log.d("VueCache","feed network fetch");return result;
    }
    boolean hasEmby(){return !prefs.getString("embyToken","").isEmpty();}
    boolean hasTwitch(){return !prefs.getString("twitchToken","").isEmpty();}
    String server(){return prefs.getString("embyServer","https://emby.example.com");}
    void loadFeeds() {
        loadFeeds(false);
    }
    void loadFeeds(boolean refreshRecent) {
        if(currentTab.equals("Requests")){requests.load(refreshRecent);return;}
        if(currentTab.equals("Your apps"))return;
        final int run=++generation;
        final long started=SystemClock.elapsedRealtime();
        final boolean live=currentTab.equals("Live");
        final boolean force=forceFeedRefresh;forceFeedRefresh=false;
        io.execute(()->{
            bypassFeedCache.set(force);
            refreshRecentRequests.set(refreshRecent);
            List<Media> resume=new ArrayList<>(),picks=new ArrayList<>(),latest=new ArrayList<>(),streams=new ArrayList<>();
            String embyError="Connect Emby for your library.", twitchError="Connect Twitch for live followed channels.";
            if(!live&&hasEmby())try {
                String user=prefs.getString("embyUser","");
                resume=embyItems("/Users/"+user+"/Items/Resume?Limit=12&Fields=Overview&MediaTypes=Video",false);
                latest=embyItems("/Users/"+user+"/Items/Latest?Limit=12&IncludeItemTypes=Movie,Episode&Fields=Overview",false);
                picks=embyItems("/Users/"+user+"/Items?Recursive=true&IncludeItemTypes=Movie,Series&IsPlayed=false&SortBy=CommunityRating&SortOrder=Descending&Limit=120&Fields=Overview,Genres,ProviderIds",true);
                embyError="Nothing here yet. Watch something in Emby and refresh.";
            }catch(Exception e){embyError="Emby: "+e.getMessage()+". Open Connections to sign in again.";}
            if(hasTwitch())try {streams=twitchStreams();twitchError="Nobody you follow is live right now. Refreshes every minute.";}catch(Exception e){twitchError=e instanceof TwitchSession.ReconnectRequired?e.getMessage():"Twitch is temporarily unavailable. Retrying automatically; check your connection.";}
            if(!live){
                List<Media> discoveries=new ArrayList<>();
                if(requests.connected())try{discoveries=requests.homeDiscoveries();}catch(Exception ignored){}
                picks=selectPicks(picks,discoveries);
            }
            final List<Media> r=resume,p=picks,l=latest,t=streams;
            final String ee=embyError,te=twitchError;
            bypassFeedCache.remove();
            refreshRecentRequests.remove();
            main.post(()->{
                if(isDestroyed()||run!=generation)return;
                android.util.Log.d("VueCache","feed ready in "+(SystemClock.elapsedRealtime()-started)+" ms");
                if(!live&&!t.isEmpty())main.post(()->{if(run==generation)livePreview.prepare(t.get(0));});
                View focused=getCurrentFocus();
                // Don't move focus out from under a person browsing a rail during an automatic refresh.
                if((focused!=null&&isDescendant(focused,rows)&&rows.getChildCount()>3)||movingApp!=null){updateRecentRow(l,ee);updatePicksRow(p);return;}
                String fingerprint=feedFingerprint(r,p,l,t)+ee+te;
                if(fingerprint.equals(renderedFeed))return;renderedFeed=fingerprint;
                // Start visible hero requests before scheduling thumbnails further down the page.
                if(!live&&!p.isEmpty()){
                    featuredItems.clear();featuredItems.addAll(p.subList(0,Math.min(3,p.size())));featuredIndex=Math.min(featuredIndex,featuredItems.size()-1);bannerMedia=featuredItems.get(featuredIndex);setBackdropTint(false);showFeatured(bannerMedia,false);
                    for(Media item:featuredItems)loadImage(new ImageView(this),item.image.replace("MaxWidth=960","MaxWidth=1920"),true);
                }
                if(live){featuredItems.clear();featuredItems.addAll(t.subList(0,Math.min(3,t.size())));featuredIndex=0;carouselDots.invalidate();if(!t.isEmpty()){bannerMedia=t.get(0);setBackdropTint(false);showFeatured(bannerMedia,false);}else{bannerMedia=null;featured=null;livePreview.stop();}}
                rebuildingRows=true;rows.removeAllViews();
                if(!live) {
                    mediaRow("Top picks for you","",p,ee);
                    appRow(false);
                    mediaRow("Continue watching","Your Emby progress",r,ee);
                    mediaRow("Recently added","New in your Emby library",l,ee);
                }
                mediaRow("Live from your follows","Twitch · updates every minute",t,te);
                rebuildingRows=false;
            });
        });
    }
    @SafeVarargs final String feedFingerprint(List<Media>... lists){StringBuilder result=new StringBuilder();for(List<Media> list:lists){result.append('|');for(Media media:list)result.append(media.provider).append(media.requestType).append(media.id).append(media.title).append(media.detail).append(media.image).append(media.meta).append(';');}return TimedDiskCache.hash(result.toString());}
    boolean isDescendant(View child,View parent){for(ViewParent p=child.getParent();p!=null;p=p.getParent())if(p==parent)return true;return false;}
    List<Media> embyItems(String path,boolean overview)throws Exception {
        boolean recent=path.contains("/Items/Latest?");String raw=cachedGet(server()+"/emby"+path,Map.of("X-Emby-Token",prefs.getString("embyToken","")),path.contains("/Resume?")?30*1000L:recent?MINUTE:5*MINUTE,recent&&refreshRecentRequests.get());if(recent&&refreshRecentRequests.get())android.util.Log.d("VueRecent","Recently added fetched from Emby on minute timer");
        JSONArray data=raw.startsWith("[")?new JSONArray(raw):new JSONObject(raw).getJSONArray("Items");List<Media> result=new ArrayList<>();
        for(int i=0;i<data.length();i++) {
            JSONObject item=data.getJSONObject(i);String id=item.getString("Id"), imageId=id;
            String title=item.optString("Name");if(item.has("SeriesName"))title=item.optString("SeriesName")+" · "+title;
            JSONObject tags=item.optJSONObject("ImageTags");String type="Primary";
            if(item.optJSONArray("BackdropImageTags")!=null&&item.getJSONArray("BackdropImageTags").length()>0)type="Backdrop";
            else if(item.has("ParentBackdropItemId")){imageId=item.getString("ParentBackdropItemId");type="Backdrop";}
            String image=server()+"/emby/Items/"+Uri.encode(imageId)+"/Images/"+type+"?MaxWidth=960&Quality=80";
            String detail=overview?item.optString("Overview","From your library"):item.optString("ProductionYear",item.optString("Type","Emby"));
            JSONObject user=item.optJSONObject("UserData");if(user!=null&&user.optDouble("PlayedPercentage")>0)detail=Math.round(user.optDouble("PlayedPercentage"))+"% watched";
            Media media=new Media(title,detail,image,"Emby",id);readEmbyMetadata(media,item);result.add(media);
        }return result;
    }
    void readEmbyMetadata(Media media,JSONObject item){
        media.mediaType=item.optString("Type",media.mediaType);
        if(!item.optString("Overview").isEmpty())media.overview=item.optString("Overview");
        media.communityRating=item.optDouble("CommunityRating",-1);media.criticRating=item.optDouble("CriticRating",-1);
        JSONObject providers=item.optJSONObject("ProviderIds");if(providers!=null)media.tmdbId=providers.optString("Tmdb","");
        List<String> facts=new ArrayList<>();if(item.optInt("ProductionYear")>0)facts.add(String.valueOf(item.optInt("ProductionYear")));String age=item.optString("OfficialRating","");if(!age.isEmpty())facts.add(age);
        long minutes=item.optLong("RunTimeTicks")/600000000L;if(minutes>0)facts.add((minutes>=60?minutes/60+"h ":"")+minutes%60+"m");
        JSONArray genres=item.optJSONArray("Genres");if(genres!=null)for(int i=0;i<Math.min(2,genres.length());i++){String genre=genres.optString(i);if(!genre.isEmpty())facts.add(genre);}
        if(facts.isEmpty())facts.add(item.optString("Type","Movie"));media.libraryMeta=String.join("  ·  ",facts);media.meta=topPickMetadata(media,null);
    }
    String topPickMetadata(Media media,JSONObject ratings){
        List<String> parts=new ArrayList<>();JSONObject imdb=ratings==null?null:ratings.optJSONObject("imdb"),rt=ratings==null?null:ratings.optJSONObject("rt");
        double imdbScore=imdb==null?-1:imdb.optDouble("criticsScore",-1),rtScore=rt==null?-1:rt.optDouble("criticsScore",-1);
        if(imdbScore>=0&&imdbScore<=10)parts.add(String.format(Locale.ROOT,"IMDb %.1f / 10",imdbScore));
        else if(media.communityRating>0&&media.communityRating<=10)parts.add(String.format(Locale.ROOT,"★ %.1f / 10",media.communityRating));
        if(rtScore>=0&&rtScore<=100)parts.add("RT critics "+Math.round(rtScore)+"%");
        else if(media.criticRating>=0&&media.criticRating<=100)parts.add("Critics "+Math.round(media.criticRating)+"%");
        if(!media.libraryMeta.isEmpty())parts.add(media.libraryMeta);return String.join("  ·  ",parts);
    }
    void enrichTopPick(Media media){
        if(media.metadataLoaded||media.metadataLoading||!media.provider.equals("Emby"))return;media.metadataLoading=true;
        io.execute(()->{JSONObject full=null;try{String url=server()+"/emby/Users/"+prefs.getString("embyUser","")+"/Items/"+Uri.encode(media.id)+"?Fields=Overview,Genres,ProviderIds";full=new JSONObject(cachedGet(url,Map.of("X-Emby-Token",prefs.getString("embyToken","")),5*MINUTE));}catch(Exception ignored){}final JSONObject item=full;
            main.post(()->{if(isDestroyed())return;media.metadataLoading=false;media.metadataLoaded=true;if(item!=null)readEmbyMetadata(media,item);updateTopPickMetadata(media);
                if(requests.connected()&&media.tmdbId.matches("[0-9]+")){Media lookup=new Media(media.title,"","","Requests",media.tmdbId);lookup.requestType=media.mediaType.equals("Series")?"tv":"movie";requests.loadRatings(lookup,ratings->{media.meta=topPickMetadata(media,ratings);updateTopPickMetadata(media);});}
            });});
    }
    CharSequence styledHeroMetadata(Media media){
        if(!media.topPick)return media.meta;android.text.SpannableString line=new android.text.SpannableString(media.meta);
        if(media.meta.startsWith("IMDb")){line.setSpan(new android.text.style.BackgroundColorSpan(color("#D7B844")),0,4,0);line.setSpan(new android.text.style.ForegroundColorSpan(color("#151A1D")),0,4,0);line.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),0,4,0);}
        int rt=media.meta.indexOf("RT critics"),end=rt<0?-1:media.meta.indexOf('%',rt);if(end>rt)line.setSpan(new android.text.style.ForegroundColorSpan(color("#B7DCC2")),rt,end+1,0);return line;
    }
    void updateTopPickMetadata(Media media){if(!isDestroyed()&&featured==media&&presentation==2){heroMeta.setText(styledHeroMetadata(media));heroDescription.setText(media.overview.isEmpty()?media.detail:media.overview);}}
    TwitchSession.Credentials readTwitchCredentials(){
        synchronized(prefs){return new TwitchSession.Credentials(prefs.getString("twitchClient",""),prefs.getString("twitchToken",""),prefs.getString("twitchRefreshToken",""),prefs.getLong("twitchSessionRevision",0));}
    }
    boolean saveTwitchCredentials(TwitchSession.Credentials expected,TwitchSession.Credentials next){
        synchronized(prefs){
            if(!profiles.exists(profileId)||!expected.equals(readTwitchCredentials()))return false;
            return prefs.edit().putString("twitchClient",next.client()).putString("twitchToken",next.access()).putString("twitchRefreshToken",next.refresh()).putLong("twitchSessionRevision",next.revision()).commit();
        }
    }
    void clearTwitchSession(String client){
        synchronized(prefs){android.content.SharedPreferences.Editor edit=prefs.edit().remove("twitchToken").remove("twitchRefreshToken").putLong("twitchSessionRevision",prefs.getLong("twitchSessionRevision",0)+1);if(client!=null)edit.putString("twitchClient",client);edit.commit();}
    }
    TwitchSession createTwitchSession(){
        return new TwitchSession(profileId,new TwitchSession.Store(){
            public TwitchSession.Credentials read(){return readTwitchCredentials();}
            public boolean save(TwitchSession.Credentials expected,TwitchSession.Credentials next){return saveTwitchCredentials(expected,next);}
        },new TwitchSession.Network(){
            public TwitchSession.Validation validate(String access)throws Exception{
                JSONObject value=new JSONObject(request("https://id.twitch.tv/oauth2/validate","GET",null,Map.of("Authorization","OAuth "+access)));
                JSONArray scopes=value.optJSONArray("scopes");boolean follows=false;if(scopes!=null)for(int i=0;i<scopes.length();i++)if(scopes.optString(i).equals("user:read:follows"))follows=true;
                return new TwitchSession.Validation(value.optString("user_id"),value.optString("client_id"),follows,value.optLong("expires_in",0));
            }
            public TwitchSession.TokenPair refresh(String client,String refresh)throws Exception{
                String body="client_id="+Uri.encode(client)+"&grant_type=refresh_token&refresh_token="+Uri.encode(refresh);
                JSONObject value=new JSONObject(request("https://id.twitch.tv/oauth2/token","POST",body,Map.of("Content-Type","application/x-www-form-urlencoded")));
                return new TwitchSession.TokenPair(value.getString("access_token"),value.getString("refresh_token"));
            }
            public String followed(String access,String client,String user)throws Exception{
                return cachedGet("https://api.twitch.tv/helix/streams/followed?first=50&user_id="+Uri.encode(user),Map.of("Authorization","Bearer "+access,"Client-Id",client),MINUTE);
            }
        },SystemClock::elapsedRealtime);
    }
    List<Media> twitchStreams()throws Exception {
        JSONObject data=new JSONObject(twitchSession.followed());
        List<Media> result=new ArrayList<>();JSONArray items=data.getJSONArray("data");
        for(int i=0;i<items.length();i++){JSONObject s=items.getJSONObject(i); Media channel=new Media(s.getString("user_name"),"LIVE · "+s.optString("game_name")+" · "+s.optInt("viewer_count")+" watching",s.optString("thumbnail_url").replace("{width}","640").replace("{height}","360"),"Twitch",s.getString("user_login"));channel.broadcasterId=s.getString("user_id");result.add(channel);}return result;
    }
    void openDeviceQuickSettings(){
        // On this Google TV build, SystemUI expands the native dashboard through
        // the status-bar service. Its activity itself requires a system permission.
        try{
            Object statusBar=getSystemService(Class.forName("android.app.StatusBarManager"));
            if(statusBar!=null){statusBar.getClass().getMethod("expandNotificationsPanel").invoke(statusBar);return;}
        }catch(ReflectiveOperationException|SecurityException failure){android.util.Log.w("VueSettings","Quick panel unavailable; opening device settings");}
        try{startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}
        catch(ActivityNotFoundException failure){toast("Device settings are unavailable");}
    }
    void connections() {
        String[] options={hasEmby()?"Emby · connected":"Connect Emby",hasTwitch()?"Twitch · connected":"Connect Twitch","Refresh content","Disconnect Emby","Disconnect Twitch","Open original Google TV","Cache settings",prefs.getBoolean("livePreview",true)?"Live previews - muted":"Live previews - off"};
        new AlertDialog.Builder(this).setTitle("Your connections").setItems(options,(d,n)->{
            if(n==0)embyLogin();if(n==1)twitchSetup();if(n==2){forceFeedRefresh=true;renderedFeed="";loadFeeds();toast("Refreshing…");}
            if(n==3){prefs.edit().remove("embyToken").remove("embyUser").apply();images.evictAll();imageTimes.clear();draw();loadFeeds();}
            if(n==4){cancelPairing();clearTwitchSession(null);images.evictAll();imageTimes.clear();draw();loadFeeds();}
            if(n==5){try{startActivity(new Intent().setComponent(new ComponentName("com.google.android.apps.tv.launcherx","com.google.android.apps.tv.launcherx.home.HomeActivity")));}catch(Exception e){toast("Google TV unavailable");}}
            if(n==6)cacheSettings();
            if(n==7){boolean enabled=!prefs.getBoolean("livePreview",true);prefs.edit().putBoolean("livePreview",enabled).apply();if(!enabled)livePreview.destroy();else if(featured!=null)livePreview.show(featured);toast(enabled?"Live previews enabled and muted":"Live previews off; using artwork");}
        }).setNegativeButton("Close",null).show();
    }
    void cacheSettings(){
        new AlertDialog.Builder(this).setTitle("Cache settings").setItems(new String[]{"Keep Emby artwork: "+imageTtl/DAY+" day(s)","Clear cached artwork and content lists"},(dialog,which)->{
            if(which==0)new AlertDialog.Builder(this).setTitle("Keep Emby artwork").setSingleChoiceItems(new String[]{"24 hours","3 days","7 days"},imageTtl==7*DAY?2:imageTtl==3*DAY?1:0,(d,index)->{imageTtl=(index==0?1:index==1?3:7)*DAY;prefs.edit().putLong("artworkTtl",imageTtl).apply();d.dismiss();toast("Artwork cache duration updated");}).setNegativeButton("Cancel",null).show();
            else {images.evictAll();backdrops.evictAll();imageTimes.clear();io.execute(()->{imageDisk.clear();feedDisk.clear();main.post(()->{if(!isDestroyed()){draw();forceFeedRefresh=true;loadFeeds();toast("Cache cleared; reloading content");}});});}
        }).setNegativeButton("Close",null).show();
    }
    EditText field(LinearLayout form,String hint,String value,int type) {
        EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setInputType(type);e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,16);form.addView(e,new LinearLayout.LayoutParams(-1,dp(58)));return e;
    }
    void embyLogin() {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(24),dp(8),dp(24),dp(8));
        EditText address=field(form,"Server URL",server(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText user=field(form,"Emby username","",InputType.TYPE_CLASS_TEXT);
        EditText password=field(form,"Password (never saved)","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connect your Emby library").setView(form).setPositiveButton("Sign in",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
            String base=address.getText().toString().trim().replaceAll("/web(?:/.*)?$","").replaceAll("/+$","");
            try{URI parsed=URI.create(base);if(!"https".equals(parsed.getScheme())||parsed.getHost()==null||parsed.getUserInfo()!=null)throw new IllegalArgumentException();}catch(Exception e){address.setError("Enter an HTTPS server address");return;}
            String username=user.getText().toString(),pw=password.getText().toString();dialog.getButton(-1).setEnabled(false);
            io.execute(()->{try {
                JSONObject body=new JSONObject().put("Username",username).put("Pw",pw);
                String auth="Emby Client=\"Vue TV\", Device=\"Google TV\", DeviceId=\""+prefs.getString("device","")+"\", Version=\"0.1\"";
                JSONObject response=new JSONObject(request(base+"/emby/Users/AuthenticateByName","POST",body.toString(),Map.of("X-Emby-Authorization",auth)));
                if(isDestroyed()||!profiles.exists(profileId))return;
                prefs.edit().putString("embyServer",base).putString("embyToken",response.getString("AccessToken")).putString("embyUser",response.getJSONObject("User").getString("Id")).apply();
                main.post(()->{password.setText("");dialog.dismiss();loadFeeds();toast("Emby connected");});
            }catch(Exception e){main.post(()->{dialog.getButton(-1).setEnabled(true);password.setError("Sign-in failed: "+e.getMessage());});}});
        }));dialog.show();
    }
    void twitchSetup() {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(24),dp(8),dp(24),dp(8));
        form.addView(text("Use the client ID of a public Twitch developer application registered for this launcher. You will authorize read access to your followed channels on your phone. Sign-in renews automatically while used regularly; older sign-ins need one reconnect to enable this.",15,"#D0DBD6"));
        EditText client=field(form,"Twitch client ID",prefs.getString("twitchClient",""),InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Connect Twitch").setView(form).setPositiveButton("Get sign-in code",(d,n)->{
            String id=client.getText().toString().trim();if(id.isEmpty()){toast("A registered Twitch client ID is required");return;}
            if(!id.equals(prefs.getString("twitchClient","")))clearTwitchSession(id);startPairing(id);
        }).setNegativeButton("Cancel",null).show();
    }
    void startPairing(String client) {
        cancelPairing();io.execute(()->{try {
            JSONObject response=new JSONObject(request("https://id.twitch.tv/oauth2/device","POST","client_id="+Uri.encode(client)+"&scopes=user%3Aread%3Afollows",Map.of("Content-Type","application/x-www-form-urlencoded")));
            main.post(()->{
                if(!active)return;
                deviceCode=response.optString("device_code");pollInterval=response.optInt("interval",5);deviceExpiry=System.currentTimeMillis()+response.optInt("expires_in",600)*1000L;
                pairing=new AlertDialog.Builder(this).setTitle("Sign in to Twitch").setMessage("On your phone, open:\nhttps://www.twitch.tv/activate\n\nEnter code: "+response.optString("user_code")+"\n\nKeep this screen open. Only read access to your follows is requested.").setNegativeButton("Cancel",(d,n)->cancelPairing()).create();
                pairing.setOnCancelListener(d->cancelPairing());pairing.show();main.postDelayed(this::pollPairing,pollInterval*1000L);
            });
        }catch(Exception e){main.post(()->toast("Twitch connection failed: "+e.getMessage()));}});
    }
    void cancelPairing(){deviceCode="";if(pairing!=null){pairing.dismiss();pairing=null;}}
    void pollPairing() {
        if(deviceCode.isEmpty()||!active)return;
        if(System.currentTimeMillis()>deviceExpiry){cancelPairing();toast("Code expired. Connect Twitch again.");return;}
        final String code=deviceCode;final String client=prefs.getString("twitchClient","");
        io.execute(()->{try {
            String body="client_id="+Uri.encode(client)+"&device_code="+Uri.encode(code)+"&scopes=user%3Aread%3Afollows&grant_type=urn:ietf:params:oauth:grant-type:device_code";
            JSONObject r=new JSONObject(request("https://id.twitch.tv/oauth2/token","POST",body,Map.of("Content-Type","application/x-www-form-urlencoded")));
            TwitchSession.TokenPair pair=new TwitchSession.TokenPair(r.getString("access_token"),r.getString("refresh_token"));
            if(pair.access().isEmpty()||pair.refresh().isEmpty())throw new IOException("Incomplete sign-in response");
            main.post(()->{if(!code.equals(deviceCode)||!active||isDestroyed())return;
                TwitchSession.Credentials current=readTwitchCredentials();
                if(!current.client().equals(client)||!saveTwitchCredentials(current,new TwitchSession.Credentials(client,pair.access(),pair.refresh(),current.revision()+1))){cancelPairing();return;}
                cancelPairing();forceFeedRefresh=true;loadFeeds();toast("Twitch connected - automatic renewal enabled");});
        }catch(Exception e){main.post(()->{if(!code.equals(deviceCode))return;String message=e.getMessage();if(message.contains("authorization_pending"))main.postDelayed(this::pollPairing,pollInterval*1000L);else if(message.contains("slow_down")){pollInterval+=5;main.postDelayed(this::pollPairing,pollInterval*1000L);}else{cancelPairing();toast("Twitch sign-in failed. Try connecting again.");}});}});
    }
    HttpURLConnection connection(String url)throws Exception {
        URL u=new URL(url);if(!u.getProtocol().equals("https"))throw new IOException("HTTPS is required");
        HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);return c;
    }
    String request(String url,String method,String body,Map<String,String> headers)throws Exception {
        HttpURLConnection c=connection(url);try {
            c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");
            for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
            if(body!=null){c.setDoOutput(true);if(!headers.containsKey("Content-Type"))c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
            int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();String value="";
            if(stream!=null)try(InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192]; int count; while((count=in.read(buffer))!=-1){out.write(buffer,0,count);if(out.size()>4*1024*1024)throw new IOException("Response too large");}value=out.toString("UTF-8");}
            if(status<200||status>=300){
                // Only forward known OAuth states; never show response bodies containing account data.
                String reason="HTTP "+status;
                if(url.startsWith("https://id.twitch.tv/")&&value.contains("authorization_pending"))reason="authorization_pending";
                if(url.startsWith("https://id.twitch.tv/")&&value.contains("slow_down"))reason="slow_down";
                throw new TwitchSession.HttpFailure(status,reason);
            }return value;
        }finally{c.disconnect();}
    }
    void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
