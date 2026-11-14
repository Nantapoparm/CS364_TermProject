package com.example.myapplication.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

public class GridBackgroundDrawable extends Drawable {
    private final int backgroundColor;
    private final Paint linePaint = new Paint();
    private final float cellSize;

    public GridBackgroundDrawable(Context context) {
        backgroundColor = ContextCompat.getColor(context, R.color.retro_bg);
        cellSize = context.getResources().getDimension(R.dimen.grid_cell);
        linePaint.setColor(ContextCompat.getColor(context, R.color.retro_grid_line));
        linePaint.setStrokeWidth(context.getResources().getDisplayMetrics().density * 1.5f);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        canvas.drawColor(backgroundColor);
        for (float x = b.left; x <= b.right; x += cellSize) {
            canvas.drawLine(x, b.top, x, b.bottom, linePaint);
        }
        for (float y = b.top; y <= b.bottom; y += cellSize) {
            canvas.drawLine(b.left, y, b.right, y, linePaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        linePaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        linePaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
