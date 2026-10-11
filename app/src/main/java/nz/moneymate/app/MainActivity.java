package nz.moneymate.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.google.android.gms.ads.*;
import com.google.android.ump.*;

public final class MainActivity extends Activity {
    private Store store; private LinearLayout root, content; private AdView ad;
    private final int ink=Color.rgb(20,41,40), green=Color.rgb(6,120,91);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new Store(this); show("Home"); initAds();
    }
    private TextView text(String s,int size,boolean bold) {
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(ink);
        t.setPadding(16,13,16,13); if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
    private Button button(String name,Runnable act) {
        Button b=new Button(this); b.setText(name);b.setAllCaps(false);b.setOnClickListener(v->act.run());return b;
    }
    private void show(String page) {
        ScrollView scroll=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(1);
        root.setPadding(16,24,16,24);root.setBackgroundColor(Color.rgb(246,250,248));
        scroll.addView(root); setContentView(scroll);
        root.addView(text("MoneyMate NZ",30,true));
        root.addView(text("Your money. Your plan. Private by default.",13,false));
        HorizontalScrollView tabs=new HorizontalScrollView(this); LinearLayout links=new LinearLayout(this);links.setOrientation(0);
        for(String tab:new String[]{"Home","Transactions","Budgets","Goals","Bills","Export"}) links.addView(button(tab,()->show(tab)));
        tabs.addView(links);root.addView(tabs);
        content=new LinearLayout(this);content.setOrientation(1);root.addView(content);
        switch(page) {
            case "Transactions": transactions();break;
            case "Budgets": budgets();break;
            case "Goals": goals();break;
            case "Bills": bills();break;
            case "Export": exports();break;
            default: home();
        }
        if(ad!=null) { if(ad.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)ad.getParent()).removeView(ad);root.addView(ad); }
    }
    private void title(String s) {content.addView(text(s,23,true));}
    private void line(String s) { content.addView(text(s,16,false)); }
    private String month() {return YearMonth.now().toString();}
    private void home() {
        title("This month · "+month());
        long in=store.sum("Income",month()),out=store.sum("Expense",month());
        line("Income: "+MoneyMath.format(in));
        line("Expenses: "+MoneyMath.format(out));
        line("Remaining: "+MoneyMath.format(MoneyMath.remaining(in,out)));
        content.addView(button("+ Add income",()->transactionForm("Income")));
        content.addView(button("+ Add expense",()->transactionForm("Expense")));
        title("Spending categories");
        try(Cursor c=store.query("SELECT category,SUM(cents) FROM transactions WHERE kind='Expense' AND substr(date,1,7)=? GROUP BY category ORDER BY SUM(cents) DESC",month())) {
            if(c.getCount()==0)line("No expenses recorded yet.");
            while(c.moveToNext())line(c.getString(0)+"  ·  "+MoneyMath.format(c.getLong(1)));
        }
        title("Upcoming bills");
        try(Cursor c=store.query("SELECT name,cents,due FROM bills WHERE due>=? ORDER BY due LIMIT 5",LocalDate.now().toString())) {
            if(c.getCount()==0)line("No upcoming bills.");
            while(c.moveToNext())line(c.getString(2)+"  "+c.getString(0)+"  "+MoneyMath.format(c.getLong(1)));
        }
    }
    private EditText input(LinearLayout layout,String hint,boolean amount) {
        EditText e=new EditText(this);e.setHint(hint);
        if(amount)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(e);return e;
    }
    private void dialog(String label,String[] hints,boolean[] numbers,java.util.function.Consumer<String[]> save) {
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(24,8,24,8);
        EditText[] es=new EditText[hints.length];
        for(int i=0;i<hints.length;i++)es[i]=input(l,hints[i],numbers[i]);
        new AlertDialog.Builder(this).setTitle(label).setView(l).setNegativeButton("Cancel",null)
          .setPositiveButton("Save",(d,w)->{
              String[] values=new String[es.length];for(int i=0;i<es.length;i++)values[i]=es[i].getText().toString().trim();
              try {save.accept(values);}catch(Exception ex){new AlertDialog.Builder(this).setMessage("Could not save: "+ex.getMessage()).setPositiveButton("OK",null).show();}
          }).show();
    }
    private void transactionForm(String kind) {
        dialog("Add "+kind,new String[]{"Category (e.g. Groceries)","Note (optional)","Amount NZD","Date YYYY-MM-DD"},new boolean[]{false,false,true,false},v->{
            long cents=MoneyMath.toCents(v[2]);if(cents<=0)throw new IllegalArgumentException("Amount must be greater than zero");
            String date=v[3].isEmpty()?LocalDate.now().toString():LocalDate.parse(v[3]).toString();
            if(v[0].isEmpty())throw new IllegalArgumentException("Category is required");
            store.addTransaction(kind,v[0],v[1],cents,date);show("Transactions");
        });
    }
    private void transactions() {
        title("Transactions");
        content.addView(button("+ Income",()->transactionForm("Income")));
        content.addView(button("+ Expense",()->transactionForm("Expense")));
        try(Cursor c=store.query("SELECT id,date,kind,category,note,cents FROM transactions ORDER BY date DESC,id DESC LIMIT 300")) {
            if(c.getCount()==0)line("Add your first transaction.");
            while(c.moveToNext()){
                long id=c.getLong(0);
                String s=c.getString(1)+"  "+c.getString(2)+" · "+c.getString(3)+"\n"+c.getString(4)+"   "+MoneyMath.format(c.getLong(5));
                content.addView(button(s,()->new AlertDialog.Builder(this).setTitle("Delete transaction?")
                  .setMessage(s).setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{store.deleteTransaction(id);show("Transactions");}).show()));
            }
        }
    }
    private void budgets() {
        title("Monthly category budgets");
        content.addView(button("+ Set budget",()->dialog("Monthly budget",new String[]{"Category","Limit NZD"},new boolean[]{false,true},v->{
            long cents=MoneyMath.toCents(v[1]);if(v[0].isEmpty()||cents<0)throw new IllegalArgumentException("Enter category and nonnegative limit");
            store.addBudget(v[0],cents);show("Budgets");
        })));
        try(Cursor c=store.query("SELECT b.category,b.cents,COALESCE((SELECT SUM(t.cents) FROM transactions t WHERE t.kind='Expense' AND t.category=b.category AND substr(t.date,1,7)=?),0) FROM budgets b ORDER BY category",month())) {
            if(c.getCount()==0)line("No budget limits yet.");
            while(c.moveToNext()) {
                long used=c.getLong(2),limit=c.getLong(1);
                line(c.getString(0)+": "+MoneyMath.format(used)+" / "+MoneyMath.format(limit));
                ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(100);p.setProgress(limit==0?100:(int)Math.min(100,(used*100.0/limit)));content.addView(p);
            }
        }
    }
    private void goals() {
        title("Savings goals");
        content.addView(button("+ New goal",()->dialog("New savings goal",new String[]{"Goal name","Target NZD"},new boolean[]{false,true},v->{
            long amount=MoneyMath.toCents(v[1]);if(amount<=0||v[0].isEmpty())throw new IllegalArgumentException("Enter name and positive target");
            store.addGoal(v[0],amount);show("Goals");
        })));
        try(Cursor c=store.query("SELECT id,name,target,saved FROM goals ORDER BY id DESC")) {
            if(c.getCount()==0)line("No savings goals yet.");
            while(c.moveToNext()) {
                long id=c.getLong(0);String name=c.getString(1);
                line(name+"  "+MoneyMath.format(c.getLong(3))+" / "+MoneyMath.format(c.getLong(2)));
                content.addView(button("+ Add savings to "+name,()->dialog("Add savings",new String[]{"Amount NZD"},new boolean[]{true},v->{
                    long amount=MoneyMath.toCents(v[0]);if(amount<=0)throw new IllegalArgumentException("Positive amount required");
                    store.contribute(id,amount);show("Goals");
                })));
            }
        }
    }
    private void bills() {
        title("Bills & due dates");
        content.addView(button("+ Add bill",()->dialog("Add bill",new String[]{"Bill name","Amount NZD","Due YYYY-MM-DD"},new boolean[]{false,true,false},v->{
            long amount=MoneyMath.toCents(v[1]);String date=LocalDate.parse(v[2]).toString();
            if(v[0].isEmpty()||amount<=0)throw new IllegalArgumentException("Enter bill and positive amount");
            store.addBill(v[0],amount,date);show("Bills");
        })));
        try(Cursor c=store.query("SELECT name,cents,due FROM bills ORDER BY due")) {
            if(c.getCount()==0)line("Add your first bill.");
            while(c.moveToNext())line(c.getString(2)+"  ·  "+c.getString(0)+"  ·  "+MoneyMath.format(c.getLong(1)));
        }
    }
    private void exports() {
        title("Your data stays on this device");
        line("Export transactions as CSV to a location you choose. No account or cloud service required.");
        content.addView(button("Export CSV",()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");
            i.putExtra(Intent.EXTRA_TITLE,"MoneyMate-NZ-"+LocalDate.now()+".csv");startActivityForResult(i,100);
        }));
        line("Keep your export somewhere safe. Clearing app data or uninstalling removes local records.");
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if(code==100&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                out.write(String.join("\r\n",store.csvRows()).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this,"Export saved",Toast.LENGTH_LONG).show();
            }catch(Exception ex){Toast.makeText(this,"Export failed: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }
    private void initAds() {
        ConsentRequestParameters params=new ConsentRequestParameters.Builder().build();
        UserMessagingPlatform.getConsentInformation(this).requestConsentInfoUpdate(this,params,
          ()->UserMessagingPlatform.loadAndShowConsentFormIfRequired(this,error->{
              if(UserMessagingPlatform.getConsentInformation(this).canRequestAds())loadTestAd();
          }),
          error->{if(UserMessagingPlatform.getConsentInformation(this).canRequestAds())loadTestAd();});
    }
    private void loadTestAd() {
        MobileAds.initialize(this,status->{});
        ad=new AdView(this);ad.setAdSize(AdSize.BANNER);ad.setAdUnitId("ca-app-pub-3940256099942544/6300978111");
        ad.loadAd(new AdRequest.Builder().build());
        if(root!=null)root.addView(ad);
    }
    @Override protected void onDestroy(){if(ad!=null)ad.destroy();store.close();super.onDestroy();}
}
