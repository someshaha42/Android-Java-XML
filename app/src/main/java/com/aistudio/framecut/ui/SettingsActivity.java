package com.aistudio.framecut.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.aistudio.framecut.R;
import com.aistudio.framecut.databinding.ActivitySettingsBinding;
import com.aistudio.framecut.model.RecorderEnums.*;
import com.aistudio.framecut.model.RecordingSettings;
import com.aistudio.framecut.model.SettingsStore;
import com.aistudio.framecut.service.RecordingSessionBus;

public final class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private SettingsStore settingsStore;
    private RecordingSettings settings;
    private boolean locked = false;
    private boolean suppressWatchers = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        settingsStore = new SettingsStore(this);
        settings = settingsStore.load();

        binding.toolbarSettings.setNavigationOnClickListener(v -> finish());
        setupListeners();
        observeSessionLock();
        render();
    }

    private void setupListeners() {
        binding.tileBitrateVbr.setOnClickListener(v -> {
            if (locked) return;
            settings.bitrateMode = BitrateModeOption.VBR;
            saveAndRender();
        });
        binding.tileBitrateCq.setOnClickListener(v -> {
            if (locked) return;
            settings.bitrateMode = BitrateModeOption.CQ;
            saveAndRender();
        });

        binding.tileColor8Bit.setOnClickListener(v -> {
            if (locked) return;
            settings.colorDepth = ColorDepthOption.BIT_8;
            saveAndRender();
        });
        binding.tileColor10Bit.setOnClickListener(v -> {
            if (locked) return;
            settings.colorDepth = ColorDepthOption.BIT_10;
            saveAndRender();
        });

        binding.tileFps30.setOnClickListener(v -> {
            if (locked) return;
            settings.videoFps = VideoFpsOption.FPS_30;
            settings.customVideoFps = null;
            saveAndRender();
        });
        binding.tileFps60.setOnClickListener(v -> {
            if (locked) return;
            settings.videoFps = VideoFpsOption.FPS_60;
            settings.customVideoFps = null;
            saveAndRender();
        });
        binding.btnResetFps.setOnClickListener(v -> {
            if (locked) return;
            settings.customVideoFps = null;
            settings.videoFps = VideoFpsOption.FPS_30;
            saveAndRender();
        });

        binding.tileGop1s.setOnClickListener(v -> {
            if (locked) return;
            settings.gopInterval = GopIntervalOption.SEC_1;
            settings.customGopSeconds = null;
            saveAndRender();
        });
        binding.tileGop05s.setOnClickListener(v -> {
            if (locked) return;
            settings.gopInterval = GopIntervalOption.SEC_0_5;
            settings.customGopSeconds = null;
            saveAndRender();
        });
        binding.btnResetGop.setOnClickListener(v -> {
            if (locked) return;
            settings.customGopSeconds = null;
            settings.gopInterval = GopIntervalOption.SEC_1;
            saveAndRender();
        });

        binding.tileRangeLimited.setOnClickListener(v -> {
            if (locked) return;
            settings.colorRange = ColorRangeOption.LIMITED;
            saveAndRender();
        });
        binding.tileRangeFull.setOnClickListener(v -> {
            if (locked) return;
            settings.colorRange = ColorRangeOption.FULL;
            saveAndRender();
        });

        binding.tileAudioAac.setOnClickListener(v -> {
            if (locked) return;
            settings.audioFormat = AudioFormatOption.MONO_AAC;
            saveAndRender();
        });
        binding.tileAudioMp3.setOnClickListener(v -> {
            if (locked) return;
            settings.audioFormat = AudioFormatOption.MONO_MP3;
            saveAndRender();
        });
        binding.btnResetAudio.setOnClickListener(v -> {
            if (locked) return;
            settings.customAudioSampleRateHz = null;
            settings.customAudioBitrateBps = null;
            settings.audioFormat = AudioFormatOption.MONO_AAC;
            saveAndRender();
        });

        binding.inputCustomFps.addTextChangedListener(new SimpleAfterWatcher(raw -> {
            if (suppressWatchers || locked) return;
            if (raw.isEmpty()) settings.customVideoFps = null;
            else {
                try {
                    int fps = Integer.parseInt(raw);
                    if (fps >= 1 && fps <= 240) settings.customVideoFps = fps;
                } catch (NumberFormatException ignored) {}
            }
            settingsStore.save(settings);
            renderSelectionTilesOnly();
        }));

        binding.inputCustomGop.addTextChangedListener(new SimpleAfterWatcher(raw -> {
            if (suppressWatchers || locked) return;
            if (raw.isEmpty()) settings.customGopSeconds = null;
            else {
                try {
                    float gop = Float.parseFloat(raw);
                    if (gop >= 0.05f) settings.customGopSeconds = gop;
                } catch (NumberFormatException ignored) {}
            }
            settingsStore.save(settings);
            renderSelectionTilesOnly();
        }));

        binding.inputCustomAudioHz.addTextChangedListener(new SimpleAfterWatcher(raw -> {
            if (suppressWatchers || locked) return;
            if (raw.isEmpty()) settings.customAudioSampleRateHz = null;
            else {
                try {
                    int hz = Integer.parseInt(raw);
                    if (hz >= 8000) settings.customAudioSampleRateHz = hz;
                } catch (NumberFormatException ignored) {}
            }
            settingsStore.save(settings);
        }));

        binding.inputCustomAudioKbps.addTextChangedListener(new SimpleAfterWatcher(raw -> {
            if (suppressWatchers || locked) return;
            if (raw.isEmpty()) settings.customAudioBitrateBps = null;
            else {
                try {
                    int kbps = Integer.parseInt(raw);
                    if (kbps >= 16) settings.customAudioBitrateBps = kbps * 1000;
                } catch (NumberFormatException ignored) {}
            }
            settingsStore.save(settings);
        }));
    }

    private void observeSessionLock() {
        RecordingSessionBus.getInstance().getSessionLiveData().observe(this, st -> {
            if (st == null) return;
            locked = st.phase != RecordingPhase.IDLE;
            binding.bannerSettingsLocked.setVisibility(locked ? View.VISIBLE : View.GONE);
            binding.inputCustomFps.setEnabled(!locked);
            binding.inputCustomGop.setEnabled(!locked);
            binding.inputCustomAudioHz.setEnabled(!locked);
            binding.inputCustomAudioKbps.setEnabled(!locked);
            binding.btnResetFps.setEnabled(!locked);
            binding.btnResetGop.setEnabled(!locked);
            binding.btnResetAudio.setEnabled(!locked);
            binding.settingsContentContainer.setAlpha(locked ? 0.65f : 1.0f);
        });
    }

    private void saveAndRender() {
        settingsStore.save(settings);
        render();
    }

    private void render() {
        suppressWatchers = true;
        binding.inputCustomFps.setText(
                settings.customVideoFps != null ? String.valueOf(settings.customVideoFps) : "");
        binding.inputCustomGop.setText(
                settings.customGopSeconds != null ? String.valueOf(settings.customGopSeconds) : "");
        binding.inputCustomAudioHz.setText(
                settings.customAudioSampleRateHz != null ? String.valueOf(settings.customAudioSampleRateHz) : "");
        binding.inputCustomAudioKbps.setText(
                settings.customAudioBitrateBps != null ? String.valueOf(settings.customAudioBitrateBps / 1000) : "");
        suppressWatchers = false;

        renderSelectionTilesOnly();
    }

    private void renderSelectionTilesOnly() {
        binding.tileBitrateVbr.setBackgroundResource(settings.bitrateMode == BitrateModeOption.VBR
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.tileBitrateCq.setBackgroundResource(settings.bitrateMode == BitrateModeOption.CQ
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);

        binding.tileColor8Bit.setBackgroundResource(settings.colorDepth == ColorDepthOption.BIT_8
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.tileColor10Bit.setBackgroundResource(settings.colorDepth == ColorDepthOption.BIT_10
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);

        boolean customFps = settings.customVideoFps != null;
        binding.tileFps30.setBackgroundResource(!customFps && settings.videoFps == VideoFpsOption.FPS_30
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.tileFps60.setBackgroundResource(!customFps && settings.videoFps == VideoFpsOption.FPS_60
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);

        boolean customGop = settings.customGopSeconds != null;
        binding.tileGop1s.setBackgroundResource(!customGop && settings.gopInterval == GopIntervalOption.SEC_1
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.tileGop05s.setBackgroundResource(!customGop && settings.gopInterval == GopIntervalOption.SEC_0_5
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);

        binding.tileRangeLimited.setBackgroundResource(settings.colorRange == ColorRangeOption.LIMITED
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.tileRangeFull.setBackgroundResource(settings.colorRange == ColorRangeOption.FULL
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);

        binding.tileAudioAac.setBackgroundResource(settings.audioFormat == AudioFormatOption.MONO_AAC
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
        binding.tileAudioMp3.setBackgroundResource(settings.audioFormat == AudioFormatOption.MONO_MP3
                ? R.drawable.bg_option_tile_selected : R.drawable.bg_option_tile_unselected);
    }

    private interface TextCallback {
        void onAfter(String trimmed);
    }

    private static final class SimpleAfterWatcher implements TextWatcher {
        private final TextCallback callback;
        SimpleAfterWatcher(TextCallback callback) { this.callback = callback; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { callback.onAfter(s.toString().trim()); }
    }
}
