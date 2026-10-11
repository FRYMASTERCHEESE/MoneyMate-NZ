package nz.moneymate.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Supabase GoTrue HTTPS API. All calls run on a single worker, never on the UI thread. */
final class AuthClient {
    interface Transport {JSONObject call(String path,String method,JSONObject body,String bearer) throws Exception;}
    private final SessionVault vault;
    private final Transport transport;
    private JSONObject session;
    AuthClient(Context context) {
        vault=new SessionVault(context);transport=this::http;
        try {String saved=vault.read();if(saved!=null){session=new JSONObject(saved);validateSession(session);}}
        catch(Exception error){session=null;vault.clear();}
    }
    AuthClient(Transport transport){this.transport=transport;vault=null;}
    static boolean configured(){return !BuildConfig.SUPABASE_URL.isEmpty()&&!BuildConfig.SUPABASE_KEY.isEmpty();}
    synchronized String userId(){return session==null?null:session.optJSONObject("user").optString("id");}
    synchronized String email(){return session==null?"":session.optJSONObject("user").optString("email");}
    private void validateSession(JSONObject value) throws Exception {
        java.util.UUID.fromString(value.getJSONObject("user").getString("id"));
        if(value.getString("access_token").isEmpty()||value.getString("refresh_token").isEmpty())throw new IOException("Invalid session");
    }
    private synchronized void save(JSONObject value) throws Exception {
        validateSession(value);
        if(!value.has("expires_at"))value.put("expires_at",System.currentTimeMillis()/1000+value.getLong("expires_in"));
        if(vault!=null)vault.write(value.toString());session=value;
    }
    synchronized void clear(){session=null;if(vault!=null)vault.clear();}
    void signup(String email,String password) throws Exception {
        checkPassword(password);
        // Deliberately do not accept a session here: email confirmation must be enabled.
        transport.call("/auth/v1/signup","POST",credentials(email,password),null);
    }
    void signin(String email,String password) throws Exception {save(transport.call("/auth/v1/token?grant_type=password","POST",credentials(email,password),null));}
    void resend(String email) throws Exception {transport.call("/auth/v1/resend","POST",new JSONObject().put("email",email.trim()).put("type","signup"),null);}
    void recover(String email) throws Exception {transport.call("/auth/v1/recover","POST",new JSONObject().put("email",email.trim()),null);}
    void confirm(String email,String code) throws Exception {
        JSONObject verified=verify(email,code,"email");
        // Confirmation does not automatically expose this user's data.
        transport.call("/auth/v1/logout?scope=local","POST",new JSONObject(),verified.getString("access_token"));
    }
    private JSONObject verify(String email,String code,String type) throws Exception {
        if(!code.matches("[0-9]{6,10}"))throw new IOException("Enter the code from your email");
        return transport.call("/auth/v1/verify","POST",new JSONObject().put("email",email.trim()).put("token",code).put("type",type),null);
    }
    void reset(String email,String code,String password) throws Exception {
        checkPassword(password);
        JSONObject recovery=verify(email,code,"recovery");String token=recovery.getString("access_token");
        try {transport.call("/auth/v1/user","PUT",new JSONObject().put("password",password),token);}
        finally {transport.call("/auth/v1/logout?scope=global","POST",new JSONObject(),token);}
    }
    private synchronized String accessToken() throws Exception {
        if(session==null)throw new IOException("Sign in again");
        if(session.getLong("expires_at")<=System.currentTimeMillis()/1000+60) {
            JSONObject refreshed=transport.call("/auth/v1/token?grant_type=refresh_token","POST",new JSONObject().put("refresh_token",session.getString("refresh_token")),null);
            if(!userId().equals(refreshed.getJSONObject("user").getString("id")))throw new IOException("Session identity changed");
            save(refreshed);
        }
        return session.getString("access_token");
    }
    void signout() throws Exception {
        try{transport.call("/auth/v1/logout?scope=global","POST",new JSONObject(),accessToken());}finally{clear();}
    }
    void deleteAccount(String password) throws Exception {
        // Reauthenticate without replacing the current session; server checks the password too.
        transport.call("/functions/v1/delete-account","POST",new JSONObject().put("password",password),accessToken());
    }
    static void checkPassword(String password) {
        if(password.length()<12||password.length()>128)throw new IllegalArgumentException("Use a password of 12–128 characters");
    }
    private JSONObject credentials(String email,String password) throws Exception {
        return new JSONObject().put("email",email.trim()).put("password",password);
    }
    private JSONObject http(String path,String method,JSONObject body,String bearer) throws Exception {
        if(!configured())throw new IOException("Accounts are not configured in this build");
        HttpURLConnection connection=(HttpURLConnection)new URL(BuildConfig.SUPABASE_URL+path).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
            connection.setRequestMethod(method);connection.setRequestProperty("apikey",BuildConfig.SUPABASE_KEY);
            if(bearer!=null)connection.setRequestProperty("Authorization","Bearer "+bearer);
            connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);
            try(OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int status=connection.getResponseCode();
            if(status<200||status>=300) {
                // Never surface response bodies, credentials, or tokens in logs/UI.
                if(status==429)throw new IOException("Too many attempts. Wait before trying again.");
                if(status>=500)throw new IOException("Account service unavailable. Try again later.");
                throw new IOException("Request unsuccessful. Check your details or request a fresh email code.");
            }
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] block=new byte[4096];int count;
                while((count=input.read(block))!=-1){if(output.size()+count>65536)throw new IOException("Invalid response");output.write(block,0,count);}
                String result=output.toString("UTF-8");return result.trim().isEmpty()?new JSONObject():new JSONObject(result);
            }
        } finally {connection.disconnect();}
    }
}
