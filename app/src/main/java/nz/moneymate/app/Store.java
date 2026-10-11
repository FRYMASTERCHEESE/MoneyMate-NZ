package nz.moneymate.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import java.util.ArrayList;
import java.util.List;

public final class Store extends SQLiteOpenHelper {
    public Store(Context c) { super(c, "moneymate.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL CHECK(kind IN ('Income','Expense')), category TEXT NOT NULL, note TEXT NOT NULL, cents INTEGER NOT NULL CHECK(cents > 0), date TEXT NOT NULL)");
        db.execSQL("CREATE TABLE budgets (category TEXT PRIMARY KEY, cents INTEGER NOT NULL CHECK(cents >= 0))");
        db.execSQL("CREATE TABLE goals (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, target INTEGER NOT NULL CHECK(target > 0), saved INTEGER NOT NULL DEFAULT 0 CHECK(saved >= 0))");
        db.execSQL("CREATE TABLE bills (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, cents INTEGER NOT NULL CHECK(cents > 0), due TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("Migration required"); }
    public void addTransaction(String kind, String category, String note, long cents, String date) {
        ContentValues v = new ContentValues(); v.put("kind", kind); v.put("category",category); v.put("note",note); v.put("cents",cents); v.put("date",date);
        getWritableDatabase().insertOrThrow("transactions",null,v);
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

    /** Complete, versioned, locally generated backup. Never uploads financial records. */
    public String backupJson() throws org.json.JSONException {
        org.json.JSONObject backup=new org.json.JSONObject();
        backup.put("format","MoneyMateNZ");
        backup.put("version",1);
        for(String table:new String[]{"transactions","budgets","goals","bills"}) {
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
        String[] tables={"transactions","budgets","goals","bills"};
        for(String table:tables) {
            org.json.JSONArray rows=backup.getJSONArray(table);
            if(rows.length()>100000) throw new org.json.JSONException("Backup too large");
            for(int i=0;i<rows.length();i++) {
                org.json.JSONObject row=rows.getJSONObject(i);
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
    private boolean allowedColumn(String table,String col) {
        switch(table) {
            case "transactions": return java.util.Arrays.asList("id","kind","category","note","cents","date").contains(col);
            case "budgets": return java.util.Arrays.asList("category","cents").contains(col);
            case "goals": return java.util.Arrays.asList("id","name","target","saved").contains(col);
            case "bills": return java.util.Arrays.asList("id","name","cents","due").contains(col);
            default: return false;
        }
    }
}
