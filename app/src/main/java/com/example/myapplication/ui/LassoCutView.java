package com.example.myapplication.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * [ใหม่ - ฟีเจอร์อวาตาร์] View สำหรับใช้นิ้ววาดวงรอบตัวเพื่อตัดออกจากพื้นหลัง (ไม่ใช้ ML)
 *
 * <ul>
 *   <li>แสดงรูปพิกเซลเล็ก ๆ ขยายเต็มช่อง ระหว่างลากนิ้วจะวาดเส้นประให้เห็น</li>
 *   <li>ยกนิ้วแล้วปิดวงอัตโนมัติ และทำส่วนนอกวงให้มืดลงเพื่อดูว่าจะได้อะไร</li>
 *   <li>{@link #cutOut()} เทสีวงลง mask ขนาดเท่ารูปพิกเซล ขอบจึงเป็นขั้นบันไดพิกเซลเอง
 *       แล้วเก็บเฉพาะพิกเซลในวงและครอปให้พอดีตัว ถ้าวงเล็กเกินไปจะคืนค่า null</li>
 * </ul>
 */
public class LassoCutView extends View {
    public interface OnLassoListener {
        void onLassoChanged(boolean closed);
    }

    /** Fewer pixels than this means a tap or a straight line, not a real loop. */
    private static final int MIN_PIXELS = 30;
    private static final int MIN_SIDE = 5;

    private final Paint bitmapPaint = new Paint();
    private final Paint dimPaint = new Paint();
    private final Paint lineDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineLight = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path = new Path();
    private final Matrix imageToView = new Matrix();
    private final Matrix viewToImage = new Matrix();
    private final RectF imageRect = new RectF();

    @Nullable
    private Bitmap image;
    private boolean drawing;
    private boolean closed;
    @Nullable
    private OnLassoListener listener;

    public LassoCutView(Context context) {
        this(context, null);
    }

    public LassoCutView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float dp = getResources().getDisplayMetrics().density;
        bitmapPaint.setFilterBitmap(false);
        dimPaint.setColor(0xA6000000);
        lineDark.setStyle(Paint.Style.STROKE);
        lineDark.setStrokeWidth(4 * dp);
        lineDark.setColor(0xFF2B1E1A);
        lineDark.setStrokeJoin(Paint.Join.ROUND);
        lineDark.setStrokeCap(Paint.Cap.ROUND);
        lineLight.set(lineDark);
        lineLight.setStrokeWidth(2 * dp);
        lineLight.setColor(Color.WHITE);
        lineLight.setPathEffect(new DashPathEffect(new float[]{6 * dp, 4 * dp}, 0));
    }

    public void setImage(@Nullable Bitmap image) {
        this.image = image;
        reset();
        updateMatrix();
    }

    public void setOnLassoListener(@Nullable OnLassoListener listener) {
        this.listener = listener;
    }

    public void reset() {
        path.reset();
        drawing = false;
        closed = false;
        invalidate();
        if (listener != null) listener.onLassoChanged(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateMatrix();
        reset();
    }

    private void updateMatrix() {
        imageToView.reset();
        imageRect.setEmpty();
        if (image == null || getWidth() == 0 || getHeight() == 0) return;
        RectF src = new RectF(0, 0, image.getWidth(), image.getHeight());
        RectF dst = new RectF(getPaddingLeft(), getPaddingTop(),
                getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        imageToView.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);
        imageToView.invert(viewToImage);
        imageToView.mapRect(imageRect, src);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (image == null) return;
        canvas.drawBitmap(image, imageToView, bitmapPaint);
        if (closed) {
            canvas.save();
            canvas.clipOutPath(path);
            canvas.drawRect(imageRect, dimPaint);
            canvas.restore();
        }
        if (!path.isEmpty()) {
            canvas.drawPath(path, lineDark);
            canvas.drawPath(path, lineLight);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (image == null) return false;
        float x = clamp(e.getX(), imageRect.left, imageRect.right);
        float y = clamp(e.getY(), imageRect.top, imageRect.bottom);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                path.reset();
                path.moveTo(x, y);
                drawing = true;
                closed = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (listener != null) listener.onLassoChanged(false);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!drawing) return false;
                for (int i = 0; i < e.getHistorySize(); i++) {
                    path.lineTo(clamp(e.getHistoricalX(i), imageRect.left, imageRect.right),
                            clamp(e.getHistoricalY(i), imageRect.top, imageRect.bottom));
                }
                path.lineTo(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (!drawing) return false;
                path.lineTo(x, y);
                path.close(); // join back to where the finger started
                drawing = false;
                closed = true;
                invalidate();
                performClick();
                if (listener != null) listener.onLassoChanged(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                reset();
                return true;
            default:
                return drawing;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /**
     * Fills the loop onto a mask the same size as the small photo, so the edge follows the
     * pixel grid, then keeps only the pixels inside and crops to the smallest box around them.
     * Returns null if no loop is drawn or the loop is too small.
     */
    @Nullable
    public Bitmap cutOut() {
        if (image == null || !closed) return null;
        int w = image.getWidth(), h = image.getHeight();

        Path imagePath = new Path(path);
        imagePath.transform(viewToImage);
        imagePath.setFillType(Path.FillType.WINDING);
        Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint fill = new Paint();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.BLACK);
        new Canvas(mask).drawPath(imagePath, fill);

        int[] maskPx = new int[w * h];
        int[] imagePx = new int[w * h];
        mask.getPixels(maskPx, 0, w, 0, 0, w, h);
        image.getPixels(imagePx, 0, w, 0, 0, w, h);
        mask.recycle();

        int left = w, top = h, right = -1, bottom = -1, count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (maskPx[i] == 0 || (imagePx[i] >>> 24) < 128) {
                    imagePx[i] = 0;
                    continue;
                }
                count++;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
            }
        }
        int cw = right - left + 1, ch = bottom - top + 1;
        if (count < MIN_PIXELS || cw < MIN_SIDE || ch < MIN_SIDE) return null;
        return Bitmap.createBitmap(imagePx, top * w + left, w, cw, ch, Bitmap.Config.ARGB_8888);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
