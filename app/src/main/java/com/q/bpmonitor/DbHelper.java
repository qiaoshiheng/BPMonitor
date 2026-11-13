package com.q.bpmonitor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "bp_monitor.db";
    private static final int DB_VERSION = 3; // v3: 用户基础资料（年龄/性别/身高/体重）

    public static final long DEFAULT_USER_ID = 1;

    /** 各用户头像可用的颜色 */
    public static final int[] USER_COLORS = {
            0xFFE5484D, 0xFF3E7BFA, 0xFF30A46C, 0xFFF5A524,
            0xFF9E6BDE, 0xFF12A594, 0xFFE56DB1, 0xFF6C7A8F
    };

    public DbHelper(Context c) {
        super(c, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bp_user(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "color INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "sex INTEGER DEFAULT -1," +
                "age REAL DEFAULT 0," +
                "height REAL DEFAULT 0," +
                "weight REAL DEFAULT 0)");
        db.execSQL("CREATE TABLE bp_record(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER NOT NULL," +
                "sys INTEGER NOT NULL," +
                "dia INTEGER NOT NULL," +
                "hr INTEGER NOT NULL," +
                "irregular INTEGER DEFAULT 0," +
                "afib INTEGER DEFAULT 0," +
                "note TEXT," +
                "user_id INTEGER DEFAULT 1)");
        db.execSQL("CREATE INDEX idx_bp_ts ON bp_record(ts)");
        db.execSQL("CREATE INDEX idx_bp_user ON bp_record(user_id)");
        ContentValues cv = new ContentValues();
        cv.put("id", DEFAULT_USER_ID);
        cv.put("name", "我");
        cv.put("color", USER_COLORS[0]);
        cv.put("created_at", System.currentTimeMillis());
        db.insert("bp_user", null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            // v1 → v2：补用户表 + 记录表加 user_id 列，历史记录归入默认用户
            db.execSQL("CREATE TABLE IF NOT EXISTS bp_user(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "color INTEGER NOT NULL," +
                    "created_at INTEGER NOT NULL)");
            db.execSQL("ALTER TABLE bp_record ADD COLUMN user_id INTEGER DEFAULT 1");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bp_user ON bp_record(user_id)");
            ContentValues cv = new ContentValues();
            cv.put("id", DEFAULT_USER_ID);
            cv.put("name", "我");
            cv.put("color", USER_COLORS[0]);
            cv.put("created_at", System.currentTimeMillis());
            db.insertWithOnConflict("bp_user", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
        if (oldV < 3) {
            // v2 → v3：用户基础资料列
            db.execSQL("ALTER TABLE bp_user ADD COLUMN sex INTEGER DEFAULT -1");
            db.execSQL("ALTER TABLE bp_user ADD COLUMN age REAL DEFAULT 0");
            db.execSQL("ALTER TABLE bp_user ADD COLUMN height REAL DEFAULT 0");
            db.execSQL("ALTER TABLE bp_user ADD COLUMN weight REAL DEFAULT 0");
        }
    }

    // ---------- 用户 ----------

    public List<User> users() {
        List<User> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM bp_user ORDER BY id ASC", null);
        try {
            while (c.moveToNext()) {
                User u = new User();
                u.id = c.getLong(c.getColumnIndexOrThrow("id"));
                u.name = c.getString(c.getColumnIndexOrThrow("name"));
                u.color = c.getInt(c.getColumnIndexOrThrow("color"));
                u.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                u.sex = c.getInt(c.getColumnIndexOrThrow("sex"));
                u.age = c.getDouble(c.getColumnIndexOrThrow("age"));
                u.height = c.getDouble(c.getColumnIndexOrThrow("height"));
                u.weight = c.getDouble(c.getColumnIndexOrThrow("weight"));
                out.add(u);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public long insertUser(String name, int color) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("color", color);
        cv.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("bp_user", null, cv);
    }

    public void updateUser(long id, String name, int sex, double age, double height, double weight) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("sex", sex);
        cv.put("age", age);
        cv.put("height", height);
        cv.put("weight", weight);
        getWritableDatabase().update("bp_user", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public User user(long id) {
        for (User u : users()) if (u.id == id) return u;
        return users().isEmpty() ? null : users().get(0);
    }

    public int nextUserColor() {
        int n = users().size();
        return USER_COLORS[n % USER_COLORS.length];
    }

    // ---------- 记录 ----------

    public long insert(Record r) {
        ContentValues cv = new ContentValues();
        cv.put("ts", r.ts);
        cv.put("sys", r.sys);
        cv.put("dia", r.dia);
        cv.put("hr", r.hr);
        cv.put("irregular", r.irregular ? 1 : 0);
        cv.put("afib", r.afib ? 1 : 0);
        cv.put("note", r.note);
        cv.put("user_id", r.userId);
        return getWritableDatabase().insert("bp_record", null, cv);
    }

    public int delete(long id) {
        return getWritableDatabase().delete("bp_record", "id=?", new String[]{String.valueOf(id)});
    }

    public Record latest(long userId) {
        List<Record> l = query("SELECT * FROM bp_record WHERE user_id=? ORDER BY ts DESC LIMIT 1",
                String.valueOf(userId));
        return l.isEmpty() ? null : l.get(0);
    }

    /** 某用户的全部记录，按时间倒序 */
    public List<Record> all(long userId) {
        return query("SELECT * FROM bp_record WHERE user_id=? ORDER BY ts DESC", String.valueOf(userId));
    }

    /** 某用户某时间点之后的记录，按时间正序 */
    public List<Record> since(long ts, long userId) {
        return query("SELECT * FROM bp_record WHERE ts>=? AND user_id=? ORDER BY ts ASC",
                String.valueOf(ts), String.valueOf(userId));
    }

    private List<Record> query(String sql, String... args) {
        List<Record> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            while (c.moveToNext()) {
                Record r = new Record();
                r.id = c.getLong(c.getColumnIndexOrThrow("id"));
                r.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
                r.sys = c.getInt(c.getColumnIndexOrThrow("sys"));
                r.dia = c.getInt(c.getColumnIndexOrThrow("dia"));
                r.hr = c.getInt(c.getColumnIndexOrThrow("hr"));
                r.irregular = c.getInt(c.getColumnIndexOrThrow("irregular")) == 1;
                r.afib = c.getInt(c.getColumnIndexOrThrow("afib")) == 1;
                r.note = c.getString(c.getColumnIndexOrThrow("note"));
                r.userId = c.getLong(c.getColumnIndexOrThrow("user_id"));
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }
}
