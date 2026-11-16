package com.example.myapplication.avatar;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;

import com.example.myapplication.R;
import com.example.myapplication.data.MemoryRepository;
import com.example.myapplication.ui.AvatarSprite;
import com.example.myapplication.ui.LassoCutView;
import com.example.myapplication.ui.Pixelator;

/**
 * [ใหม่ - ฟีเจอร์อวาตาร์] หน้าต่าง {@code avatar.cut} สำหรับตัดคนออกจากรูปในอัลบั้ม
 *
 * <ul>
 *   <li>เปิดจากปุ่ม ★ Make avatar ใน {@code PhotoViewerDialog}</li>
 *   <li>โหลดรูปจากฐานข้อมูล ถ้าเป็นรูป JPEG ใหญ่ (รูปตัวอย่าง) จะย่อเหลือกว้าง 64 พิกเซลก่อน</li>
 *   <li>ให้ผู้ใช้วาดวงรอบตัวใน {@link LassoCutView} แล้วโชว์ตัว chibi ตัวอย่างในช่องเล็ก</li>
 *   <li>ปุ่ม Redraw = วาดใหม่, ปุ่ม Use this = บันทึกลงตาราง {@code avatars} ผ่าน {@link MemoryRepository}</li>
 * </ul>
 */
public class AvatarCutDialog extends DialogFragment {
    private static final String ARG_ID = "memory_id";
    /** Sample photos are full-size JPEGs; shrink them to about the size of a 16 BIT pixel photo. */
    private static final int MAX_WORK_WIDTH = 200;
    private static final int WORK_WIDTH = 64;

    private MemoryRepository repository;
    private long memoryId;
    private LassoCutView lasso;
    private ImageView preview;
    private TextView hint;
    private TextView useButton;
    @Nullable
    private Bitmap cutOut;
    private boolean saving;

    public static AvatarCutDialog newInstance(long memoryId) {
        AvatarCutDialog dialog = new AvatarCutDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_ID, memoryId);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, R.style.PhotoViewerDialog);
        memoryId = requireArguments().getLong(ARG_ID);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_avatar_cut, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() != null ? getDialog().getWindow() : null;
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = MemoryRepository.get(requireContext());
        lasso = view.findViewById(R.id.avatar_lasso);
        preview = view.findViewById(R.id.avatar_preview);
        hint = view.findViewById(R.id.avatar_hint);
        useButton = view.findViewById(R.id.avatar_use);

        view.findViewById(R.id.avatar_close).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.avatar_redraw).setOnClickListener(v -> lasso.reset());
        useButton.setOnClickListener(v -> save());
        lasso.setOnLassoListener(this::onLassoChanged);
        showCut(null);

        repository.loadImage(memoryId, photo -> {
            if (getView() == null) return;
            if (photo == null) {
                Toast.makeText(requireContext(), R.string.avatar_failed, Toast.LENGTH_SHORT).show();
                dismissAllowingStateLoss();
                return;
            }
            Bitmap work = photo;
            if (photo.getWidth() > MAX_WORK_WIDTH) {
                int h = Math.max(1, Math.round(photo.getHeight() * (float) WORK_WIDTH / photo.getWidth()));
                work = Bitmap.createScaledBitmap(photo, WORK_WIDTH, h, true);
            }
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) lasso.getLayoutParams();
            lp.dimensionRatio = work.getWidth() + ":" + work.getHeight();
            lasso.setLayoutParams(lp);
            lasso.setImage(work);
        });
    }

    private void onLassoChanged(boolean closed) {
        if (!closed) {
            hint.setText(R.string.avatar_hint);
            showCut(null);
            return;
        }
        Bitmap cut = lasso.cutOut();
        hint.setText(cut == null ? R.string.avatar_too_small : R.string.avatar_ready);
        showCut(cut);
    }

    private void showCut(@Nullable Bitmap cut) {
        cutOut = cut;
        if (cut == null) {
            preview.setImageDrawable(null);
        } else {
            preview.setImageDrawable(Pixelator.crispDrawable(getResources(), new AvatarSprite(cut).idle));
        }
        boolean enabled = cut != null && !saving;
        useButton.setEnabled(enabled);
        useButton.setAlpha(enabled ? 1f : 0.4f);
    }

    private void save() {
        Bitmap cut = cutOut;
        if (cut == null || saving) return;
        saving = true;
        showCut(cut);
        repository.saveAvatar(cut, memoryId, avatar -> {
            saving = false;
            if (getContext() != null) {
                Toast.makeText(getContext(), avatar != null ? R.string.avatar_saved : R.string.avatar_failed,
                        Toast.LENGTH_SHORT).show();
            }
            if (avatar != null && isAdded()) {
                dismissAllowingStateLoss();
            } else if (getView() != null) {
                showCut(cutOut);
            }
        });
    }
}
