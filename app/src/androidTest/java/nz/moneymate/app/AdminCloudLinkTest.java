package nz.moneymate.app;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AdminCloudLinkTest {
    private static JSONObject session(String email) throws Exception {
        return new JSONObject().put("access_token","synthetic-token")
            .put("refresh_token","synthetic-refresh")
            .put("expires_in",3600)
            .put("user",new JSONObject().put("id","11111111-1111-4111-8111-111111111111").put("email",email));
    }
    @Test public void adminRequiresServerGrantedRoleNotEmailText() throws Exception {
        AuthClient denied=new AuthClient((path,method,body,bearer)->{
            if(path.contains("grant_type=password"))return session("admin@example.test");
            throw new AuthClient.HttpFailure(403,"No admin membership");
        });
        denied.signin("admin@example.test","synthetic-password");
        try{denied.adminOverview();fail("Email-only administration must never work");}
        catch(AuthClient.HttpFailure expected){assertEquals(403,expected.status);}
        AuthClient allowed=new AuthClient((path,method,body,bearer)->{
            if(path.contains("grant_type=password"))return session("admin@example.test");
            if(path.equals("/rest/v1/rpc/moneymate_admin_overview")) {
                assertEquals("POST",method);
                assertEquals("synthetic-token",bearer);
                return new JSONObject().put("role","admin").put("registered_users",4)
                    .put("verified_users",4).put("google_users",2)
                    .put("registrations_last_7_days",4).put("cloud_sync_accounts",1);
            }
            throw new IllegalStateException("Unexpected request");
        });
        allowed.signin("admin@example.test","synthetic-password");
        assertEquals(4,allowed.adminOverview().getInt("registered_users"));
    }
    @Test public void existingCloudLinksOnlyToEmptyDeviceWithoutAutomaticOverwrite() throws Exception {
        CloudSync.Metadata disabled=new CloudSync.Metadata(false,0,"");
        CloudSync.Metadata enabled=new CloudSync.Metadata(true,1,"fingerprint");
        {
            // Pure policy test; no live account, network or device storage.
            JSONObject empty=new JSONObject().put("format","MoneyMateNZ").put("version",1);
            for(String name:new String[]{"transactions","budgets","goals","bills","recurring"})
                empty.put(name,new org.json.JSONArray());
            assertTrue(CloudSync.shouldAutoLinkExisting(disabled,2,empty.toString()));
            assertFalse(CloudSync.shouldAutoLinkExisting(disabled,0,empty.toString()));
            assertFalse(CloudSync.shouldAutoLinkExisting(enabled,2,empty.toString()));
            empty.getJSONArray("transactions").put(new JSONObject().put("id",1));
            assertFalse(CloudSync.shouldAutoLinkExisting(disabled,2,empty.toString()));
        }
    }
}
