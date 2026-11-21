package je.vu.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Profile roster contains display names/IDs only. Account sessions stay in separate stores. */
final class ProfileStore {
    static final String ORIGINAL="default";
    static final class User {
        final String id,name;
        final AvatarStyle avatar;
        User(String key,String label){this(key,label,new AvatarStyle());}
        User(String key,String label,AvatarStyle style){id=key;name=label;avatar=style.copy();}
        String initial(){return name.substring(0,name.offsetByCodePoints(0,1)).toUpperCase(Locale.ROOT);}
    }
    final Context context;
    final SharedPreferences roster;
    ProfileStore(Context owner){context=owner;roster=owner.getSharedPreferences("vue_profiles",Context.MODE_PRIVATE);if(users().isEmpty()){List<User> first=new ArrayList<>();first.add(new User(ORIGINAL,"R"));save(first);roster.edit().putString("active",ORIGINAL).commit();}}
    List<User> users(){List<User> result=new ArrayList<>();try{JSONArray data=new JSONArray(roster.getString("users","[]"));for(int i=0;i<data.length();i++){JSONObject item=data.getJSONObject(i);String id=item.getString("id"),name=item.getString("name").trim();AvatarStyle style=new AvatarStyle();JSONObject avatar=item.optJSONObject("avatar");if(avatar!=null){style.variant=avatar.optInt("variant");style.background=avatar.optInt("background",-1);style.skin=avatar.optInt("skin",-1);style.hair=avatar.optInt("hair",-1);style.hairColor=avatar.optInt("hairColor",-1);style.eyes=avatar.optInt("eyes",-1);style.accessory=avatar.optInt("accessory",-1);}if((id.equals(ORIGINAL)||id.matches("[a-f0-9-]{36}"))&&!name.isEmpty())result.add(new User(id,name,style));}}catch(Exception ignored){}return result;}
    User active(){String id=roster.getString("active",ORIGINAL);List<User> all=users();for(User user:all)if(user.id.equals(id))return user;return all.get(0);}
    boolean exists(String id){for(User user:users())if(user.id.equals(id))return true;return false;}
    SharedPreferences preferences(String id){return context.getSharedPreferences(id.equals(ORIGINAL)?"connections":"connections_"+id,Context.MODE_PRIVATE);}
    String cacheFolder(String kind,String id){return id.equals(ORIGINAL)?kind:kind+"-"+id;}
    void activate(String id){for(User user:users())if(user.id.equals(id)){roster.edit().putString("active",id).commit();return;}throw new IllegalArgumentException("That user no longer exists.");}
    User create(String raw,SharedPreferences template){
        List<User> all=users();if(all.size()>=12)throw new IllegalArgumentException("You can add up to 12 users.");String name=validate(raw,all,"");User user=new User(UUID.randomUUID().toString(),name);
        // Copy public setup hints only; never copy tokens, sessions, user IDs or app order.
        preferences(user.id).edit().putString("embyServer",template.getString("embyServer","https://emby.example.com")).putString("seerrServer",template.getString("seerrServer","https://jellyseerr.example.com")).putString("twitchClient",template.getString("twitchClient","")).putLong("artworkTtl",template.getLong("artworkTtl",MainActivity.DAY)).commit();
        all.add(user);save(all);return user;
    }
    void update(String id,String raw,AvatarStyle avatar){List<User> all=users();String name=validate(raw,all,id);for(int i=0;i<all.size();i++)if(all.get(i).id.equals(id)){all.set(i,new User(id,name,avatar));save(all);return;}throw new IllegalArgumentException("That profile no longer exists.");}
    void remove(String id){synchronized(preferences(id)){List<User> all=users();if(all.size()<=1)throw new IllegalArgumentException("Keep at least one profile. Create another first.");boolean selected=active().id.equals(id);all.removeIf(user->user.id.equals(id));save(all);if(selected)activate(all.get(0).id);context.deleteSharedPreferences(id.equals(ORIGINAL)?"connections":"connections_"+id);for(String kind:new String[]{"artwork","feeds"}){java.io.File folder=new java.io.File(context.getCacheDir(),cacheFolder(kind,id));java.io.File[] files=folder.listFiles();if(files!=null)for(java.io.File file:files)if(file.isFile())file.delete();folder.delete();}}}
    String validate(String raw,List<User> all,String except){String name=raw.trim();if(name.isEmpty()||name.codePointCount(0,name.length())>24)throw new IllegalArgumentException("Enter a name from 1 to 24 characters.");for(User user:all)if(!user.id.equals(except)&&user.name.equalsIgnoreCase(name))throw new IllegalArgumentException("A user with that name already exists.");return name;}
    void save(List<User> all){JSONArray data=new JSONArray();try{for(User user:all){AvatarStyle style=user.avatar;JSONObject avatar=new JSONObject().put("variant",style.variant).put("background",style.background).put("skin",style.skin).put("hair",style.hair).put("hairColor",style.hairColor).put("eyes",style.eyes).put("accessory",style.accessory);data.put(new JSONObject().put("id",user.id).put("name",user.name).put("avatar",avatar));}}catch(Exception failure){throw new IllegalStateException(failure);}if(!roster.edit().putString("users",data.toString()).commit())throw new IllegalStateException("Unable to save users.");}
}
