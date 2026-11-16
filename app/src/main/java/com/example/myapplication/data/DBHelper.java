package com.example.myapplication.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import com.example.myapplication.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

public class DBHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "pixel_snap.db";

    public static final int DB_VERSION = 4;

    public static final String TABLE_MEMORIES = "memories";
    public static final String COL_ID = "_id";
    public static final String COL_TAKEN_AT = "taken_at";
    public static final String COL_FAVORITE = "is_favorite";
    public static final String COL_IMAGE = "image_base64";

    // [เพิ่ม - ฟีเจอร์อวาตาร์] ตาราง avatars และคอลัมน์ (DB v4)
    public static final String TABLE_AVATARS = "avatars";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_MEMORY_ID = "memory_id";
    public static final String COL_SPRITE = "sprite_base64";

    private static final String INDEX_TAKEN_AT = "idx_memories_taken_at";

    private static final String SQL_CREATE_MEMORIES =
            "CREATE TABLE " + TABLE_MEMORIES + " ("
                    + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_TAKEN_AT + " INTEGER NOT NULL, "
                    + COL_FAVORITE + " INTEGER NOT NULL DEFAULT 0, "
                    + COL_IMAGE + " TEXT NOT NULL)";

    private static final String SQL_CREATE_INDEX =
            "CREATE INDEX " + INDEX_TAKEN_AT + " ON " + TABLE_MEMORIES + " (" + COL_TAKEN_AT + " DESC)";

    /** One cut-out avatar. If its source photo is deleted, memory_id becomes NULL and the avatar stays. */
    private static final String SQL_CREATE_AVATARS =
            "CREATE TABLE " + TABLE_AVATARS + " ("
                    + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_CREATED_AT + " INTEGER NOT NULL, "
                    + COL_MEMORY_ID + " INTEGER REFERENCES " + TABLE_MEMORIES + "(" + COL_ID + ") ON DELETE SET NULL, "
                    + COL_SPRITE + " TEXT NOT NULL)";

    private static final Sample[] SAMPLES = {
            new Sample("sample_daisy", R.raw.sample_daisy, 14, 9, 41, true),
            new Sample("sample_cat", R.raw.sample_cat, 14, 8, 12, true),
            new Sample("sample_beach", R.raw.sample_beach, 13, 16, 30, false),
            new Sample("sample_matcha", R.raw.sample_matcha, 13, 11, 5, false),
            new Sample("sample_city", R.raw.sample_city, 12, 17, 48, false),
            new Sample("sample_forest", R.raw.sample_forest, 12, 10, 24, false),
    };

    private final Context context;

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    // [เพิ่ม - ฟีเจอร์อวาตาร์] เปิด foreign key ให้ ON DELETE SET NULL ของตาราง avatars ทำงาน
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // SQLite leaves foreign keys off by default; ON DELETE SET NULL needs them on.
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_MEMORIES);
        db.execSQL(SQL_CREATE_INDEX);
        db.execSQL(SQL_CREATE_AVATARS);
        for (Sample s : SAMPLES) {
            insert(db, s.takenAt(), s.favorite, readRaw(s.rawRes));
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            upgradeFromV1(db);
        } else if (oldVersion < 3) {
            upgradeFromV2(db);
        }
        // [เพิ่ม - ฟีเจอร์อวาตาร์] v3 → v4: เพิ่มตาราง avatars โดยไม่แตะข้อมูลรูปเดิม
        if (oldVersion < 4) {
            db.execSQL(SQL_CREATE_AVATARS);
        }
    }

    private void upgradeFromV1(SQLiteDatabase db) {
        String old = renameOldTable(db);
        try (Cursor c = db.rawQuery("SELECT taken_at, is_favorite, sample_name, photo FROM " + old, null)) {
            while (c.moveToNext()) {
                long takenAt = c.getLong(0);
                boolean favorite = c.getInt(1) == 1;
                if (!c.isNull(3)) {
                    insert(db, takenAt, favorite, c.getBlob(3));
                } else {
                    Sample s = sampleByName(c.getString(2));
                    if (s != null) insert(db, takenAt, favorite, readRaw(s.rawRes));
                }
            }
        }
        db.execSQL("DROP TABLE " + old);
    }

    private void upgradeFromV2(SQLiteDatabase db) {
        String old = renameOldTable(db);
        db.execSQL("INSERT INTO " + TABLE_MEMORIES + " (" + COL_ID + ", " + COL_TAKEN_AT + ", "
                + COL_FAVORITE + ", " + COL_IMAGE + ") SELECT _id, taken_at, is_favorite, image_base64 FROM " + old);
        db.execSQL("DROP TABLE " + old);
    }

    private static String renameOldTable(SQLiteDatabase db) {
        String old = TABLE_MEMORIES + "_old";
        db.execSQL("DROP INDEX IF EXISTS " + INDEX_TAKEN_AT);
        db.execSQL("ALTER TABLE " + TABLE_MEMORIES + " RENAME TO " + old);
        db.execSQL(SQL_CREATE_MEMORIES);
        db.execSQL(SQL_CREATE_INDEX);
        return old;
    }

    @NonNull
    public static String encodeImage(@NonNull byte[] imageBytes) {
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
    }

    @NonNull
    public static byte[] decodeImage(@NonNull String base64) {
        return Base64.decode(base64, Base64.NO_WRAP);
    }

    private static long insert(SQLiteDatabase db, long takenAt, boolean favorite, byte[] image) {
        ContentValues values = new ContentValues();
        values.put(COL_TAKEN_AT, takenAt);
        values.put(COL_FAVORITE, favorite ? 1 : 0);
        values.put(COL_IMAGE, encodeImage(image));
        return db.insert(TABLE_MEMORIES, null, values);
    }

    private byte[] readRaw(@RawRes int res) {
        try (InputStream in = context.getResources().openRawResource(res)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read sample image", e);
        }
    }

    @Nullable
    private static Sample sampleByName(@Nullable String name) {
        for (Sample s : SAMPLES) {
            if (s.name.equals(name)) return s;
        }
        return null;
    }

    private static final class Sample {
        final String name;
        @RawRes
        final int rawRes;
        final int day, hour, minute;
        final boolean favorite;

        Sample(String name, @RawRes int rawRes, int day, int hour, int minute, boolean favorite) {
            this.name = name;
            this.rawRes = rawRes;
            this.day = day;
            this.hour = hour;
            this.minute = minute;
            this.favorite = favorite;
        }

        long takenAt() {
            Calendar c = Calendar.getInstance();
            c.set(2026, Calendar.NOVEMBER, day, hour, minute, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        }
    }
}
