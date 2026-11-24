package com.aistudio.framecut.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import com.aistudio.framecut.R;
import com.aistudio.framecut.databinding.ActivityMainBinding;
import com.aistudio.framecut.model.NormalizedRegion;
import com.aistudio.framecut.model.PixelRegion;
import com.aistudio.framecut.model.RecorderEnums.*;
import com.aistudio.framecut.model.RecordingSessionState;
import com.aistudio.framecut.model.RecordingSettings;
import com.aistudio.framecut.model.SettingsStore;
import com.aistudio.framecut.region.RegionSelectionManager;
import com.aistudio.framecut.service.RecordingService;
import com.aistudio.framecut.service.RecordingSessionBus;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private SettingsStore settingsStore;
    private RecordingSettings settings;
    private boolean updatingCustomField = false;
    private boolean recordingActive = false;

    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO))) {
                    checkOverlayAndLaunchProjection();
                } else {
                    showBanner("Microphone permission is required for microphone capture.", true);
                }
            });

    private final ActivityResultLauncher<Intent> projectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent svc = new Intent(this, RecordingService.class)
                            .setAction(RecordingService.ACTION_START)
                            .putExtra(RecordingService.EXTRA_RESULT_CODE, result.getResultCode())
                            .putExtra(RecordingService.EXTRA_RESULT_DATA, result.getData());
                    ContextCompat.startForegroundService(this, svc);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        settingsStore = new SettingsStore(this);
        settings = settingsStore.load();
        applyNightMode(settings.darkThemeOverride);

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupListeners();
        observeSession();
        renderSettingsUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        settings = settingsStore.load();
        renderSettingsUi();
    }

    private void applyNightMode(@Nullable Boolean darkOverride) {
        if (darkOverride == null) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else if (darkOverride) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void setupListeners() {
        binding.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        binding.btnOpenRecordings.setOnClickListener(v ->
                startActivity(new Intent(this, RecordingsActivity.class)));
        binding.btnCropTool.setOnClickListener(v ->
                startActivity(new Intent(this, CropToolActivity.class)));
        binding.btnOpenCropTool.setOnClickListener(v ->
                startActivity(new Intent(this, CropToolActivity.class)));

        binding.btnThemeToggle.setOnClickListener(v -> {
            boolean currentlyDark = settings.darkThemeOverride == null || settings.darkThemeOverride;
            settings.darkThemeOverride = !currentlyDark;
            settingsStore.save(settings);
            applyNightMode(settings.darkThemeOverride);
        });

        binding.btnRecordPrimary.setOnClickListener(v -> onStartRecordingClicked());
        binding.btnPauseResume.setOnClickListener(v -> {
            RecordingSessionState st = RecordingSessionBus.getInstance().getCurrentState();
            String action = st.phase == RecordingPhase.PAUSED
                    ? RecordingService.ACTION_RESUME
                    : RecordingService.ACTION_PAUSE;
            startService(new Intent(this, RecordingService.class).setAction(action));
        });
        binding.btnStop.setOnClickListener(v ->
                startService(new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_STOP)));

        binding.tileFullScreen.setOnClickListener(v -> updateMode(RecordingMode.FULL_SCREEN));
        binding.tileRegion.setOnClickListener(v -> updateMode(RecordingMode.REGION));
        binding.btnAdjustRegion.setOnClickListener(v -> openRegionSelector());

        binding.tilePortrait.setOnClickListener(v -> updateOrientation(OrientationMode.PORTRAIT));
        binding.tileLandscape.setOnClickListener(v -> updateOrientation(OrientationMode.LANDSCAPE));

        binding.tileAudioInternal.setOnClickListener(v -> updateAudio(AudioSourceOption.INTERNAL));
        binding.tileAudioMic.setOnClickListener(v -> updateAudio(AudioSourceOption.MICROPHONE));

        binding.chipBitrate100.setOnClickListener(v -> selectBitratePreset(VideoBitrateOption.MBPS_100));
        binding.chipBitrate150.setOnClickListener(v -> selectBitratePreset(VideoBitrateOption.MBPS_150));
        binding.chipBitrate200.setOnClickListener(v -> selectBitratePreset(VideoBitrateOption.MBPS_200));
        binding.chipBitrate250.setOnClickListener(v -> selectBitratePreset(VideoBitrateOption.MBPS_250));
        binding.chipBitrate300.setOnClickListener(v -> selectBitratePreset(VideoBitrateOption.MBPS_300));
        binding.chipBitrate350.setOnClickListener(v -> selectBitratePreset(VideoBitrateOption.MBPS_350));

        binding.inputCustomBitrate.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (updatingCustomField || recordingActive) return;
                String raw = s.toString().trim();
                if (raw.isEmpty()) {
                    settings.customVideoBitrateMbps = null;
                } else {
                    try {
                        int val = Integer.parseInt(raw);
                        if (val >= 1) settings.customVideoBitrateMbps = val;
                    } catch (NumberFormatException ignored) {
                    }
                }
                settingsStore.save(settings);
                renderBitrateLabelsOnly();
            }
        });

        binding.btnResetBitrate.setOnClickListener(v -> {
            if (recordingActive) return;
            settings.customVideoBitrateMbps = null;
            settings.videoBitrate = VideoBitrateOption.MBPS_150;
            settingsStore.save(settings);
            renderSettingsUi();
        });

        binding.tileAnimShow.setOnClickListener(v -> updateRegionAnim(RegionAnimationOption.SHOW));
        binding.tileAnimHide.setOnClickListener(v -> updateRegionAnim(RegionAnimationOption.HIDE));
    }

    private void updateMode(RecordingMode mode) {
        if (recordingActive) return;
        settings.recordingMode = mode;
        settingsStore.save(settings);
        renderSettingsUi();
    }

    private void updateOrientation(OrientationMode mode) {
        if (recordingActive) return;
        settings.orientationMode = mode;
        settingsStore.save(settings);
        renderSettingsUi();
    }

    private void updateAudio(AudioSourceOption source) {
        if (recordingActive) return;
        settings.audioSource = source;
        settingsStore.save(settings);
        renderSettingsUi();
    }

    private void selectBitratePreset(VideoBitrateOption preset) {
        if (recordingActive) return;
        settings.videoBitrate = preset;
        settings.customVideoBitrateMbps = null;
        settingsStore.save(settings);
        renderSettingsUi();
    }

    private void updateRegionAnim(RegionAnimationOption option) {
        if (recordingActive) return;
        settings.regionAnimation = option;
        settingsStore.save(settings);
        renderSettingsUi();
    }

    private void openRegionSelector() {
        if (recordingActive) return;
        int[] dims = getTargetScreenDimensions();
        RegionSelectorDialogFragment dlg = RegionSelectorDialogFragment.newInstance(
                settings.selectedRegion, dims[0], dims[1]);
        dlg.setOnRegionConfirmedListener(region -> {
            settings.selectedRegion = region;
            settingsStore.save(settings);
            renderSettingsUi();
        });
        dlg.show(getSupportFragmentManager(), "region_selector");
    }

    private void onStartRecordingClicked() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) {
            permissionsLauncher.launch(needed.toArray(new String[0]));
            return;
        }
        checkOverlayAndLaunchProjection();
    }

    private void checkOverlayAndLaunchProjection() {
        if (settings.recordingMode == RecordingMode.REGION
                && settings.regionAnimation == RegionAnimationOption.SHOW
                && !Settings.canDrawOverlays(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Overlay Permission Recommended")
                    .setMessage("Allow FrameCut to display the animated red capture border over other apps while recording a region.")
                    .setNegativeButton("Record Without Border", (d, w) -> launchMediaProjectionPrompt())
                    .setPositiveButton("Allow Overlay", (d, w) -> {
                        Intent intent = new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .show();
            return;
        }
        launchMediaProjectionPrompt();
    }

    private void launchMediaProjectionPrompt() {
        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            projectionLauncher.launch(mpm.createScreenCaptureIntent());
        }
    }

    private void observeSession() {
        RecordingSessionBus.getInstance().getSessionLiveData().observe(this, state -> {
            if (state == null) return;
            recordingActive = state.phase != RecordingPhase.IDLE;

            long totalSecs = Math.max(0L, state.elapsedMillis / 1000L);
            long mins = totalSecs / 60L;
            long secs = totalSecs % 60L;
            binding.timerText.setText(String.format(Locale.US, "%02d:%02d", mins, secs));

            if (state.phase == RecordingPhase.IDLE) {
                binding.statusText.setText(R.string.status_ready);
                binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.fc_ready_accent));
                binding.btnRecordPrimary.setVisibility(View.VISIBLE);
                binding.activeControlsRow.setVisibility(View.GONE);
            } else if (state.phase == RecordingPhase.RECORDING) {
                binding.statusText.setText(R.string.status_recording);
                binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.fc_record_accent));
                binding.btnRecordPrimary.setVisibility(View.GONE);
                binding.activeControlsRow.setVisibility(View.VISIBLE);
                binding.btnPauseResume.setText(R.string.action_pause);
                binding.btnPauseResume.setIconResource(R.drawable.ic_pause);
            } else if (state.phase == RecordingPhase.PAUSED) {
                binding.statusText.setText(R.string.status_paused);
                binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.fc_paused_accent));
                binding.btnRecordPrimary.setVisibility(View.GONE);
                binding.activeControlsRow.setVisibility(View.VISIBLE);
                binding.btnPauseResume.setText(R.string.action_resume);
                binding.btnPauseResume.setIconResource(R.drawable.ic_play);
            } else {
                binding.statusText.setText(R.string.status_finalizing);
                binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.fc_paused_accent));
                binding.btnRecordPrimary.setVisibility(View.GONE);
                binding.activeControlsRow.setVisibility(View.GONE);
            }

            if (state.errorMessage != null) {
                showBanner(state.errorMessage, true);
            } else if (state.warningMessage != null) {
                showBanner(state.warningMessage, true);
            } else if (state.lastSavedUri != null && state.lastSavedName != null) {
                showBanner(getString(R.string.saved_to_recordings) + ": " + state.lastSavedName, false);
            } else {
                binding.bannerMessage.setVisibility(View.GONE);
            }

            binding.inputCustomBitrate.setEnabled(!recordingActive);
            binding.btnResetBitrate.setEnabled(!recordingActive);
            binding.btnAdjustRegion.setEnabled(!recordingActive);
        });
    }

    private void showBanner(String message, boolean isError) {
        binding.bannerMessage.setText(message);
        binding.bannerMessage.setTextColor(ContextCompat.getColor(
                this, isError ? R.color.fc_error : R.color.fc_ready_accent));
        binding.bannerMessage.setVisibility(View.VISIBLE);
    }

    private int[] getTargetScreenDimensions() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics = getResources().getDisplayMetrics();
        }
        return RegionSelectionManager.resolveFullScreenDimensions(
                metrics.widthPixels, metrics.heightPixels, settings.orientationMode);
    }

    private void renderSettingsUi() {
        int fps = settings.getEffectiveVideoFps();
        int mbps = settings.getEffectiveVideoBitrateMbps();
        String codecLabel = settings.colorDepth == ColorDepthOption.BIT_10 ? "HEVC 10-bit" : "H.265/HEVC";
        binding.headerPipelineSubtitle.setText(codecLabel + " · " + fps + " fps · " + mbps + " Mbps");

        String audioStr = settings.audioSource == AudioSourceOption.INTERNAL
                ? getString(R.string.audio_internal)
                : getString(R.string.audio_microphone);
        binding.consoleSummaryText.setText(audioStr + " · " + mbps + " Mbps · " + fps + " fps");
        binding.metaResolutionText.setText(settings.recordingMode == RecordingMode.REGION
                ? getString(R.string.mode_region)
                : getString(R.string.mode_full_screen));

        boolean isRegion = settings.recordingMode == RecordingMode.REGION;
        binding.tileFullScreen.setBackgroundResource(
                !isRegion ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.tileRegion.setBackgroundResource(
                isRegion ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.cardSelectedRegion.setVisibility(isRegion ? View.VISIBLE : View.GONE);

        if (isRegion) {
            int[] dims = getTargetScreenDimensions();
            PixelRegion px = RegionSelectionManager.toPixelRegion(settings.selectedRegion, dims[0], dims[1]);
            binding.regionDimensionsText.setText(px.width + " x " + px.height + " px");
            binding.regionPreviewView.setRegionFractions(
                    settings.selectedRegion.left,
                    settings.selectedRegion.top,
                    settings.selectedRegion.right,
                    settings.selectedRegion.bottom);
            binding.regionPreviewView.setAppearance(
                    settings.regionAnimation == RegionAnimationOption.SHOW,
                    settings.regionAnimation == RegionAnimationOption.SHOW,
                    ContextCompat.getColor(this, R.color.fc_outline_variant),
                    ContextCompat.getColor(this, R.color.fc_primary),
                    2f
            );
        }

        boolean isPortrait = settings.orientationMode == OrientationMode.PORTRAIT;
        binding.tilePortrait.setBackgroundResource(
                isPortrait ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.tileLandscape.setBackgroundResource(
                !isPortrait ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);

        boolean isInternal = settings.audioSource == AudioSourceOption.INTERNAL;
        binding.tileAudioInternal.setBackgroundResource(
                isInternal ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.tileAudioMic.setBackgroundResource(
                !isInternal ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);

        boolean showAnim = settings.regionAnimation == RegionAnimationOption.SHOW;
        binding.tileAnimShow.setBackgroundResource(
                showAnim ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.tileAnimHide.setBackgroundResource(
                !showAnim ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);

        updatingCustomField = true;
        String customStr = settings.customVideoBitrateMbps != null
                ? String.valueOf(settings.customVideoBitrateMbps) : "";
        if (!customStr.equals(String.valueOf(binding.inputCustomBitrate.getText()))) {
            binding.inputCustomBitrate.setText(customStr);
        }
        updatingCustomField = false;

        renderBitrateLabelsOnly();
    }

    private void renderBitrateLabelsOnly() {
        int mbps = settings.getEffectiveVideoBitrateMbps();
        binding.bitrateBadgeText.setText(mbps + " Mbps");
        binding.headerPipelineSubtitle.setText(
                (settings.colorDepth == ColorDepthOption.BIT_10 ? "HEVC 10-bit" : "H.265/HEVC")
                        + " · " + settings.getEffectiveVideoFps() + " fps · " + mbps + " Mbps");

        boolean customActive = settings.customVideoBitrateMbps != null;
        highlightBitrateChip(binding.chipBitrate100, !customActive && settings.videoBitrate == VideoBitrateOption.MBPS_100);
        highlightBitrateChip(binding.chipBitrate150, !customActive && settings.videoBitrate == VideoBitrateOption.MBPS_150);
        highlightBitrateChip(binding.chipBitrate200, !customActive && settings.videoBitrate == VideoBitrateOption.MBPS_200);
        highlightBitrateChip(binding.chipBitrate250, !customActive && settings.videoBitrate == VideoBitrateOption.MBPS_250);
        highlightBitrateChip(binding.chipBitrate300, !customActive && settings.videoBitrate == VideoBitrateOption.MBPS_300);
        highlightBitrateChip(binding.chipBitrate350, !customActive && settings.videoBitrate == VideoBitrateOption.MBPS_350);
    }

    private void highlightBitrateChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
    }
}
