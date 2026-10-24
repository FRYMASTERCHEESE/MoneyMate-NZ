package nz.moneymate.app;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlanningTest {
    private static final String OWNER="bbbbbbbb-1111-4111-8111-bbbbbbbbbbbb";
    @Test public void planningDoesNotDoubleSubtractBillsOrIncludeUnbudgetedExpenses(){
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ctx.deleteDatabase(Store.databaseName(OWNER));
        try(Store store=new Store(ctx,OWNER)){
            store.addBudget("Food",10000);store.addBudget("Transport",5000);
            store.addTransaction("Expense","Food","",12000,"2026-10-01");
            store.addTransaction("Expense","Other","",30000,"2026-10-02");
            store.addTransaction("Income","Food","",50000,"2026-10-02");
            store.addTransaction("Expense","Transport","",5000,"2026-09-30");
            store.addBill("Power",12345,"2026-10-31");store.addBill("Phone",5000,"2026-11-01");
            store.addGoal("Buffer",10000);store.contribute(1,12000);store.addGoal("Trip",10000);
            assertArrayEquals(new long[]{3000,12345,1,2},store.monthlyPlan("2026-10"));
            assertEquals(8000,store.sum("Income","2026-10")-store.sum("Expense","2026-10"));
        }finally{ctx.deleteDatabase(Store.databaseName(OWNER));}
    }
    @Test public void recurringCatchupRollsBackWholeRunWhenLimitExceeded(){
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ctx.deleteDatabase(Store.databaseName(OWNER));
        try(Store store=new Store(ctx,OWNER)){
            String start=LocalDate.now().minusWeeks(1201).toString();
            store.addRecurring("Expense","Rent","",100,start,"Weekly");
            assertThrows(IllegalArgumentException.class,store::postDueRecurring);
            try(android.database.Cursor rows=store.query("SELECT COUNT(*) FROM transactions")){assertTrue(rows.moveToFirst());assertEquals(0,rows.getInt(0));}
            try(android.database.Cursor rows=store.query("SELECT next_date FROM recurring")){assertTrue(rows.moveToFirst());assertEquals(start,rows.getString(0));}
        }finally{ctx.deleteDatabase(Store.databaseName(OWNER));}
    }
}
