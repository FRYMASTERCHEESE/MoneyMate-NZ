package nz.moneymate.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Per-user opt-in cloud snapshots with guarded server revisions.
 * Never merges conflicting records or overwrites a non-empty local database automatically.
 * All network calls belong on a background worker.
 */
final class CloudSync {
    static final String PREFS="moneymate_cloud_sync_v1";
    static final class Metadata {
        final boolean enabled;final long revision;final String fingerprint;
        Metadata(boolean enabled,long revision,String fingerprint){
            this.enabled=enabled;this.revision=revision;this.fingerprint=fingerprint;
        }
    }
    static final class Result {
        final String message,snapshot;final Metadata state;
        Result(String message,String snapshot,Metadata state){
            this.message=message;this.snapshot=snapshot;this.state=state;
        }
    }
    static Metadata metadata(Context context,String userId) {
        if(userId==null)return new Metadata(false,0,"");
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String prefix=userId+".";
        return new Metadata(p.getBoolean(prefix+"enabled",false),
          p.getLong(prefix+"revision",0),p.getString(prefix+"fingerprint",""));
    }
    static void persist(Context context,String userId,Metadata value){
        if(userId==null)throw new IllegalArgumentException("Account required");
        String prefix=userId+".";
        boolean ok=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putBoolean(prefix+"enabled",value.enabled).putLong(prefix+"revision",value.revision)
            .putString(prefix+"fingerprint",value.fingerprint).commit();
        if(!ok)throw new IllegalStateException("Could not save sync revision");
    }
    static String digest(String snapshot) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(snapshot.getBytes(StandardCharsets.UTF_8));
        StringBuilder text=new StringBuilder();
        for(byte b:hash)text.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return text.toString();
    }
    static boolean empty(String raw) throws Exception {
        JSONObject j=new JSONObject(raw);
        for(String table:new String[]{"transactions","budgets","goals","bills","recurring"})
            if(j.getJSONArray(table).length()!=0)return false;
        return true;
    }
    static Result evaluate(Metadata state,long remoteRevision,JSONObject remoteData,String local,
                           java.util.function.BiFunction<Long,JSONObject,JSONObject> commit) throws Exception {
        if(!state.enabled)return new Result("Cloud sync is off",null,state);
        if(remoteRevision<0)throw new IOException("Invalid cloud revision");
        String current=digest(local);
        if(remoteRevision==0) {
            if(state.revision!=0)return new Result("Sync conflict: cloud records missing; local data is safe.",null,state);
            JSONObject result=commit.apply(0L,new JSONObject(local));
            if(result.optBoolean("success",false)){
                long rev=result.getLong("revision");
                return new Result("Synced securely",null,new Metadata(true,rev,current));
            }
            return new Result("Another device updated cloud data; sync again.",null,state);
        }
        if(remoteData==null)throw new IOException("Cloud data missing");
        String remote=remoteData.toString();
        if(state.revision==0) {
            if(empty(local)) return new Result("Cloud records available",remote,new Metadata(true,remoteRevision,""));
            return new Result("Sync conflict: existing device data and cloud data differ. Export a JSON backup first.",null,state);
        }
        if(remoteRevision!=state.revision) {
            if(!state.fingerprint.equals(current))
                return new Result("Sync conflict: both devices changed records. Both copies have been preserved.",null,state);
            return new Result("New cloud records available",remote,new Metadata(true,remoteRevision,""));
        }
        if(state.fingerprint.equals(current))
            return new Result("Up to date",null,state);
        JSONObject result=commit.apply(state.revision,new JSONObject(local));
        if(result.optBoolean("success",false))
            return new Result("Synced securely",null,new Metadata(true,result.getLong("revision"),current));
        return new Result("Sync conflict: cloud changed before upload. Local data is safe.",null,state);
    }
    static Result synchronize(AuthClient auth,Metadata state,String local) throws Exception {
        JSONObject remote=auth.readAccountSnapshot();
        long revision=remote.getLong("revision");
        JSONObject snapshot=remote.optJSONObject("data");
        return evaluate(state,revision,snapshot,local,(expected,body)->{
            try{return auth.commitAccountSnapshot(expected,body);}
            catch(Exception error){throw new CloudNetworkException(error);}
        });
    }
    static final class CloudNetworkException extends RuntimeException{
        CloudNetworkException(Exception cause){super(cause);}
    }
    static void recoveryCopy(Context ctx,String userId,String raw) throws Exception {
        File directory=new File(ctx.getNoBackupFilesDir(),"financial-recovery");
        if(!directory.exists()&&!directory.mkdirs())throw new IOException("Cannot create recovery folder");
        File target=new File(directory,"before-cloud-"+userId+"-"+System.currentTimeMillis()+".json");
        try(FileOutputStream out=new FileOutputStream(target)) {
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }
}
