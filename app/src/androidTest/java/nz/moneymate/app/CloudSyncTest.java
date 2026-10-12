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
}
