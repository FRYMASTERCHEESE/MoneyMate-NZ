package nz.moneymate.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import java.util.ArrayList;
import java.util.List;

public final class Store extends SQLiteOpenHelper {
    public Store(Context c) { this(c, null); }
    public Store(Context c, String userId) { super(c, databaseName(userId), null, 2); }
    static String databaseName(String userId) {
        return userId == null ? "moneymate.db" : "account-" + java.util.UUID.fromString(userId).toString() + ".db";
    }
    public boolean isEmpty() {
        for(String table:new String[]{"transactions","budgets","goals","bills","recurring"})
            try(Cursor c=query("SELECT COUNT(*) FROM "+table)) {c.moveToFirst();if(c.getLong(0)>0)return false;}
        return true;
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL CHECK(kind IN ('Income','Expense')), category TEXT NOT NULL, note TEXT NOT NULL, cents INTEGER NOT NULL CHECK(cents > 0), date TEXT NOT NULL)");
        db.execSQL("CREATE TABLE budgets (category TEXT PRIMARY KEY, cents INTEGER NOT NULL CHECK(cents >= 0))");
        db.execSQL("CREATE TABLE goals (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, target INTEGER NOT NULL CHECK(target > 0), saved INTEGER NOT NULL DEFAULT 0 CHECK(saved >= 0))");
        db.execSQL("CREATE TABLE bills (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, cents INTEGER NOT NULL CHECK(cents > 0), due TEXT NOT NULL)");
        db.execSQL("CREATE TABLE recurring (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, cents INTEGER NOT NULL CHECK(cents > 0), next_date TEXT NOT NULL, frequency TEXT NOT NULL CHECK(frequency IN ('Weekly','Fortnightly','Monthly')))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2)db.execSQL("CREATE TABLE recurring (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, cents INTEGER NOT NULL CHECK(cents > 0), next_date TEXT NOT NULL, frequency TEXT NOT NULL CHECK(frequency IN ('Weekly','Fortnightly','Monthly')))");
    }
    public void addTransaction(String kind, String category, String note, long cents, String date) {
        ContentValues v = new ContentValues(); v.put("kind", kind); v.put("category",category); v.put("note",note); v.put("cents",cents); v.put("date",date);
        getWritableDatabase().insertOrThrow("transactions",null,v);
    }
    public static final class BankImportResult {
        public final int inserted,skipped;
        BankImportResult(int inserted,int skipped){this.inserted=inserted;this.skipped=skipped;}
    }
    /** Import only after explicit preview/confirmation. Uses a single SQLite transaction.
     * Exact bank-imported rows are counted as a MULTISET, so distinct identical entries
     * can be kept without importing the same statement twice. No existing rows are replaced.
     * Account nicknames must stay consistent across file imports.
     */
    public BankImportResult importBankCsv(List<BankCsv.Entry> incoming,boolean preview) {
        if(incoming==null||incoming.size()>5000)throw new IllegalArgumentException("Invalid bank import");
        SQLiteDatabase db=preview?getReadableDatabase():getWritableDatabase();
        if(!preview)db.beginTransaction();
        try {
            java.util.Map<String,Integer> existing=new java.util.HashMap<>();
            try(Cursor cursor=db.rawQuery(
                "SELECT date,kind,category,note,cents FROM transactions",null)) {
                while(cursor.moveToNext()){
                    BankCsv.Entry entry=new BankCsv.Entry(cursor.getString(0),cursor.getString(1),
                        cursor.getString(2),cursor.getString(3),cursor.getLong(4));
                    String key=entry.key();existing.put(key,existing.getOrDefault(key,0)+1);
                }
            }
            java.util.Map<String,Integer> seen=new java.util.HashMap<>();
            int inserted=0,skipped=0;
            for(BankCsv.Entry row:incoming) {
                if(row==null||row.cents<1||row.cents>9_007_199_254_740_991L)
                    throw new IllegalArgumentException("Invalid bank amount");
                String key=row.key(),counted=key;
                int ordinal=seen.getOrDefault(counted,0)+1;
                seen.put(counted,ordinal);
                if(ordinal<=existing.getOrDefault(key,0)){skipped++;continue;}
                if(!preview){
                    ContentValues v=new ContentValues();
                    v.put("date",row.date);v.put("kind",row.kind);v.put("category",row.category);
                    v.put("note",row.note);v.put("cents",row.cents);
                    db.insertOrThrow("transactions",null,v);
                }
                inserted++;
            }
            if(!preview)db.setTransactionSuccessful();
            return new BankImportResult(inserted,skipped);
        } finally {if(!preview)db.endTransaction();}
    }
    public void addBudget(String category, long cents) {
        ContentValues v=new ContentValues(); v.put("category",category);v.put("cents",cents);
        getWritableDatabase().insertWithOnConflict("budgets",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void addGoal(String name,long target) {
        ContentValues v=new ContentValues();v.put("name",name);v.put("target",target);
        getWritableDatabase().insertOrThrow("goals",null,v);
    }
    public void contribute(long id,long cents) {
        getWritableDatabase().execSQL("UPDATE goals SET saved=saved+? WHERE id=?",new Object[]{cents,id});
    }
    public void addBill(String name,long cents,String due) {
        ContentValues v=new ContentValues();v.put("name",name);v.put("cents",cents);v.put("due",due);
        getWritableDatabase().insertOrThrow("bills",null,v);
    }
    /** Record a paid bill as a local expense and remove it from upcoming bills atomically.
     * This is bookkeeping only; it never transfers money or contacts a bank.
     * Returns false when the bill is already gone, so repeat taps cannot double charge.
     */
    public boolean recordBillPaid(long id, String paidDate) {
        java.time.LocalDate.parse(paidDate);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            String name;
            long cents;
            try(Cursor c=db.rawQuery("SELECT name,cents FROM bills WHERE id=?",new String[]{Long.toString(id)})) {
                if(!c.moveToFirst()) {db.setTransactionSuccessful();return false;}
                name=c.getString(0);cents=c.getLong(1);
            }
            ContentValues expense=new ContentValues();
            expense.put("kind","Expense");expense.put("category","Bills");
            expense.put("note",name);expense.put("cents",cents);expense.put("date",paidDate);
            db.insertOrThrow("transactions",null,expense);
            if(db.delete("bills","id=?",new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("Bill changed before payment recording");
            db.setTransactionSuccessful();
            return true;
        } finally {db.endTransaction();}
    }
    public void deleteBill(long id) {
        getWritableDatabase().delete("bills","id=?",new String[]{Long.toString(id)});
    }
    public void deleteTransaction(long id) { getWritableDatabase().delete("transactions","id=?",new String[]{String.valueOf(id)}); }
    public Cursor query(String sql,String... args) { return getReadableDatabase().rawQuery(sql,args); }
    public long sum(String kind,String month) {
        try(Cursor c=query("SELECT COALESCE(SUM(cents),0) FROM transactions WHERE kind=? AND substr(date,1,7)=?",kind,month)) { c.moveToFirst(); return c.getLong(0); }
    }
    public List<String> csvRows() {
        List<String> rows=new ArrayList<>();
        rows.add("Date,Type,Category,Note,Amount NZD");
        try(Cursor c=query("SELECT date,kind,category,note,cents FROM transactions ORDER BY date DESC,id DESC")) {
            while(c.moveToNext()) rows.add(quote(c.getString(0))+","+quote(c.getString(1))+","+quote(c.getString(2))+","+quote(c.getString(3))+","+String.format(java.util.Locale.US,"%.2f",c.getLong(4)/100.0));
        }
        return rows;
    }
    private String quote(String s) { return "\"" + s.replace("\"","\"\"") + "\""; }


    public void addRecurring(String kind,String category,String note,long cents,String start,String frequency) {
        if(!java.util.Arrays.asList("Weekly","Fortnightly","Monthly").contains(frequency))
            throw new IllegalArgumentException("Unsupported frequency");
        java.time.LocalDate.parse(start);
        ContentValues v=new ContentValues();
        v.put("kind",kind);v.put("category",category);v.put("note",note);v.put("cents",cents);v.put("next_date",start);v.put("frequency",frequency);
        getWritableDatabase().insertOrThrow("recurring",null,v);
    }
    public void deleteRecurring(long id) { getWritableDatabase().delete("recurring","id=?",new String[]{Long.toString(id)}); }
    /** Process missed dates exactly once. Each insertion and next-date advance commits atomically. */
    public int postDueRecurring() {
        SQLiteDatabase db=getWritableDatabase();
        java.time.LocalDate today=java.time.LocalDate.now();
        int created=0;
        db.beginTransaction();
        try(Cursor c=db.rawQuery("SELECT id,kind,category,note,cents,next_date,frequency FROM recurring",null)) {
            while(c.moveToNext()) {
                long id=c.getLong(0),cents=c.getLong(4);
                java.time.LocalDate next=java.time.LocalDate.parse(c.getString(5));
                String frequency=c.getString(6);
                int safety=0;
                while(!next.isAfter(today)&&safety++<1200) {
                    ContentValues t=new ContentValues();
                    t.put("kind",c.getString(1));t.put("category",c.getString(2));t.put("note",c.getString(3));
                    t.put("cents",cents);t.put("date",next.toString());
                    db.insertOrThrow("transactions",null,t);created++;
                    switch(frequency) {
                        case "Weekly":next=next.plusWeeks(1);break;
                        case "Fortnightly":next=next.plusWeeks(2);break;
                        case "Monthly":next=next.plusMonths(1);break;
                        default:throw new IllegalStateException("Invalid recurrence");
                    }
                }
                ContentValues advance=new ContentValues();advance.put("next_date",next.toString());
                db.update("recurring",advance,"id=?",new String[]{Long.toString(id)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return created;
    }
    /** Complete, versioned, locally generated backup. Never uploads financial records. */
    public String backupJson() throws org.json.JSONException {
        org.json.JSONObject backup=new org.json.JSONObject();
        backup.put("format","MoneyMateNZ");
        backup.put("version",1);
        for(String table:new String[]{"transactions","budgets","goals","bills","recurring"}) {
            org.json.JSONArray rows=new org.json.JSONArray();
            try(Cursor cursor=query("SELECT * FROM "+table)) {
                String[] names=cursor.getColumnNames();
                while(cursor.moveToNext()) {
                    org.json.JSONObject row=new org.json.JSONObject();
                    for(int i=0;i<names.length;i++)
                        if(cursor.getType(i)==Cursor.FIELD_TYPE_INTEGER) row.put(names[i],cursor.getLong(i));
                        else row.put(names[i],cursor.getString(i));
                    rows.put(row);
                }
            }
            backup.put(table,rows);
        }
        return backup.toString(2);
    }
    /** Atomic restore, replacing all existing records only after validating the full backup. */
    public void restoreJson(String json) throws org.json.JSONException {
        org.json.JSONObject backup=new org.json.JSONObject(json);
        if(!"MoneyMateNZ".equals(backup.optString("format"))||backup.optInt("version")!=1)
            throw new org.json.JSONException("Not a supported MoneyMate NZ backup");
        String[] tables={"transactions","budgets","goals","bills","recurring"};
        for(String table:tables) {
            org.json.JSONArray rows=backup.getJSONArray(table);
            if(rows.length()>100000) throw new org.json.JSONException("Backup too large");
            java.util.Set<String> identities=new java.util.HashSet<>();
            for(int i=0;i<rows.length();i++) {
                org.json.JSONObject row=rows.getJSONObject(i);
                validateBackupRecord(table,row);
                String identity=table.equals("budgets")?row.getString("category"):Long.toString(row.getLong("id"));
                if(!identities.add(identity))throw new org.json.JSONException("Duplicate backup record");
                if(table.equals("transactions")) {
                    if(!row.has("id")||!row.has("date")||!row.has("kind")||!row.has("category")||!row.has("note")||row.getLong("cents")<=0)
                        throw new org.json.JSONException("Invalid transaction");
                    java.time.LocalDate.parse(row.getString("date"));
                    String kind=row.getString("kind");
                    if(!kind.equals("Income")&&!kind.equals("Expense")) throw new org.json.JSONException("Invalid transaction type");
                } else if(table.equals("budgets")) {
                    if(row.getLong("cents")<0||row.getString("category").isEmpty()) throw new org.json.JSONException("Invalid budget");
                } else if(table.equals("goals")) {
                    if(row.getLong("target")<=0||row.getLong("saved")<0||row.getString("name").isEmpty()) throw new org.json.JSONException("Invalid goal");
                } else if(table.equals("recurring")) {
                    if(row.getLong("cents")<=0||row.getString("category").isEmpty()||!java.util.Arrays.asList("Weekly","Fortnightly","Monthly").contains(row.getString("frequency")))
                        throw new org.json.JSONException("Invalid recurring transaction");
                    java.time.LocalDate.parse(row.getString("next_date"));
                } else {
                    if(row.getLong("cents")<=0||row.getString("name").isEmpty()) throw new org.json.JSONException("Invalid bill");
                    java.time.LocalDate.parse(row.getString("due"));
                }
            }
        }
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            for(String table:tables) {
                db.delete(table,null,null);
                org.json.JSONArray rows=backup.getJSONArray(table);
                for(int i=0;i<rows.length();i++) {
                    org.json.JSONObject row=rows.getJSONObject(i);
                    ContentValues values=new ContentValues();
                    java.util.Iterator<String> keys=row.keys();
                    while(keys.hasNext()) {
                        String key=keys.next();
                        Object value=row.get(key);
                        if(!allowedColumn(table,key))throw new org.json.JSONException("Unknown backup field: "+key);
                        if(value instanceof Number)values.put(key,((Number)value).longValue());
                        else if(value instanceof String)values.put(key,(String)value);
                        else throw new org.json.JSONException("Invalid backup data");
                    }
                    db.insertOrThrow(table,null,values);
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static void validateBackupRecord(String table,org.json.JSONObject row) throws org.json.JSONException {
        String[] numbers,texts;
        switch(table){
            case "transactions":numbers=new String[]{"id","cents"};texts=new String[]{"kind","category","note","date"};break;
            case "budgets":numbers=new String[]{"cents"};texts=new String[]{"category"};break;
            case "goals":numbers=new String[]{"id","target","saved"};texts=new String[]{"name"};break;
            case "bills":numbers=new String[]{"id","cents"};texts=new String[]{"name","due"};break;
            default:numbers=new String[]{"id","cents"};texts=new String[]{"kind","category","note","next_date","frequency"};
        }
        for(String key:numbers){
            Object value=row.get(key);
            if(!(value instanceof Number))throw new org.json.JSONException("Numeric field required");
            double amount=((Number)value).doubleValue();
            if(!Double.isFinite(amount)||amount<0||amount>9_007_199_254_740_991L||amount!=Math.floor(amount))
                throw new org.json.JSONException("Invalid integer amount");
            if((key.equals("id")||key.equals("target")||(key.equals("cents")&&!table.equals("budgets")))&&amount<1)
                throw new org.json.JSONException("Positive value required");
        }
        for(String key:texts){
            Object value=row.get(key);
            if(!(value instanceof String)||((String)value).length()>2000)throw new org.json.JSONException("Invalid text");
            if((key.equals("name")||key.equals("category"))&&((String)value).trim().isEmpty())throw new org.json.JSONException("Name required");
        }
        if(table.equals("transactions")||table.equals("recurring"))
            if(!java.util.Arrays.asList("Income","Expense").contains(row.getString("kind")))throw new org.json.JSONException("Invalid direction");
        if(row.length()!=numbers.length+texts.length)throw new org.json.JSONException("Unexpected backup field");
    }
    private boolean allowedColumn(String table,String col) {
        switch(table) {
            case "transactions": return java.util.Arrays.asList("id","kind","category","note","cents","date").contains(col);
            case "budgets": return java.util.Arrays.asList("category","cents").contains(col);
            case "goals": return java.util.Arrays.asList("id","name","target","saved").contains(col);
            case "bills": return java.util.Arrays.asList("id","name","cents","due").contains(col);
            case "recurring": return java.util.Arrays.asList("id","kind","category","note","cents","next_date","frequency").contains(col);
            default: return false;
        }
    }
}
