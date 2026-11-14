package com.example.myapplication.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.example.myapplication.data.DBHelper.COL_FAVORITE;
import static com.example.myapplication.data.DBHelper.COL_ID;
import static com.example.myapplication.data.DBHelper.COL_IMAGE;
import static com.example.myapplication.data.DBHelper.COL_TAKEN_AT;
import static com.example.myapplication.data.DBHelper.TABLE_MEMORIES;

public final class MemoryRepository {
    public static final int MAX_MEMORIES = 99;

    public interface Callback<T> {
        void onResult(@Nullable T result);
    }

    public interface Listener {
        void onAlbumChanged();
    }

    private static MemoryRepository instance;

    private final DBHelper dbHelper;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<Memory> memories = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private boolean loaded;

    private final LruCache<Long, Bitmap> imageCache =
            new LruCache<Long, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 8, 32L * 1024 * 1024)) {
                @Override
                protected int sizeOf(Long key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    @MainThread
    public static MemoryRepository get(@NonNull Context context) {
        if (instance == null) {
            instance = new MemoryRepository(context.getApplicationContext());
        }
        return instance;
    }

    private MemoryRepository(Context appContext) {
        dbHelper = new DBHelper(appContext);
        dbExecutor.execute(() -> {
            List<Memory> rows = queryAll();
            mainHandler.post(() -> {
                memories.clear();
                memories.addAll(rows);
                loaded = true;
                notifyChanged();
            });
        });
    }

    @MainThread
    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
    }

    @MainThread
    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @MainThread
    private void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.onAlbumChanged();
        }
    }

    @MainThread
    public boolean isLoaded() {
        return loaded;
    }

    @MainThread
    public List<Memory> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(memories));
    }

    @MainThread
    public List<Memory> getFavorites() {
        List<Memory> result = new ArrayList<>();
        for (Memory m : memories) {
            if (m.isFavorite()) result.add(m);
        }
        return Collections.unmodifiableList(result);
    }

    @MainThread
    public int size() {
        return memories.size();
    }

    @MainThread
    public int favoriteCount() {
        return getFavorites().size();
    }

    @MainThread
    public boolean isFull() {
        return memories.size() >= MAX_MEMORIES;
    }

    @MainThread
    @Nullable
    public Memory find(long id) {
        for (Memory m : memories) {
            if (m.getId() == id) return m;
        }
        return null;
    }

    @MainThread
    @Nullable
    public Memory getLatest() {
        return memories.isEmpty() ? null : memories.get(0);
    }

    @MainThread
    public void loadImage(long id, @NonNull Callback<Bitmap> callback) {
        Bitmap cached = imageCache.get(id);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }
        dbExecutor.execute(() -> {
            Bitmap bitmap = queryImage(id);
            if (bitmap != null) imageCache.put(id, bitmap);
            mainHandler.post(() -> callback.onResult(bitmap));
        });
    }

    @MainThread
    public void addPhoto(@NonNull Bitmap photo, @NonNull Callback<Memory> callback) {
        long takenAt = System.currentTimeMillis();
        dbExecutor.execute(() -> {
            long id = insertPhoto(photo, takenAt);
            if (id != -1) imageCache.put(id, photo);
            mainHandler.post(() -> {
                if (id == -1) {
                    callback.onResult(null);
                    return;
                }
                Memory m = new Memory(id, takenAt, false);
                memories.add(0, m);
                callback.onResult(m);
                notifyChanged();
            });
        });
    }

    @MainThread
    public void toggleFavorite(long id, @NonNull Callback<Memory> callback) {
        Memory current = find(id);
        if (current == null) {
            callback.onResult(null);
            return;
        }
        boolean favorite = !current.isFavorite();
        dbExecutor.execute(() -> {
            boolean ok = updateFavorite(id, favorite);
            mainHandler.post(() -> {
                Memory updated = null;
                if (ok) {
                    for (int i = 0; i < memories.size(); i++) {
                        if (memories.get(i).getId() == id) {
                            updated = memories.get(i).withFavorite(favorite);
                            memories.set(i, updated);
                            break;
                        }
                    }
                }
                callback.onResult(updated);
                if (updated != null) notifyChanged();
            });
        });
    }

    @MainThread
    public void delete(long id, @NonNull Callback<Boolean> callback) {
        dbExecutor.execute(() -> {
            boolean ok = deleteRow(id);
            if (ok) imageCache.remove(id);
            mainHandler.post(() -> {
                if (ok) {
                    for (int i = 0; i < memories.size(); i++) {
                        if (memories.get(i).getId() == id) {
                            memories.remove(i);
                            break;
                        }
                    }
                }
                callback.onResult(ok);
                if (ok) notifyChanged();
            });
        });
    }

    @WorkerThread
    private List<Memory> queryAll() {
        List<Memory> rows = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query(TABLE_MEMORIES,
                new String[]{COL_ID, COL_TAKEN_AT, COL_FAVORITE},
                null, null, null, null, COL_TAKEN_AT + " DESC")) {
            while (c.moveToNext()) {
                rows.add(new Memory(c.getLong(0), c.getLong(1), c.getInt(2) == 1));
            }
        }
        return rows;
    }

    @WorkerThread
    @Nullable
    private Bitmap queryImage(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query(TABLE_MEMORIES, new String[]{COL_IMAGE},
                COL_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            byte[] bytes = DBHelper.decodeImage(c.getString(0));
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @WorkerThread
    private long insertPhoto(Bitmap photo, long takenAt) {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!photo.compress(Bitmap.CompressFormat.PNG, 100, png)) return -1;
        ContentValues values = new ContentValues();
        values.put(COL_TAKEN_AT, takenAt);
        values.put(COL_FAVORITE, 0);
        values.put(COL_IMAGE, DBHelper.encodeImage(png.toByteArray()));
        return dbHelper.getWritableDatabase().insert(TABLE_MEMORIES, null, values);
    }

    @WorkerThread
    private boolean updateFavorite(long id, boolean favorite) {
        ContentValues values = new ContentValues();
        values.put(COL_FAVORITE, favorite ? 1 : 0);
        return dbHelper.getWritableDatabase().update(TABLE_MEMORIES, values,
                COL_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    @WorkerThread
    private boolean deleteRow(long id) {
        return dbHelper.getWritableDatabase().delete(TABLE_MEMORIES,
                COL_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }
}
