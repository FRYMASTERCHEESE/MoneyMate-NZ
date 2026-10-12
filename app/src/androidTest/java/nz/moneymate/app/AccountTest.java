package nz.moneymate.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AccountTest {
    private static final String A="11111111-1111-4111-8111-111111111111",B="22222222-2222-4222-8222-222222222222";
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    @Test public void preservesVersionOneAndSeparatesAccountsAndBackups() throws Exception {
        Context c=context();c.deleteDatabase("moneymate.db");c.deleteDatabase(Store.databaseName(A));c.deleteDatabase(Store.databaseName(B));
        // Recreate the actual legacy schema at version 1; upgrade must preserve every row.
        try(SQLiteDatabase old=c.openOrCreateDatabase("moneymate.db",0,null)) {
            old.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT,category TEXT,note TEXT,cents INTEGER,date TEXT)");
            old.execSQL("CREATE TABLE budgets(category TEXT PRIMARY KEY,cents INTEGER)");
            old.execSQL("CREATE TABLE goals(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,target INTEGER,saved INTEGER DEFAULT 0)");
            old.execSQL("CREATE TABLE bills(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,cents INTEGER,due TEXT)");
            old.execSQL("INSERT INTO transactions VALUES(1,'Income','Salary','Legacy',12345,'2026-10-12')");old.setVersion(1);
        }
        try(Store guest=new Store(c);Store a=new Store(c,A);Store b=new Store(c,B)) {
            assertEquals(12345,guest.sum("Income","2026-10"));assertTrue(a.isEmpty());assertTrue(b.isEmpty());
            guest.addBudget("Food",10000);guest.addGoal("Goal",20000);guest.addBill("Bill",5000,"2026-10-13");
            guest.addRecurring("Expense","Food","Repeat",500,"2026-10-13","Weekly");
            a.restoreJson(guest.backupJson());assertEquals(12345,a.sum("Income","2026-10"));assertFalse(a.isEmpty());
            b.addTransaction("Expense","Private","Account B",999,"2026-10-12");
            assertEquals(0,a.sum("Expense","2026-10"));assertEquals(0,guest.sum("Expense","2026-10"));
            assertEquals(0,b.sum("Income","2026-10"));
            JSONObject invalid=new JSONObject(a.backupJson());invalid.getJSONArray("transactions").getJSONObject(0).put("unexpected","bad");
            try{a.restoreJson(invalid.toString());fail("Invalid restore accepted");}catch(org.json.JSONException expected){}
            assertEquals(12345,a.sum("Income","2026-10")); // failed restore rolled back
        }
        c.deleteDatabase(Store.databaseName(A));
        try(Store guest=new Store(c);Store b=new Store(c,B)) {assertEquals(12345,guest.sum("Income","2026-10"));assertEquals(999,b.sum("Expense","2026-10"));}
        c.deleteDatabase("moneymate.db");c.deleteDatabase(Store.databaseName(B));
    }
    @Test public void vaultEncryptsAndPersistsSession() throws Exception {
        SessionVault vault=new SessionVault(context());vault.clear();
        vault.write("private-refresh-token");assertEquals("private-refresh-token",new SessionVault(context()).read());
        java.io.File file=new java.io.File(context().getNoBackupFilesDir(),"auth-session");
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        try(java.io.FileInputStream in=new java.io.FileInputStream(file)){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
        assertFalse(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8).contains("private-refresh-token"));vault.clear();assertNull(vault.read());
    }
    private JSONObject session(String id) throws Exception {return new JSONObject().put("access_token","access").put("refresh_token","refresh").put("expires_in",3600).put("user",new JSONObject().put("id",id).put("email","test@example.invalid"));}
    @Test public void signoutLocksLocallyEvenWhenOffline() throws Exception {
        AuthClient client=new AuthClient((path,method,body,bearer)->{if(path.contains("token"))return session(A);throw new java.io.IOException("offline");});
        client.signin("test@example.invalid","secret");assertEquals(A,client.userId());
        try{client.signout();fail("Expected offline error");}catch(java.io.IOException expected){}assertNull(client.userId());
    }
    @Test public void recoveryDoesNotReplaceActiveIdentityAndUsesScopedOtp() throws Exception {
        java.util.List<String> calls=new java.util.ArrayList<>();
        AuthClient client=new AuthClient((path,method,body,bearer)->{calls.add(path);if(path.contains("grant_type=password"))return session(A);if(path.endsWith("verify")){assertEquals("recovery",body.getString("type"));return session(B);}return new JSONObject();});
        client.signin("test@example.invalid","secret");client.reset("other@example.invalid","123456","new long password");
        assertEquals(A,client.userId());assertTrue(calls.contains("/auth/v1/user"));assertTrue(calls.contains("/auth/v1/logout?scope=global"));
    }
    @Test public void failedDeleteKeepsProfileAndSignupCannotActivateSession() throws Exception {
        AuthClient client=new AuthClient((path,method,body,bearer)->{if(path.contains("delete-account"))throw new java.io.IOException("failure");return session(A);});
        client.signup("test@example.invalid","a long test password");assertNull(client.userId());
        client.signin("test@example.invalid","secret");try{client.deleteAccount("secret");fail("Expected failure");}catch(java.io.IOException expected){}assertEquals(A,client.userId());
    }
    @Test public void onlineValidationDistinguishesRevokedSessionFromOffline() throws Exception {
        AuthClient offline=new AuthClient((path,method,body,bearer)->{if(path.contains("token"))return session(A);throw new java.io.IOException("offline");});
        offline.signin("test@example.invalid","secret");assertTrue(offline.validateOnline());
        AuthClient revoked=new AuthClient((path,method,body,bearer)->{if(path.contains("token"))return session(A);throw new AuthClient.HttpFailure(401,"revoked");});
        revoked.signin("test@example.invalid","secret");assertFalse(revoked.validateOnline());
    }
    @Test public void eightCharacterPasswordAcceptedButShorterRejected() {
        AuthClient.checkPassword("eight888");
        try { AuthClient.checkPassword("seven77"); fail("Too-short password accepted"); }
        catch(IllegalArgumentException expected) {}
    }
    @Test public void paidBillRecordsOneExpenseAndCanBeRemovedWithoutPaying() throws Exception {
        Context c=context();
        String owner="33333333-3333-4333-8333-333333333333";
        String file=Store.databaseName(owner);
        c.deleteDatabase(file);
        try(Store db=new Store(c,owner)) {
            db.addBill("Electricity",3456,"2026-10-01");
            assertTrue(db.recordBillPaid(1,"2026-10-12"));
            assertFalse(db.recordBillPaid(1,"2026-10-12"));
            assertEquals(3456,db.sum("Expense","2026-10"));
            try(android.database.Cursor cursor=db.query("SELECT category,note FROM transactions WHERE kind='Expense'")) {
                assertTrue(cursor.moveToFirst());
                assertEquals("Bills",cursor.getString(0));
                assertEquals("Electricity",cursor.getString(1));
                assertFalse(cursor.moveToNext());
            }
            db.addBill("Unpaid example",1000,"2026-10-18");
            db.deleteBill(2);
            assertEquals(3456,db.sum("Expense","2026-10"));
            try(android.database.Cursor cursor=db.query("SELECT COUNT(*) FROM bills")) {
                assertTrue(cursor.moveToFirst());
                assertEquals(0,cursor.getLong(0));
            }
        } finally {c.deleteDatabase(file);}
    }

    @Test public void cloudDigestDetectsLocalChanges() throws Exception {
        String before="local records",after="new local records";
        assertEquals(CloudSync.digest(before),CloudSync.digest(before));
        assertNotEquals(CloudSync.digest(before),CloudSync.digest(after));
        CloudSync.Metadata state=new CloudSync.Metadata(false,0,"");
        assertFalse(state.enabled);
    }

    @Test public void simulatedTwoDeviceSyncPreservesTransactionsAndAvoidsStaleOverwrite() throws Exception {
        // Real SQLite records on two isolated local profiles; cloud uses the same
        // revision-based protocol as Supabase but never contacts a production account.
        Context c=context();
        String phone="44444444-4444-4444-8444-444444444444";
        String laptop="55555555-5555-4555-8555-555555555555";
        c.deleteDatabase(Store.databaseName(phone));c.deleteDatabase(Store.databaseName(laptop));
        try(Store first=new Store(c,phone);Store second=new Store(c,laptop)) {
            final JSONObject[] cloud={null};
            final long[] rev={0};
            java.util.function.BiFunction<Long,JSONObject,JSONObject> commit=(expected,payload)->{
                try {
                    if(expected!=rev[0])return new JSONObject().put("success",false).put("revision",rev[0]);
                    cloud[0]=new JSONObject(payload.toString());rev[0]++;
                    return new JSONObject().put("success",true).put("revision",rev[0]);
                } catch(Exception error){throw new RuntimeException(error);}
            };
            first.addTransaction("Income","Salary","Phone",12345,"2026-10-12");
            CloudSync.Metadata firstState=new CloudSync.Metadata(true,0,"");
            CloudSync.Result firstUpload=CloudSync.evaluate(firstState,rev[0],cloud[0],first.backupJson(),commit);
            assertEquals("Synced securely",firstUpload.message);
            firstState=firstUpload.state;assertEquals(1,rev[0]);
            assertEquals(12345,first.sum("Income","2026-10"));

            CloudSync.Metadata secondState=new CloudSync.Metadata(true,0,"");
            CloudSync.Result secondDownload=CloudSync.evaluate(secondState,rev[0],cloud[0],second.backupJson(),commit);
            assertNotNull(secondDownload.snapshot);
            second.restoreJson(secondDownload.snapshot);
            secondState=new CloudSync.Metadata(true,secondDownload.state.revision,CloudSync.digest(second.backupJson()));
            assertEquals(12345,second.sum("Income","2026-10"));

            second.addTransaction("Expense","Groceries","Laptop",2999,"2026-10-12");
            CloudSync.Result secondUpload=CloudSync.evaluate(secondState,rev[0],cloud[0],second.backupJson(),commit);
            assertEquals("Synced securely",secondUpload.message);
            assertEquals(2,rev[0]);secondState=secondUpload.state;
            CloudSync.Result firstDownload=CloudSync.evaluate(firstState,rev[0],cloud[0],first.backupJson(),commit);
            assertNotNull(firstDownload.snapshot);
            first.restoreJson(firstDownload.snapshot);
            firstState=new CloudSync.Metadata(true,firstDownload.state.revision,CloudSync.digest(first.backupJson()));
            assertEquals(2999,first.sum("Expense","2026-10"));
            assertEquals(12345,first.sum("Income","2026-10"));

            // Now each device changes its local data independently.
            first.addTransaction("Expense","Transport","First",75,"2026-10-12");
            second.addTransaction("Expense","Utilities","Second",200,"2026-10-12");
            CloudSync.Result thirdUpload=CloudSync.evaluate(firstState,rev[0],cloud[0],first.backupJson(),commit);
            assertEquals("Synced securely",thirdUpload.message);
            CloudSync.Result stale=CloudSync.evaluate(secondState,rev[0],cloud[0],second.backupJson(),commit);
            assertNull(stale.snapshot);
            assertTrue(stale.message.contains("conflict"));
            assertEquals(200,second.sum("Expense","2026-10")-2999);
            assertEquals(75,first.sum("Expense","2026-10")-2999);
        } finally {
            c.deleteDatabase(Store.databaseName(phone));
            c.deleteDatabase(Store.databaseName(laptop));
        }
    }

    @Test public void googleOAuthCallbackMustMatchSchemePathExpireAndBeSingleUse() {
        Context ctx=context();
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("moneymate_google_pkce",Context.MODE_PRIVATE);
        prefs.edit().clear().putString("verifier", "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG")
            .putLong("started",System.currentTimeMillis()).commit();
        assertNull(GoogleOAuth.consume(ctx,android.net.Uri.parse("https://evil.invalid/auth/callback?code=stolencode")));
        assertEquals("abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG",prefs.getString("verifier",null));
        assertNull(GoogleOAuth.consume(ctx,android.net.Uri.parse("moneymatenz://evil/callback?code=stolencode")));
        assertNull(GoogleOAuth.consume(ctx,android.net.Uri.parse("moneymatenz://auth/notcallback?code=stolencode")));
        String[] verified=GoogleOAuth.consume(ctx,android.net.Uri.parse("moneymatenz://auth/callback?code=sample_authorization_code"));
        assertNotNull(verified);
        assertEquals("sample_authorization_code",verified[0]);
        assertEquals("abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG",verified[1]);
        assertNull(GoogleOAuth.consume(ctx,android.net.Uri.parse("moneymatenz://auth/callback?code=sample_authorization_code")));
        prefs.edit().putString("verifier", "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG")
            .putLong("started",System.currentTimeMillis()-700000L).commit();
        assertNull(GoogleOAuth.consume(ctx,android.net.Uri.parse("moneymatenz://auth/callback?code=sample_authorization_code")));
    }
}
