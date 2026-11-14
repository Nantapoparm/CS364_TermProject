package com.example.myapplication.ui;

import android.graphics.Bitmap;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.example.myapplication.R;

public final class PhotoViews {
    private PhotoViews() {
    }

    public static void show(ImageView view, @Nullable Bitmap photo) {
        show(view, photo, ImageView.ScaleType.CENTER_CROP);
    }

    public static void show(ImageView view, @Nullable Bitmap photo, ImageView.ScaleType scaleType) {
        if (photo == null) {
            showPlaceholder(view);
            return;
        }
        view.setScaleType(scaleType);
        view.setImageDrawable(Pixelator.crispDrawable(view.getResources(), photo));
    }

    public static void showPlaceholder(ImageView view) {
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        view.setImageResource(R.drawable.placeholder_image);
    }
}
