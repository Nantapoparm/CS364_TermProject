package com.example.myapplication.gallery;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;

import com.example.myapplication.R;
import com.example.myapplication.data.Memory;
import com.example.myapplication.data.MemoryRepository;

public class PhotoViewerDialog extends DialogFragment {
    public static final String RESULT_CHANGED = "photo_viewer_changed";

    private static final String ARG_ID = "memory_id";

    private MemoryRepository repository;
    private long memoryId;
    private TextView likeButton;

    public static PhotoViewerDialog newInstance(long memoryId) {
        PhotoViewerDialog dialog = new PhotoViewerDialog();
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
        return inflater.inflate(R.layout.dialog_photo_viewer, container, false);
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
        Memory memory = repository.find(memoryId);
        if (memory == null) {
            dismissAllowingStateLoss();
            return;
        }

        TextView title = view.findViewById(R.id.viewer_title);
        ImageView image = view.findViewById(R.id.viewer_image);
        likeButton = view.findViewById(R.id.viewer_like);

        title.setText(memory.formatDate());
        memory.loadInto(image, ImageView.ScaleType.FIT_CENTER);
        matchImageRatio(image);
        showLike(memory.isFavorite());

        view.findViewById(R.id.viewer_root).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.viewer_close).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.viewer_done).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.viewer_delete).setOnClickListener(v -> confirmDelete());
        likeButton.setOnClickListener(v -> toggleLike());
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setPositiveButton(R.string.delete_confirm_yes, (d, which) -> deletePhoto())
                .setNegativeButton(R.string.delete_confirm_no, null)
                .show();
    }

    private void deletePhoto() {
        boolean deleted = repository.delete(memoryId);
        Toast.makeText(requireContext(), deleted ? R.string.deleted : R.string.delete_failed,
                Toast.LENGTH_SHORT).show();
        if (deleted) {
            getParentFragmentManager().setFragmentResult(RESULT_CHANGED, Bundle.EMPTY);
            dismiss();
        }
    }

    private static void matchImageRatio(ImageView image) {
        Drawable d = image.getDrawable();
        if (d == null || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0) return;
        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) image.getLayoutParams();
        lp.dimensionRatio = d.getIntrinsicWidth() + ":" + d.getIntrinsicHeight();
        image.setLayoutParams(lp);
    }

    private void toggleLike() {
        Memory updated = repository.toggleFavorite(memoryId);
        if (updated == null) return;
        showLike(updated.isFavorite());
        getParentFragmentManager().setFragmentResult(RESULT_CHANGED, Bundle.EMPTY);
    }

    private void showLike(boolean liked) {
        likeButton.setSelected(liked);
        likeButton.setText(liked ? R.string.viewer_liked : R.string.viewer_like);
    }
}
