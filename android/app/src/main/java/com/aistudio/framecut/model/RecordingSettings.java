package com.aistudio.framecut.model;

import com.aistudio.framecut.model.RecorderEnums.*;

public final class RecordingSettings {
    public static final int DEFAULT_AUDIO_SAMPLE_RATE_HZ = 48_000;
    public static final int DEFAULT_AUDIO_BITRATE_BPS = 128_000;

    public RecordingMode recordingMode = RecordingMode.FULL_SCREEN;
    public OrientationMode orientationMode = OrientationMode.LANDSCAPE;
    public AudioSourceOption audioSource = AudioSourceOption.INTERNAL;
    public VideoBitrateOption videoBitrate = VideoBitrateOption.MBPS_150;
    public RegionAnimationOption regionAnimation = RegionAnimationOption.SHOW;
    public NormalizedRegion selectedRegion = NormalizedRegion.DEFAULT;

    public BitrateModeOption bitrateMode = BitrateModeOption.VBR;
    public ColorDepthOption colorDepth = ColorDepthOption.BIT_8;
    public VideoFpsOption videoFps = VideoFpsOption.FPS_30;
    public GopIntervalOption gopInterval = GopIntervalOption.SEC_1;
    public ColorRangeOption colorRange = ColorRangeOption.LIMITED;
    public AudioFormatOption audioFormat = AudioFormatOption.MONO_AAC;
    public Boolean darkThemeOverride = Boolean.TRUE;

    public Integer customVideoBitrateMbps = null;
    public Integer customVideoFps = null;
    public Float customGopSeconds = null;
    public Integer customAudioSampleRateHz = null;
    public Integer customAudioBitrateBps = null;

    public RecordingSettings copy() {
        RecordingSettings c = new RecordingSettings();
        c.recordingMode = this.recordingMode;
        c.orientationMode = this.orientationMode;
        c.audioSource = this.audioSource;
        c.videoBitrate = this.videoBitrate;
        c.regionAnimation = this.regionAnimation;
        c.selectedRegion = this.selectedRegion;
        c.bitrateMode = this.bitrateMode;
        c.colorDepth = this.colorDepth;
        c.videoFps = this.videoFps;
        c.gopInterval = this.gopInterval;
        c.colorRange = this.colorRange;
        c.audioFormat = this.audioFormat;
        c.darkThemeOverride = this.darkThemeOverride;
        c.customVideoBitrateMbps = this.customVideoBitrateMbps;
        c.customVideoFps = this.customVideoFps;
        c.customGopSeconds = this.customGopSeconds;
        c.customAudioSampleRateHz = this.customAudioSampleRateHz;
        c.customAudioBitrateBps = this.customAudioBitrateBps;
        return c;
    }

    public int getEffectiveVideoBitrateMbps() {
        int val = customVideoBitrateMbps != null ? customVideoBitrateMbps : videoBitrate.mbps;
        return Math.max(1, val);
    }

    public int getEffectiveVideoBitrateBps() {
        long bps = (long) getEffectiveVideoBitrateMbps() * 1_000_000L;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1_000_000L, bps));
    }

    public int getEffectiveVideoFps() {
        int val = customVideoFps != null ? customVideoFps : videoFps.fps;
        return Math.max(1, val);
    }

    public float getEffectiveGopSeconds() {
        float val = customGopSeconds != null ? customGopSeconds : gopInterval.seconds;
        return Math.max(0.05f, val);
    }

    public int getEffectiveAudioSampleRateHz() {
        int val = customAudioSampleRateHz != null ? customAudioSampleRateHz : DEFAULT_AUDIO_SAMPLE_RATE_HZ;
        return Math.max(8_000, val);
    }

    public int getEffectiveAudioBitrateBps() {
        int val = customAudioBitrateBps != null ? customAudioBitrateBps : DEFAULT_AUDIO_BITRATE_BPS;
        return Math.max(16_000, val);
    }
}
