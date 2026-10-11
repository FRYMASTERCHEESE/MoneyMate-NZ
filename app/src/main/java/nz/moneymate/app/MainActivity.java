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
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); auth=new AuthClient(this);store=new Store(this,auth.userId());
        try {
            applyTheme(ThemeConfig.parse(getPreferences(MODE_PRIVATE).getString(THEME_KEY,"")));
        }catch(Exception ignored) {applyTheme(ThemeConfig.DEFAULT);}
        store.postDueRecurring(); show("Home");
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
        // Automatically check the public theme on launch/return, no account or financial data upload.
        if(!(BuildConfig.DEBUG&&getIntent().getBooleanExtra("captureMode",false)) &&
           System.currentTimeMillis()-lastThemeCheck > 60000L) refreshTheme(false);
    }
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
            case "Goals": goals();break;case "Bills": bills();break;case "Export": exports();break;case "Account": account();break;default: home();
        }
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);tabs.setBackgroundColor(Color.WHITE);
        LinearLayout links=new LinearLayout(this);links.setOrientation(0);
        final View[] selectedTab=new View[1];
        for(String tab:new String[]{"Home","Transactions","Budgets","Goals","Bills","Export","Account"}) {
            Button nav=button(tab,()->show(tab));nav.setBackground(surface(tab.equals(page)?green:Color.WHITE,16));
            nav.setTextColor(tab.equals(page)?Color.WHITE:ink);nav.setElevation(0);if(tab.equals(page))selectedTab[0]=nav;links.addView(nav,new LinearLayout.LayoutParams(-2,dp(56)));
        }
        tabs.addView(links);frame.addView(tabs);tabs.post(()->tabs.scrollTo(Math.max(0,selectedTab[0].getLeft()-dp(16)),0));
        if(ad!=null) {if(ad.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)ad.getParent()).removeView(ad);root.addView(ad);}
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
        try(Cursor c=store.query("SELECT id,date,kind,category,note,cents FROM transactions ORDER BY date DESC,id DESC LIMIT 300")) {
            if(c.getCount()==0)empty("Your story starts here","Record your first income or expense.");
            while(c.moveToNext()){
                long id=c.getLong(0);
                String s=c.getString(1)+"  "+c.getString(2)+" · "+c.getString(3)+"\n"+c.getString(4)+"   "+MoneyMath.format(c.getLong(5));
                content.addView(record(s,()->new AlertDialog.Builder(this).setTitle("Delete transaction?")
                  .setMessage(s).setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{store.deleteTransaction(id);show("Transactions");}).show()));
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
        try(Cursor c=store.query("SELECT name,cents,due FROM bills ORDER BY due")) {
            if(c.getCount()==0)empty("Stay ahead of due dates","Add your first bill to keep it in view.");
            while(c.moveToNext())line(c.getString(2)+"  ·  "+c.getString(0)+"  ·  "+MoneyMath.format(c.getLong(1)));
        }
    }
    private void exports() {
        title("Your data stays on this device");
        line("Export or restore records for the current local profile only. Accounts do not sync financial records to the cloud.");
        content.addView(button("Export CSV",()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");
            i.putExtra(Intent.EXTRA_TITLE,"MoneyMate-NZ-"+LocalDate.now()+".csv");startActivityForResult(i,100);
        }));
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
    @Override public void startActivityForResult(Intent intent,int requestCode) {
        if(requestCode>=100&&requestCode<=102)documentOwner=Store.databaseName(auth.userId());
        super.startActivityForResult(intent,requestCode);
    }
    private void switchProfile() {
        store.close();store=new Store(this,auth.userId());store.postDueRecurring();show("Account");
    }
    private void account() {
        title("Your MoneyMate account");
        line("Financial records stay on this device. Each signed-in account has a separate local profile. Accounts do not provide cloud backup or sync.");
        content.addView(button("Privacy policy",()->new AlertDialog.Builder(this).setTitle("Privacy and accounts")
            .setMessage("Email and authentication details are processed by Supabase. Passwords are not stored in this app. Session tokens are encrypted with Android Keystore. Financial records stay in app-private storage. Signing out hides your account records; it does not erase them. Backups you export remain under your control. Google advertising and public design downloads also use network services. Read the full policy for retention and deletion details.")
            .setPositiveButton("Read full policy",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://frymastercheese.github.io/MoneyMate-NZ/privacy.html"))))
            .setNegativeButton("Close",null).show()));
        if(auth.userId()==null) {
            line("Local profile • your existing records are preserved");
            if(!AuthClient.configured()){line("Accounts are not available in this build yet. Local budgeting and backups still work.");return;}
            content.addView(button("Sign in",()->credentialsForm(false)));
            content.addView(button("Create account",()->credentialsForm(true)));
            content.addView(button("Confirm email",()->emailCodeForm(false)));
            content.addView(button("Resend confirmation code",()->emailRequest(false)));
            content.addView(button("Forgot password",()->emailRequest(true)));
            content.addView(button("Reset password with email code",()->emailCodeForm(true)));
        } else {
            line("Signed in as "+auth.email());
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
    private LinearLayout form(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(dp(24),dp(8),dp(24),dp(8));return layout;}
    private EditText emailInput(LinearLayout layout){EditText field=input(layout,"Email",false);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);return field;}
    private EditText passwordInput(LinearLayout layout,String hint){EditText field=input(layout,hint,false);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return field;}
    private String emailValue(EditText field){String value=field.getText().toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches())throw new IllegalArgumentException("Enter a valid email address");return value;}
    private void credentialsForm(boolean signup) {
        LinearLayout layout=form();EditText email=emailInput(layout),password=passwordInput(layout,signup?"Password (at least 12 characters)":"Password");
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
        EditText password=recovery?passwordInput(layout,"New password (at least 12 characters)"):null;
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
        if(code>=100&&code<=102&&!Store.databaseName(auth.userId()).equals(documentOwner)){message("Profile changed. Please choose the file again.");return;}
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
        ad=new AdView(this);ad.setAdSize(AdSize.BANNER);ad.setAdUnitId("ca-app-pub-3940256099942544/6300978111");
        ad.loadAd(new AdRequest.Builder().build());
        if(root!=null)root.addView(ad);
    }
    @Override protected void onDestroy(){if(ad!=null)ad.destroy();accountWorker.shutdown();store.close();super.onDestroy();}
}
