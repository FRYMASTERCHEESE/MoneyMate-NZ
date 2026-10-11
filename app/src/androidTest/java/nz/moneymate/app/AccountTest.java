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
}
