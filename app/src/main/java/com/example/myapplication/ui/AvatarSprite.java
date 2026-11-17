package com.example.myapplication.ui;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

/**
 * Turns a cut-out photo into a chibi sprite: the cut-out becomes a big head/body,
 * short pixel legs are drawn by code, and every frame gets a 1-pixel dark outline.
 * All frames face right and are built once up front; the view mirrors them to face left.
 */
public final class AvatarSprite {
    private static final int BODY_HEIGHT = 22;
    private static final int MAX_BODY_WIDTH = 26;
    private static final int LEG_LENGTH = 5;
    /** Free space on each side of the body so swinging legs fit in the frame. */
    private static final int SIDE_PAD = 3;
    private static final int OUTLINE = 0xFF2B1E1A;

    /** Walk cycle: /\ → || (bounce up) → \/ → || (bounce up). */
    public final Bitmap[] walk = new Bitmap[4];
    public final Bitmap idle;
    /** Crouching before a jump and when landing: shorter and wider. */
    public final Bitmap squash;
    /** In the air: taller and thinner. */
    public final Bitmap stretch;

    private final int frontLeg;
    private final int backLeg;

    public AvatarSprite(@NonNull Bitmap cutOut) {
        int bh = BODY_HEIGHT;
        int bw = Math.max(1, Math.round(cutOut.getWidth() * (float) bh / cutOut.getHeight()));
        if (bw > MAX_BODY_WIDTH) {
            bw = MAX_BODY_WIDTH;
            bh = Math.max(1, Math.round(cutOut.getHeight() * (float) bw / cutOut.getWidth()));
        }
        Bitmap body = Bitmap.createScaledBitmap(cutOut, bw, bh, false);

        int legColor = darken(bottomColor(body), 0.7f);
        frontLeg = legColor;
        backLeg = darken(legColor, 0.75f);

        walk[0] = frame(body, LEG_LENGTH, 0, 2, -2);
        walk[1] = frame(body, LEG_LENGTH, 1, 0, 0);
        walk[2] = frame(body, LEG_LENGTH, 0, -2, 2);
        walk[3] = walk[1];
        idle = frame(body, LEG_LENGTH, 0, 0, 0);

        Bitmap wide = Bitmap.createScaledBitmap(body,
                Math.round(bw * 1.25f), Math.max(1, Math.round(bh * 0.8f)), false);
        squash = frame(wide, 2, 0, 1, -1);
        Bitmap tall = Bitmap.createScaledBitmap(body,
                Math.max(1, Math.round(bw * 0.85f)), Math.round(bh * 1.15f), false);
        stretch = frame(tall, LEG_LENGTH, 0, -1, 1);
    }

    /**
     * Draws one frame: body on top, two 2-pixel-wide legs underneath, then an outline.
     * {@code lift} raises the body by 1 pixel (legs get longer so feet stay on the ground).
     * {@code frontDx}/{@code backDx} are how far each foot swings forward (+) or back (-).
     */
    private Bitmap frame(Bitmap body, int legLength, int lift, int frontDx, int backDx) {
        int bw = body.getWidth(), bh = body.getHeight();
        int innerW = bw + SIDE_PAD * 2;
        int innerH = 1 + bh + legLength;
        int w = innerW + 2, h = innerH + 2;
        int[] px = new int[w * h];

        int bodyLeft = 1 + SIDE_PAD;
        int bodyTop = 1 + (1 - lift);
        int[] bodyPx = new int[bw * bh];
        body.getPixels(bodyPx, 0, bw, 0, 0, bw, bh);

        int hipLeft = bw, hipRight = -1;
        for (int x = 0; x < bw; x++) {
            if (opaque(bodyPx[(bh - 1) * bw + x])) {
                hipLeft = Math.min(hipLeft, x);
                hipRight = Math.max(hipRight, x);
            }
        }
        if (hipRight < 0) {
            hipLeft = 0;
            hipRight = bw - 1;
        }
        int center = bodyLeft + (hipLeft + hipRight) / 2;
        int gap = Math.min(2, Math.max(0, (hipRight - hipLeft) / 4));

        int legTop = bodyTop + bh;
        int ground = h - 2;
        drawLeg(px, w, center - gap - 1, legTop, ground, backDx, backLeg);
        drawLeg(px, w, center + gap, legTop, ground, frontDx, frontLeg);

        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int c = bodyPx[y * bw + x];
                if (opaque(c)) px[(bodyTop + y) * w + bodyLeft + x] = c | 0xFF000000;
            }
        }

        addOutline(px, w, h);
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    private static void drawLeg(int[] px, int w, int hipX, int top, int ground, int dx, int color) {
        int rows = ground - top + 1;
        for (int y = top; y <= ground; y++) {
            float t = (y - top + 1) / (float) rows;
            int x = hipX + Math.round(dx * t);
            boolean foot = y == ground;
            int c = foot ? OUTLINE : color;
            set(px, w, x, y, c);
            set(px, w, x + 1, y, c);
            if (foot) set(px, w, x + 2, y, OUTLINE); // shoe tip points forward
        }
    }

    private static void set(int[] px, int w, int x, int y, int c) {
        if (x >= 0 && x < w && y >= 0 && y < px.length / w) px[y * w + x] = c;
    }

    /** Every empty pixel that touches a filled one (up/down/left/right) becomes dark outline. */
    private static void addOutline(int[] px, int w, int h) {
        boolean[] edge = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (px[i] != 0) continue;
                edge[i] = (x > 0 && px[i - 1] != 0) || (x < w - 1 && px[i + 1] != 0)
                        || (y > 0 && px[i - w] != 0) || (y < h - 1 && px[i + w] != 0);
            }
        }
        for (int i = 0; i < px.length; i++) {
            if (edge[i]) px[i] = OUTLINE;
        }
    }

    /** Average colour of the bottom two rows, e.g. the shirt or trousers. */
    private static int bottomColor(Bitmap body) {
        int bw = body.getWidth(), bh = body.getHeight();
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = Math.max(0, bh - 2); y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int c = body.getPixel(x, y);
                if (!opaque(c)) continue;
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0xFF4A3730;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    private static int darken(int c, float f) {
        int r = Math.round(((c >> 16) & 0xFF) * f);
        int g = Math.round(((c >> 8) & 0xFF) * f);
        int b = Math.round((c & 0xFF) * f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static boolean opaque(int c) {
        return (c >>> 24) >= 128;
    }
}
