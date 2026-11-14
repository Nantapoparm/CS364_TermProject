package com.example.myapplication.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.Memory;
import com.example.myapplication.data.MemoryRepository;
import com.example.myapplication.ui.PixelFont;

import java.util.List;
import java.util.Locale;

public class GalleryFragment extends Fragment {
    private static final String KEY_FAVORITES = "favorites_only";
    private static final String KEY_GRID = "grid_mode";

    private MemoryRepository repository;

    private boolean favoritesOnly = false;
    private boolean gridMode = true;

    private TextView albumCount;
    private TextView tabAll;
    private TextView tabFavorites;
    private ImageView toggleLayout;
    private TextView emptyText;
    private TextView footer;
    private GridLayoutManager layoutManager;
    private MemoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gallery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = MemoryRepository.get(requireContext());
        if (savedInstanceState != null) {
            favoritesOnly = savedInstanceState.getBoolean(KEY_FAVORITES, favoritesOnly);
            gridMode = savedInstanceState.getBoolean(KEY_GRID, gridMode);
        }

        albumCount = view.findViewById(R.id.album_count);
        tabAll = view.findViewById(R.id.tab_all);
        tabFavorites = view.findViewById(R.id.tab_favorites);
        toggleLayout = view.findViewById(R.id.btn_toggle_layout);
        emptyText = view.findViewById(R.id.empty_text);
        footer = view.findViewById(R.id.gallery_footer);

        adapter = new MemoryAdapter(new MemoryAdapter.OnMemoryClickListener() {
            @Override
            public void onMemoryClick(Memory memory) {
                onMemoryClicked(memory);
            }

            @Override
            public void onMemoryLongClick(Memory memory) {
                openViewer(memory);
            }
        });

        getChildFragmentManager().setFragmentResultListener(PhotoViewerDialog.RESULT_CHANGED,
                getViewLifecycleOwner(), (key, result) -> refresh());
        layoutManager = new GridLayoutManager(requireContext(), gridMode ? 2 : 1);
        RecyclerView list = view.findViewById(R.id.memory_list);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);

        tabAll.setOnClickListener(v -> setFavoritesOnly(false));
        tabFavorites.setOnClickListener(v -> setFavoritesOnly(true));
        toggleLayout.setOnClickListener(v -> setGridMode(!gridMode));

        setGridMode(gridMode);
        refresh();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) refresh();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_FAVORITES, favoritesOnly);
        outState.putBoolean(KEY_GRID, gridMode);
    }

    private void setFavoritesOnly(boolean favoritesOnly) {
        this.favoritesOnly = favoritesOnly;
        refresh();
    }

    private void setGridMode(boolean gridMode) {
        this.gridMode = gridMode;
        layoutManager.setSpanCount(gridMode ? 2 : 1);
        adapter.setGridMode(gridMode);

        toggleLayout.setImageResource(gridMode ? R.drawable.ic_grid : R.drawable.ic_list);
    }

    private static final String TAG_VIEWER = "photo_viewer";

    private void openViewer(Memory memory) {
        if (getChildFragmentManager().findFragmentByTag(TAG_VIEWER) != null) return;
        PhotoViewerDialog.newInstance(memory.getId()).show(getChildFragmentManager(), TAG_VIEWER);
    }

    private void onMemoryClicked(Memory memory) {
        Memory updated = repository.toggleFavorite(memory.getId());
        if (updated != null) {
            Toast.makeText(requireContext(),
                    updated.isFavorite() ? R.string.added_favorite : R.string.removed_favorite,
                    Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void refresh() {
        int total = repository.size();
        int favorites = repository.favoriteCount();
        List<Memory> items = favoritesOnly ? repository.getFavorites() : repository.getAll();
        adapter.submitList(items);

        albumCount.setText(String.format(Locale.US, "%02d", total));
        tabAll.setText(getString(R.string.tab_all, total));
        tabFavorites.setText(getString(R.string.tab_favorites, favorites));
        styleTab(tabAll, !favoritesOnly);
        styleTab(tabFavorites, favoritesOnly);

        footer.setText(favoritesOnly
                ? getResources().getQuantityString(R.plurals.gallery_footer_fav, favorites, favorites)
                : getResources().getQuantityString(R.plurals.gallery_footer_all, total, total));
        emptyText.setText(favoritesOnly ? R.string.gallery_empty_fav : R.string.gallery_empty_all);
        emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static void styleTab(TextView tab, boolean selected) {
        tab.setSelected(selected);

        tab.setTypeface(PixelFont.get(tab.getContext(), false));
    }
}
