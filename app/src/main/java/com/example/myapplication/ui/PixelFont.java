package com.example.myapplication.ui;

import android.content.Context;
import android.graphics.Typeface;

import androidx.core.content.res.ResourcesCompat;

import com.example.myapplication.R;

public final class PixelFont {
    private static Typeface regular;
    private static Typeface bold;

    private PixelFont() {
    }

    public static Typeface get(Context context, boolean isBold) {
        if (regular == null) {
            Typeface family = ResourcesCompat.getFont(context.getApplicationContext(), R.font.pixel_font);
            regular = Typeface.create(family, Typeface.NORMAL);
            bold = Typeface.create(family, Typeface.BOLD);
        }
        return isBold ? bold : regular;
    }
}
