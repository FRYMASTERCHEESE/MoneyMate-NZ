package nz.moneymate.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic tests only: no real OAuth exchange, ads request or finance changes. */
@RunWith(AndroidJUnit4.class)
public class ReleaseReadinessTest {
    @Test public void busyAccountOperationBlocksBackAndCompletionReleasesGuard() {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(
            new Intent(context,MainActivity.class).putExtra("captureMode",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(()->{
                activity.setAccountBusy(true);
                assertTrue(activity.getOnBackPressedDispatcher().hasEnabledCallbacks());
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertFalse(activity.isFinishing());
                activity.setAccountBusy(false);
                assertFalse(activity.getOnBackPressedDispatcher().hasEnabledCallbacks());
            });
        } finally {instrumentation.runOnMainSync(activity::finish);}
    }

    @Test public void googleCallbackConsumesVerifierOnceAndRejectsExpiredOrForeignCallbacks() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs=context.getSharedPreferences("moneymate_google_pkce",Context.MODE_PRIVATE);
        try {
            prefs.edit().putString("verifier","synthetic-verifier").putLong("started",System.currentTimeMillis()).commit();
            assertNull(GoogleOAuth.consume(context,Uri.parse("other://auth/callback?code=synthetic-code")));
            String[] result=GoogleOAuth.consume(context,Uri.parse("moneymatenz://auth/callback?code=synthetic-code"));
            assertArrayEquals(new String[]{"synthetic-code","synthetic-verifier"},result);
            assertNull(GoogleOAuth.consume(context,Uri.parse("moneymatenz://auth/callback?code=synthetic-code")));
            prefs.edit().putString("verifier","expired").putLong("started",System.currentTimeMillis()-700000).commit();
            assertNull(GoogleOAuth.consume(context,Uri.parse("moneymatenz://auth/callback?code=synthetic-code")));
        } finally {prefs.edit().clear().commit();}
    }

    @Test public void testBuildHasOfficialTestAdsAndExpectedVersion() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
        assertEquals("0.3.7",info.versionName);assertEquals(11,info.versionCode);
        assertEquals("ca-app-pub-3940256099942544/6300978111",BuildConfig.ADMOB_BANNER_ID);
        android.content.pm.ApplicationInfo app=context.getPackageManager().getApplicationInfo(context.getPackageName(),android.content.pm.PackageManager.GET_META_DATA);
        assertEquals("ca-app-pub-3940256099942544~3347511713",app.metaData.getString("com.google.android.gms.ads.APPLICATION_ID"));
    }
}
