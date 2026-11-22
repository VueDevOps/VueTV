package je.vu.tvhome;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cinematic detail window, kept separate from the request/approval workflow. */
final class RequestDetails {
    final Requests requests;
    final MainActivity host;
    final MainActivity.Media media;
    final JSONObject detail;
    final Dialog window;
    final LinearLayout scores;
    TextView ratingsNote;

    RequestDetails(Requests owner,MainActivity.Media item,JSONObject data){
        requests=owner;host=owner.host;media=item;detail=data;window=new Dialog(host);
        window.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout scene=new FrameLayout(host);scene.setBackgroundColor(host.color("#0B1118"));
        String backdrop=image("backdropPath","w1280");
        if(!backdrop.isEmpty()){
            ImageView art=new ImageView(host);art.setScaleType(ImageView.ScaleType.CENTER_CROP);scene.addView(art,new FrameLayout.LayoutParams(-1,-1));host.loadImage(art,backdrop,false);
        }
        View shade=new View(host);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{host.color("#FF0B1118"),host.color("#F50B1118"),host.color("#A00B1118"),host.color("#400B1118")}));scene.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        View floor=new View(host);floor.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{host.color("#000B1118"),host.color("#A00B1118"),host.color("#FF0B1118")}));FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,host.dp(180),Gravity.BOTTOM);scene.addView(floor,fp);

        String poster=image("posterPath","w500");
        if(!poster.isEmpty()){
            ImageView portrait=new ImageView(host);portrait.setContentDescription(media.title+" poster");portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);portrait.setBackground(host.background("#23313D",12));portrait.setClipToOutline(true);portrait.setElevation(host.dp(12));FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(host.dp(192),host.dp(288),Gravity.END|Gravity.CENTER_VERTICAL);pp.rightMargin=host.dp(58);scene.addView(portrait,pp);host.loadImage(portrait,poster,false);
        }

        LinearLayout column=new LinearLayout(host);column.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(host.dp(550),-1,Gravity.START|Gravity.TOP);cp.leftMargin=host.dp(54);cp.topMargin=host.dp(34);cp.bottomMargin=host.dp(28);scene.addView(column,cp);
        ScrollView scroll=new ScrollView(host);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);LinearLayout copy=new LinearLayout(host);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(0,host.dp(10),host.dp(8),host.dp(10));scroll.addView(copy);column.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        JSONObject info=detail.optJSONObject("mediaInfo");int state=info==null?1:info.optInt("status",1);
        TextView eyebrow=host.text(Requests.status(state).toUpperCase(Locale.ROOT),11,"#B8CDD9");eyebrow.setLetterSpacing(.11f);copy.addView(eyebrow);space(copy,10);
        TextView title=host.text(media.title,media.title.length()>35?32:40,"#F1F6FA");title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);space(copy,10);
        TextView metadata=host.text(metadata(),12,"#C3CED7");metadata.setMaxLines(2);copy.addView(metadata);space(copy,14);
        scores=new LinearLayout(host);scores.setOrientation(LinearLayout.HORIZONTAL);copy.addView(scores);
        double tmdb=detail.optDouble("voteAverage",-1);if(detail.optInt("voteCount",0)>0&&tmdb>=0&&tmdb<=10)addScore("TMDB audience",String.format(Locale.ROOT,"%.1f / 10",tmdb),"#D3E3EF");
        ratingsNote=host.text("Loading critic scores…",10,"#98AAB8");LinearLayout.LayoutParams noteP=new LinearLayout.LayoutParams(-1,-2);noteP.topMargin=host.dp(7);copy.addView(ratingsNote,noteP);space(copy,14);
        String tagline=detail.optString("tagline","");if(!tagline.isEmpty()){TextView quote=host.text(tagline,14,"#D3E3EF");quote.setTypeface(null,Typeface.ITALIC);copy.addView(quote);space(copy,10);}
        TextView overview=host.text(detail.optString("overview",media.overview),14,"#C3CED7");if(overview.getText().length()==0)overview.setText("A synopsis is not available for this title yet.");overview.setLineSpacing(host.dp(3),1);copy.addView(overview);space(copy,14);
        String cast=cast();if(!cast.isEmpty()){TextView actors=host.text("Starring  "+cast,11,"#9EB0BE");actors.setMaxLines(2);copy.addView(actors);}

        LinearLayout actions=new LinearLayout(host);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setClipChildren(false);column.addView(actions,new LinearLayout.LayoutParams(-1,host.dp(48)));
        TextView first=null;
        if(media.requestType.equals("tv")&&state!=5&&state!=6)first=action(actions,"Choose seasons",()->{close();requests.seasons(media,detail);});
        else if(media.requestType.equals("movie")&&(state==1||state==7))first=action(actions,"Request movie",()->{close();requests.confirm(media,null);});
        String trailer=trailer();TextView trailerButton=action(actions,trailer.isEmpty()?"Find trailer":"Watch trailer",()->playTrailer(trailer));if(first==null)first=trailerButton;
        action(actions,"Close",this::close);
        window.setContentView(scene);Window androidWindow=window.getWindow();if(androidWindow!=null){androidWindow.setBackgroundDrawableResource(android.R.color.transparent);androidWindow.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);androidWindow.setDimAmount(0);}
        window.show();if(androidWindow!=null)androidWindow.setLayout(-1,-1);if(!first.requestFocus())first.requestFocusFromTouch();
        final TextView initialAction=first;
        requests.findEmby(media,match->{
            if(!window.isShowing()||match==null)return;
            TextView watch=action(actions,"Watch in Emby",()->host.openEmby(match));
            actions.removeView(watch);actions.addView(watch,0);
            if(initialAction.hasFocus()&&!watch.requestFocus())watch.requestFocusFromTouch();
        });
        requests.loadRatings(media,ratings->{if(!window.isShowing())return;showRatings(ratings);});
    }
    String image(String field,String size){String path=detail.optString(field,"");return path.isEmpty()||path.equals("null")?"":"https://image.tmdb.org/t/p/"+size+path;}
    void space(LinearLayout column,int height){column.addView(new View(host),new LinearLayout.LayoutParams(1,host.dp(height)));}
    TextView action(LinearLayout row,String label,Runnable run){
        TextView button=host.text(label,14,"#D3E3EF");button.setGravity(Gravity.CENTER);button.setPadding(host.dp(14),0,host.dp(14),0);button.setFocusable(true);button.setClickable(true);button.setDefaultFocusHighlightEnabled(false);
        button.setBackground(host.background("#B3263441",24));button.setOnClickListener(v->run.run());button.setOnFocusChangeListener((v,focused)->{button.setTextColor(host.color(focused?"#101820":"#D3E3EF"));button.setBackground(host.background(focused?"#E1F2FF":"#B3263441",24));host.lift(button,focused,1.025f);});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,host.dp(42));p.setMargins(0,0,host.dp(10),0);row.addView(button,p);return button;
    }
    String metadata(){
        List<String> parts=new ArrayList<>();String date=detail.optString("releaseDate",detail.optString("firstAirDate",""));if(date.length()>=4)parts.add(date.substring(0,4));
        if(media.requestType.equals("tv")){int count=detail.optInt("numberOfSeasons");if(count>0)parts.add(count+" season"+(count==1?"":"s"));}else{int runtime=detail.optInt("runtime");if(runtime>0)parts.add((runtime/60>0?runtime/60+"h ":"")+runtime%60+"m");}
        JSONArray genres=detail.optJSONArray("genres");if(genres!=null)for(int i=0;i<Math.min(3,genres.length());i++){JSONObject genre=genres.optJSONObject(i);if(genre!=null&&!genre.optString("name").isEmpty())parts.add(genre.optString("name"));}return String.join("  ·  ",parts);
    }
    String cast(){JSONObject credits=detail.optJSONObject("credits");JSONArray cast=credits==null?null:credits.optJSONArray("cast");List<String> names=new ArrayList<>();if(cast!=null)for(int i=0;i<Math.min(4,cast.length());i++){JSONObject actor=cast.optJSONObject(i);if(actor!=null&&!actor.optString("name").isEmpty())names.add(actor.optString("name"));}return String.join(", ",names);}
    void addScore(String source,String score,String accent){LinearLayout chip=new LinearLayout(host);chip.setOrientation(LinearLayout.VERTICAL);chip.setPadding(host.dp(10),host.dp(7),host.dp(10),host.dp(7));chip.setBackground(host.background("#C31B2935",8));TextView number=host.text(score,18,accent);number.setTypeface(null,Typeface.BOLD);chip.addView(number);chip.addView(host.text(source,9,"#B1C1CD"));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.rightMargin=host.dp(8);scores.addView(chip,p);}
    void showRatings(JSONObject ratings){
        JSONObject rt=ratings.optJSONObject("rt"),imdb=ratings.optJSONObject("imdb");boolean extra=false;
        if(rt!=null){double critics=rt.optDouble("criticsScore",-1),audience=rt.optDouble("audienceScore",-1);if(valid(rt,"criticsScore",critics,100)){addScore("RT critics",Math.round(critics)+"%",critics>=60?"#A8DAB5":"#ECC59F");extra=true;}if(valid(rt,"audienceScore",audience,100)){addScore("RT audience",Math.round(audience)+"%","#D3E3EF");extra=true;}}
        if(imdb!=null){double score=imdb.optDouble("criticsScore",-1);if(valid(imdb,"criticsScore",score,10)){addScore("IMDb",String.format(Locale.ROOT,"%.1f / 10",score),"#F4D582");extra=true;}}
        ratingsNote.setText(extra?"Rotten Tomatoes (RT) · scores shown when available":"No additional critic scores available for this title.");
        if(scores.getChildCount()==0)ratingsNote.setText("Ratings are not available for this title yet.");
    }
    static boolean valid(JSONObject data,String field,double value,double max){return data.has(field)&&!data.isNull(field)&&Double.isFinite(value)&&value>=0&&value<=max;}
    String trailer(){
        JSONArray videos=detail.optJSONArray("relatedVideos");if(videos==null){JSONObject raw=detail.optJSONObject("videos");if(raw!=null)videos=raw.optJSONArray("results");}if(videos==null)return "";
        String teaser="";for(int i=0;i<videos.length();i++){JSONObject video=videos.optJSONObject(i);if(video==null||!video.optString("site").equalsIgnoreCase("YouTube"))continue;String key=video.optString("key");if(!key.matches("[A-Za-z0-9_-]{11}"))continue;if(video.optString("type").equalsIgnoreCase("Trailer"))return key;if(video.optString("type").equalsIgnoreCase("Teaser")&&teaser.isEmpty())teaser=key;}return teaser;
    }
    void playTrailer(String key){
        String url=key.isEmpty()?"https://www.youtube.com/results?search_query="+Uri.encode(media.title+" "+detail.optString("releaseDate",detail.optString("firstAirDate","")).split("-")[0]+" official trailer"):"https://www.youtube.com/watch?v="+key;
        Intent video=new Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage("com.google.android.youtube.tv");
        try{host.startActivity(video);}catch(ActivityNotFoundException unavailable){try{host.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException missing){host.toast("Install YouTube to watch trailers.");}}
    }
    void close(){window.dismiss();}
}
