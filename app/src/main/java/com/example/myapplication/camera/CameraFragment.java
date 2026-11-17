package com.example.myapplication.camera;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.example.myapplication.data.Memory;
import com.example.myapplication.data.MemoryRepository;
import com.example.myapplication.ui.Pixelator;
import com.example.myapplication.ui.PhotoViews;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFragment extends Fragment {
    public interface Host {
        void openGallery();
    }

    private static final String KEY_BIT = "bit_index";
    private static final String KEY_ZOOM = "zoom";
    private static final String KEY_FRONT = "front_camera";
    private static final String KEY_GAME_BOY = "game_boy";

    private static final Pixelator.Style[] STYLES = {
            Pixelator.STYLE_8_BIT, Pixelator.STYLE_16_BIT, Pixelator.STYLE_32_BIT};
    private static final int MAX_ZOOM = 3;

    private static final long FREEZE_MS = 800;

    private MemoryRepository repository;
    private final MemoryRepository.Listener albumListener = this::refreshAlbumInfo;
    private int pendingSaves;

    private int bitIndex = 1;
    private volatile Pixelator.Style style = STYLES[1];
    private volatile boolean gameBoy = false;

    private final Pixelator pixelator = new Pixelator();
    private int zoom = 1;
    private volatile boolean useFrontCamera = false;

    private ExecutorService cameraExecutor;
    @Nullable
    private ProcessCameraProvider cameraProvider;
    @Nullable
    private Camera camera;
    private boolean cameraStarting;
    private boolean permissionAsked;

    @Nullable
    private volatile Bitmap latestPixelFrame;
    private volatile long frozenUntil;
    @Nullable
    private Bitmap frameBuffer;

    private ImageView viewfinderImage;
    private View permissionPanel;
    private TextView permissionButton;
    private View flashOverlay;
    private View liveDot;
    private TextView zoomButton;
    private TextView gameBoyButton;
    private TextView shotCounter;
    private ImageView recentThumb;
    private TextView[] bitButtons;
    @Nullable
    private Animation blink;
    @Nullable
    private SoundPool soundPool;
    private int shutterSoundId;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    showPermissionPanel();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = MemoryRepository.get(requireContext());
        cameraExecutor = Executors.newSingleThreadExecutor();
        if (savedInstanceState != null) {
            bitIndex = savedInstanceState.getInt(KEY_BIT, bitIndex);
            zoom = savedInstanceState.getInt(KEY_ZOOM, zoom);
            useFrontCamera = savedInstanceState.getBoolean(KEY_FRONT, useFrontCamera);
            gameBoy = savedInstanceState.getBoolean(KEY_GAME_BOY, gameBoy);
        }

        viewfinderImage = view.findViewById(R.id.viewfinder_image);
        permissionPanel = view.findViewById(R.id.permission_panel);
        permissionButton = view.findViewById(R.id.btn_allow_camera);
        flashOverlay = view.findViewById(R.id.flash_overlay);
        liveDot = view.findViewById(R.id.live_dot);
        zoomButton = view.findViewById(R.id.btn_zoom);
        gameBoyButton = view.findViewById(R.id.btn_game_boy);
        shotCounter = view.findViewById(R.id.shot_counter);
        recentThumb = view.findViewById(R.id.recent_thumb);
        bitButtons = new TextView[]{
                view.findViewById(R.id.btn_bit_8),
                view.findViewById(R.id.btn_bit_16),
                view.findViewById(R.id.btn_bit_32),
        };

        for (int i = 0; i < bitButtons.length; i++) {
            final int index = i;
            bitButtons[i].setOnClickListener(v -> selectBit(index));
        }
        zoomButton.setOnClickListener(v -> nextZoom());
        gameBoyButton.setOnClickListener(v -> setGameBoy(!gameBoy));
        view.findViewById(R.id.btn_shutter).setOnClickListener(v -> takePhoto());
        view.findViewById(R.id.btn_header_camera).setOnClickListener(v -> takePhoto());
        view.findViewById(R.id.btn_switch_camera).setOnClickListener(v -> switchCamera());
        permissionButton.setOnClickListener(v -> onAllowCameraClicked());
        recentThumb.setOnClickListener(v -> {
            if (getActivity() instanceof Host) ((Host) getActivity()).openGallery();
        });

        PhotoViews.showPlaceholder(viewfinderImage);
        selectBit(bitIndex);
        setGameBoy(gameBoy);
        updateZoomLabel();
        repository.addListener(albumListener);
        refreshAlbumInfo();
        startLiveBlink();
        loadShutterSound();
    }

    @Override
    public void onResume() {
        super.onResume();
        ensureCamera();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            stopCamera();
        } else {
            refreshAlbumInfo();
            ensureCamera();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_BIT, bitIndex);
        outState.putInt(KEY_ZOOM, zoom);
        outState.putBoolean(KEY_FRONT, useFrontCamera);
        outState.putBoolean(KEY_GAME_BOY, gameBoy);
    }

    @Override
    public void onDestroyView() {
        repository.removeListener(albumListener);
        stopCamera();
        cameraExecutor.shutdown();
        if (blink != null) blink.cancel();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        super.onDestroyView();
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureCamera() {
        if (!isAdded() || isHidden() || getView() == null) return;
        if (hasCameraPermission()) {
            permissionPanel.setVisibility(View.GONE);
            startCamera();
        } else {
            showPermissionPanel();
            if (!permissionAsked) {
                permissionAsked = true;
                permissionLauncher.launch(Manifest.permission.CAMERA);
            }
        }
    }

    private void showPermissionPanel() {
        permissionPanel.setVisibility(View.VISIBLE);

        boolean blocked = permissionAsked && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
        permissionButton.setText(blocked ? R.string.open_settings : R.string.allow_camera);
    }

    private void onAllowCameraClicked() {
        if (permissionAsked && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().getPackageName(), null));
            startActivity(intent);
        } else {
            permissionAsked = true;
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        if (camera != null || cameraStarting) return;
        cameraStarting = true;
        Context context = requireContext();
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            cameraStarting = false;
            if (!isAdded() || isHidden() || getView() == null) return;
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) {
                toast(R.string.camera_error);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCamera() {
        ProcessCameraProvider provider = cameraProvider;
        if (provider == null) return;

        CameraSelector selector = selectorFor(useFrontCamera);
        if (!hasCamera(selector)) {
            useFrontCamera = !useFrontCamera;
            selector = selectorFor(useFrontCamera);
            if (!hasCamera(selector)) {
                toast(R.string.camera_error);
                return;
            }
        }

        ImageAnalysis analysis = new ImageAnalysis.Builder()

                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        try {
            provider.unbindAll();
            camera = provider.bindToLifecycle(getViewLifecycleOwner(), selector, analysis);
            latestPixelFrame = null;
            zoom = Math.min(zoom, maxZoomSteps());
            applyZoom();
        } catch (Exception e) {
            camera = null;
            toast(R.string.camera_error);
        }
    }

    private void stopCamera() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        camera = null;
    }

    private static CameraSelector selectorFor(boolean front) {
        return front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
    }

    private boolean hasCamera(CameraSelector selector) {
        try {
            return cameraProvider != null && cameraProvider.hasCamera(selector);
        } catch (Exception e) {
            return false;
        }
    }

    private void analyzeFrame(@NonNull ImageProxy frame) {
        Bitmap pixel;
        try {
            Bitmap full = frameToBitmap(frame);
            int rotation = frame.getImageInfo().getRotationDegrees();
            pixel = pixelator.render(full, rotation, useFrontCamera, style, gameBoy);
        } finally {
            frame.close();
        }
        latestPixelFrame = pixel;
        if (SystemClock.uptimeMillis() < frozenUntil) return;
        viewfinderImage.post(() -> {
            if (getView() != null && camera != null) PhotoViews.show(viewfinderImage, pixel);
        });
    }

    private Bitmap frameToBitmap(ImageProxy frame) {
        ImageProxy.PlaneProxy plane = frame.getPlanes()[0];
        int w = frame.getWidth();
        int h = frame.getHeight();
        if (plane.getPixelStride() != 4 || plane.getRowStride() != w * 4) {
            return frame.toBitmap();
        }
        if (frameBuffer == null || frameBuffer.getWidth() != w || frameBuffer.getHeight() != h) {
            frameBuffer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        frameBuffer.copyPixelsFromBuffer(buffer);
        return frameBuffer;
    }

    private void selectBit(int index) {
        bitIndex = index;
        style = STYLES[index];
        for (int i = 0; i < bitButtons.length; i++) {
            bitButtons[i].setSelected(i == index);
        }
    }

    /** Toggles the 4-green Game Boy palette; works together with any 8/16/32 BIT size. */
    private void setGameBoy(boolean on) {
        gameBoy = on;
        gameBoyButton.setSelected(on);
    }

    private void nextZoom() {
        if (camera == null) {
            toast(R.string.camera_not_ready);
            return;
        }
        zoom = zoom % maxZoomSteps() + 1;
        applyZoom();
    }

    private int maxZoomSteps() {
        if (camera == null) return 1;
        ZoomState state = camera.getCameraInfo().getZoomState().getValue();
        float max = state != null ? state.getMaxZoomRatio() : 1f;
        return Math.max(1, Math.min(MAX_ZOOM, (int) Math.floor(max)));
    }

    private void applyZoom() {
        if (camera != null) camera.getCameraControl().setZoomRatio(zoom);
        updateZoomLabel();
    }

    private void updateZoomLabel() {
        zoomButton.setText(getString(R.string.zoom_level, zoom));
    }

    private void switchCamera() {
        if (cameraProvider == null || camera == null) {
            toast(R.string.camera_not_ready);
            return;
        }
        if (!hasCamera(selectorFor(!useFrontCamera))) {
            toast(R.string.only_one_camera);
            return;
        }
        useFrontCamera = !useFrontCamera;
        zoom = 1;
        camera = null;
        bindCamera();
    }

    private void takePhoto() {
        Bitmap shot = latestPixelFrame;
        if (camera == null || shot == null) {
            toast(hasCameraPermission() ? R.string.camera_not_ready : R.string.camera_permission_needed);
            return;
        }
        if (!repository.isLoaded() || repository.size() + pendingSaves >= MemoryRepository.MAX_MEMORIES) {
            toast(repository.isLoaded() ? R.string.album_full : R.string.camera_not_ready);
            return;
        }

        frozenUntil = SystemClock.uptimeMillis() + FREEZE_MS;
        PhotoViews.show(viewfinderImage, shot);
        playFlash();
        playShutterSound();

        pendingSaves++;
        repository.addPhoto(shot, saved -> {
            pendingSaves--;
            toast(saved != null ? R.string.saved_to_album : R.string.save_failed);
        });
    }

    private void refreshAlbumInfo() {
        if (getView() == null) return;
        shotCounter.setText(getString(R.string.shot_counter,
                repository.size(), MemoryRepository.MAX_MEMORIES));
        Memory latest = repository.getLatest();
        if (latest != null) {
            latest.loadInto(recentThumb);
        } else {
            PhotoViews.showPlaceholder(recentThumb);
        }
    }

    /** Loads the short 8-bit "ding" once so each shot plays it without delay. */
    private void loadShutterSound() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(attributes)
                .build();
        shutterSoundId = soundPool.load(requireContext(), R.raw.snap_ding, 1);
    }

    private void playShutterSound() {
        if (soundPool != null && shutterSoundId != 0) {
            soundPool.play(shutterSoundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void playFlash() {
        flashOverlay.animate().cancel();
        flashOverlay.setAlpha(0.9f);
        flashOverlay.animate().alpha(0f).setDuration(350).start();
    }

    private void startLiveBlink() {
        blink = new AlphaAnimation(1f, 0.15f);
        blink.setDuration(700);
        blink.setInterpolator(new LinearInterpolator());
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(Animation.INFINITE);
        liveDot.startAnimation(blink);
    }

    private void toast(int messageRes) {
        Context context = getContext();
        if (context != null) Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show();
    }
}
