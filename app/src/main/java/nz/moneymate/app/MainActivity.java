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

public final class MainActivity extends androidx.activity.ComponentActivity {
    private final androidx.activity.OnBackPressedCallback accountBackGuard =
        new androidx.activity.OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { /* Wait for the account operation. */ }
        };
    private Store store; private LinearLayout root, content, adSlot; private AdView ad;
    private LinearLayout screenFrame;
    private Button privacyChoices;
    private boolean adsInitialized=false,adLoaded=false;
    private boolean captureMode(){return (BuildConfig.DEBUG || "r8Test".equals(BuildConfig.BUILD_TYPE))&&getIntent().getBooleanExtra("captureMode",false);}
    private void postScheduledEntries(){
        if(accountBusy||cloudBusy)return;
        try {if(store.postDueRecurring()>0){show(currentPage);Toast.makeText(this,"Repeating entries recorded. No bank payments were made.",Toast.LENGTH_LONG).show();}}
        catch(Exception error){Toast.makeText(this,"Repeating entries could not be recorded. Review your schedules in Transactions.",Toast.LENGTH_LONG).show();}
    }
    private int ink=ThemeConfig.DEFAULT.ink, green=ThemeConfig.DEFAULT.accent;
    private int canvasColor=ThemeConfig.DEFAULT.background, heroColor=ThemeConfig.DEFAULT.hero;
    private String tagline=ThemeConfig.DEFAULT.tagline;
    private String currentPage="Home";
    private long lastThemeCheck=0L;
    private static final String THEME_URL="https://frymastercheese.github.io/MoneyMate-NZ/app-theme.json";
    private static final String THEME_KEY="remote_theme_v1";
    private AuthClient auth;
    // Admin access is granted only by the server RPC, never by an email string or UI flag.
    private org.json.JSONObject adminReport;
    private String adminReportOwner;
    private boolean adminCheckRunning=false,cloudLinkCheckRunning=false;
    private long lastCloudLinkCheck=0L;
    private boolean accountBusy=false;
    private final java.util.concurrent.ExecutorService accountWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private String documentOwner;
    private long lastAccountCheck;
    private long lastCloudAttempt;
    private boolean cloudBusy=false,ignoreNextAutoSync=false;
    private String cloudStatus="Cloud syncing is not enabled";
    private final android.os.Handler cloudTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable periodicCloudCheck=new Runnable(){
        @Override public void run() {
            if(!isFinishing()&&!isDestroyed()) {
                postScheduledEntries();
                if(auth!=null&&auth.userId()!=null)requestCloudSync(false);
                cloudTimer.postDelayed(this,45000L);
            }
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, accountBackGuard);
        auth=new AuthClient(this);store=new Store(this,auth.userId());
        try {
            applyTheme(ThemeConfig.parse(getPreferences(MODE_PRIVATE).getString(THEME_KEY,"")));
        }catch(Exception ignored) {applyTheme(ThemeConfig.DEFAULT);}
        postScheduledEntries(); show("Home");
        handleGoogleCallback(getIntent());
        if(!((BuildConfig.DEBUG || "r8Test".equals(BuildConfig.BUILD_TYPE))&&getIntent().getBooleanExtra("captureMode",false)))initAds();
    }
    private void applyTheme(ThemeConfig config) {
        ink=config.ink;green=config.accent;canvasColor=config.background;
        heroColor=config.hero;tagline=config.tagline;
    }
    @Override protected void onResume() {
        super.onResume();
        if(ad!=null)ad.resume();
        postScheduledEntries();
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
        if(auth.userId()!=null){
            requestCloudSync(false);
            requestAdminOverview(false);
            attachExistingCloudIfEmpty();
            offerAutomaticSyncOnce();
        }
        // Automatically check the public theme on launch/return.
        if(!((BuildConfig.DEBUG || "r8Test".equals(BuildConfig.BUILD_TYPE))&&getIntent().getBooleanExtra("captureMode",false)) &&
           System.currentTimeMillis()-lastThemeCheck > 60000L) refreshTheme(false);
    }
    @Override protected void onPause(){if(ad!=null)ad.pause();cloudTimer.removeCallbacks(periodicCloudCheck);super.onPause();}
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
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),dp(8),dp(8),dp(8));
        l.setBackground(surface(Color.WHITE,24));l.setElevation(dp(2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(4),dp(8),dp(4),dp(12));l.setLayoutParams(lp);return l;
    }
    private void empty(String heading,String detail) {
        LinearLayout l=card();Illustration art=new Illustration(this);l.addView(art,new LinearLayout.LayoutParams(-1,dp(130)));
        l.addView(text(heading,20,true));l.addView(text(detail,14,false));content.addView(l);
    }
    private void show(String page) {
        if(accountBusy)return;
        if("Admin".equals(page)&&!isVerifiedAdmin())page="Account";
        currentPage=page;
        LinearLayout frame=new LinearLayout(this);screenFrame=frame;privacyChoices=null;frame.setOrientation(LinearLayout.VERTICAL);frame.setBackgroundColor(canvasColor);
        frame.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(frame);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(24));scroll.addView(root);
        LinearLayout brandRow=new LinearLayout(this);brandRow.setGravity(android.view.Gravity.CENTER_VERTICAL);brandRow.setPadding(dp(12),dp(4),dp(12),dp(4));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.brand_icon);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        logo.setBackground(surface(heroColor,14));logo.setClipToOutline(true);brandRow.addView(logo,new LinearLayout.LayoutParams(dp(46),dp(46)));
        TextView brand=text("MoneyMate NZ",24,true);brandRow.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(brandRow);
        String syncLabel=auth.userId()==null?" · Local profile":
            (CloudSync.metadata(this,auth.userId()).enabled?" · Automatic sync on":" · Sync not enabled");
        TextView subtitle=text(tagline+syncLabel,13,false);subtitle.setTextColor(0xff60746d);root.addView(subtitle);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);
        switch(page) {
            case "Transactions": transactions();break;case "Budgets": budgets();break;
            case "Goals": goals();break;case "Bills": bills();break;case "Export": exports();break;case "Account": account();break;case "Banking": banking();break;case "Help": help();break;case "Admin": admin();break;default: home();
        }
        adSlot=new LinearLayout(this);adSlot.setOrientation(LinearLayout.VERTICAL);adSlot.setGravity(android.view.Gravity.CENTER);adSlot.setPadding(dp(16),dp(8),dp(16),dp(16));adSlot.setBackgroundColor(Color.WHITE);
        frame.addView(adSlot,new LinearLayout.LayoutParams(-1,-2));attachBanner();
        LinearLayout links=new LinearLayout(this);links.setOrientation(LinearLayout.HORIZONTAL);links.setBackgroundColor(Color.WHITE);links.setPadding(dp(6),dp(6),dp(6),dp(6));
        String[] destinations={"Home","Transactions","Budgets","Bills","More"};String[] captions={"Home","Activity","Budgets","Bills","More"};
        for(int i=0;i<destinations.length;i++) {
            String tab=destinations[i];boolean selected=tab.equals(page)||("More".equals(tab)&&!java.util.Arrays.asList("Home","Transactions","Budgets","Bills").contains(page));
            Button nav=button(captions[i],()->{if("More".equals(tab))moreSections();else show(tab);});nav.setTextSize(12);nav.setPadding(dp(2),dp(8),dp(2),dp(8));nav.setMinWidth(0);
            nav.setContentDescription("Transactions".equals(tab)?"Transactions":tab);nav.setBackground(surface(selected?0xffe4f2d9:Color.WHITE,14));nav.setTextColor(selected?0xff22553b:ink);nav.setElevation(0);
            links.addView(nav,new LinearLayout.LayoutParams(0,dp(52),1));
        }
        frame.addView(links);frame.post(this::loadBannerIfAllowed);

        if(ignoreNextAutoSync)ignoreNextAutoSync=false;
        else if(auth.userId()!=null&&System.currentTimeMillis()-lastCloudAttempt>2500L)requestCloudSync(false);
    }
    private void moreSections(){
        java.util.List<String> items=new java.util.ArrayList<>(java.util.Arrays.asList("Goals","Export","Account","Banking","Help"));
        if(isVerifiedAdmin())items.add("Admin");
        new AlertDialog.Builder(this).setTitle("Your MoneyMate").setItems(items.toArray(new String[0]),(dialog,which)->show(items.get(which))).setNegativeButton("Close",null).show();
    }
    private void title(String s) {content.addView(text(s,23,true));}
    private void line(String s) { LinearLayout c=card();c.addView(text(s,16,false));content.addView(c); }
    private String month() {return YearMonth.now().toString();}
    private void home() {
        title(YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",java.util.Locale.forLanguageTag("en-NZ"))));
        long in=store.sum("Income",month()),out=store.sum("Expense",month());
        LinearLayout balance=card();android.graphics.drawable.GradientDrawable hero=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{heroColor,0xff206148});hero.setCornerRadius(dp(26));balance.setBackground(hero);
        TextView label=text("MONTHLY CASH FLOW · NZD",12,true);label.setTextColor(0xffbce7d6);balance.addView(label);
        TextView amount=text(MoneyMath.format(MoneyMath.remaining(in,out)),38,true);amount.setTextColor(Color.WHITE);balance.addView(amount);
        TextView note=text("Recorded income minus expenses · not your bank balance",12,false);note.setTextColor(0xffd1e6db);balance.addView(note);
        TextView totals=text("Income  "+MoneyMath.format(in)+"\nSpent    "+MoneyMath.format(out),16,false);totals.setTextColor(Color.WHITE);balance.addView(totals);content.addView(balance);
        if(in==0&&out==0)empty("Welcome to your money plan","Start with your income, then add expenses as you go. Records are saved on this device; optional account sync also stores a cloud copy.");
        content.addView(button("+ Add income",()->transactionForm("Income")));
        content.addView(button("+ Add expense",()->transactionForm("Expense")));
        long[] plan=store.monthlyPlan(month());
        LinearLayout overview=card();overview.addView(text("Your monthly plan",18,true));
        overview.addView(text("Category budgets remaining   "+MoneyMath.format(plan[0]),15,true));
        overview.addView(text("Unpaid bills due this month   "+MoneyMath.format(plan[1]),15,false));
        overview.addView(text("Savings goals reached   "+plan[2]+" / "+plan[3],15,false));
        overview.addView(text("Budgets cover their categories only. Unpaid bills are not deducted from cash flow.",12,false));content.addView(overview);

        // Forward planning from existing unpaid bills and repeating schedules only.
        // This never changes a bank balance, sends a payment, or edits records.
        try {
            FortnightPlan.Summary future=FortnightPlan.calculate(store,LocalDate.now(),14);
            LinearLayout lookahead=card();
            lookahead.addView(text("Next 14 days · NZ money plan",18,true));
            lookahead.addView(text("Upcoming money movements you scheduled",13,false));
            lookahead.addView(text("↗  Scheduled income    "+MoneyMath.format(future.incoming),15,true));
            lookahead.addView(text("↘  Planned outgoings   "+MoneyMath.format(future.outgoing),15,true));
            TextView difference=text("≈  Planned difference   "+MoneyMath.format(future.net),16,true);
            if(future.net<0)difference.setTextColor(0xffad4136);
            lookahead.addView(difference);
            if(future.events.isEmpty()) {
                lookahead.addView(text("Add unpaid bills or repeating income and expenses to plan your fortnight.",13,false));
            }else{
                lookahead.addView(text("On the horizon",15,true));
                for(int i=0;i<Math.min(6,future.events.size());i++) {
                    FortnightPlan.Event e=future.events.get(i);
                    String amount=(e.kind.equals("Income")?"+":"−")+MoneyMath.format(e.cents);
                    lookahead.addView(text(e.date+"  "+e.name+" · "+e.type+"   "+amount,13,false));
                }
                if(future.events.size()>6)
                    lookahead.addView(text("And "+(future.events.size()-6)+" more planned entries this fortnight.",12,false));
            }
            lookahead.addView(text("Planning estimate only — not your bank balance or a payment promise. Bills also entered as repeating expenses appear twice; check your schedules.",12,false));
            content.addView(lookahead);
        }catch(Exception problem) {
            // Bad old local schedules must not prevent a customer opening Home.
            line("Your upcoming money plan is unavailable. Existing financial records remain unchanged.");
        }

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
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(24),dp(8),dp(24),dp(8));
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
        content.addView(button("Import NZD bank PDF for free",()->chooseBankPdf()));
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
        history.setOrientation(LinearLayout.VERTICAL);
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
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(24),dp(8),dp(24),dp(8));
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
        content.addView(button("Import bank PDF (NZD, free)",()->chooseBankPdf()));
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
        privacyChoices=button("Advertising privacy choices",()->{
            UserMessagingPlatform.showPrivacyOptionsForm(this,error->{
                if(isFinishing()||isDestroyed())return;
                if(error!=null)Toast.makeText(this,"Choices unavailable: "+error.getMessage(),Toast.LENGTH_LONG).show();
                else {if(ad!=null){ad.destroy();ad=null;}adLoaded=false;attachBanner();loadBannerIfAllowed();}
                updatePrivacyChoices();
            });
        });
        content.addView(privacyChoices);updatePrivacyChoices();
        line("Backups contain sensitive financial information. Store them securely. Restoring replaces current records.");
    }
    /** The Android document picker reads a statement locally; no file or banking details
     * are sent to a provider. Import is opt-in and additive, never a JSON restore.
     */
    private void chooseBankPdf(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent,104);
    }
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
        nickname.setSingleLine(true);nickname.setText(R.string.default_account_nickname);
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
        if(requestCode>=100&&requestCode<=104)documentOwner=Store.databaseName(auth.userId());
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
        adminReport=null;adminReportOwner=null;lastCloudLinkCheck=0L;
        store.close();store=new Store(this,auth.userId());show("Account");postScheduledEntries();
        if(auth.userId()!=null){
            requestAdminOverview(false);
            attachExistingCloudIfEmpty();
            offerAutomaticSyncOnce();
        }
    }
    /** One-time, explicit, user-approved setup. Financial records remain local
     * until this account holder accepts the cloud-storage disclosure.
     * Guest records are NEVER copied or uploaded without the user's choice.
     */
    private void offerAutomaticSyncOnce() {
        final String owner=auth.userId();
        if(owner==null||accountBusy||cloudBusy||captureMode()||CloudSync.metadata(this,owner).enabled)return;
        final android.content.SharedPreferences prefs=getSharedPreferences("moneymate_auto_sync_setup_v1",MODE_PRIVATE);
        if(prefs.getBoolean(owner,false))return;
        final boolean copyGuest;
        try(Store guest=new Store(this)) {
            copyGuest=store.isEmpty()&&!guest.isEmpty();
        }catch(Exception error){return;}
        String prompt=copyGuest
            ?"This signed-in account has no financial records yet, but your phone has saved local records. Copy the local records (including savings goals) into this account and turn on automatic syncing? The original local copy is kept. Your private records will be stored in Supabase in Sydney, Australia. Different cloud data will never be overwritten automatically. Save a JSON backup when possible."
            :"Turn on automatic MoneyMate NZ syncing for this signed-in account? Transactions, savings goals, budgets, bills and repeating entries will be backed up privately in Supabase in Sydney, Australia. Your local records remain on the phone. Conflicting cloud records are never silently overwritten. Save a JSON backup when possible.";
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(copyGuest?"Bring your saved goals to this account?":"Keep your devices in sync?")
            .setMessage(prompt).setNegativeButton("Not now",null)
            .setPositiveButton(copyGuest?"Copy and enable auto-sync":"Enable automatic sync",(d,w)->{
                if(!owner.equals(auth.userId())||accountBusy||cloudBusy||CloudSync.metadata(this,owner).enabled)return;
                try{
                    if(copyGuest) {
                        if(!store.isEmpty()){
                            message("This account now has records; the separate local records were kept. Review both backups before combining them.");
                            return;
                        }
                        try(Store guest=new Store(this)) {
                            String original=guest.backupJson();
                            if(CloudSync.empty(original))return;
                            // A copy, not a move. Local guest finances remain intact.
                            store.restoreJson(original);
                        }
                    }
                    beginAutomaticCloudSync(owner);
                }catch(Exception error){message("Cloud setup was stopped; original local records remain safe. Review Account and try again.");}
            }).create();
        dialog.show();
        prefs.edit().putBoolean(owner,true).apply();
    }
    private boolean isVerifiedAdmin() {
        return auth.userId()!=null&&auth.userId().equals(adminReportOwner)&&adminReport!=null;
    }
    private void requestAdminOverview(boolean notify) {
        final String owner=auth.userId();
        if(owner==null||adminCheckRunning||accountBusy)return;
        adminCheckRunning=true;
        accountWorker.execute(()->{
            org.json.JSONObject result=null;
            try {result=auth.adminOverview();}catch(Exception ignored) {
                // A 403 or unavailable server never grants admin UI privileges.
            }
            final org.json.JSONObject verified=result;
            runOnUiThread(()->{
                adminCheckRunning=false;
                if(isFinishing()||isDestroyed()||!owner.equals(auth.userId()))return;
                adminReport=verified;
                adminReportOwner=verified==null?null:owner;
                ignoreNextAutoSync=true;
                show(currentPage);
                if(notify&&verified==null)message("Admin access unavailable or denied by the account service.");
            });
        });
    }
    private void admin(){
        if(!isVerifiedAdmin())return;
        title("Administrator dashboard");
        line("Authorized account: "+auth.email()+" · Read-only totals from the secure server.");
        for(String[] item:new String[][]{
            {"Registered accounts","registered_users"},
            {"Confirmed accounts","verified_users"},
            {"Google accounts","google_users"},
            {"New registrations (last 7 days)","registrations_last_7_days"},
            {"Cloud-backed profiles","cloud_sync_accounts"}
        }){
            line(item[0]+": "+adminReport.optLong(item[1],0));
        }
        line("No individual customer transactions, passwords or private financial data are displayed here.");
        content.addView(button("Refresh admin statistics",()->requestAdminOverview(true)));
    }
    /** Reuse a previously consented cloud copy when this device has no account records.
     * Never upload new records without consent or replace populated local records.
     */
    private void attachExistingCloudIfEmpty(){
        final String owner=auth.userId();
        if(owner==null||accountBusy||cloudBusy||cloudLinkCheckRunning||
           CloudSync.metadata(this,owner).enabled)return;
        long now=System.currentTimeMillis();
        if(now-lastCloudLinkCheck<60000L)return;
        final String before;
        try{
            before=store.backupJson();
            if(!CloudSync.empty(before))return;
            // Prefer offering to COPY existing phone guest goals before auto-linking
            // an empty account to the cloud. Guest records must never be stranded
            // behind an unrelated signed-in profile without a visible choice.
            try(Store guest=new Store(this)) {
                if(!guest.isEmpty())return;
            }
        }catch(Exception ignored){return;}
        lastCloudLinkCheck=now;cloudLinkCheckRunning=true;
        accountWorker.execute(()->{
            long revision=0;
            try{revision=auth.readAccountSnapshot().getLong("revision");}
            catch(Exception ignored){/* Offline: retain all existing records and retry. */}
            final long availableRevision=revision;
            runOnUiThread(()->{
                cloudLinkCheckRunning=false;
                if(isFinishing()||isDestroyed()||accountBusy||!owner.equals(auth.userId()))return;
                try{
                    if(!CloudSync.shouldAutoLinkExisting(CloudSync.metadata(this,owner),
                         availableRevision,store.backupJson()) ||
                       !CloudSync.digest(before).equals(CloudSync.digest(store.backupJson())))return;
                    CloudSync.persist(this,owner,new CloudSync.Metadata(true,0,""));
                    cloudStatus="Existing cloud backup found; syncing this empty device profile.";
                    requestCloudSync(false);
                    if("Account".equals(currentPage)){ignoreNextAutoSync=true;show("Account");}
                }catch(Exception ignored){/* Retain local records and opt-in controls. */}
            });
        });
    }


    private void banking(){
        title("Bank connections · NZ & Australia");
        line("Free NZD bank statement imports are available from CSV or PDF files. PDF text recognition runs on your device, but some layouts may fail. Neither requires a bank login inside MoneyMate NZ or provider fees.");
        content.addView(button("Import NZD bank statement CSV",()->chooseBankCsv()));
        content.addView(button("Import NZD bank statement PDF (free)",()->chooseBankPdf()));
        line("ANZ NZ statements with Withdrawals / Deposits / Balance can be checked using running balances. ASB, BNZ, Westpac, Kiwibank, TSB, SBS, The Co-operative Bank, Heartland, Rabobank and other NZ bank PDFs may work when the transaction table is clear, but each bank layout still needs real-world testing.");
        line("Automatic bank connections are NOT LIVE. AUD statement imports are blocked until separate AUD budgets are available.");
        line("New Zealand: accredited Akahu open banking. Australia: Basiq Consumer Data Right. Both require approved provider onboarding before people can connect.");
        content.addView(button("About Akahu (NZ)",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.akahu.nz/")))));
        content.addView(button("About Basiq (Australia)",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.basiq.io/")))));
        line("Never enter banking passwords in MoneyMate NZ. Future consent will be read-only and revocable. AUD amounts must stay separate from NZD until currency conversion is supported.");
    }
    /** Simple in-app guide. Accessible without an account or network. */
    private void help(){
        title("How to use MoneyMate NZ");
        line("MoneyMate NZ helps you track income, expenses, budgets, savings goals and bills. It does not move money or pay bills. Use the tabs below; More opens Goals, Export, Account, Banking and Help.");
        title("1. Sign in or use a local profile");
        line("Open Account to sign in using Google (when available) or email, or continue without signing in. To enable private cloud sync across devices, use the same account on Android and the website.");
        title("2. Add income or expenses");
        line("Home or Transactions → + Income / + Expense. Choose a category, enter the amount in NZD, and save the entry. Home displays monthly income, spending and balance.");
        title("3. Import a bank CSV or PDF for free");
        line("Download a NZD CSV or PDF statement from your bank. In Banking choose Import bank CSV or Import bank PDF. Use a consistent nickname, but never your actual bank account number. For PDFs, check every amount, date and transaction direction in the preview. This never connects to your bank or automatically downloads transactions.");
        line("Only NZD files are supported; AUD is blocked. PDF recognition can fail on scanned or complex layouts and must be checked against the original. Save a JSON backup before a large import. Duplicate detection is best-effort.");
        title("4. Plan with budgets and goals");
        line("Budgets → set a monthly amount for a category. Goals → create a target and add contributions as you save. Bills → add amounts and due dates; Record as paid adds an expense without sending money.");
        title("5. Sync Android with the website");
        line("First save JSON backups on both devices in Export. Sign in with the SAME Google/email account on Android and the website. Android 0.4.1 offers to copy saved Local profile goals into your account and enable automatic sync; the original records stay put. If your signed-in profile already has records, approve the one-time sync prompt or use Account → Enable secure cloud sync. The empty website profile will automatically fetch an existing cloud copy when online. Edits sync while the app/website is open or resumed, without tapping Sync now. Conflicts preserve both copies; never replace either before exporting backups.");
        title("6. Back up and restore safely");
        line("Export → Backup all data (JSON) preserves your current profile. Export CSV saves transaction rows for spreadsheets. Restore full backup (JSON) REPLACES existing profile records; bank CSV import only ADDS new transactions.");
        title("7. Help and privacy");
        line("Automatic live bank connections are not yet available. For login or syncing problems, retry online and check the same account on both devices. Never share a bank password, account token, or financial export publicly.");
        content.addView(button("Open full help guide on website",()->startActivity(new Intent(
            Intent.ACTION_VIEW,android.net.Uri.parse("https://frymastercheese.github.io/MoneyMate-NZ/help.html")))));
        content.addView(button("Read privacy policy",()->startActivity(new Intent(
            Intent.ACTION_VIEW,android.net.Uri.parse("https://frymastercheese.github.io/MoneyMate-NZ/privacy.html")))));
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
            if(isVerifiedAdmin())content.addView(button("✦ Admin dashboard",()->show("Admin")));
            CloudSync.Metadata cloud=CloudSync.metadata(this,auth.userId());
            line(cloudStatus+" · "+(cloud.enabled?"Cloud sync enabled":"Cloud sync disabled"));
            if(!cloud.enabled)content.addView(button("Enable secure cloud sync",()->reviewCloudEnable()));

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
                .setMessage("Sign out of this device only. Your other devices stay signed in. Your account records remain here and are hidden until you sign in again. The separate local profile will open. If offline, this device signs out but its server session cannot be revoked yet.")
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
    /** Avoid silently uploading an empty signed-in profile when older guest
     * finances are still on this phone. Explicit users may keep profiles separate.
     */
    private void reviewCloudEnable() {
        if(auth.userId()==null||cloudBusy||accountBusy)return;
        if(store.isEmpty()) {
            final boolean guestHasRecords;
            try(Store guest=new Store(this)) {guestHasRecords=!guest.isEmpty();}
            catch(Exception error) {
                message("Could not check existing local records. Save a JSON backup before enabling sync.");
                return;
            }
            if(guestHasRecords) {
                new AlertDialog.Builder(this)
                    .setTitle("Existing local records found")
                    .setMessage("This signed-in account is empty, but your guest profile contains financial records. Use 'Import existing local records' before cloud sync if you want those records on Android and the website. Your guest records remain safe if you keep them separate.")
                    .setNegativeButton("Cancel",null)
                    .setNeutralButton("Review account",(d,w)->show("Account"))
                    .setPositiveButton("Keep separate",(d,w)->confirmCloudEnable())
                    .show();
                return;
            }
        }
        confirmCloudEnable();
    }
    private void beginAutomaticCloudSync(String owner) {
        if(owner==null||!owner.equals(auth.userId())||cloudBusy||accountBusy)return;
        try{
            if(!CloudSync.metadata(this,owner).enabled)
                CloudSync.persist(this,owner,new CloudSync.Metadata(true,0,""));
            cloudStatus="Checking private cloud records before syncing.";
            ignoreNextAutoSync=true;show("Account");
            requestCloudSync(true);
        }catch(Exception error){message("Cloud setup failed. Local records remain safe.");}
    }
    private void confirmCloudEnable() {
        final String owner=auth.userId();
        if(owner==null||cloudBusy||accountBusy)return;
        new AlertDialog.Builder(this)
            .setTitle("Back up MoneyMate NZ to the cloud?")
            .setMessage("Your private income, expenses, budgets, goals, bills and repeating entries will be saved to Supabase servers in Sydney, Australia and synced across your signed-in devices. Guest records are NOT included unless copied into this account. This is optional; save a full JSON backup first. Existing cloud records will not be silently overwritten.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Enable secure sync",(d,w)->{
                try{
                    beginAutomaticCloudSync(owner);
                }catch(Exception error){message("Cloud setup failed. Local records remain safe.");}
            }).show();
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
    private LinearLayout form(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(24),dp(8),dp(24),dp(8));return layout;}
    private EditText emailInput(LinearLayout layout){EditText field=input(layout,"Email",false);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);return field;}
    private EditText passwordInput(LinearLayout layout,String hint){EditText field=input(layout,hint,false);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return field;}
    private String emailValue(EditText field){String value=field.getText().toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches())throw new IllegalArgumentException("Enter a valid email address");return value;}
    private void credentialsForm(boolean signup) {
        LinearLayout layout=form();EditText email=emailInput(layout),password=passwordInput(layout,signup?"Password (at least 8 characters)":"Password");
        CheckBox consent=new CheckBox(this);consent.setText(R.string.account_signup_consent);if(signup)layout.addView(consent);
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
                store.close();auth.clear();adminReport=null;adminReportOwner=null;deleteDatabase(database);store=new Store(this);show("Account");message("Account deleted. Exported files and records on other devices must be removed separately.");
            },false);
        }).show();
    }
    private interface AccountWork {void run() throws Exception;}
    void setAccountBusy(boolean busy) {
        accountBusy=busy;
        accountBackGuard.setEnabled(busy);
    }
    private void accountTask(AccountWork work,Runnable success,boolean localSignout) {
        if(accountBusy)return;setAccountBusy(true);
        int priorOrientation=getRequestedOrientation();setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        AlertDialog progress=new AlertDialog.Builder(this).setMessage("Contacting account service…").setCancelable(false).create();progress.show();
        accountWorker.execute(()->{
            String failure=null;try{work.run();}catch(Exception error){failure=error instanceof IllegalArgumentException?error.getMessage():"Could not complete the account request. Check your connection and details, or request a fresh code.";}
            final String problem=failure;
            runOnUiThread(()->{
                setAccountBusy(false);if(isFinishing()||isDestroyed())return;progress.dismiss();
                if(problem==null)success.run();else if(localSignout){switchProfile();message("Signed out on this device. Remote session revocation could not be confirmed.");}else message(problem);
                setRequestedOrientation(priorOrientation);
            });
        });
    }
    private void message(String value){new AlertDialog.Builder(this).setMessage(value).setPositiveButton("OK",null).show();}
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if(code>=100&&code<=104&&!Store.databaseName(auth.userId()).equals(documentOwner)){message("Profile changed. Please choose the file again.");return;}
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
        } else if(code==104&&result==RESULT_OK&&data!=null&&data.getData()!=null) {
            PdfImport.start(this,store,()->Store.databaseName(auth.userId()),data,()->show("Transactions"));
        } else if(code==102&&result==RESULT_OK&&data!=null&&data.getData()!=null) {
            try(java.io.InputStream in=getContentResolver().openInputStream(data.getData())) {
                final Store restoreStore=store;
                final String before=store.backupJson();
                final String owner=auth.userId();
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
                        try{
                            if(store!=restoreStore||!before.equals(store.backupJson()))throw new IllegalStateException("Profile or records changed during review. Nothing was restored.");
                            CloudSync.recoveryCopy(this,owner==null?"guest":owner,before);
                            store.restoreJson(backup);show("Home");Toast.makeText(this,"Restore completed",Toast.LENGTH_LONG).show();
                        }
                        catch(Exception ex){new AlertDialog.Builder(this).setTitle("Restore failed").setMessage(ex.getMessage()).setPositiveButton("OK",null).show();}
                    }).show();
            }catch(Exception ex){Toast.makeText(this,"Could not read backup: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }
    private void updatePrivacyChoices(){
        if(privacyChoices!=null)privacyChoices.setVisibility(UserMessagingPlatform.getConsentInformation(this).getPrivacyOptionsRequirementStatus()==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED?View.VISIBLE:View.GONE);
    }
    private void initAds() {
        ConsentRequestParameters params=new ConsentRequestParameters.Builder().build();
        UserMessagingPlatform.getConsentInformation(this).requestConsentInfoUpdate(this,params,
          ()->{
              if(isFinishing()||isDestroyed())return;
              updatePrivacyChoices();
              UserMessagingPlatform.loadAndShowConsentFormIfRequired(this,error->{updatePrivacyChoices();loadBannerIfAllowed();});
          },error->{updatePrivacyChoices();loadBannerIfAllowed();});
    }
    private boolean bannerPage(){return java.util.Arrays.asList("Home","Transactions","Budgets","Goals","Bills").contains(currentPage);}
    private void attachBanner(){
        if(adSlot==null)return;
        if(ad!=null&&ad.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)ad.getParent()).removeView(ad);
        adSlot.removeAllViews();adSlot.setVisibility(adLoaded&&bannerPage()?View.VISIBLE:View.GONE);
        if(adLoaded&&ad!=null&&bannerPage()){
            TextView label=text("ADVERTISEMENT",10,false);label.setTextColor(0xff617368);label.setGravity(android.view.Gravity.CENTER);label.setPadding(0,0,0,dp(6));adSlot.addView(label);
            adSlot.addView(ad);
        }
    }
    private void loadBannerIfAllowed() {
        if(captureMode()||isFinishing()||isDestroyed()||!bannerPage()||!UserMessagingPlatform.getConsentInformation(this).canRequestAds())return;
        if(!adsInitialized){adsInitialized=true;MobileAds.initialize(this,status->runOnUiThread(this::loadBannerIfAllowed));return;}
        if(ad!=null||screenFrame==null||screenFrame.getWidth()==0)return;
        int width=Math.max(1,Math.round((screenFrame.getWidth()-screenFrame.getPaddingLeft()-screenFrame.getPaddingRight()-dp(32))/getResources().getDisplayMetrics().density));
        ad=new AdView(this);ad.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this,width));ad.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
        final AdView requested=ad;
        ad.setAdListener(new AdListener(){
            @Override public void onAdLoaded(){if(ad==requested&&!isFinishing()&&!isDestroyed()){adLoaded=true;attachBanner();}}
            @Override public void onAdFailedToLoad(LoadAdError error){if(ad==requested&&!isDestroyed()){adLoaded=false;attachBanner();}}
        });
        ad.loadAd(new AdRequest.Builder().build());
    }
    @Override protected void onDestroy(){if(ad!=null)ad.destroy();accountWorker.shutdown();store.close();super.onDestroy();}
}
