package com.example.myapplication.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.example.myapplication.data.DBHelper.COL_FAVORITE;
import static com.example.myapplication.data.DBHelper.COL_ID;
import static com.example.myapplication.data.DBHelper.COL_IMAGE;
import static com.example.myapplication.data.DBHelper.COL_TAKEN_AT;
import static com.example.myapplication.data.DBHelper.TABLE_MEMORIES;

public final class MemoryRepository {
    public static final int MAX_MEMORIES = 99;

    private static MemoryRepository instance;

    private final DBHelper dbHelper;
    private final List<Memory> memories = new ArrayList<>();

    private final LruCache<Long, Bitmap> imageCache =
            new LruCache<Long, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 8, 32L * 1024 * 1024)) {
                @Override
                protected int sizeOf(Long key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    public static synchronized MemoryRepository get(@NonNull Context context) {
        if (instance == null) {
            instance = new MemoryRepository(context.getApplicationContext());
        }
        return instance;
    }

    private MemoryRepository(Context appContext) {
        dbHelper = new DBHelper(appContext);
        loadAll();
    }

    private void loadAll() {
        memories.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query(TABLE_MEMORIES,
                new String[]{COL_ID, COL_TAKEN_AT, COL_FAVORITE},
                null, null, null, null, COL_TAKEN_AT + " DESC")) {
            while (c.moveToNext()) {
                memories.add(new Memory(c.getLong(0), c.getLong(1), c.getInt(2) == 1));
            }
        }
    }

    public synchronized List<Memory> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(memories));
    }

    public synchronized List<Memory> getFavorites() {
        List<Memory> result = new ArrayList<>();
        for (Memory m : memories) {
            if (m.isFavorite()) result.add(m);
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized int size() {
        return memories.size();
    }

    public synchronized int favoriteCount() {
        return getFavorites().size();
    }

    public synchronized boolean isFull() {
        return memories.size() >= MAX_MEMORIES;
    }

    @Nullable
    public synchronized Memory find(long id) {
        for (Memory m : memories) {
            if (m.getId() == id) return m;
        }
        return null;
    }

    @Nullable
    public synchronized Memory getLatest() {
        return memories.isEmpty() ? null : memories.get(0);
    }

    @Nullable
    public synchronized Bitmap loadImage(long id) {
        Bitmap cached = imageCache.get(id);
        if (cached != null) return cached;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query(TABLE_MEMORIES, new String[]{COL_IMAGE},
                COL_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            byte[] bytes = DBHelper.decodeImage(c.getString(0));
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap != null) imageCache.put(id, bitmap);
            return bitmap;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public synchronized Memory addPhoto(@NonNull Bitmap photo) {
        ByteArrayOutputStream png = new ByteArrayOutputStream();

        if (!photo.compress(Bitmap.CompressFormat.PNG, 100, png)) return null;

        long takenAt = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(COL_TAKEN_AT, takenAt);
        values.put(COL_FAVORITE, 0);
        values.put(COL_IMAGE, DBHelper.encodeImage(png.toByteArray()));
        long id = dbHelper.getWritableDatabase().insert(TABLE_MEMORIES, null, values);
        if (id == -1) return null;

        imageCache.put(id, photo);
        Memory m = new Memory(id, takenAt, false);
        memories.add(0, m);
        return m;
    }

    @Nullable
    public synchronized Memory toggleFavorite(long id) {
        for (int i = 0; i < memories.size(); i++) {
            Memory m = memories.get(i);
            if (m.getId() == id) {
                Memory updated = m.withFavorite(!m.isFavorite());
                ContentValues values = new ContentValues();
                values.put(COL_FAVORITE, updated.isFavorite() ? 1 : 0);
                int rows = dbHelper.getWritableDatabase().update(TABLE_MEMORIES, values,
                        COL_ID + " = ?", new String[]{String.valueOf(id)});
                if (rows == 0) return null;
                memories.set(i, updated);
                return updated;
            }
        }
        return null;
    }

    public synchronized boolean delete(long id) {
        int rows = dbHelper.getWritableDatabase().delete(TABLE_MEMORIES,
                COL_ID + " = ?", new String[]{String.valueOf(id)});
        if (rows == 0) return false;
        for (int i = 0; i < memories.size(); i++) {
            if (memories.get(i).getId() == id) {
                memories.remove(i);
                break;
            }
        }
        imageCache.remove(id);
        return true;
    }
}
