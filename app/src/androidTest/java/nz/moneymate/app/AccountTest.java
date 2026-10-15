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
    @Test public void rejectedSavingsContributionPreservesTheEntireProfile() throws Exception {
        Context c=context();String owner="44444444-4444-4444-8444-444444444444";
        c.deleteDatabase(Store.databaseName(owner));
        try(Store s=new Store(c,owner)){
            s.addGoal("Synthetic limit test",100);
            s.contribute(1,9_007_199_254_740_991L);
            String before=s.backupJson();
            for(long amount:new long[]{1,-1,0,Long.MAX_VALUE}){
                try{s.contribute(1,amount);fail("Unsafe contribution accepted");}
                catch(IllegalArgumentException expected){}
                assertEquals(before,s.backupJson());
            }
            try{s.contribute(999,1);fail("Missing goal accepted");}
            catch(IllegalArgumentException expected){}
            assertEquals(before,s.backupJson());
        }finally{c.deleteDatabase(Store.databaseName(owner));}
    }
    @Test public void malformedBackupNeverReplacesExistingRecords() throws Exception {
        Context c=context();String owner="33333333-3333-4333-8333-333333333333";
        c.deleteDatabase(Store.databaseName(owner));
        try(Store s=new Store(c,owner)){
            s.addTransaction("Income","Wages","Synthetic",1234,"2026-10-13");
            String baseline=s.backupJson();
            for(Object bad:new Object[]{1.5,"1234",-1,9_007_199_254_740_992L}){
                JSONObject copy=new JSONObject(baseline);copy.getJSONArray("transactions").getJSONObject(0).put("cents",bad);
                try{s.restoreJson(copy.toString());fail("Invalid amount accepted");}catch(org.json.JSONException expected){}
                assertEquals(baseline,s.backupJson());
            }
            JSONObject copy=new JSONObject(baseline);copy.getJSONArray("transactions").getJSONObject(0).remove("id");
            try{s.restoreJson(copy.toString());fail("Missing ID accepted");}catch(org.json.JSONException expected){}
            assertEquals(baseline,s.backupJson());
            String csv=String.join("\n",s.csvRows());
            assertEquals(1,s.importBankCsv(BankCsv.parse(csv,"Daily"),false).skipped);
            assertEquals(baseline,s.backupJson());
        }finally{c.deleteDatabase(Store.databaseName(owner));}
    }
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
    @Test public void googleSessionSurvivesReopenAndRotatesExpiredTokens() throws Exception {
        Context c=context();SessionVault vault=new SessionVault(c);vault.clear();
        final int[] rotations={0};
        try {
            AuthClient.Transport fake=(path,method,body,bearer)->{
                if(path.contains("grant_type=pkce"))return session(A).put("expires_at",1);
                if(path.contains("grant_type=refresh_token")){
                    rotations[0]++;assertEquals("refresh",body.getString("refresh_token"));
                    return session(A).put("refresh_token","rotated-refresh").put("access_token","rotated-access");
                }
                if(path.equals("/auth/v1/user")){assertEquals("rotated-access",bearer);return new JSONObject().put("id",A);}
                throw new java.io.IOException("Unexpected request");
            };
            AuthClient login=new AuthClient(c,fake);
            login.exchangeGooglePkce("synthetic-code","abcdefghijklmnopqrstuvwxyz0123456789");
            AuthClient reopened=new AuthClient(c,fake);assertEquals(A,reopened.userId());
            assertTrue(reopened.validateOnline());assertEquals(1,rotations[0]);
            AuthClient reopenedAgain=new AuthClient(c,fake);
            assertEquals(A,reopenedAgain.userId());assertTrue(reopenedAgain.validateOnline());
            assertEquals(1,rotations[0]);assertEquals("rotated-refresh",new JSONObject(vault.read()).getString("refresh_token"));
        } finally {vault.clear();}
    }
    @Test public void unreadableSavedSessionIsLockedWithoutDeletingVault() throws Exception {
        SessionVault vault=new SessionVault(context());vault.clear();
        try {
            vault.write("temporarily unreadable session");
            AuthClient reopened=new AuthClient(context(),(path,method,body,bearer)->{throw new java.io.IOException("offline");});
            assertNull(reopened.userId());
            assertEquals("temporarily unreadable session",vault.read());
            reopened.clear();assertNull(vault.read());
        } finally {vault.clear();}
    }
    @Test public void emailSessionReopensOfflineThenPersistsRotatedToken() throws Exception {
        SessionVault vault=new SessionVault(context());vault.clear();
        final boolean[] offline={true};final int[] rotations={0};
        AuthClient.Transport fake=(path,method,body,bearer)->{
            if(path.contains("grant_type=password"))return session(A).put("expires_at",1);
            if(offline[0])throw new java.io.IOException("offline");
            if(path.contains("grant_type=refresh_token")){
                rotations[0]++;assertEquals("refresh",body.getString("refresh_token"));
                return session(A).put("access_token","rotated-email-access").put("refresh_token","rotated-email-refresh");
            }
            if(path.equals("/auth/v1/user")){assertEquals("rotated-email-access",bearer);return new JSONObject().put("id",A);}
            throw new java.io.IOException("Unexpected request");
        };
        try {
            new AuthClient(context(),fake).signin("test@example.invalid","long sample password");
            String saved=vault.read();AuthClient reopened=new AuthClient(context(),fake);
            assertEquals(A,reopened.userId());assertTrue(reopened.validateOnline());assertEquals(saved,vault.read());
            offline[0]=false;assertTrue(reopened.validateOnline());assertEquals(1,rotations[0]);
            assertEquals("rotated-email-refresh",new JSONObject(vault.read()).getString("refresh_token"));
            assertTrue(new AuthClient(context(),fake).validateOnline());assertEquals(1,rotations[0]);
        } finally {vault.clear();}
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

    @Test public void clientRetainsEncryptedAccountAfterActivityRestartAndSignsOut() throws Exception {
        Context c=context();
        SessionVault vault=new SessionVault(c);vault.clear();
        try {
            AuthClient.Transport fake=(path,method,body,bearer)->{
                if(path.contains("grant_type=password"))return session(A);
                if(path.equals("/auth/v1/user"))return new JSONObject().put("id",A);
                if(path.contains("/logout?scope=global"))return new JSONObject();
                throw new java.io.IOException("Unexpected request");
            };
            AuthClient login=new AuthClient(c,fake);
            assertNull(login.userId());
            login.signin("test@example.invalid","long sample password");
            assertEquals(A,login.userId());
            AuthClient reopened=new AuthClient(c,fake);
            assertEquals(A,reopened.userId());
            assertTrue(reopened.validateOnline());
            reopened.signout();
            assertNull(new AuthClient(c,fake).userId());
        } finally {vault.clear();}
    }
}
