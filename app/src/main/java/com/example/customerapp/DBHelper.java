package com.example.customerapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {

    public DBHelper(Context c) {
        super(c, "customers.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE customers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, father TEXT, mobile TEXT, location TEXT, address TEXT, " +
                "date TEXT, time TEXT, aadhaar_front TEXT, aadhaar_back TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        db.execSQL("DROP TABLE IF EXISTS customers");
        onCreate(db);
    }

    /** मोबाइल (अगर दिया हो) या नाम + पिता का नाम पहले से मौजूद हो तो true */
    public boolean isDuplicate(String name, String father, String mobile) {
        SQLiteDatabase db = getReadableDatabase();
        if (mobile != null && !mobile.isEmpty()) {
            Cursor c = db.rawQuery("SELECT 1 FROM customers WHERE mobile=?", new String[]{mobile});
            boolean f = c.moveToFirst();
            c.close();
            if (f) return true;
        }
        Cursor c = db.rawQuery(
                "SELECT 1 FROM customers WHERE name=? COLLATE NOCASE AND father=? COLLATE NOCASE",
                new String[]{name, father == null ? "" : father});
        boolean f = c.moveToFirst();
        c.close();
        return f;
    }

    private ContentValues values(Customer c) {
        ContentValues v = new ContentValues();
        v.put("name", c.name);
        v.put("father", c.father);
        v.put("mobile", c.mobile);
        v.put("location", c.location);
        v.put("address", c.address);
        v.put("date", c.date);
        v.put("time", c.time);
        v.put("aadhaar_front", c.front);
        v.put("aadhaar_back", c.back);
        return v;
    }

    public boolean insert(Customer c) {
        return getWritableDatabase().insert("customers", null, values(c)) != -1;
    }

    public void update(Customer c) {
        getWritableDatabase().update("customers", values(c), "id=?",
                new String[]{String.valueOf(c.id)});
    }

    public void delete(int id) {
        getWritableDatabase().delete("customers", "id=?", new String[]{String.valueOf(id)});
    }

    /** नाम, पिता का नाम, मोबाइल, पता/गाँव और लोकेशन में खोजता है */
    public List<Customer> search(String q) {
        List<Customer> list = new ArrayList<>();
        SQLiteDatabase d = getReadableDatabase();
        Cursor c;
        if (q == null || q.trim().isEmpty()) {
            c = d.rawQuery("SELECT * FROM customers ORDER BY id DESC", null);
        } else {
            String like = "%" + q.trim() + "%";
            c = d.rawQuery("SELECT * FROM customers WHERE name LIKE ? OR father LIKE ? " +
                            "OR mobile LIKE ? OR address LIKE ? OR location LIKE ? " +
                            "ORDER BY name COLLATE NOCASE",
                    new String[]{like, like, like, like, like});
        }
        while (c.moveToNext()) {
            Customer x = new Customer();
            x.id = c.getInt(c.getColumnIndexOrThrow("id"));
            x.name = s(c, "name");
            x.father = s(c, "father");
            x.mobile = s(c, "mobile");
            x.location = s(c, "location");
            x.address = s(c, "address");
            x.date = s(c, "date");
            x.time = s(c, "time");
            x.front = s(c, "aadhaar_front");
            x.back = s(c, "aadhaar_back");
            list.add(x);
        }
        c.close();
        return list;
    }

    private String s(Cursor c, String col) {
        String v = c.getString(c.getColumnIndexOrThrow(col));
        return v == null ? "" : v;
    }

    /** रीस्टोर के बाद फ़ोटो के रास्ते मौजूदा फ़ोल्डर के हिसाब से ठीक करता है */
    public void fixPhotoPaths(String dir) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id, aadhaar_front, aadhaar_back FROM customers", null);
        while (c.moveToNext()) {
            ContentValues v = new ContentValues();
            String f = c.getString(1), b = c.getString(2);
            v.put("aadhaar_front", (f == null || f.isEmpty()) ? "" : dir + "/" + new File(f).getName());
            v.put("aadhaar_back", (b == null || b.isEmpty()) ? "" : dir + "/" + new File(b).getName());
            db.update("customers", v, "id=?", new String[]{String.valueOf(c.getInt(0))});
        }
        c.close();
    }
}
