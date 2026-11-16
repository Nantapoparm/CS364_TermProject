package com.example.myapplication.data;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

/** One row of the avatars table: the cut-out body. Legs and walk frames are rebuilt from code. */
public final class Avatar {
    private final long id;
    private final long createdAt;
    private final Bitmap cutOut;

    Avatar(long id, long createdAt, @NonNull Bitmap cutOut) {
        this.id = id;
        this.createdAt = createdAt;
        this.cutOut = cutOut;
    }

    public long getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /** Small transparent PNG of the cut-out part, without outline. */
    @NonNull
    public Bitmap getCutOut() {
        return cutOut;
    }
}
