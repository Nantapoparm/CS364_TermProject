package com.example.myapplication.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;

public final class Pixelator {
    public static final class Style {
        final int blocksAcross;
        final int[] palette;
        final int outlineThreshold;

        Style(int blocksAcross, int[] palette, int outlineThreshold) {
            this.blocksAcross = blocksAcross;
            this.palette = palette;
            this.outlineThreshold = outlineThreshold;
        }
    }

    private static final int OUTLINE = 0xFF2B1E1A;

    private static final int[] PALETTE_33 = {
            0xFF2B1E1A, 0xFF4A3730, 0xFF7A5236, 0xFF6E5F58,
            0xFF9A8B82, 0xFFC9BBAE, 0xFFF4EAD8, 0xFFFFF8EE,
            0xFF5E3A2A, 0xFF8A5A3C, 0xFFB97A55, 0xFFD9A27C,
            0xFFF0C8A0, 0xFFFBE0C4,
            0xFFA8323A, 0xFFD9634A, 0xFFE8678A, 0xFFF4A9A8,
            0xFFF2A970, 0xFFE8C14F, 0xFFF8E08E,
            0xFF2F5D3A, 0xFF4E8A4A, 0xFFA6D46B, 0xFF79C6B1,
            0xFF23365C, 0xFF3E6FA8, 0xFF7FB2E5, 0xFFDDE8F8,
            0xFF5B4A8A, 0xFF9B86C9, 0xFF1A1A24, 0xFFB8916E,
    };

    private static final int[] PALETTE_23 = {
            0xFF2B1E1A, 0xFF4A3730, 0xFF7A5236, 0xFF9A8B82, 0xFFC9BBAE,
            0xFFE6CDB0, 0xFFF4EAD8, 0xFFFFF8EE, 0xFF8A5A3C, 0xFFD9A27C,
            0xFFF0C8A0, 0xFFFBE0C4, 0xFFD9634A, 0xFFE8678A, 0xFFF2A970,
            0xFFE8C14F, 0xFF4E8A4A, 0xFFA6D46B, 0xFF3E6FA8, 0xFF7FB2E5,
            0xFF9B86C9, 0xFF1A1A24, 0xFFB8916E,
    };

    private static final int[] PALETTE_15 = {
            0xFF2B1E1A, 0xFF7A5236, 0xFF9A8B82, 0xFFC9BBAE, 0xFFE6CDB0,
            0xFFF4EAD8, 0xFF8A5A3C, 0xFFD9A27C, 0xFFF0C8A0, 0xFFD9634A,
            0xFFF2A970, 0xFF4E8A4A, 0xFF3E6FA8, 0xFF7FB2E5, 0xFFB8916E,
    };

    public static final Style STYLE_8_BIT = new Style(40, PALETTE_15, 84);

    public static final Style STYLE_16_BIT = new Style(64, PALETTE_23, 76);

    public static final Style STYLE_32_BIT = new Style(96, PALETTE_33, 68);

    private int[] bigPixels = new int[0];
    private int[] smoothPixels = new int[0];
    private int[] smallPixels = new int[0];
    private int[] smallLuma = new int[0];
    private boolean[] outline = new boolean[0];

    private int[] sumR = new int[0], sumG = new int[0], sumB = new int[0], sumL = new int[0], sumL2 = new int[0];

    private final int[] paletteCache = new int[1 << 15];
    private int[] cachedPalette;

    public Bitmap render(Bitmap frame, int rotation, boolean mirror, Style style) {
        boolean sideways = rotation % 180 != 0;
        int uprightW = sideways ? frame.getHeight() : frame.getWidth();
        int uprightH = sideways ? frame.getWidth() : frame.getHeight();
        int targetW = Math.max(1, Math.min(style.blocksAcross, uprightW / 2));
        int targetH = Math.max(1, Math.round(targetW * (float) uprightH / uprightW));

        int w = sideways ? targetH : targetW;
        int h = sideways ? targetW : targetH;

        Bitmap big = downscale(frame, w * 2, h * 2);
        int bw = w * 2, bh = h * 2;
        bigPixels = ensure(bigPixels, bw * bh);
        big.getPixels(bigPixels, 0, bw, 0, 0, bw, bh);
        big.recycle();

        smoothPixels = ensure(smoothPixels, bw * bh);
        kuwahara(bigPixels, smoothPixels, bw, bh, 3);
        smallPixels = ensure(smallPixels, w * h);
        halve(smoothPixels, bw, bh, smallPixels);

        cartoonColors(smallPixels, w, h, style);

        Bitmap small = Bitmap.createBitmap(smallPixels, 0, w, w, h, Bitmap.Config.ARGB_8888);
        if (rotation == 0 && !mirror) return small;
        Matrix m = new Matrix();
        m.postRotate(rotation);
        if (mirror) m.postScale(-1f, 1f);
        Bitmap upright = Bitmap.createBitmap(small, 0, 0, w, h, m, false);
        if (upright != small) small.recycle();
        return upright;
    }

