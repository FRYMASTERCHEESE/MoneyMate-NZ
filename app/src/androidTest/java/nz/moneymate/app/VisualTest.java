package nz.moneymate.app;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.File;
import java.time.LocalDate;

@RunWith(AndroidJUnit4.class)
public class VisualTest {
    @Test public void captureRealScreensAndVerifyRecords() throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();Context ctx=inst.getTargetContext();
        ctx.deleteDatabase("moneymate.db");
        UiDevice device=UiDevice.getInstance(inst);device.setOrientationNatural();
        Intent launch=new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity activity=inst.startActivitySync(launch);inst.waitForIdleSync();
        assertTrue(device.wait(Until.hasObject(By.text("Welcome to your money plan")),10000));
        File dir=new File(ctx.getExternalFilesDir(null),"screenshots");dir.mkdirs();
        assertTrue(device.takeScreenshot(new File(dir,"01-welcome.png")));
        final android.app.Activity first=activity;inst.runOnMainSync(()->first.finish());inst.waitForIdleSync();
        String today=LocalDate.now().toString();
        try(Store data=new Store(ctx)) {
            data.addTransaction("Income","Salary","Example data",480000,today);
            data.addTransaction("Expense","Groceries","Example data",62000,today);
            data.addTransaction("Expense","Rent","Example data",180000,today);
            data.addTransaction("Expense","Transport","Example data",18500,today);
            data.addBudget("Groceries",80000);data.addBudget("Transport",25000);
            data.addGoal("Summer getaway",200000);data.contribute(1,75000);
            data.addBill("Power",14500,LocalDate.now().plusDays(5).toString());
        }
        activity=inst.startActivitySync(launch);inst.waitForIdleSync();
        assertTrue(device.wait(Until.hasObject(By.text("AVAILABLE THIS MONTH · NZD")),10000));
        assertTrue(device.takeScreenshot(new File(dir,"02-home.png")));
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollIntoView(new UiSelector().textContains("Rent   "));
        assertTrue(device.takeScreenshot(new File(dir,"03-spending.png")));
        for(String page:new String[]{"Transactions","Budgets","Goals","Bills","Export"}) {
            UiObject tab=device.findObject(new UiSelector().className("android.widget.Button").text(page));
            if(!tab.exists()) {
                UiScrollable nav=new UiScrollable(new UiSelector().className("android.widget.HorizontalScrollView"));nav.setAsHorizontalList();nav.scrollIntoView(new UiSelector().text(page));
            }
            tab.click();inst.waitForIdleSync();
            assertTrue(device.takeScreenshot(new File(dir,page.toLowerCase()+".png")));
        }
        try(Store data=new Store(ctx)){assertEquals(480000,data.sum("Income",today.substring(0,7)));assertEquals(260500,data.sum("Expense",today.substring(0,7)));}
        final android.app.Activity last=activity;inst.runOnMainSync(()->last.finish());
    }
}
