package nz.moneymate.app;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FortnightPlanTest {
    private static final String OWNER="4fb391fe-9128-498d-b607-ab81e59b42f1";

    @Test public void SharedWebFixturesMatchEveryTotalAndEventWithoutWrites() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        org.json.JSONArray fixtures;
        try(java.io.InputStream input=InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("fortnight-plan-cases.json")) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
            fixtures=new org.json.JSONArray(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
        }
        for(int i=0;i<fixtures.length();i++) {
            org.json.JSONObject fixture=fixtures.getJSONObject(i);
            String label=fixture.getString("name");
            ctx.deleteDatabase(Store.databaseName(OWNER));
            try(Store store=new Store(ctx,OWNER)) {
                // Only the isolated synthetic account is seeded. The calculation is read-only.
                store.restoreJson(fixture.getJSONObject("data").toString());
                String before=store.backupJson();
                LocalDate start=LocalDate.parse(fixture.getString("start"));
                int days=fixture.getInt("days");
                if(fixture.has("error")) {
                    assertThrows(label,IllegalArgumentException.class,()->FortnightPlan.calculate(store,start,days));
                }else{
                    FortnightPlan.Summary actual=FortnightPlan.calculate(store,start,days);
                    org.json.JSONObject expected=fixture.getJSONObject("expected");
                    assertEquals(label,expected.getString("start"),actual.start.toString());
                    assertEquals(label,expected.getString("end"),actual.end.toString());
                    assertEquals(label,expected.getLong("incoming"),actual.incoming);
                    assertEquals(label,expected.getLong("outgoing"),actual.outgoing);
                    assertEquals(label,expected.getLong("net"),actual.net);
                    org.json.JSONArray events=expected.getJSONArray("events");
                    assertEquals(label,events.length(),actual.events.size());
                    for(int j=0;j<events.length();j++) {
                        org.json.JSONObject event=events.getJSONObject(j);
                        FortnightPlan.Event entry=actual.events.get(j);
                        assertEquals(label,event.getString("date"),entry.date.toString());
                        assertEquals(label,event.getString("type"),entry.type);
                        assertEquals(label,event.getString("name"),entry.name);
                        assertEquals(label,event.getString("kind"),entry.kind);
                        assertEquals(label,event.getLong("cents"),entry.cents);
                    }
                }
                assertEquals(label,before,store.backupJson());
            }finally{ctx.deleteDatabase(Store.databaseName(OWNER));}
        }
    }

    @Test public void NZFortnightIncludesScheduledPaydaysAndUnpaidBillsWithoutDatabaseWrites() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ctx.deleteDatabase(Store.databaseName(OWNER));
        try(Store store=new Store(ctx,OWNER)) {
            store.addBill("Power",5000,"2026-11-07");
            store.addBill("Phone",2500,"2026-10-25");
            store.addBill("Last month",1500,"2026-10-24");
            store.addBill("Later",4000,"2026-11-08");
            store.addRecurring("Income","Pay","",25000,"2026-10-26","Weekly");
            store.addRecurring("Expense","Groceries","",4000,"2026-10-28","Fortnightly");
            store.addRecurring("Expense","Internet","",10000,"2026-11-01","Monthly");
            String before=store.backupJson();
            FortnightPlan.Summary future=FortnightPlan.calculate(store,LocalDate.of(2026,10,25),14);
            assertEquals(LocalDate.of(2026,11,7),future.end);
            assertEquals(50000L,future.incoming);
            assertEquals(21500L,future.outgoing);
            assertEquals(28500L,future.net);
            List<String> dates=new ArrayList<>();
            for(FortnightPlan.Event e:future.events)dates.add(e.date.toString());
            assertEquals(Arrays.asList("2026-10-25","2026-10-26","2026-10-28","2026-11-01","2026-11-02","2026-11-07"),dates);
            assertEquals(before,store.backupJson());
        }finally{ctx.deleteDatabase(Store.databaseName(OWNER));}
    }

    @Test public void MonthlyRecurrenceClampsLeapFebruaryAndNeverPostsTransactions() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ctx.deleteDatabase(Store.databaseName(OWNER));
        try(Store store=new Store(ctx,OWNER)) {
            store.addRecurring("Income","Pay","",10000,"2028-01-31","Monthly");
            FortnightPlan.Summary lookahead=FortnightPlan.calculate(store,LocalDate.of(2028,1,30),31);
            assertEquals(2,lookahead.events.size());
            assertEquals(LocalDate.of(2028,1,31),lookahead.events.get(0).date);
            assertEquals(LocalDate.of(2028,2,29),lookahead.events.get(1).date);
            assertEquals(20000L,lookahead.incoming);
            assertEquals(0L,lookahead.outgoing);
            try(android.database.Cursor c=store.query("SELECT COUNT(*) FROM transactions")) {
                assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0));
            }
            assertTrue(FortnightPlan.calculate(store,LocalDate.of(2028,2,1),14).events.isEmpty());
            assertThrows(IllegalArgumentException.class,()->FortnightPlan.calculate(store,LocalDate.of(2028,2,1),32));
        }finally{ctx.deleteDatabase(Store.databaseName(OWNER));}
    }

    @Test public void ExistingRepeatingRulesAreNotChangedDuringProjection() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ctx.deleteDatabase(Store.databaseName(OWNER));
        try(Store store=new Store(ctx,OWNER)) {
            store.addRecurring("Expense","Rent","",20000,"2026-10-25","Weekly");
            String before=store.backupJson();
            FortnightPlan.Summary future=FortnightPlan.calculate(store,LocalDate.of(2026,10,25),14);
            assertEquals(40000L,future.outgoing);
            assertEquals(2,future.events.size());
            assertEquals(before,store.backupJson());
        }finally{ctx.deleteDatabase(Store.databaseName(OWNER));}
    }
}