    private void kuwahara(int[] src, int[] dst, int w, int h, int r) {
        int sw = w + 1;
        int n = sw * (h + 1);
        sumR = ensure(sumR, n);
        sumG = ensure(sumG, n);
        sumB = ensure(sumB, n);
        sumL = ensure(sumL, n);
        sumL2 = ensure(sumL2, n);
        for (int x = 0; x <= w; x++) {
            sumR[x] = sumG[x] = sumB[x] = sumL[x] = sumL2[x] = 0;
        }
        for (int y = 0; y < h; y++) {
            int row = (y + 1) * sw;
            sumR[row] = sumG[row] = sumB[row] = sumL[row] = sumL2[row] = 0;
            for (int x = 0; x < w; x++) {
                int c = src[y * w + x];
                int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
                int l = (cr * 77 + cg * 150 + cb * 29) >> 10;
                int i = row + x + 1;
                sumR[i] = cr + sumR[i - 1] + sumR[i - sw] - sumR[i - sw - 1];
                sumG[i] = cg + sumG[i - 1] + sumG[i - sw] - sumG[i - sw - 1];
                sumB[i] = cb + sumB[i - 1] + sumB[i - sw] - sumB[i - sw - 1];
                sumL[i] = l + sumL[i - 1] + sumL[i - sw] - sumL[i - sw - 1];
                sumL2[i] = l * l + sumL2[i - 1] + sumL2[i - sw] - sumL2[i - sw - 1];
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float bestVar = Float.MAX_VALUE;
                int best = 0;
                for (int q = 0; q < 4; q++) {
                    int x0 = (q & 1) == 0 ? Math.max(0, x - r) : x;
                    int x1 = (q & 1) == 0 ? x : Math.min(w - 1, x + r);
                    int y0 = q < 2 ? Math.max(0, y - r) : y;
                    int y1 = q < 2 ? y : Math.min(h - 1, y + r);
                    int area = (x1 - x0 + 1) * (y1 - y0 + 1);
                    float mean = rect(sumL, sw, x0, y0, x1, y1) / (float) area;
                    float var = rect(sumL2, sw, x0, y0, x1, y1) / (float) area - mean * mean;
                    if (var < bestVar) {
                        bestVar = var;
                        int cr = rect(sumR, sw, x0, y0, x1, y1) / area;
                        int cg = rect(sumG, sw, x0, y0, x1, y1) / area;
                        int cb = rect(sumB, sw, x0, y0, x1, y1) / area;
                        best = 0xFF000000 | (cr << 16) | (cg << 8) | cb;
                    }
                }
                dst[y * w + x] = best;
            }
        }
    }

    private static int rect(int[] s, int sw, int x0, int y0, int x1, int y1) {
        return s[(y1 + 1) * sw + x1 + 1] - s[y0 * sw + x1 + 1] - s[(y1 + 1) * sw + x0] + s[y0 * sw + x0];
    }

