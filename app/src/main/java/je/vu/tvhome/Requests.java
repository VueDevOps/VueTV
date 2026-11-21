package je.vu.tvhome;

import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.net.URLDecoder;

/** Native, remote-friendly client for the user's Jellyseerr instance. */
final class Requests {
    String server(){return host.prefs.getString("seerrServer","https://jellyseerr.example.com");}
    final MainActivity host;
    int ticket;
    String query="";
    long lastLoaded;
    boolean loading;
    boolean showingMine;
    final Map<String,String> securityCookies=new ConcurrentHashMap<>();
    RequestDetails detailScreen;
    int detailTicket;
    final Map<String,JSONObject> ratingCache=new ConcurrentHashMap<>();
    final Map<String,Long> ratingTimes=new ConcurrentHashMap<>();
    Requests(MainActivity activity){host=activity;}
    boolean connected(){return !host.prefs.getString("seerrSession","").isEmpty();}
    boolean visible(){return !host.isDestroyed()&&host.currentTab.equals("Requests");}

    void draw(){
        ticket++;loading=false;lastLoaded=0;query="";showingMine=false;
        controls();
        if(!connected())host.placeholder("Request movies and shows","Sign in with your Emby account",this::login);
        else host.title("Discover something new","");
    }
    void controls(){
        TextView heading=host.text("Requests",28,"#E1F2FF");heading.setPadding(host.dp(58),host.dp(26),0,host.dp(12));host.rows.addView(heading);
        LinearLayout actions=host.rail();
        addAction(actions,"Search movies and shows",this::search);
        if(connected()){
            addAction(actions,"Discover",()->{query="";showingMine=false;load(false,true);});
            addAction(actions,"My requests",this::myRequests);
            addAction(actions,"Sign out",()->dialog(new AlertDialog.Builder(host).setTitle("Sign out of Requests?").setMessage("Your Emby and Twitch connections will stay signed in.").setNegativeButton("Cancel",null).setPositiveButton("Sign out",(d,w)->logout())));
        }else addAction(actions,"Sign in",this::login);
    }
    void addAction(LinearLayout row,String title,Runnable action){TextView button=host.button(title,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,host.dp(44));p.setMargins(0,0,host.dp(12),host.dp(12));row.addView(button,p);}
    void search(){
        if(!connected()){login();return;}
        EditText field=new EditText(host);field.setSingleLine(true);field.setHint("Movie or TV show");field.setText(query);
        dialog(new AlertDialog.Builder(host).setTitle("Search for something to watch").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Search",(d,w)->{query=field.getText().toString().trim();showingMine=false;load(false,true);}));
    }
    void login(){
        LinearLayout form=new LinearLayout(host);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(host.dp(24),host.dp(8),host.dp(24),0);
        TextView info=host.text("Enter your Jellyseerr server and sign in using your Emby account. Your password is used only to sign in and is not saved.",14,"#BFC2C5");form.addView(info);
        EditText address=host.field(form,"Server URL",server(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText user=host.field(form,"Emby username",host.prefs.getString("seerrUsername",""),InputType.TYPE_CLASS_TEXT);
        EditText password=host.field(form,"Emby password","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog signIn=new AlertDialog.Builder(host).setTitle("Connect Requests").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Sign in",null).create();
        signIn.setOnShowListener(d->{style(signIn);signIn.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String base=address.getText().toString().trim().replaceAll("/+$","");
            try{java.net.URI parsed=java.net.URI.create(base);if(!"https".equals(parsed.getScheme())||parsed.getHost()==null||parsed.getUserInfo()!=null||parsed.getQuery()!=null||parsed.getFragment()!=null)throw new IllegalArgumentException();}catch(Exception e){address.setError("Enter an HTTPS server address without a query or fragment");return;}
            String name=user.getText().toString().trim();if(name.isEmpty()){user.setError("Enter your Emby username");return;}
            if(!base.equals(server())){securityCookies.clear();ratingCache.clear();ratingTimes.clear();host.prefs.edit().remove("seerrSession").remove("seerrUserId").putString("seerrServer",base).apply();}
            String secret=password.getText().toString();signIn.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);info.setText("Signing in…");
            host.io.execute(()->{try{
                JSONObject body=new JSONObject().put("username",name).put("password",secret).put("serverType",3);
                JSONObject account=api("/auth/jellyfin","POST",body);
                if(host.isDestroyed()||!host.profiles.exists(host.profileId))return;
                if(!connected())throw new IOException("The server did not return a sign-in session.");
                host.prefs.edit().putString("seerrUsername",name).putInt("seerrUserId",account.getInt("id")).apply();
                host.main.post(()->{password.setText("");if(!host.isDestroyed()){signIn.dismiss();if(visible()){host.rows.removeAllViews();controls();load(false,true);}host.toast("Requests connected");}});
            }catch(Exception failure){host.main.post(()->{if(signIn.isShowing()){info.setText("HTTP 401".equals(failure.getMessage())?"Sign-in failed. Check your Emby username and password.":message(failure));signIn.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}});}});
        });});signIn.show();
    }
    void logout(){
        // Invalidate only this session; never touch the user's media-server sign-in.
        host.io.execute(()->{try{api("/auth/logout","POST",new JSONObject());}catch(Exception ignored){}host.prefs.edit().remove("seerrSession").remove("seerrUserId").apply();host.main.post(()->{if(visible()){host.rows.removeAllViews();draw();}});});
    }
    void load(boolean periodic){load(periodic,false);}
    void load(boolean periodic,boolean force){
        if(!visible()||!connected()||(loading&&!force))return;
        if(showingMine&&!force)return;
        // Timer refreshes status without replacing a row being browsed.
        if(periodic&&host.rows.hasFocus())return;
        if(!force&&System.currentTimeMillis()-lastLoaded<MainActivity.MINUTE)return;
        final int run=++ticket;final String search=query;loading=true;
        if(force){host.rows.removeAllViews();controls();host.title(search.isEmpty()?"Loading discoveries…":"Searching…","");}
        host.io.execute(()->{
            List<List<MainActivity.Media>> sections=new ArrayList<>();String error="";
            try{
                if(!search.isEmpty())sections.add(media(api("/search?query="+Uri.encode(search)+"&page=1","GET",null),""));
                else{
                    sections.add(media(api("/discover/trending?page=1","GET",null),""));
                    sections.add(media(api("/discover/movies?page=1","GET",null),"movie"));
                    sections.add(media(api("/discover/tv?page=1","GET",null),"tv"));
                }
            }catch(Exception failure){error=message(failure);}
            final String problem=error;
            host.main.post(()->{
                if(run!=ticket)return;loading=false;if(!visible())return;
                if(!force&&host.rows.hasFocus())return;
                host.rows.removeAllViews();controls();lastLoaded=System.currentTimeMillis();
                String[] names=search.isEmpty()?new String[]{"Trending now","Popular movies","Popular shows"}:new String[]{"Results for “"+search+"”"};
                for(int i=0;i<sections.size();i++)host.mediaRow(names[i],"",sections.get(i),"No titles found. Try another search.");
                if(!problem.isEmpty())host.placeholder("Requests unavailable",problem,connected()?()->load(false,true):this::login);
                if(force){List<List<View>> groups=host.focusGroups();if(groups.size()>1&&!groups.get(1).isEmpty())groups.get(1).get(0).requestFocus();}
            });
        });
    }
    List<MainActivity.Media> media(JSONObject response,String defaultType)throws Exception{
        List<MainActivity.Media> result=new ArrayList<>();JSONArray items=response.optJSONArray("results");if(items==null)return result;
        for(int i=0;i<items.length()&&result.size()<20;i++){
            JSONObject item=items.getJSONObject(i);String type=item.optString("mediaType",defaultType);if(!type.equals("movie")&&!type.equals("tv"))continue;
            String title=item.optString("title",item.optString("name","Untitled")),path=item.optString("backdropPath","");if(path.isEmpty()||path.equals("null"))path=item.optString("posterPath","");
            String image=path.isEmpty()||path.equals("null")?"":"https://image.tmdb.org/t/p/w780"+path;
            JSONObject state=item.optJSONObject("mediaInfo");String status=status(state==null?1:state.optInt("status",1));
            MainActivity.Media card=new MainActivity.Media(title,status,image,"Requests",String.valueOf(item.getInt("id")));card.requestType=type;card.overview=item.optString("overview","");
            String date=item.optString("releaseDate",item.optString("firstAirDate",""));card.meta=(date.length()>=4?date.substring(0,4)+" · ":"")+(type.equals("tv")?"TV show":"Movie")+" · "+status;result.add(card);
        }return result;
    }
    List<MainActivity.Media> homePool=new ArrayList<>();
    long homePoolTime;
    synchronized List<MainActivity.Media> homeDiscoveries()throws Exception{
        if(!connected())return new ArrayList<>();
        if(System.currentTimeMillis()-homePoolTime<15*MainActivity.MINUTE)return new ArrayList<>(homePool);
        List<MainActivity.Media> result=media(api("/discover/trending?page=1","GET",null),"");
        result.addAll(media(api("/discover/movies?page=1","GET",null),"movie"));
        result.addAll(media(api("/discover/tv?page=1","GET",null),"tv"));
        homePool=result;homePoolTime=System.currentTimeMillis();return new ArrayList<>(result);
    }
    void details(MainActivity.Media media){
        final int run=++detailTicket;final String origin=host.currentTab;
        Toast progress=Toast.makeText(host,"Loading title details…",Toast.LENGTH_SHORT);progress.show();host.io.execute(()->{try{
            JSONObject detail=api("/"+media.requestType+"/"+media.id,"GET",null);
            host.main.post(()->{progress.cancel();if(host.active&&!host.isDestroyed()&&origin.equals(host.currentTab)&&run==detailTicket)showDetails(media,detail);});
        }catch(Exception failure){host.main.post(()->{progress.cancel();if(run==detailTicket&&!host.isDestroyed())host.toast(message(failure));});}});
    }
    void showDetails(MainActivity.Media media,JSONObject detail){
        closeDetails();detailScreen=new RequestDetails(this,media,detail);
    }
    void closeDetails(){if(detailScreen!=null){detailScreen.close();detailScreen=null;}}
    void loadRatings(MainActivity.Media media,java.util.function.Consumer<JSONObject> ready){
        String key=media.requestType+":"+media.id;JSONObject cached=ratingCache.get(key);if(cached!=null&&System.currentTimeMillis()-ratingTimes.getOrDefault(key,0L)<60*MainActivity.MINUTE){ready.accept(cached);return;}
        host.io.execute(()->{JSONObject ratings=new JSONObject();try{JSONObject response=api("/"+media.requestType+"/"+media.id+(media.requestType.equals("movie")?"/ratingscombined":"/ratings"),"GET",null);if(media.requestType.equals("movie"))ratings=response;else ratings.put("rt",response);}catch(Exception unavailable){/* Missing third-party scores never block requesting. */}
            if(ratingCache.size()>40){ratingCache.clear();ratingTimes.clear();}ratingCache.put(key,ratings);ratingTimes.put(key,System.currentTimeMillis());final JSONObject result=ratings;host.main.post(()->{if(!host.isDestroyed())ready.accept(result);});});
    }
    void seasons(MainActivity.Media media,JSONObject detail){
        List<Integer> numbers=new ArrayList<>();List<String> labels=new ArrayList<>();Set<Integer> unavailable=new HashSet<>();JSONObject info=detail.optJSONObject("mediaInfo");
        if(info!=null){JSONArray existing=info.optJSONArray("seasons");if(existing!=null)for(int i=0;i<existing.length();i++){JSONObject season=existing.optJSONObject(i);if(season!=null&&season.optInt("status",1)>=2&&season.optInt("status",1)<=5)unavailable.add(season.optInt("seasonNumber"));}}
        JSONArray seasons=detail.optJSONArray("seasons");if(seasons!=null)for(int i=0;i<seasons.length();i++){JSONObject season=seasons.optJSONObject(i);if(season==null)continue;int number=season.optInt("seasonNumber");if(number<=0||season.optInt("episodeCount")==0||unavailable.contains(number))continue;numbers.add(number);labels.add("Season "+number);}
        if(numbers.isEmpty()){host.toast("No additional seasons are available to request.");return;}
        boolean[] checked=new boolean[numbers.size()];
        AlertDialog choice=new AlertDialog.Builder(host).setTitle("Request seasons of "+media.title).setMultiChoiceItems(labels.toArray(new String[0]),checked,(d,index,on)->checked[index]=on).setNegativeButton("Cancel",null).setPositiveButton("Continue",null).create();
        choice.setOnShowListener(d->{style(choice);choice.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{JSONArray selected=new JSONArray();for(int i=0;i<checked.length;i++)if(checked[i])selected.put(numbers.get(i));if(selected.length()==0){host.toast("Choose at least one season.");return;}choice.dismiss();confirm(media,selected);});});choice.show();
    }
    void confirm(MainActivity.Media media,JSONArray seasons){
        String scope=seasons==null?"Movie":"Seasons "+seasons.toString();
        dialog(new AlertDialog.Builder(host).setTitle("Request “"+media.title+"”?").setMessage(scope+"\n\nThis sends a request to VueSerr. Your account’s approval rules and limits apply.").setNegativeButton("Cancel",null).setPositiveButton("Send request",(d,w)->submit(media,seasons)));
    }
    void submit(MainActivity.Media media,JSONArray seasons){
        host.io.execute(()->{try{
            JSONObject body=new JSONObject().put("mediaType",media.requestType).put("mediaId",Integer.parseInt(media.id)).put("is4k",false);if(seasons!=null)body.put("seasons",seasons);
            JSONObject response=api("/request","POST",body);int state=response.optInt("status",1);
            host.main.post(()->{host.toast(state==1?"Request sent — awaiting approval":"Request sent");lastLoaded=0;details(media);});
        }catch(Exception failure){host.main.post(()->host.toast(message(failure)));}});
    }
    void myRequests(){
        final int run=++ticket;showingMine=true;loading=true;host.toast("Loading your latest requests…");host.io.execute(()->{try{
            JSONObject response=api("/request?take=12&skip=0&sort=added&requestedBy="+host.prefs.getInt("seerrUserId",0),"GET",null);JSONArray items=response.optJSONArray("results");List<MainActivity.Media> cards=new ArrayList<>();
            if(items!=null)for(int i=0;i<items.length();i++){JSONObject request=items.getJSONObject(i),media=request.optJSONObject("media");if(media==null)continue;String type=request.optString("type",media.optString("mediaType","movie"));if(!type.equals("movie")&&!type.equals("tv"))continue;JSONObject detail=api("/"+type+"/"+media.getInt("tmdbId"),"GET",null);detail.put("mediaType",type);cards.addAll(media(new JSONObject().put("results",new JSONArray().put(detail)),type));}
            host.main.post(()->{if(run!=ticket)return;loading=false;if(visible()){host.rows.removeAllViews();controls();host.mediaRow("My latest requests","",cards,"You haven’t requested anything yet.");}});
        }catch(Exception failure){host.main.post(()->{if(run==ticket){loading=false;host.toast(message(failure));}});}});
    }
    static String status(int state){switch(state){case 2:return "Awaiting approval";case 3:return "Requested · processing";case 4:return "Partly available";case 5:return "Available";case 6:return "Unavailable to request";default:return "Not requested";}}
    String message(Exception failure){String text=failure.getMessage();if(text==null)return "Unable to reach VueSerr. Try again.";if(text.equals("HTTP 401"))return "Sign in again to use Requests.";if(text.equals("HTTP 403"))return "Your VueSerr account does not have permission for this action.";if(text.equals("HTTP 409"))return "This title has already been requested.";return text.startsWith("HTTP ")?"VueSerr could not complete this action ("+text+"). Check your account limits or try again.":"Unable to connect to VueSerr. Check your connection and sign-in details.";}
    void dialog(AlertDialog.Builder builder){AlertDialog dialog=builder.create();dialog.setOnShowListener(d->style(dialog));dialog.show();}
    void style(AlertDialog dialog){if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(host.background("#18232D",18));for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE})if(dialog.getButton(id)!=null)dialog.getButton(id).setTextColor(Color.rgb(211,227,239));}

    JSONObject api(String path,String method,JSONObject body)throws Exception{
        // Fetch CSRF cookies before writes when the server has protection enabled.
        if(!method.equals("GET")&&securityCookies.isEmpty())api("/status","GET",null);
        HttpURLConnection connection=host.connection(server()+"/api/v1"+path);
        try{
            connection.setRequestMethod(method);connection.setRequestProperty("Accept","application/json");String session=host.prefs.getString("seerrSession","");StringBuilder cookies=new StringBuilder(session);for(Map.Entry<String,String> cookie:securityCookies.entrySet()){if(cookies.length()>0)cookies.append("; ");cookies.append(cookie.getKey()).append('=').append(cookie.getValue());}if(cookies.length()>0)connection.setRequestProperty("Cookie",cookies.toString());String csrf=securityCookies.get("XSRF-TOKEN");if(csrf!=null)connection.setRequestProperty("X-XSRF-TOKEN",URLDecoder.decode(csrf,"UTF-8"));
            if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=connection.getResponseCode();String value="";InputStream stream=code>=400?connection.getErrorStream():connection.getInputStream();
            if(stream!=null)try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){out.write(buffer,0,count);if(out.size()>4*1024*1024)throw new IOException("Response too large");}value=out.toString("UTF-8");}
            if(code==401&& !path.equals("/auth/jellyfin"))host.prefs.edit().remove("seerrSession").apply();
            if(code<200||code>=300)throw new IOException("HTTP "+code);
            if(!host.isDestroyed()&&host.profiles.exists(host.profileId))for(Map.Entry<String,List<String>> entry:connection.getHeaderFields().entrySet())if("Set-Cookie".equalsIgnoreCase(entry.getKey()))for(String cookie:entry.getValue()){String pair=cookie.split(";",2)[0];if(pair.startsWith("connect.sid=")&&!pair.equals("connect.sid="))host.prefs.edit().putString("seerrSession",pair).apply();String[] parts=pair.split("=",2);if(parts.length==2&&(parts[0].equals("_csrf")||parts[0].equals("XSRF-TOKEN")))securityCookies.put(parts[0],parts[1]);}
            return value.isEmpty()?new JSONObject():new JSONObject(value);
        }finally{connection.disconnect();}
    }
}
