package com.aistudio.framecut.model;

import android.content.Context;
import android.content.SharedPreferences;
import com.aistudio.framecut.model.RecorderEnums.*;
import com.aistudio.framecut.region.RegionSelectionManager;

public final class SettingsStore {
    private static final String PREFS_NAME = "framecut_recording_settings";

    private static final String KEY_MODE = "recording_mode";
    private static final String KEY_ORIENTATION = "orientation_mode";
    private static final String KEY_AUDIO = "audio_source";
    private static final String KEY_BITRATE = "video_bitrate";
    private static final String KEY_REGION_ANIM = "region_animation";
    private static final String KEY_REG_LEFT = "region_left";
    private static final String KEY_REG_TOP = "region_top";
    private static final String KEY_REG_RIGHT = "region_right";
    private static final String KEY_REG_BOTTOM = "region_bottom";
    private static final String KEY_BITRATE_MODE = "bitrate_mode";
    private static final String KEY_COLOR_DEPTH = "color_depth";
    private static final String KEY_FPS = "video_fps";
    private static final String KEY_GOP = "gop_interval";
    private static final String KEY_COLOR_RANGE = "color_range";
    private static final String KEY_AUDIO_FMT = "audio_format";
    private static final String KEY_DARK_THEME = "dark_theme_override";
    private static final String KEY_CUSTOM_BITRATE = "custom_bitrate_mbps";
    private static final String KEY_CUSTOM_FPS = "custom_fps";
    private static final String KEY_CUSTOM_GOP = "custom_gop_sec";
    private static final String KEY_CUSTOM_AUDIO_HZ = "custom_audio_hz";
    private static final String KEY_CUSTOM_AUDIO_BPS = "custom_audio_bps";

    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public RecordingSettings load() {
        RecordingSettings s = new RecordingSettings();
        s.recordingMode = parseEnum(RecordingMode.class, prefs.getString(KEY_MODE, null), s.recordingMode);
        s.orientationMode = parseEnum(OrientationMode.class, prefs.getString(KEY_ORIENTATION, null), s.orientationMode);
        s.audioSource = parseEnum(AudioSourceOption.class, prefs.getString(KEY_AUDIO, null), s.audioSource);
        s.videoBitrate = parseEnum(VideoBitrateOption.class, prefs.getString(KEY_BITRATE, null), s.videoBitrate);
        s.regionAnimation = parseEnum(RegionAnimationOption.class, prefs.getString(KEY_REGION_ANIM, null), s.regionAnimation);

        float l = prefs.getFloat(KEY_REG_LEFT, NormalizedRegion.DEFAULT.left);
        float t = prefs.getFloat(KEY_REG_TOP, NormalizedRegion.DEFAULT.top);
        float r = prefs.getFloat(KEY_REG_RIGHT, NormalizedRegion.DEFAULT.right);
        float b = prefs.getFloat(KEY_REG_BOTTOM, NormalizedRegion.DEFAULT.bottom);
        s.selectedRegion = RegionSelectionManager.normalize(new NormalizedRegion(l, t, r, b));

        s.bitrateMode = parseEnum(BitrateModeOption.class, prefs.getString(KEY_BITRATE_MODE, null), s.bitrateMode);
        s.colorDepth = parseEnum(ColorDepthOption.class, prefs.getString(KEY_COLOR_DEPTH, null), s.colorDepth);
        s.videoFps = parseEnum(VideoFpsOption.class, prefs.getString(KEY_FPS, null), s.videoFps);
        s.gopInterval = parseEnum(GopIntervalOption.class, prefs.getString(KEY_GOP, null), s.gopInterval);
        s.colorRange = parseEnum(ColorRangeOption.class, prefs.getString(KEY_COLOR_RANGE, null), s.colorRange);
        s.audioFormat = parseEnum(AudioFormatOption.class, prefs.getString(KEY_AUDIO_FMT, null), s.audioFormat);

        if (prefs.contains(KEY_DARK_THEME)) {
            s.darkThemeOverride = prefs.getBoolean(KEY_DARK_THEME, true);
        }
        if (prefs.contains(KEY_CUSTOM_BITRATE)) {
            s.customVideoBitrateMbps = prefs.getInt(KEY_CUSTOM_BITRATE, 150);
        }
        if (prefs.contains(KEY_CUSTOM_FPS)) {
            s.customVideoFps = prefs.getInt(KEY_CUSTOM_FPS, 30);
        }
        if (prefs.contains(KEY_CUSTOM_GOP)) {
            s.customGopSeconds = prefs.getFloat(KEY_CUSTOM_GOP, 1.0f);
        }
        if (prefs.contains(KEY_CUSTOM_AUDIO_HZ)) {
            s.customAudioSampleRateHz = prefs.getInt(KEY_CUSTOM_AUDIO_HZ, 48_000);
        }
        if (prefs.contains(KEY_CUSTOM_AUDIO_BPS)) {
            s.customAudioBitrateBps = prefs.getInt(KEY_CUSTOM_AUDIO_BPS, 128_000);
        }
        return s;
    }

    public void save(RecordingSettings s) {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString(KEY_MODE, s.recordingMode.name());
        ed.putString(KEY_ORIENTATION, s.orientationMode.name());
        ed.putString(KEY_AUDIO, s.audioSource.name());
        ed.putString(KEY_BITRATE, s.videoBitrate.name());
        ed.putString(KEY_REGION_ANIM, s.regionAnimation.name());
        ed.putFloat(KEY_REG_LEFT, s.selectedRegion.left);
        ed.putFloat(KEY_REG_TOP, s.selectedRegion.top);
        ed.putFloat(KEY_REG_RIGHT, s.selectedRegion.right);
        ed.putFloat(KEY_REG_BOTTOM, s.selectedRegion.bottom);
        ed.putString(KEY_BITRATE_MODE, s.bitrateMode.name());
        ed.putString(KEY_COLOR_DEPTH, s.colorDepth.name());
        ed.putString(KEY_FPS, s.videoFps.name());
        ed.putString(KEY_GOP, s.gopInterval.name());
        ed.putString(KEY_COLOR_RANGE, s.colorRange.name());
        ed.putString(KEY_AUDIO_FMT, s.audioFormat.name());

        if (s.darkThemeOverride != null) ed.putBoolean(KEY_DARK_THEME, s.darkThemeOverride);
        else ed.remove(KEY_DARK_THEME);

        if (s.customVideoBitrateMbps != null) ed.putInt(KEY_CUSTOM_BITRATE, s.customVideoBitrateMbps);
        else ed.remove(KEY_CUSTOM_BITRATE);

        if (s.customVideoFps != null) ed.putInt(KEY_CUSTOM_FPS, s.customVideoFps);
        else ed.remove(KEY_CUSTOM_FPS);

        if (s.customGopSeconds != null) ed.putFloat(KEY_CUSTOM_GOP, s.customGopSeconds);
        else ed.remove(KEY_CUSTOM_GOP);

        if (s.customAudioSampleRateHz != null) ed.putInt(KEY_CUSTOM_AUDIO_HZ, s.customAudioSampleRateHz);
        else ed.remove(KEY_CUSTOM_AUDIO_HZ);

        if (s.customAudioBitrateBps != null) ed.putInt(KEY_CUSTOM_AUDIO_BPS, s.customAudioBitrateBps);
        else ed.remove(KEY_CUSTOM_AUDIO_BPS);

        ed.apply();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> cls, String raw, E fallback) {
        if (raw == null) return fallback;
        try {
            return Enum.valueOf(cls, raw);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
