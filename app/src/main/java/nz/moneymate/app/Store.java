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
}
