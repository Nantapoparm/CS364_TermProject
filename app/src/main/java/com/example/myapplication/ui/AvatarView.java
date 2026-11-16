package com.example.myapplication.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.data.Avatar;

import java.util.Random;

/**
 * [ใหม่ - ฟีเจอร์อวาตาร์] ชั้นโปร่งใสที่วางทับอัลบั้ม ให้ตัวละคร chibi เดินเล่นตามขอบล่าง
 *
 * <ul>
 *   <li>เปลี่ยนเฟรมประมาณ 12 ครั้งต่อวินาที ให้ฟีลเกมพิกเซล</li>
 *   <li>แตะโดนตัวละคร = กระโดด (แจ้ง {@link Listener#onAvatarJump()} ให้เล่นเสียงติ้ง)</li>
 *   <li>กดค้าง = แจ้ง {@link Listener#onAvatarLongPress()} ให้ถามว่าจะลบไหม</li>
 *   <li>แตะไม่โดนตัวละครจะคืนค่า false ทำให้การแตะทะลุไปถึงการ์ดรูปข้างล่าง</li>
 *   <li>หยุด animation เองเมื่อซ่อนแท็บอัลบั้มหรือปิดจอ ({@link #onVisibilityAggregated}) เพื่อประหยัดแบต</li>
 * </ul>
 *
 * <pre>
 * WALK ⇄ IDLE (stands still for a moment)
 *   │ tapped
 *   ▼
 * JUMP ──done──▶ WALK
 * </pre>
 */
public class AvatarView extends View {
    public interface Listener {
        void onAvatarJump();

        void onAvatarLongPress();
    }

    private enum State {WALK, IDLE, JUMP}

    /** About 12 frames per second, like an old handheld game. */
    private static final long FRAME_MS = 83;
    private static final int PIXEL_DP = 3;

    private static final int CROUCH_TICKS = 2;
    private static final int AIR_TICKS = 8;
    private static final int LAND_TICKS = 2;
    private static final int JUMP_HEIGHT = 16; // in sprite pixels

    private final Paint paint = new Paint();
    private final Random random = new Random();
    private final RectF hitRect = new RectF();
    private final int scale;
    private final float groundGap;
    private final float touchSlop;

    @Nullable
    private AvatarSprite sprite;
    private long avatarId = -1;
    @Nullable
    private Listener listener;

    private State state = State.WALK;
    private int ticks;
    private int stateLength;
    private int walkFrame;
    private boolean facingRight = true;
    /** Center of the feet in view pixels; negative until the first frame places it. */
    private float footX = -1;

    private boolean attached;
    private boolean shown;
    private boolean running;
    private boolean pressing;
    private boolean longPressed;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            step();
            invalidate();
            postOnAnimationDelayed(this, FRAME_MS);
        }
    };

    private final Runnable longPress = () -> {
        longPressed = true;
        if (listener != null) listener.onAvatarLongPress();
    };

    public AvatarView(Context context) {
        this(context, null);
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        scale = Math.max(1, Math.round(PIXEL_DP * density));
        groundGap = 4 * density;
        touchSlop = 8 * density;
        paint.setFilterBitmap(false);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** Shows the given avatar (or hides the layer when null). Same avatar again keeps walking as is. */
    public void setAvatar(@Nullable Avatar avatar) {
        long id = avatar == null ? -1 : avatar.getId();
        if (id == avatarId) return;
        avatarId = id;
        sprite = avatar == null ? null : new AvatarSprite(avatar.getCutOut());
        footX = -1;
        startWalk();
        updateRunning();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        shown = isShown();
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        updateRunning();
        super.onDetachedFromWindow();
    }

    /** False when the album tab is hidden or the screen is off, so the animation stops and saves battery. */
    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        shown = isVisible;
        updateRunning();
    }

    private void updateRunning() {
        boolean shouldRun = attached && shown && sprite != null;
        if (shouldRun == running) return;
        running = shouldRun;
        removeCallbacks(tick);
        if (running) postOnAnimation(tick);
    }

    private void startWalk() {
        state = State.WALK;
        ticks = 0;
        stateLength = 36 + random.nextInt(48);
    }

    private void step() {
        if (sprite == null || getWidth() == 0) return;
        float half = sprite.idle.getWidth() * scale / 2f;
        float minX = getPaddingLeft() + half;
        float maxX = getWidth() - getPaddingRight() - half;
        if (footX < 0) {
            footX = minX + random.nextFloat() * Math.max(0, maxX - minX);
            facingRight = random.nextBoolean();
        }
        ticks++;
        switch (state) {
            case WALK:
                walkFrame = (walkFrame + 1) % sprite.walk.length;
                footX += facingRight ? scale : -scale;
                if (footX >= maxX) {
                    footX = maxX;
                    facingRight = false;
                } else if (footX <= minX) {
                    footX = minX;
                    facingRight = true;
                }
                if (ticks >= stateLength) {
                    state = State.IDLE;
                    ticks = 0;
                    stateLength = 12 + random.nextInt(24);
                }
                break;
            case IDLE:
                if (ticks >= stateLength) {
                    if (random.nextInt(3) == 0) facingRight = !facingRight;
                    startWalk();
                }
                break;
            case JUMP:
                if (ticks >= CROUCH_TICKS + AIR_TICKS + LAND_TICKS) startWalk();
                break;
        }
    }

    private Bitmap currentFrame(@NonNull AvatarSprite s) {
        switch (state) {
            case WALK:
                return s.walk[walkFrame];
            case JUMP:
                boolean inAir = ticks >= CROUCH_TICKS && ticks < CROUCH_TICKS + AIR_TICKS;
                return inAir ? s.stretch : s.squash;
            default:
                return s.idle;
        }
    }

    /** Height above the ground in view pixels: a parabola while in the air. */
    private float jumpLift() {
        if (state != State.JUMP) return 0;
        float t = (ticks - CROUCH_TICKS) / (float) AIR_TICKS;
        if (t <= 0 || t >= 1) return 0;
        return JUMP_HEIGHT * scale * 4 * t * (1 - t);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        AvatarSprite s = sprite;
        if (s == null || footX < 0) return;
        Bitmap frame = currentFrame(s);
        float w = frame.getWidth() * scale;
        float h = frame.getHeight() * scale;
        float left = footX - w / 2f;
        float top = getHeight() - getPaddingBottom() - groundGap - h - jumpLift();
        hitRect.set(left - touchSlop, top - touchSlop, left + w + touchSlop, top + h + touchSlop);

        canvas.save();
        if (!facingRight) canvas.scale(-1f, 1f, footX, 0f);
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        canvas.drawBitmap(frame, 0f, 0f, paint);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (sprite == null || !hitRect.contains(e.getX(), e.getY())) return false;
                pressing = true;
                longPressed = false;
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_UP:
                if (!pressing) return false;
                pressing = false;
                removeCallbacks(longPress);
                if (!longPressed) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressing = false;
                removeCallbacks(longPress);
                return true;
            default:
                return pressing;
        }
    }

    /** A tap makes the avatar jump. */
    @Override
    public boolean performClick() {
        super.performClick();
        if (sprite == null || state == State.JUMP) return true;
        state = State.JUMP;
        ticks = 0;
        invalidate();
        if (listener != null) listener.onAvatarJump();
        return true;
    }
}
