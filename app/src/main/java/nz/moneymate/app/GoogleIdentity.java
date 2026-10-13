package nz.moneymate.app;

import android.app.Activity;
import android.os.CancellationSignal;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.Credential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/** Native Google account picker. Existing browser PKCE remains a safe fallback. */
final class GoogleIdentity {
    interface Listener {void onToken(String token);void onUnavailable();}
    private GoogleIdentity() {}
    static void start(Activity activity,Listener listener){
        if(!BuildConfig.GOOGLE_ENABLED||BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty()){
            listener.onUnavailable();return;
        }
        try{
            GetSignInWithGoogleOption option=new GetSignInWithGoogleOption.Builder(
                BuildConfig.GOOGLE_WEB_CLIENT_ID).build();
            GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(option).build();
            CredentialManager.create(activity).getCredentialAsync(activity,request,
                new CancellationSignal(),activity.getMainExecutor(),
                new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
                    @Override public void onResult(GetCredentialResponse result){
                        Credential credential=result.getCredential();
                        if(!(credential instanceof CustomCredential)||
                          !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())){
                            listener.onUnavailable();return;
                        }
                        try{
                            String token=GoogleIdTokenCredential.createFrom(
                                ((CustomCredential)credential).getData()).getIdToken();
                            if(token==null||token.isEmpty()){listener.onUnavailable();return;}
                            listener.onToken(token);
                        }catch(Exception error){listener.onUnavailable();}
                    }
                    @Override public void onError(GetCredentialException error){listener.onUnavailable();}
                });
        }catch(Exception error){listener.onUnavailable();}
    }
}
