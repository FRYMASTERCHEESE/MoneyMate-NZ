package nz.moneymate.app;
import android.app.Activity;
import android.content.MutableContextWrapper;
import android.os.CancellationSignal;
import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.CustomCredential;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import java.util.function.Consumer;

/** Native Google sign-in; browser PKCE remains available as a separate backup. */
final class NativeGoogleSignIn {
    private NativeGoogleSignIn(){}
    static void start(Activity activity,String webClientId,Consumer<String> success,Runnable failure){
        if(webClientId==null||webClientId.isEmpty()){failure.run();return;}
        try{
            GetSignInWithGoogleOption option=new GetSignInWithGoogleOption.Builder(webClientId).build();
            GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(option).build();
            CredentialManager.create(activity).getCredentialAsync(
                new MutableContextWrapper(activity),request,new CancellationSignal(),activity.getMainExecutor(),
                new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
                    @Override public void onResult(GetCredentialResponse response){
                        if(activity.isFinishing()||activity.isDestroyed())return;
                        try{
                            if(!(response.getCredential() instanceof CustomCredential))throw new IllegalStateException();
                            CustomCredential cred=(CustomCredential)response.getCredential();
                            if(!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(cred.getType()))
                                throw new IllegalStateException();
                            String token=GoogleIdTokenCredential.createFrom(cred.getData()).getIdToken();
                            if(token==null||token.isEmpty())throw new IllegalStateException();
                            success.accept(token);
                        }catch(Exception error){failure.run();}
                    }
                    @Override public void onError(GetCredentialException error){
                        if(!activity.isFinishing()&&!activity.isDestroyed())failure.run();
                    }
                });
        }catch(Exception error){failure.run();}
    }
}
