package nz.moneymate.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** OAuth 2.0 PKCE using the system browser, no embedded client secrets. */
final class GoogleOAuth {
    private static final String PREFS="moneymate_google_pkce";
    private static final String CALLBACK="moneymatenz://auth/callback";
    private static String base64(byte[] data) {
        return Base64.encodeToString(data,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }
    static Intent start(Context context) throws Exception {
        if(!BuildConfig.GOOGLE_ENABLED || !AuthClient.configured())
            throw new IllegalStateException("Google sign-in is not configured yet.");
        byte[] nonce=new byte[48];new SecureRandom().nextBytes(nonce);
        String verifier=base64(nonce);
        String challenge=base64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8)));
        // Private ephemeral verifier. Never place it in the browser URL or logs.
        if(!context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
             .putString("verifier",verifier).putLong("started",System.currentTimeMillis()).commit())
            throw new IllegalStateException("Could not save Google sign-in challenge.");
        Uri destination=Uri.parse(BuildConfig.SUPABASE_URL+"/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider","google")
            .appendQueryParameter("redirect_to",CALLBACK)
            .appendQueryParameter("code_challenge",challenge)
            .appendQueryParameter("code_challenge_method","s256").build();
        return new Intent(Intent.ACTION_VIEW,destination).addCategory(Intent.CATEGORY_BROWSABLE);
    }
    static String[] consume(Context context,Uri uri) {
        if(uri==null || !"moneymatenz".equals(uri.getScheme()) || !"auth".equals(uri.getHost()) ||
           !"/callback".equals(uri.getPath()))return null;
        android.content.SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String verifier=prefs.getString("verifier",null);
        long age=System.currentTimeMillis()-prefs.getLong("started",0);
        prefs.edit().clear().apply();
        String code=uri.getQueryParameter("code");
        if(verifier==null||age<0||age>600000||code==null||code.length()<8)return null;
        return new String[]{code,verifier};
    }
}
