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
    private int ink=ThemeConfig.DEFAULT.ink, green=ThemeConfig.DEFAULT.accent;
    private int canvasColor=ThemeConfig.DEFAULT.background, heroColor=ThemeConfig.DEFAULT.hero;
    private String tagline=ThemeConfig.DEFAULT.tagline;
    private String currentPage="Home";
    private long lastThemeCheck=0L;
    private static final String THEME_URL="https://frymastercheese.github.io/MoneyMate-NZ/app-theme.json";
    private static final String THEME_KEY="remote_theme_v1";
    private AuthClient auth;
    private boolean accountBusy=false;
    private final java.util.concurrent.ExecutorService accountWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private String documentOwner;
    private long lastAccountCheck;
    private long lastCloudAttempt;
    private boolean cloudBusy=false,ignoreNextAutoSync=false;
    private String cloudStatus="Cloud syncing is not enabled";
    private boolean rewardsBusy=false,rewardsRequested=false,dailyClaimed=false;
    private long rewardPoints=-1;
    private String rewardAccount=null;
    private final android.os.Handler cloudTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable periodicCloudCheck=new Runnable(){
        @Override public void run() {
            if(!isFinishing()&&!isDestroyed()) {
                if(auth!=null&&auth.userId()!=null)requestCloudSync(false);
                cloudTimer.postDelayed(this,45000L);
            }
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); auth=new AuthClient(this);store=new Store(this,auth.userId());
        try {
            applyTheme(ThemeConfig.parse(getPreferences(MODE_PRIVATE).getString(THEME_KEY,"")));
        }catch(Exception ignored) {applyTheme(ThemeConfig.DEFAULT);}
        store.postDueRecurring(); show("Home");
        handleGoogleCallback(getIntent());
        if(!(BuildConfig.DEBUG&&getIntent().getBooleanExtra("captureMode",false)))initAds();
    }
    private void applyTheme(ThemeConfig config) {
        ink=config.ink;green=config.accent;canvasColor=config.background;
        heroColor=config.hero;tagline=config.tagline;
    }
    @Override protected void onResume() {
        super.onResume();
        if(auth.userId()!=null&&AuthClient.configured()&&!accountBusy&&System.currentTimeMillis()-lastAccountCheck>60000L) {
            lastAccountCheck=System.currentTimeMillis();final Store checked=store;
            accountWorker.execute(()->{
                boolean valid=auth.validateOnline();
                if(!valid)runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&!accountBusy&&store==checked){
                    auth.clear();
                    // Recreate also closes any old transaction/restore dialogs so they cannot write to another profile.
                    Toast.makeText(this,"Session ended. Sign in again to open retained account records.",Toast.LENGTH_LONG).show();recreate();
                }});
            });
        }
        cloudTimer.removeCallbacks(periodicCloudCheck);
        cloudTimer.postDelayed(periodicCloudCheck,45000L);
        if(auth.userId()!=null)requestCloudSync(false);
        // Automatically check the public theme on launch/return.
        if(!(BuildConfig.DEBUG&&getIntent().getBooleanExtra("captureMode",false)) &&
           System.currentTimeMillis()-lastThemeCheck > 60000L) refreshTheme(false);
    }
    @Override protected void onPause(){cloudTimer.removeCallbacks(periodicCloudCheck);super.onPause();}
    private void refreshTheme(boolean userRequested) {
        lastThemeCheck=System.currentTimeMillis();
        new Thread(()->{
            java.net.HttpURLConnection conn=null;
            try {
                conn=(java.net.HttpURLConnection)new java.net.URL(THEME_URL).openConnection();
                conn.setConnectTimeout(3500);conn.setReadTimeout(3500);
                conn.setRequestProperty("Cache-Control","no-cache");
                if(conn.getResponseCode()!=200)throw new java.io.IOException("Theme service unavailable");
                java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
                try(java.io.InputStream input=conn.getInputStream()) {
                    byte[] chunk=new byte[1024];int n;
                    while((n=input.read(chunk))!=-1) {
                        if(buf.size()+n>8192)throw new java.io.IOException("Theme file too large");
                        buf.write(chunk,0,n);
                    }
                }
                String payload=new String(buf.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
                ThemeConfig downloaded=ThemeConfig.parse(payload);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    String old=getPreferences(MODE_PRIVATE).getString(THEME_KEY,"");
                    if(!payload.equals(old)) {
                        getPreferences(MODE_PRIVATE).edit().putString(THEME_KEY,payload).apply();
                        applyTheme(downloaded);
                        show(currentPage);
                        if(userRequested)Toast.makeText(this,"Design updated",Toast.LENGTH_SHORT).show();
                    } else if(userRequested)Toast.makeText(this,"Design is up to date",Toast.LENGTH_SHORT).show();
                });
            }catch(Exception error) {
                if(userRequested)runOnUiThread(()->Toast.makeText(this,
                    "Could not check design. Your saved theme still works offline.",Toast.LENGTH_LONG).show());
            }finally{if(conn!=null)conn.disconnect();}
        },"MoneyMateTheme").start();
    }
    private int dp(float n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private android.graphics.drawable.GradientDrawable surface(int color,int radius) {
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView text(String s,int size,boolean bold) {
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(ink);
        t.setPadding(dp(16),dp(12),dp(16),dp(12));if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
    private Button button(String name,Runnable act) {
        Button b=new Button(this);b.setText(name);b.setTextSize(14);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setMinHeight(dp(52));
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33000000),surface(green,16),null));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(4),dp(5),dp(4),dp(5));b.setLayoutParams(lp);
        b.setOnClickListener(v->act.run());return b;
    }
    private Button record(String label,Runnable action) {
        Button b=button(label,action);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x2208785b),surface(Color.WHITE,18),null));
        b.setTextColor(ink);b.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);b.setElevation(dp(1));return b;
    }
    private LinearLayout card() {
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(8),dp(8),dp(8),dp(8));
        l.setBackground(surface(Color.WHITE,24));l.setElevation(dp(2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(4),dp(8),dp(4),dp(12));l.setLayoutParams(lp);return l;
    }
    private void empty(String heading,String detail) {
        LinearLayout l=card();Illustration art=new Illustration(this);l.addView(art,new LinearLayout.LayoutParams(-1,dp(130)));
        l.addView(text(heading,20,true));l.addView(text(detail,14,false));content.addView(l);
    }
    private void show(String page) {
        if(accountBusy)return;
        currentPage=page;
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(1);frame.setBackgroundColor(canvasColor);
        frame.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(frame);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(12),dp(16),dp(24));scroll.addView(root);
        TextView brand=text("◈  MoneyMate NZ",24,true);root.addView(brand);
        TextView subtitle=text(tagline,13,false);subtitle.setTextColor(0xff60746d);root.addView(subtitle);
        content=new LinearLayout(this);content.setOrientation(1);root.addView(content);
        switch(page) {
            case "Transactions": transactions();break;case "Budgets": budgets();break;
            case "Goals": goals();break;case "Bills": bills();break;case "Export": exports();break;case "Account": account();break;case "Banking": banking();break;case "Rewards": rewards();break;case "Help": help();break;default: home();
        }
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);tabs.setBackgroundColor(Color.WHITE);
        LinearLayout links=new LinearLayout(this);links.setOrientation(0);
        final View[] selectedTab=new View[1];
        for(String tab:new String[]{"Home","Transactions","Budgets","Goals","Bills","Export","Account","Banking","Rewards","Help"}) {
            Button nav=button(tab,()->show(tab));nav.setBackground(surface(tab.equals(page)?green:Color.WHITE,16));
            nav.setTextColor(tab.equals(page)?Color.WHITE:ink);nav.setElevation(0);if(tab.equals(page))selectedTab[0]=nav;links.addView(nav,new LinearLayout.LayoutParams(-2,dp(56)));
        }
        tabs.addView(links);frame.addView(tabs);tabs.post(()->tabs.scrollTo(Math.max(0,selectedTab[0].getLeft()-dp(16)),0));
        if(ad!=null) {if(ad.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)ad.getParent()).removeView(ad);root.addView(ad);}
        if(ignoreNextAutoSync)ignoreNextAutoSync=false;
        else if(auth.userId()!=null&&System.currentTimeMillis()-lastCloudAttempt>2500L)requestCloudSync(false);
    }
    private void title(String s) {content.addView(text(s,23,true));}
    private void line(String s) { LinearLayout c=card();c.addView(text(s,16,false));content.addView(c); }
    private String month() {return YearMonth.now().toString();}
    private void home() {
        title(YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",java.util.Locale.forLanguageTag("en-NZ"))));
        long in=store.sum("Income",month()),out=store.sum("Expense",month());
        LinearLayout balance=card();balance.setBackground(surface(heroColor,24));
        TextView label=text("AVAILABLE THIS MONTH · NZD",12,true);label.setTextColor(0xffbce7d6);balance.addView(label);
        TextView amount=text(MoneyMath.format(MoneyMath.remaining(in,out)),38,true);amount.setTextColor(Color.WHITE);balance.addView(amount);
        TextView totals=text("Income  "+MoneyMath.format(in)+"\nSpent    "+MoneyMath.format(out),16,false);totals.setTextColor(Color.WHITE);balance.addView(totals);content.addView(balance);
        if(in==0&&out==0)empty("Welcome to your money plan","Start with your income, then add expenses as you go. Your financial records stay on this device.");
        content.addView(button("+ Add income",()->transactionForm("Income")));
        content.addView(button("+ Add expense",()->transactionForm("Expense")));
        title("Spending categories");
        try(Cursor c=store.query("SELECT category,SUM(cents) FROM transactions WHERE kind='Expense' AND substr(date,1,7)=? GROUP BY category ORDER BY SUM(cents) DESC",month())) {
            if(c.getCount()==0)empty("Make spending visible","Add an expense to see your category breakdown here.");
            java.util.ArrayList<String> names=new java.util.ArrayList<>();java.util.ArrayList<Long> values=new java.util.ArrayList<>();
            while(c.moveToNext()){names.add(c.getString(0));values.add(c.getLong(1));}
            if(!values.isEmpty()) {
                LinearLayout chart=card();chart.addView(new SpendingChart(this,values),new LinearLayout.LayoutParams(-1,dp(220)));
                long total=0;for(long value:values)total+=value;
                for(int i=0;i<names.size();i++) {
                    TextView row=text("●  "+names.get(i)+"   "+MoneyMath.format(values.get(i))+"  ·  "+Math.round(values.get(i)*100.0/total)+"%",14,false);
                    row.setTextColor(SpendingChart.COLORS[i%SpendingChart.COLORS.length]);chart.addView(row);
                }
                content.addView(chart);
            }
        }
        try(Cursor late=store.query("SELECT name,cents,due FROM bills WHERE due<? ORDER BY due LIMIT 5",LocalDate.now().toString())) {
            if(late.moveToFirst()) {
                title("Overdue bills");
                do {
                    String name=late.getString(0),amountLabel=MoneyMath.format(late.getLong(1));
                    content.addView(record("⚠  "+name+" · "+amountLabel+" · due "+late.getString(2),()->show("Bills")));
                } while(late.moveToNext());
            }
        }
        title("Upcoming bills");
        try(Cursor c=store.query("SELECT name,cents,due FROM bills WHERE due>=? ORDER BY due LIMIT 5",LocalDate.now().toString())) {
            if(c.getCount()==0)empty("A clear view of what’s next","Add bills and their due dates to plan ahead.");
            while(c.moveToNext())line(c.getString(2)+"  "+c.getString(0)+"  "+MoneyMath.format(c.getLong(1)));
        }
    }
    private EditText input(LinearLayout layout,String hint,boolean amount) {
        EditText e=new EditText(this);e.setHint(hint);
        if(amount)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setMinHeight(dp(52));e.setTextColor(ink);
        layout.addView(e);return e;
    }
    private void dialog(String label,String[] hints,boolean[] numbers,java.util.function.Consumer<String[]> save) {
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(24),dp(8),dp(24),dp(8));
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
        content.addView(button("+ Set recurring payment",()->recurringForm()));
        content.addView(button("Import NZD bank CSV for free",()->chooseBankCsv()));
        title("Recurring schedules");
        try(Cursor schedule=store.query("SELECT id,kind,category,cents,next_date,frequency FROM recurring ORDER BY next_date")) {
            if(schedule.getCount()==0)empty("Build a steady routine","Schedule regular income or expenses once.");
            while(schedule.moveToNext()) {
                long id=schedule.getLong(0);
                String name=schedule.getString(1)+" · "+schedule.getString(2)+" · "+MoneyMath.format(schedule.getLong(3))+" · "+schedule.getString(5)+" · next "+schedule.getString(4);
                content.addView(record(name+"  (tap to stop)",()->new AlertDialog.Builder(this)
                    .setTitle("Stop this recurring transaction?").setMessage(name)
                    .setNegativeButton("Cancel",null).setPositiveButton("Stop",(d,w)->{store.deleteRecurring(id);show("Transactions");}).show()));
            }
        }
        title("Transaction history");
        EditText search=input(content,"Search date, category, type or note",false);
        search.setSingleLine(true);
        search.setContentDescription("Search transactions");
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout history=new LinearLayout(this);
        history.setOrientation(1);
        content.addView(history);
        renderTransactionHistory(history,"");
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                renderTransactionHistory(history,s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s){}
        });
    }
    /** Parameterised search treats user input literally, including % and _ characters. */
    private void renderTransactionHistory(LinearLayout history,String term) {
        history.removeAllViews();
        String needle=term.trim();
        try(Cursor c=store.query("SELECT id,date,kind,category,note,cents FROM transactions "
                +"WHERE (?='' OR instr(lower(category),lower(?))>0 OR instr(lower(note),lower(?))>0 "
                +"OR instr(lower(kind),lower(?))>0 OR instr(date,?)>0) "
                +"ORDER BY date DESC,id DESC LIMIT 300",needle,needle,needle,needle,needle)) {
            if(c.getCount()==0) {
                history.addView(text(needle.isEmpty()?"No transactions yet. Add income or an expense.":"No matching transactions.",16,false));
            }
            while(c.moveToNext()) {
                long id=c.getLong(0);
                String summary=c.getString(1)+"  "+c.getString(2)+" · "+c.getString(3)
                    +"\n"+c.getString(4)+"   "+MoneyMath.format(c.getLong(5));
                history.addView(record(summary,()->new AlertDialog.Builder(this)
                  .setTitle("Delete transaction?").setMessage(summary)
                  .setNegativeButton("Cancel",null)
                  .setPositiveButton("Delete",(d,w)->{store.deleteTransaction(id);show("Transactions");}).show()));
            }
        }
    }
    private void recurringForm() {
        final String[] kinds={"Expense","Income"};
        final String[] frequencies={"Weekly","Fortnightly","Monthly"};
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(24),dp(8),dp(24),dp(8));
        Spinner kind=new Spinner(this);kind.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,kinds));l.addView(kind);
        EditText category=input(l,"Category",false);
        EditText note=input(l,"Note (optional)",false);
        EditText amount=input(l,"Amount NZD",true);
        EditText date=input(l,"First date YYYY-MM-DD",false);date.setText(LocalDate.now().toString());
        Spinner frequency=new Spinner(this);frequency.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,frequencies));l.addView(frequency);
        new AlertDialog.Builder(this).setTitle("New repeating transaction").setView(l)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
                try {
                    long cents=MoneyMath.toCents(amount.getText().toString());
                    if(cents<=0||category.getText().toString().trim().isEmpty())throw new IllegalArgumentException("Category and positive amount required");
                    store.addRecurring(kinds[kind.getSelectedItemPosition()],category.getText().toString().trim(),
                        note.getText().toString().trim(),cents,
                        LocalDate.parse(date.getText().toString().trim()).toString(),
                        frequencies[frequency.getSelectedItemPosition()]);
                    store.postDueRecurring();show("Transactions");
                }catch(Exception ex){new AlertDialog.Builder(this).setMessage("Could not save: "+ex.getMessage()).setPositiveButton("OK",null).show();}
            }).show();
    }
    private void budgets() {
        title("Monthly category budgets");
        content.addView(button("+ Set budget",()->dialog("Monthly budget",new String[]{"Category","Limit NZD"},new boolean[]{false,true},v->{
            long cents=MoneyMath.toCents(v[1]);if(v[0].isEmpty()||cents<0)throw new IllegalArgumentException("Enter category and nonnegative limit");
            store.addBudget(v[0],cents);show("Budgets");
        })));
        try(Cursor c=store.query("SELECT b.category,b.cents,COALESCE((SELECT SUM(t.cents) FROM transactions t WHERE t.kind='Expense' AND t.category=b.category AND substr(t.date,1,7)=?),0) FROM budgets b ORDER BY category",month())) {
            if(c.getCount()==0)empty("Give every category a plan","Set a monthly limit and follow your progress.");
            while(c.moveToNext()) {
                long used=c.getLong(2),limit=c.getLong(1);
                LinearLayout budget=card();budget.addView(text(c.getString(0),20,true));
                budget.addView(text(MoneyMath.format(used)+" spent of "+MoneyMath.format(limit),15,false));
                budget.addView(text(used>limit?MoneyMath.format(used-limit)+" over budget":MoneyMath.format(limit-used)+" left",14,true));
                ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setProgressTintList(android.content.res.ColorStateList.valueOf(used>limit?0xffb84f38:green));p.setMax(100);p.setProgress(limit==0?(used>0?100:0):(int)Math.min(100,(used*100.0/limit)));budget.addView(p);content.addView(budget);
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
            if(c.getCount()==0)empty("Make room for something good","Name a goal and start saving towards it.");
            while(c.moveToNext()) {
                long id=c.getLong(0);String name=c.getString(1);
                LinearLayout goal=card();goal.addView(text(name,20,true));goal.addView(text(MoneyMath.format(c.getLong(3))+" saved of "+MoneyMath.format(c.getLong(2)),15,false));
                ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
                progress.setProgressTintList(android.content.res.ColorStateList.valueOf(green));progress.setMax(100);
                progress.setProgress((int)Math.min(100,c.getLong(3)*100.0/c.getLong(2)));goal.addView(progress);content.addView(goal);
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
        line("MoneyMate tracks bills only. Recording one as paid adds an expense, but does not make a bank payment.");
        try(Cursor c=store.query("SELECT id,name,cents,due FROM bills ORDER BY due")) {
            if(c.getCount()==0)empty("Stay ahead of due dates","Add your first bill to keep it in view.");
            while(c.moveToNext()) {
                long id=c.getLong(0),cents=c.getLong(2);
                String name=c.getString(1),date=c.getString(3);
                long days=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(date));
                String timing=days<0?"OVERDUE by "+(-days)+" days"
                    :days==0?"DUE TODAY":days<=7?"Due in "+days+" days":"Due "+date;
                LinearLayout item=card();
                item.addView(text(name+"  ·  "+MoneyMath.format(cents),19,true));
                TextView due=text(timing,15,true);due.setTextColor(days<0?0xffae4237:days<=7?0xffa06b15:green);
                item.addView(due);
                item.addView(button("Record as paid",()->new AlertDialog.Builder(this)
                  .setTitle("Record bill as paid?")
                  .setMessage("Add a "+MoneyMath.format(cents)+" Bills expense for "+name
                      +" and remove this bill from your list? This does NOT transfer money.")
                  .setNegativeButton("Cancel",null)
                  .setPositiveButton("Record payment",(d,w)->{
                      try {
                          if(store.recordBillPaid(id,LocalDate.now().toString()))show("Bills");
                          else message("This bill was already removed.");
                      }catch(Exception error){message("Could not record this payment.");}
                  }).show()));
                item.addView(record("Remove bill without recording payment",()->new AlertDialog.Builder(this)
                  .setTitle("Remove this bill?").setMessage(name+" will be removed. No expense will be recorded.")
                  .setNegativeButton("Cancel",null)
                  .setPositiveButton("Remove",(d,w)->{store.deleteBill(id);show("Bills");}).show()));
                content.addView(item);
            }
        }
    }
    private void exports() {
        title("Your data stays on this device");
        line("Export or restore records for the current profile. Signed-in users can enable cloud sync in Account. Please keep JSON backups; different edits made offline on two devices may need manual conflict resolution.");
        content.addView(button("Export CSV",()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");
            i.putExtra(Intent.EXTRA_TITLE,"MoneyMate-NZ-"+LocalDate.now()+".csv");startActivityForResult(i,100);
        }));
        content.addView(button("Import bank CSV (NZD, free)",()->chooseBankCsv()));
        content.addView(button("Backup all data (JSON)",()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE,"MoneyMate-NZ-backup-"+LocalDate.now()+".json");
            startActivityForResult(i,101);
        }));
        content.addView(button("Restore full backup (replaces existing data)",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            startActivityForResult(i,102);
        }));
        content.addView(button("↻ Refresh app colours & text",()->refreshTheme(true)));
        line("Colour and tagline changes published to the MoneyMate NZ website can appear here without a new APK. New features still need an app update.");
        content.addView(button("Advertising privacy choices",()->{
            UserMessagingPlatform.showPrivacyOptionsForm(this,error->{
                if(error!=null)Toast.makeText(this,"Choices unavailable: "+error.getMessage(),Toast.LENGTH_LONG).show();
            });
        }));
        line("Backups contain sensitive financial information. Store them securely. Restoring replaces current records.");
    }
    /** The Android document picker reads a statement locally; no file or banking details
     * are sent to a provider. Import is opt-in and additive, never a JSON restore.
     */
    private void chooseBankCsv(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/comma-separated-values","text/plain","application/vnd.ms-excel"});
        startActivityForResult(intent,103);
    }
    private void previewBankCsv(Intent chosen) {
        final String owner=Store.databaseName(auth.userId());
        final String contents;
        try(java.io.InputStream in=getContentResolver().openInputStream(chosen.getData())){
            if(in==null)throw new java.io.IOException("Cannot read selected file");
            java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
            byte[] block=new byte[8192];int n;
            while((n=in.read(block))!=-1){
                if(buf.size()+n>2_000_000)throw new IllegalArgumentException("CSV is larger than 2 MB");
                buf.write(block,0,n);
            }
            contents=new String(buf.toByteArray(),StandardCharsets.UTF_8);
        }catch(Exception ex){message("Could not read bank CSV: "+ex.getMessage());return;}
        EditText nickname=new EditText(this);
        nickname.setSingleLine(true);nickname.setText("Everyday");
        nickname.setHint("Account nickname, never account number");
        nickname.setPadding(dp(24),dp(14),dp(24),dp(14));
        new AlertDialog.Builder(this).setTitle("Free NZD bank statement import")
          .setMessage("Choose a nickname for this account, such as Everyday or Savings. Reuse it when importing this account again; use different nicknames for separate bank accounts. This file must contain NZD, NOT AUD.")
          .setView(nickname).setNegativeButton("Cancel",null)
          .setPositiveButton("Preview",(dialog,button)->{
              if(!owner.equals(Store.databaseName(auth.userId()))){message("Account changed. Choose the file again.");return;}
              try{
                  List<BankCsv.Entry> entries=BankCsv.parse(contents,nickname.getText().toString());
                  Store.BankImportResult planned=store.importBankCsv(entries,true);
                  if(planned.inserted==0){message("Nothing to add. "+planned.skipped+" bank entries were already imported.");return;}
                  StringBuilder preview=new StringBuilder();
                  preview.append(entries.size()).append(" rows in the CSV\n")
                      .append(planned.inserted).append(" new NZD transactions\n")
                      .append(planned.skipped).append(" existing duplicates\n\n");
                  for(int i=0;i<Math.min(3,entries.size());i++){
                      BankCsv.Entry row=entries.get(i);
                      preview.append(row.date).append(" · ").append(row.kind).append(" · ")
                          .append(MoneyMath.format(row.cents)).append(" · ").append(row.category).append("\n");
                  }
                  preview.append("\nImport only if the bank statement is NZD. Entries are added without deleting local records. Keep a JSON backup. Indistinguishable identical bank transactions can be hard to distinguish.");
                  new AlertDialog.Builder(this).setTitle("Review bank CSV import")
                    .setMessage(preview.toString()).setNegativeButton("Cancel",null)
                    .setPositiveButton("Add transactions",(d,which)->{
                        if(!owner.equals(Store.databaseName(auth.userId()))){
                            message("Account changed. Bank CSV was not imported.");return;
                        }
                        try{
                            Store.BankImportResult done=store.importBankCsv(entries,false);
                            show("Transactions");
                            message("Added "+done.inserted+" NZD transactions; skipped "+done.skipped+" duplicates. Existing records were not replaced.");
                        }catch(Exception error){message("Bank CSV import failed safely: "+error.getMessage());}
                    }).show();
              }catch(Exception error){message("CSV preview failed: "+error.getMessage());}
          }).show();
    }
    @Override public void startActivityForResult(Intent intent,int requestCode) {
        if(requestCode>=100&&requestCode<=103)documentOwner=Store.databaseName(auth.userId());
        super.startActivityForResult(intent,requestCode);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleGoogleCallback(intent);
    }
    private void handleGoogleCallback(Intent intent) {
        if(intent==null||intent.getData()==null)return;
        android.net.Uri uri=intent.getData();
        if(!"moneymatenz".equals(uri.getScheme())||!"auth".equals(uri.getHost()))return;
        intent.setData(null); // Consume deep-link code once; never keep in activity state.
        String[] pkce=GoogleOAuth.consume(this,uri);
        if(pkce==null){message("Google sign-in expired or could not be completed. Try again.");return;}
        accountTask(()->auth.exchangeGooglePkce(pkce[0],pkce[1]),()->{
            switchProfile();
            message("Google account connected. Your existing records remain protected. You can enable cloud sync under Account.");
        },false);
    }
    private void switchProfile() {
        rewardPoints=-1;rewardsRequested=false;rewardAccount=null;dailyClaimed=false;
        store.close();store=new Store(this,auth.userId());store.postDueRecurring();show("Account");
    }

    private void banking(){
        title("Bank connections · NZ & Australia");
        line("Free NZD bank statement imports are available from a CSV file. Download your statement from your bank and choose the file below. No bank login or provider fee is required.");
        content.addView(button("Import NZD bank statement CSV",()->chooseBankCsv()));
        line("Automatic bank connections are NOT LIVE. AUD statement imports are blocked until separate AUD budgets are available.");
        line("New Zealand: accredited Akahu open banking. Australia: Basiq Consumer Data Right. Both require approved provider onboarding before people can connect.");
        content.addView(button("About Akahu (NZ)",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.akahu.nz/")))));
        content.addView(button("About Basiq (Australia)",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.basiq.io/")))));
        line("Never enter banking passwords in MoneyMate NZ. Future consent will be read-only and revocable. AUD amounts must stay separate from NZD until currency conversion is supported.");
    }
    /** Simple in-app guide. Accessible without an account or network. */
    private void help(){
        title("How to use MoneyMate NZ");
        line("MoneyMate NZ helps you track income, expenses, budgets, savings goals and bills. It does not move money or pay bills. Use the navigation tabs at the bottom to open each section.");
        title("1. Sign in or use a local profile");
        line("Open Account to sign in using Google (when available) or email, or continue without signing in. To share your points or enable private cloud sync across devices, use the same account on Android and the website.");
        title("2. Add income or expenses");
        line("Home or Transactions → + Income / + Expense. Choose a category, enter the amount in NZD, and save the entry. Home displays monthly income, spending and balance.");
        title("3. Import bank CSV for free");
        line("Download a CSV statement in NZD from your bank's official website or app. Choose Banking → Import NZD bank statement CSV, or Transactions / Export → Import bank CSV. Enter a consistent nickname for the account (not its account number), review the preview and tap Add transactions. This never asks for bank passwords and does not download new transactions automatically.");
        line("Only NZD files are supported. AUD files are blocked. Recognised duplicates are skipped but truly identical transactions can be hard to distinguish. Save a JSON backup before large imports.");
        title("4. Plan with budgets and goals");
        line("Budgets → set a monthly amount for a category. Goals → create a target and add contributions as you save. Bills → add amounts and due dates; Record as paid adds an expense without sending money.");
        title("5. Earn your free daily points");
        line("Sign in and open Rewards. Tap Claim 10 points once per UTC day. Points share with your account, but they have no cash value and cannot be redeemed, transferred or withdrawn.");
        title("6. Sync Android with the website");
        line("First save a JSON backup separately on BOTH devices in Export. Then use the same signed-in account on each. On the first device tap Account → Enable secure cloud sync; wait for confirmation. On the second device also enable sync. Sync happens while online and when edits are saved. If there is a conflict, export both copies first and do not replace data blindly.");
        title("7. Back up and restore safely");
        line("Export → Backup all data (JSON) preserves your current profile. Export CSV saves transaction rows for spreadsheets. Restore full backup (JSON) REPLACES existing profile records; bank CSV import only ADDS new transactions.");
        title("8. Help and privacy");
        line("Automatic live bank connections are not yet available. For login or syncing problems, retry online and check the same account on both devices. Never share a bank password, account token, or financial export publicly.");
        content.addView(button("Open full help guide on website",()->startActivity(new Intent(
            Intent.ACTION_VIEW,android.net.Uri.parse("https://frymastercheese.github.io/MoneyMate-NZ/help.html")))));
        content.addView(button("Read privacy policy",()->startActivity(new Intent(
            Intent.ACTION_VIEW,android.net.Uri.parse("https://frymastercheese.github.io/MoneyMate-NZ/privacy.html")))));
    }
    private void rewards(){
        title("MoneyMate Points");
        line("Collect 10 free points for one daily check-in (UTC). No spending or bank connection required. Points are not money and cannot be redeemed, withdrawn or transferred.");
        final String id=auth.userId();
        if(id==null){line("Sign in with Google or email to collect points.");content.addView(button("Open Account",()->show("Account")));return;}
        if(!id.equals(rewardAccount)){rewardAccount=id;rewardPoints=-1;rewardsRequested=false;dailyClaimed=false;}
        if(rewardPoints<0){
            line(rewardsBusy?"Fetching your points balance…":"Points have not been loaded or the service is unavailable.");
            content.addView(button("Refresh points",()->loadRewards(true)));
            if(!rewardsRequested&&!rewardsBusy)loadRewards(false);
            return;
        }
        line("Your points: "+rewardPoints);
        line(dailyClaimed?"Today's points are collected; return after the next UTC date begins.":"Your daily 10 points are ready.");
        if(!dailyClaimed)content.addView(button("Claim 10 points",()->claimRewards()));
        content.addView(button("Refresh points",()->loadRewards(true)));
    }
    private void loadRewards(boolean notify){
        final String id=auth.userId();if(id==null||rewardsBusy)return;
        rewardsRequested=true;rewardsBusy=true;
        accountWorker.execute(()->{
            org.json.JSONObject answer=null;String issue=null;
            try{answer=auth.pointsStatus();}catch(Exception ex){issue="Points service unavailable. Please retry later.";}
            final org.json.JSONObject result=answer;final String failure=issue;
            runOnUiThread(()->{
                try{
                    if(isFinishing()||isDestroyed()||!id.equals(auth.userId()))return;
                    if(failure!=null){if(notify)message(failure);return;}
                    if(result==null||result.optLong("points",-1)<0||result.optInt("daily_points")!=10)throw new IllegalStateException("Invalid points response");
                    rewardAccount=id;rewardPoints=result.getLong("points");dailyClaimed=result.getBoolean("daily_claimed");
                }catch(Exception ex){if(notify)message("Could not check your points.");}
                finally{
                    rewardsBusy=false;
                    if(!isFinishing()&&!isDestroyed()&&id.equals(auth.userId())&&currentPage.equals("Rewards"))show("Rewards");
                }
            });
        });
    }
    private void claimRewards(){
        final String id=auth.userId();if(id==null||rewardsBusy)return;
        rewardsBusy=true;
        accountWorker.execute(()->{
            org.json.JSONObject answer=null;String issue=null;
            try{answer=auth.claimDailyPoints();}catch(Exception ex){issue="Could not claim points. Try again later.";}
            final org.json.JSONObject result=answer;final String failure=issue;
            runOnUiThread(()->{
                try{
                    if(isFinishing()||isDestroyed()||!id.equals(auth.userId()))return;
                    if(failure!=null){message(failure);return;}
                    int granted=result.getInt("awarded");
                    if(result.optLong("points",-1)<0||(granted!=0&&granted!=10)||!result.getBoolean("daily_claimed"))
                        throw new IllegalStateException("Invalid claim response");
                    rewardAccount=id;rewardPoints=result.getLong("points");dailyClaimed=true;
                    message(granted==10?"You earned 10 points!":"Today's points were already collected.");
                }catch(Exception ex){message("Could not verify points balance.");}
                finally{
                    rewardsBusy=false;
                    if(!isFinishing()&&!isDestroyed()&&id.equals(auth.userId())&&currentPage.equals("Rewards"))show("Rewards");
                }
            });
        });
    }

    private void account() {
        title("Your MoneyMate account");
        line("Your records stay local unless you opt into secure Supabase cloud sync. When enabled, Android and the website can share records under the same verified account. Changes on two devices at once can cause a safe sync conflict.");
        content.addView(button("Privacy policy",()->new AlertDialog.Builder(this).setTitle("Privacy and accounts")
            .setMessage("Email and authentication details are processed by Supabase. Passwords are not stored in this app. Session tokens are encrypted with Android Keystore. Financial records stay in app-private storage unless you explicitly enable Supabase cloud sync. Cloud sync stores a separate copy in Sydney, Australia. Signing out hides your local account records; it does not erase them. Backups you export remain under your control. Google advertising and public design downloads also use network services. Read the full policy for retention and deletion details.")
            .setPositiveButton("Read full policy",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://frymastercheese.github.io/MoneyMate-NZ/privacy.html"))))
            .setNegativeButton("Close",null).show()));
        if(auth.userId()==null) {
            line("Local profile • your existing records are preserved");
            if(!AuthClient.configured()){line("Accounts are not available in this build yet. Local budgeting and backups still work.");return;}
            content.addView(button("Sign in",()->credentialsForm(false)));
            if(BuildConfig.GOOGLE_ENABLED)content.addView(button("Continue with Google",()->{
                try{startActivity(GoogleOAuth.start(this));}
                catch(Exception failure){message("Google sign-in is unavailable. Please use email sign-in.");}
            }));
            content.addView(button("Create account",()->credentialsForm(true)));
            content.addView(button("Confirm email",()->emailCodeForm(false)));
            content.addView(button("Resend confirmation code",()->emailRequest(false)));
            content.addView(button("Forgot password",()->emailRequest(true)));
            content.addView(button("Reset password with email code",()->emailCodeForm(true)));
        } else {
            line("Signed in as "+auth.email());
            CloudSync.Metadata cloud=CloudSync.metadata(this,auth.userId());
            line(cloudStatus+" · "+(cloud.enabled?"Cloud sync enabled":"Cloud sync disabled"));
            if(!cloud.enabled)content.addView(button("Enable secure cloud sync",()->new AlertDialog.Builder(this)
                .setTitle("Back up MoneyMate NZ to the cloud?")
                .setMessage("Your private financial records will be uploaded to Supabase in Sydney, Australia and become available on your other devices using the same account. This is optional. Keep a full JSON backup first. Existing cloud data is never silently overwritten. Continue?")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Enable",(d,w)->{
                    try {
                        CloudSync.persist(this,auth.userId(),new CloudSync.Metadata(true,0,""));
                        cloudStatus="Cloud sync opted in; checking for existing cloud records";
                        requestCloudSync(true);
                    }catch(Exception error){message("Cloud setup failed. Local data was not changed.");}
                }).show()));
            else content.addView(button("Sync records now",()->requestCloudSync(true)));
            if(cloud.enabled&&cloudStatus.toLowerCase(java.util.Locale.ROOT).contains("conflict"))
                content.addView(button("Use cloud copy after backup",()->new AlertDialog.Builder(this)
                  .setTitle("Replace this device's finances?")
                  .setMessage("Export a full JSON backup first. This will copy newer cloud records to this device. Unsynced entries could disappear from the active profile. We will keep a local recovery copy. Have you saved a backup?")
                  .setNegativeButton("Cancel",null)
                  .setPositiveButton("Continue",(d,w)->new AlertDialog.Builder(this)
                    .setTitle("Final confirmation")
                    .setMessage("Replace local finances with the cloud copy for this account?")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Use cloud copy",(e,t)->restoreCloudCopy()).show()).show()));
            content.addView(button("Sign out",()->new AlertDialog.Builder(this).setTitle("Sign out?")
                .setMessage("Your account records remain on this device and are hidden until you sign in again. The separate local profile will open. If offline, this device still signs out; remote sessions may remain active.")
                .setNegativeButton("Cancel",null).setPositiveButton("Sign out",(d,w)->accountTask(()->auth.signout(),()->{switchProfile();},true)).show()));
            if(store.isEmpty())content.addView(button("Import existing local records",()->new AlertDialog.Builder(this).setTitle("Copy local records into this account?")
                .setMessage("This copies all records from the original local profile into this empty account profile. The original local records remain intact. Only import records that belong to you.")
                .setNegativeButton("Cancel",null).setPositiveButton("Import",(d,w)->{
                    try(Store original=new Store(this)){if(!store.isEmpty())throw new IllegalStateException("Account profile must be empty");store.restoreJson(original.backupJson());show("Account");}
                    catch(Exception error){message("Import failed. Your original records are safe.");}
                }).show()));
            content.addView(button("Delete account permanently",()->deleteForm()));
        }
    }
    private void restoreCloudCopy() {
        final String owner=auth.userId();
        if(owner==null||cloudBusy||accountBusy)return;
        final String baseline;
        try {baseline=store.backupJson();}
        catch(Exception error){message("Could not save recovery data; nothing changed.");return;}
        cloudBusy=true;
        accountWorker.execute(()->{
            org.json.JSONObject remote=null;String error=null;
            try {remote=auth.readAccountSnapshot();}
            catch(Exception ex){error="Cloud copy could not be downloaded. Your data is safe.";}
            final org.json.JSONObject received=remote;
            final String failure=error;
            runOnUiThread(()->{
                try {
                    if(isFinishing()||isDestroyed()||!owner.equals(auth.userId()))return;
                    if(failure!=null){message(failure);return;}
                    if(received==null||received.optLong("revision")<1||received.optJSONObject("data")==null)
                        throw new IllegalStateException("Cloud copy missing");
                    if(!CloudSync.digest(store.backupJson()).equals(CloudSync.digest(baseline))){
                        message("Local records changed during download. Nothing was replaced.");return;
                    }
                    CloudSync.recoveryCopy(this,owner,baseline);
                    store.restoreJson(received.getJSONObject("data").toString());
                    CloudSync.persist(this,owner,new CloudSync.Metadata(true,received.getLong("revision"),
                        CloudSync.digest(store.backupJson())));
                    cloudStatus="Cloud records restored. Previous finances saved in a local recovery copy.";
                    ignoreNextAutoSync=true;show("Account");
                    message(cloudStatus);
                }catch(Exception ex){message("Cloud restore stopped. Existing records remain available; check your backup.");}
                finally{cloudBusy=false;}
            });
        });
    }
    private void requestCloudSync(boolean notify) {
        final String user=auth.userId();
        if(user==null||cloudBusy||accountBusy)return;
        final CloudSync.Metadata baseline=CloudSync.metadata(this,user);
        if(!baseline.enabled)return;
        final String before;
        try {before=store.backupJson();}
        catch(Exception failure){if(notify)message("Could not read local data for syncing");return;}
        cloudBusy=true;lastCloudAttempt=System.currentTimeMillis();
        accountWorker.execute(()->{
            CloudSync.Result result=null;String problem=null;
            try{result=CloudSync.synchronize(auth,baseline,before);}
            catch(Exception error){problem="Cloud unavailable. Your saved local records are safe.";}
            final CloudSync.Result value=result;final String failure=problem;
            runOnUiThread(()->{
                try {
                    if(isFinishing()||isDestroyed()||!user.equals(auth.userId()))return;
                    if(failure!=null){cloudStatus=failure;if(notify)message(failure);return;}
                    String fresh=store.backupJson();
                    if(value.snapshot!=null) {
                        // Never replace changes made on this phone while the request was running.
                        if(!CloudSync.digest(fresh).equals(CloudSync.digest(before))){
                            cloudStatus="Local records changed while syncing; download postponed.";
                            return;
                        }
                        CloudSync.recoveryCopy(this,user,fresh);
                        store.restoreJson(value.snapshot);
                        CloudSync.persist(this,user,new CloudSync.Metadata(true,value.state.revision,CloudSync.digest(store.backupJson())));
                    }else if(value.state!=baseline)CloudSync.persist(this,user,value.state);
                    cloudStatus=value.message;
                    if(notify||cloudStatus.toLowerCase(java.util.Locale.ROOT).contains("conflict"))
                        Toast.makeText(this,cloudStatus,Toast.LENGTH_LONG).show();
                    if(value.snapshot!=null||currentPage.equals("Account")){
                        ignoreNextAutoSync=true;show(currentPage);
                    }
                }catch(Exception failure2){
                    cloudStatus="Cloud sync could not be completed; local records kept.";
                    if(notify)message(cloudStatus);
                }finally{cloudBusy=false;}
            });
        });
    }
    private LinearLayout form(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(dp(24),dp(8),dp(24),dp(8));return layout;}
    private EditText emailInput(LinearLayout layout){EditText field=input(layout,"Email",false);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);return field;}
    private EditText passwordInput(LinearLayout layout,String hint){EditText field=input(layout,hint,false);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return field;}
    private String emailValue(EditText field){String value=field.getText().toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches())throw new IllegalArgumentException("Enter a valid email address");return value;}
    private void credentialsForm(boolean signup) {
        LinearLayout layout=form();EditText email=emailInput(layout),password=passwordInput(layout,signup?"Password (at least 8 characters)":"Password");
        CheckBox consent=new CheckBox(this);consent.setText("I have read the privacy policy and agree to create an account");if(signup)layout.addView(consent);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(signup?"Create account":"Sign in").setView(layout).setNegativeButton("Cancel",null).setPositiveButton(signup?"Create account":"Sign in",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                String address=emailValue(email),secret=password.getText().toString();
                if(signup){if(!consent.isChecked())throw new IllegalArgumentException("Please read and accept the privacy policy");AuthClient.checkPassword(secret);}
                password.setText("");dialog.dismiss();
                accountTask(()->{if(signup)auth.signup(address,secret);else auth.signin(address,secret);},()->{
                    if(signup)message("If registration is available, an email code will arrive. Use Confirm email, then sign in.");else switchProfile();
                },false);
            }catch(Exception error){email.setError(error.getMessage());}
        }));dialog.show();
    }
    private void emailRequest(boolean recovery) {
        LinearLayout layout=form();EditText email=emailInput(layout);
        new AlertDialog.Builder(this).setTitle(recovery?"Request password reset":"Resend confirmation").setView(layout).setNegativeButton("Cancel",null)
            .setPositiveButton("Send code",(d,w)->{try{String address=emailValue(email);accountTask(()->{if(recovery)auth.recover(address);else auth.resend(address);},()->message("If eligible, a code will arrive by email. Check your inbox and spam folder."),false);}catch(Exception error){message(error.getMessage());}}).show();
    }
    private void emailCodeForm(boolean recovery) {
        LinearLayout layout=form();EditText email=emailInput(layout),code=input(layout,"Email code",false);code.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText password=recovery?passwordInput(layout,"New password (at least 8 characters)"):null;
        new AlertDialog.Builder(this).setTitle(recovery?"Reset password":"Confirm email").setView(layout).setNegativeButton("Cancel",null)
            .setPositiveButton(recovery?"Reset password":"Confirm",(d,w)->{try{
                String address=emailValue(email),token=code.getText().toString().trim(),secret=recovery?password.getText().toString():"";
                if(recovery){AuthClient.checkPassword(secret);password.setText("");}
                accountTask(()->{if(recovery)auth.reset(address,token,secret);else auth.confirm(address,token);},()->message(recovery?"Password reset. Sign in with your new password.":"Email confirmed. You can now sign in."),false);
            }catch(Exception error){message(error.getMessage());}}).show();
    }
    private void deleteForm() {
        LinearLayout layout=form();layout.addView(text("Permanently deletes your Supabase account and this account's local records on this device. Other devices and exported files must be cleared separately. Your original local profile is preserved. This cannot be undone.",14,false));
        EditText password=passwordInput(layout,"Current password"),confirm=input(layout,"Type DELETE",false);
        new AlertDialog.Builder(this).setTitle("Delete account permanently").setView(layout).setNegativeButton("Cancel",null).setPositiveButton("Delete account",(d,w)->{
            if(!"DELETE".equals(confirm.getText().toString())){message("Type DELETE to confirm");return;}
            String secret=password.getText().toString(),database=Store.databaseName(auth.userId());password.setText("");
            accountTask(()->auth.deleteAccount(secret),()->{
                store.close();auth.clear();deleteDatabase(database);store=new Store(this);show("Account");message("Account deleted. Exported files and records on other devices must be removed separately.");
            },false);
        }).show();
    }
    private interface AccountWork {void run() throws Exception;}
    private void accountTask(AccountWork work,Runnable success,boolean localSignout) {
        if(accountBusy)return;accountBusy=true;
        int priorOrientation=getRequestedOrientation();setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        AlertDialog progress=new AlertDialog.Builder(this).setMessage("Contacting account service…").setCancelable(false).create();progress.show();
        accountWorker.execute(()->{
            String failure=null;try{work.run();}catch(Exception error){failure=error instanceof IllegalArgumentException?error.getMessage():"Could not complete the account request. Check your connection and details, or request a fresh code.";}
            final String problem=failure;
            runOnUiThread(()->{
                accountBusy=false;if(isFinishing()||isDestroyed())return;progress.dismiss();
                if(problem==null)success.run();else if(localSignout){switchProfile();message("Signed out on this device. Remote session revocation could not be confirmed.");}else message(problem);
                setRequestedOrientation(priorOrientation);
            });
        });
    }
    private void message(String value){new AlertDialog.Builder(this).setMessage(value).setPositiveButton("OK",null).show();}
    @Override public void onBackPressed(){if(!accountBusy)super.onBackPressed();}
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if(code>=100&&code<=103&&!Store.databaseName(auth.userId()).equals(documentOwner)){message("Profile changed. Please choose the file again.");return;}
        if(result==RESULT_OK&&data!=null&&data.getData()!=null&&code==100){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                out.write(String.join("\r\n",store.csvRows()).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this,"Export saved",Toast.LENGTH_LONG).show();
            }catch(Exception ex){Toast.makeText(this,"Export failed: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        } else if(code==101&&result==RESULT_OK&&data!=null&&data.getData()!=null) {
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                out.write(store.backupJson().getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this,"Complete backup saved",Toast.LENGTH_LONG).show();
            }catch(Exception ex){Toast.makeText(this,"Backup failed: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        } else if(code==103&&result==RESULT_OK&&data!=null&&data.getData()!=null) {
            previewBankCsv(data);
        } else if(code==102&&result==RESULT_OK&&data!=null&&data.getData()!=null) {
            try(java.io.InputStream in=getContentResolver().openInputStream(data.getData())) {
                java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
                byte[] block=new byte[8192];int n;
                while((n=in.read(block))!=-1) {
                    if(buf.size()+n>20_000_000)throw new IllegalArgumentException("Backup exceeds 20 MB");
                    buf.write(block,0,n);
                }
                String backup=new String(buf.toByteArray(),StandardCharsets.UTF_8);
                new AlertDialog.Builder(this).setTitle("Replace all local data?")
                    .setMessage("Your existing transactions, budgets, goals and bills will be replaced. Continue?")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Restore",(dialog,which)->{
                        try{store.restoreJson(backup);show("Home");Toast.makeText(this,"Restore completed",Toast.LENGTH_LONG).show();}
                        catch(Exception ex){new AlertDialog.Builder(this).setTitle("Restore failed").setMessage(ex.getMessage()).setPositiveButton("OK",null).show();}
                    }).show();
            }catch(Exception ex){Toast.makeText(this,"Could not read backup: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
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
        ad=new AdView(this);ad.setAdSize(AdSize.BANNER);ad.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
        ad.loadAd(new AdRequest.Builder().build());
        if(root!=null)root.addView(ad);
    }
    @Override protected void onDestroy(){if(ad!=null)ad.destroy();accountWorker.shutdown();store.close();super.onDestroy();}
}
