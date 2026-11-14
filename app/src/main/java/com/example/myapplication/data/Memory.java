package com.example.myapplication.data;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.example.myapplication.ui.PhotoViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public final class Memory {
    private static final Locale DATE_LOCALE = Locale.ENGLISH;

    private static final int MAX_PIXEL_ART_WIDTH = 200;

    private final long id;
    private final long takenAt;
    private final boolean favorite;

    Memory(long id, long takenAt, boolean favorite) {
        this.id = id;
        this.takenAt = takenAt;
        this.favorite = favorite;
    }

    public long getId() {
        return id;
    }

    public long getTakenAt() {
        return takenAt;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public Memory withFavorite(boolean favorite) {
        return new Memory(id, takenAt, favorite);
    }

    public void loadInto(@NonNull ImageView view) {
        loadInto(view, ImageView.ScaleType.CENTER_CROP);
    }

    public void loadInto(@NonNull ImageView view, @NonNull ImageView.ScaleType scaleType) {
        Bitmap image = MemoryRepository.get(view.getContext()).loadImage(id);
        if (image != null && image.getWidth() > MAX_PIXEL_ART_WIDTH) {
            view.setScaleType(scaleType);
            view.setImageDrawable(new BitmapDrawable(view.getResources(), image));
        } else {
            PhotoViews.show(view, image, scaleType);
        }
    }

    public String formatDate() {
        return new SimpleDateFormat("d MMM · HH:mm", DATE_LOCALE).format(new Date(takenAt));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Memory)) return false;
        Memory m = (Memory) o;
        return id == m.id && takenAt == m.takenAt && favorite == m.favorite;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, takenAt, favorite);
    }
}
