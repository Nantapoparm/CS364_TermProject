package com.example.myapplication;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.myapplication.camera.CameraFragment;
import com.example.myapplication.gallery.GalleryFragment;
import com.example.myapplication.ui.GridBackgroundDrawable;
import com.example.myapplication.ui.PixelFont;

public class MainActivity extends AppCompatActivity implements CameraFragment.Host {
    public static final String TAB_CAMERA = "camera";
    public static final String TAB_GALLERY = "gallery";
    private static final String KEY_TAB = "current_tab";
    private static final float MAX_FONT_SCALE = 1.3f;

    private String currentTab = TAB_CAMERA;

    private LinearLayout navCamera;
    private LinearLayout navGallery;
    private TextView navCameraLabel;
    private TextView navGalleryLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        root.setBackground(new GridBackgroundDrawable(this));

        navCamera = findViewById(R.id.btn_nav_camera);
        navGallery = findViewById(R.id.btn_nav_gallery);
        navCameraLabel = findViewById(R.id.nav_camera_label);
        navGalleryLabel = findViewById(R.id.nav_gallery_label);
        navCamera.setOnClickListener(v -> showTab(TAB_CAMERA));
        navGallery.setOnClickListener(v -> showTab(TAB_GALLERY));

        applySystemBarInsets(root);

        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getString(KEY_TAB, TAB_CAMERA);
        }
        showTab(currentTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_TAB, currentTab);
    }

    @Override
    public void openGallery() {
        showTab(TAB_GALLERY);
    }

    private void showTab(String tag) {
        currentTab = tag;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);

        for (Fragment f : fm.getFragments()) {
            if (!tag.equals(f.getTag())) tx.hide(f);
        }
        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            target = TAB_CAMERA.equals(tag) ? new CameraFragment() : new GalleryFragment();
            tx.add(R.id.fragment_container, target, tag);
        } else {
            tx.show(target);
        }
        tx.commit();

        updateNav();
    }

    private void updateNav() {
        boolean camera = TAB_CAMERA.equals(currentTab);
        styleNavItem(navCamera, navCameraLabel, camera);
        styleNavItem(navGallery, navGalleryLabel, !camera);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration config = newBase.getResources().getConfiguration();
        if (config.fontScale > MAX_FONT_SCALE) {
            Configuration capped = new Configuration(config);
            capped.fontScale = MAX_FONT_SCALE;
            newBase = newBase.createConfigurationContext(capped);
        }
        super.attachBaseContext(newBase);
    }

    private void styleNavItem(LinearLayout item, TextView label, boolean selected) {
        int shadow = getResources().getDimensionPixelSize(R.dimen.button_shadow);
        if (selected) {
            item.setBackgroundResource(R.drawable.bg_nav_selected);

            item.setPadding(0, 0, shadow, shadow);
        } else {
            item.setBackground(null);
            item.setPadding(0, 0, 0, 0);
        }
        item.setSelected(selected);

        label.setTypeface(PixelFont.get(this, false));
    }

    private void applySystemBarInsets(View root) {
        View container = findViewById(R.id.fragment_container);
        View bottomNav = findViewById(R.id.bottom_nav);
        int navStart = bottomNav.getPaddingStart();
        int navTop = bottomNav.getPaddingTop();
        int navEnd = bottomNav.getPaddingEnd();
        int navBottom = bottomNav.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            container.setPadding(bars.left, bars.top, bars.right, 0);
            bottomNav.setPaddingRelative(navStart + bars.left, navTop,
                    navEnd + bars.right, navBottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
