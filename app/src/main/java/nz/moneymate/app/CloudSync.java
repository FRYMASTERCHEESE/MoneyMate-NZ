package nz.moneymate.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Optional per-account cloud snapshots. Never sends local records without explicit opt-in.
 * Remote writes use compare-and-swap revision checks, never last-write-wins.
 * Local SQLite always saves first, even when offline or when the cloud has changed. */
final class CloudSync {
    private static final String PREFS="moneymate_cloud_sync_v1";
    static final class Outcome {
        final String message;
        final boolean changedLocal, conflict;
        Outcome(String message, boolean changedLocal, boolean conflict){
            this.message=message;this.changedLocal=changedLocal;this.conflict=conflict;
        }
    }
    private static SharedPreferences prefs(Context context) {return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static String key(String uid,String field) {return uid+":"+field;}
    static boolean enabled(Context context,String uid){
        return uid!=null&&prefs(context).getBoolean(key(uid,"enabled"),false);
    }
    static void clear(Context context,String uid){
        if(uid!=null)prefs(context).edit()
            .remove(key(uid,"enabled")).remove(key(uid,"revision")).remove(key(uid,"digest")).apply();
    }
    private static long revision(Context context,String uid){return prefs(context).getLong(key(uid,"revision"),0);}
    private static String digest(Context context,String uid){return prefs(context).getString(key(uid,"digest"),"");}
    private static void mark(Context context,String uid,long revision,String digest){
        if(revision<=0)throw new IllegalArgumentException("Invalid cloud revision");
        if(!prefs(context).edit().putBoolean(key(uid,"enabled"),true)
            .putLong(key(uid,"revision"),revision).putString(key(uid,"digest"),digest).commit())
            throw new IllegalStateException("Could not save cloud sync settings");
    }
    static String fingerprint(String text) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();
        for(byte b:hash)out.append(String.format(java.util.Locale.US,"%02x",b&255));
        return out.toString();
    }
    static String decision(boolean enabled,long baseRevision,String lastSaved,String localNow,long remoteRevision,boolean empty){
        if(!enabled)return "consent";
        if(baseRevision!=remoteRevision)return lastSaved.equals(localNow)||empty?"download":"conflict";
        return lastSaved.equals(localNow)?"unchanged":"upload";
    }
    private static String snapshotJson(Store store) throws Exception {
        String value=store.backupJson();
        if(value.getBytes(StandardCharsets.UTF_8).length>480_000)
            throw new IllegalArgumentException("Cloud copy exceeds 480 KB. Keep a local JSON backup.");
        return value;
    }
    private static void retainPrivateSafetyCopy(Context context,String uid,String previous) throws Exception {
        // Recovery copy only: users should also export a file outside the app before reinstalling.
        File file=new File(context.getFilesDir(),"moneymate-cloud-recovery-"+uid+"-"+System.currentTimeMillis()+".json");
        try(FileOutputStream stream=new FileOutputStream(file)){
            stream.write(previous.getBytes(StandardCharsets.UTF_8));stream.getFD().sync();
        }
    }
    /** First opt-in never overwrites an existing remote document or nonempty local profile. */
    static Outcome enable(Context context,AuthClient auth,String uid) throws Exception {
        if(uid==null||!uid.equals(auth.userId()))throw new IllegalStateException("Sign in to enable sync");
        JSONObject remote=auth.snapshotRead();
        long remoteRev=remote.getLong("revision");
        try(Store store=new Store(context,uid)){
            String local=snapshotJson(store);
            if(remoteRev>0){
                JSONObject server=remote.getJSONObject("data");
                if(!store.isEmpty()) {
                    return new Outcome("Cloud already has records. Export a full local JSON backup and choose which copy to keep. Nothing was overwritten.",false,true);
                }
                store.restoreJson(server.toString());
                mark(context,uid,remoteRev,fingerprint(snapshotJson(store)));
                return new Outcome("Cloud records restored to this device. Secure sync is enabled.",true,false);
            }
            JSONObject result=auth.snapshotCommit(0,new JSONObject(local));
            if(!result.getBoolean("success"))return new Outcome("Another device saved a cloud copy first. No records were overwritten.",false,true);
            mark(context,uid,result.getLong("revision"),fingerprint(local));
            return new Outcome("Secure cloud sync enabled. Your records were saved to your account.",false,false);
        }
    }
    /** Called after saved local edits and when returning to the app; offline edits are retained. */
    static Outcome sync(Context context,AuthClient auth,String uid) throws Exception {
        if(uid==null||!uid.equals(auth.userId())||!enabled(context,uid))
            return new Outcome("Cloud sync has not been enabled.",false,false);
        JSONObject remote=auth.snapshotRead();
        long remoteRev=remote.getLong("revision");
        try(Store store=new Store(context,uid)){
            String local=snapshotJson(store),hash=fingerprint(local);
            String action=decision(true,revision(context,uid),digest(context,uid),hash,remoteRev,store.isEmpty());
            if("conflict".equals(action))
                return new Outcome("Cloud sync conflict: both devices changed financial records. Local and cloud copies are preserved; export a JSON backup before choosing.",false,true);
            if("download".equals(action)){
                if(remoteRev==0)return new Outcome("Cloud records are missing. Local data was not deleted.",false,true);
                // Verify nothing edited locally before replacing; a concurrent edit must not be lost.
                if(!hash.equals(fingerprint(snapshotJson(store))))
                    return new Outcome("Local records changed during sync. Retry; nothing was overwritten.",false,true);
                String next=remote.getJSONObject("data").toString();
                retainPrivateSafetyCopy(context,uid,local);
                store.restoreJson(next);
                mark(context,uid,remoteRev,fingerprint(snapshotJson(store)));
                return new Outcome("New cloud records downloaded to this device.",true,false);
            }
            if("upload".equals(action)){
                JSONObject result=auth.snapshotCommit(remoteRev,new JSONObject(local));
                if(!result.getBoolean("success"))
                    return new Outcome("Cloud changed while saving. Your local records were kept; resolve the conflict before syncing.",false,true);
                mark(context,uid,result.getLong("revision"),hash);
                return new Outcome("Changes saved to secure cloud.",false,false);
            }
            return new Outcome("Cloud records are up to date.",false,false);
        }
    }
    /** Explicit recovery action. Never runs as an automatic conflict resolution. */
    static Outcome restoreCloud(Context context,AuthClient auth,String uid) throws Exception {
        if(uid==null||!uid.equals(auth.userId()))throw new IllegalStateException("Sign in again");
        JSONObject remote=auth.snapshotRead();
        long rev=remote.getLong("revision");
        if(rev<=0)return new Outcome("No cloud records exist yet. Local data unchanged.",false,true);
        try(Store store=new Store(context,uid)){
            String old=store.backupJson();
            String replacement=remote.getJSONObject("data").toString();
            // Keep a private safety copy in app storage before the atomic SQLite restore.
            retainPrivateSafetyCopy(context,uid,old);
            store.restoreJson(replacement);
            mark(context,uid,rev,fingerprint(snapshotJson(store)));
            return new Outcome("Cloud copy restored. An internal recovery copy was kept. Keep an external JSON backup too.",true,false);
        }
    }
}