    private static void halve(int[] src, int w, int h, int[] dst) {
        int hw = w / 2, hh = h / 2;
        for (int y = 0; y < hh; y++) {
            for (int x = 0; x < hw; x++) {
                int a = src[(2 * y) * w + 2 * x], b = src[(2 * y) * w + 2 * x + 1];
                int c = src[(2 * y + 1) * w + 2 * x], d = src[(2 * y + 1) * w + 2 * x + 1];
                int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF) + ((c >> 16) & 0xFF) + ((d >> 16) & 0xFF)) >> 2;
                int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF) + ((c >> 8) & 0xFF) + ((d >> 8) & 0xFF)) >> 2;
                int bl = ((a & 0xFF) + (b & 0xFF) + (c & 0xFF) + (d & 0xFF)) >> 2;
                dst[y * hw + x] = 0xFF000000 | (r << 16) | (g << 8) | bl;
            }
        }
    }

    private void cartoonColors(int[] px, int w, int h, Style style) {
        int n = w * h;
        smallLuma = ensure(smallLuma, n);
        outline = ensure(outline, n);
        if (cachedPalette != style.palette) {
            java.util.Arrays.fill(paletteCache, -1);
            cachedPalette = style.palette;
        }

        for (int i = 0; i < n; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int l = (r * 299 + g * 587 + b * 114) / 1000;
            smallLuma[i] = l;

            r = lift(clamp(contrast(l + (r - l) * 27 / 20)));
            g = lift(clamp(contrast(l + (g - l) * 27 / 20)));
            b = lift(clamp(contrast(l + (b - l) * 27 / 20)));

            int key = ((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3);
            int mapped = paletteCache[key];
            if (mapped == -1) {
                mapped = nearest(style.palette, r, g, b);
                paletteCache[key] = mapped;
            }
            px[i] = mapped;
            outline[i] = false;
        }

        despeckle(px, w, h);

        int t = style.outlineThreshold;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (x + 1 < w) markEdge(i, i + 1, t);
                if (y + 1 < h) markEdge(i, i + w, t);
            }
        }
        for (int i = 0; i < n; i++) {
            if (outline[i]) px[i] = OUTLINE;
        }
    }

    private void markEdge(int a, int b, int threshold) {
        int la = smallLuma[a], lb = smallLuma[b];
        if (Math.abs(la - lb) > threshold) {
            outline[la < lb ? a : b] = true;
        }
    }

    private int[] despeckleCopy = new int[0];
    private final int[] neighbourColors = new int[8];

    private void despeckle(int[] px, int w, int h) {
        despeckleCopy = ensure(despeckleCopy, w * h);
        System.arraycopy(px, 0, despeckleCopy, 0, w * h);
        int[] src = despeckleCopy;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int self = src[y * w + x];
                int count = 0;
                boolean shared = false;
                for (int dy = -1; dy <= 1 && !shared; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int c = src[ny * w + nx];
                        if (c == self) {
                            shared = true;
                            break;
                        }
                        neighbourColors[count++] = c;
                    }
                }
                if (shared || count == 0) continue;

                int best = neighbourColors[0], bestCount = 0;
                for (int i = 0; i < count; i++) {
                    int c = neighbourColors[i], k = 0;
                    for (int j = 0; j < count; j++) if (neighbourColors[j] == c) k++;
                    if (k > bestCount) {
                        bestCount = k;
                        best = c;
                    }
                }
                px[y * w + x] = best;
            }
        }
    }

    private static int lift(int v) {
        return v + (255 - v) * 8 / 100;
    }

    private static int contrast(int v) {
        return (v - 128) * 110 / 100 + 128;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    private static int nearest(int[] palette, int r, int g, int b) {
        int best = palette[0];
        long bestD = Long.MAX_VALUE;
        for (int p : palette) {
            int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;
            int rm = (r + pr) / 2;
            int dr = r - pr, dg = g - pg, db = b - pb;
            long d = (((512L + rm) * dr * dr) >> 8) + 4L * dg * dg + (((767L - rm) * db * db) >> 8);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static Bitmap downscale(Bitmap src, int w, int h) {
        Bitmap current = src;
        while (current.getWidth() / 2 >= w * 2 && current.getHeight() / 2 >= h * 2) {
            Bitmap half = Bitmap.createScaledBitmap(current, current.getWidth() / 2, current.getHeight() / 2, true);
            if (current != src) current.recycle();
            current = half;
        }
        Bitmap result = Bitmap.createScaledBitmap(current, w, h, true);
        if (result == src) {
            result = src.copy(Bitmap.Config.ARGB_8888, false);
        } else if (current != src && current != result) {
            current.recycle();
        }
        return result;
    }

    private static int[] ensure(int[] a, int size) {
        return a.length >= size ? a : new int[size];
    }

    private static boolean[] ensure(boolean[] a, int size) {
        return a.length >= size ? a : new boolean[size];
    }

    public static BitmapDrawable crispDrawable(Resources res, Bitmap bitmap) {
        BitmapDrawable d = new BitmapDrawable(res, bitmap);
        d.setFilterBitmap(false);
        return d;
    }
}
