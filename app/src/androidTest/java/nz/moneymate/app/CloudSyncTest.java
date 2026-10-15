package nz.moneymate.app;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** Offline regression coverage for the same revision/consent rules used on the website. */
@RunWith(AndroidJUnit4.class)
public class CloudSyncTest {
    private final String empty="{\"format\":\"MoneyMateNZ\",\"version\":1,\"transactions\":[],\"budgets\":[],\"goals\":[],\"bills\":[],\"recurring\":[]}";
    private String changed() throws Exception {
        JSONObject copy=new JSONObject(empty);
        copy.getJSONArray("transactions").put(new JSONObject()
          .put("id",1).put("date","2026-10-12").put("kind","Expense")
          .put("category","Food").put("note","Test").put("cents",1500));
        return copy.toString();
    }
    @Test public void noCloudCommitWithoutOptIn() throws Exception {
        CloudSync.Metadata disabled=new CloudSync.Metadata(false,0,"");
        CloudSync.Result result=CloudSync.evaluate(disabled,0,null,changed(),(revision,body)->{
            fail("Disabled cloud sync attempted upload");return null;
        });
        assertEquals("Cloud sync is off",result.message);
        assertNull(result.snapshot);
    }
    @Test public void initialUploadAndFreshDeviceDownload() throws Exception {
        String fromAndroid=changed();
        CloudSync.Result uploaded=CloudSync.evaluate(new CloudSync.Metadata(true,0,""),0,null,fromAndroid,(revision,body)->{
            assertEquals(0L,revision.longValue());
            assertEquals("Food",body.optJSONArray("transactions").optJSONObject(0).optString("category"));
            return new JSONObject(java.util.Map.of("success",true,"revision",1));
        });
        assertEquals(1,uploaded.state.revision);
        assertEquals(CloudSync.digest(fromAndroid),uploaded.state.fingerprint);
        CloudSync.Result onWebsitePhone=CloudSync.evaluate(new CloudSync.Metadata(true,0,""),1,new JSONObject(fromAndroid),empty,(revision,body)->{
            fail("Fresh device should download, not upload");return null;
        });
        assertNotNull(onWebsitePhone.snapshot);
        assertEquals(1,onWebsitePhone.state.revision);
    }
    @Test public void differingExistingFinancialRecordsNeedManualResolution() throws Exception {
        CloudSync.Result result=CloudSync.evaluate(new CloudSync.Metadata(true,0,""),2,new JSONObject(changed()),changed(),(revision,body)->{
            fail("Existing data should never overwrite cloud");return null;
        });
        assertNull(result.snapshot);
        assertTrue(result.message.contains("conflict"));
    }
    @Test public void remoteChangesDownloadOnlyWhenLocalStillUnmodified() throws Exception {
        String baseline=changed();
        CloudSync.Metadata state=new CloudSync.Metadata(true,1,CloudSync.digest(baseline));
        CloudSync.Result downloaded=CloudSync.evaluate(state,2,new JSONObject(baseline),baseline,(revision,body)->{
            fail("New cloud revision must be downloaded, not uploaded");return null;
        });
        assertNotNull(downloaded.snapshot);
        CloudSync.Result conflict=CloudSync.evaluate(state,2,new JSONObject(baseline),empty,(revision,body)->{
            fail("Unsynced edits must not be uploaded");return null;
        });
        assertNull(conflict.snapshot);
        assertTrue(conflict.message.contains("conflict"));
    }
    @Test public void rejectedRevisionKeepsLocalSnapshot() throws Exception {
        String copy=changed();
        CloudSync.Metadata state=new CloudSync.Metadata(true,2,CloudSync.digest(empty));
        CloudSync.Result result=CloudSync.evaluate(state,2,new JSONObject(empty),copy,(revision,body)->new JSONObject(java.util.Map.of("success",false,"revision",3)));
        assertNull(result.snapshot);
        assertEquals(2,state.revision);
        assertTrue(result.message.contains("conflict"));
    }
    @Test public void metadataIsolatedByUserIdentity() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String u1="11111111-1111-4111-8111-111111111111",u2="22222222-2222-4222-8222-222222222222";
        CloudSync.Metadata previous1=CloudSync.metadata(context,u1),previous2=CloudSync.metadata(context,u2);
        try {
            CloudSync.persist(context,u1,new CloudSync.Metadata(true,4,"fingerprint"));
            assertEquals(4,CloudSync.metadata(context,u1).revision);
            assertEquals(previous2.revision,CloudSync.metadata(context,u2).revision);
            assertEquals(previous2.enabled,CloudSync.metadata(context,u2).enabled);
        }finally {
            CloudSync.persist(context,u1,previous1);
            CloudSync.persist(context,u2,previous2);
        }
    }

    /** Two isolated databases stand in for two phones; both authenticate as the same account.
     * Only synthetic records and an in-memory revision-checked server are used.
     */
    @Test public void twoPhonesRoundTripAllRecordsAndPreserveConflictingEdits() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String account="55555555-5555-4555-8555-555555555555";
        String deviceFile="66666666-6666-4666-8666-666666666666";
        context.deleteDatabase(Store.databaseName(account));context.deleteDatabase(Store.databaseName(deviceFile));
        final long[] revision={0};final JSONObject[] cloud={null};
        AuthClient.Transport server=(path,method,body,bearer)->{
            if(path.contains("grant_type=password"))return new JSONObject().put("access_token","synthetic-access")
                .put("refresh_token","synthetic-refresh").put("expires_in",3600)
                .put("user",new JSONObject().put("id",account));
            assertEquals("synthetic-access",bearer);
            if(path.endsWith("/read_account_snapshot"))return new JSONObject().put("revision",revision[0])
                .put("data",cloud[0]==null?JSONObject.NULL:new JSONObject(cloud[0].toString()));
            if(path.endsWith("/commit_account_snapshot")) {
                boolean accepted=body.getLong("p_expected_revision")==revision[0];
                if(accepted){cloud[0]=new JSONObject(body.getJSONObject("p_data").toString());revision[0]++;}
                return new JSONObject().put("success",accepted).put("revision",revision[0]);
            }
            throw new java.io.IOException("Unexpected request");
        };
        AuthClient phoneA=new AuthClient(server),phoneB=new AuthClient(server);
        phoneA.signin("synthetic@example.invalid","synthetic password");
        phoneB.signin("synthetic@example.invalid","synthetic password");
        assertEquals(phoneA.userId(),phoneB.userId());
        try(Store a=new Store(context,account);Store b=new Store(context,deviceFile)) {
            a.addTransaction("Expense","Food","Synthetic",1234,"2026-10-13");
            a.addBudget("Food",30000);a.addGoal("Emergency",100000);a.contribute(1,5000);
            a.addBill("Power",12000,"2026-10-20");
            a.addRecurring("Income","Salary","Synthetic",100000,"2099-10-20","Weekly");
            CloudSync.Metadata stateA=syncDevice(phoneA,new CloudSync.Metadata(true,0,""),a);
            CloudSync.Metadata stateB=syncDevice(phoneB,new CloudSync.Metadata(true,0,""),b);
            assertEquals(a.backupJson(),b.backupJson());assertEquals(1,revision[0]);
            b.contribute(1,2000);b.deleteBill(1);
            stateB=syncDevice(phoneB,stateB,b);stateA=syncDevice(phoneA,stateA,a);
            assertEquals(2,revision[0]);assertEquals(a.backupJson(),b.backupJson());
            a.addTransaction("Expense","Transport","Phone A offline edit",400,"2026-10-14");
            b.addBudget("Transport",8000);stateB=syncDevice(phoneB,stateB,b);
            String localBefore=a.backupJson(),cloudBefore=cloud[0].toString();
            CloudSync.Result conflict=CloudSync.synchronize(phoneA,stateA,localBefore);
            assertNull(conflict.snapshot);assertTrue(conflict.message.contains("conflict"));
            assertEquals(localBefore,a.backupJson());assertEquals(cloudBefore,cloud[0].toString());
            assertEquals(3,revision[0]);assertEquals(3,stateB.revision);
        } finally {context.deleteDatabase(Store.databaseName(account));context.deleteDatabase(Store.databaseName(deviceFile));}
    }
    private CloudSync.Metadata syncDevice(AuthClient auth,CloudSync.Metadata state,Store store) throws Exception {
        CloudSync.Result result=CloudSync.synchronize(auth,state,store.backupJson());
        if(result.snapshot!=null)store.restoreJson(result.snapshot);
        return new CloudSync.Metadata(true,result.state.revision,CloudSync.digest(store.backupJson()));
    }
}
