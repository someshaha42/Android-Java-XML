package com.aistudio.framecut.model;

public final class RecorderEnums {
    private RecorderEnums() {}

    public enum RecordingMode {
        FULL_SCREEN,
        REGION
    }

    public enum OrientationMode {
        PORTRAIT,
        LANDSCAPE
    }

    public enum AudioSourceOption {
        INTERNAL,
        MICROPHONE
    }

    public enum VideoBitrateOption {
        MBPS_100(100_000_000, 100, "100 Mbps"),
        MBPS_150(150_000_000, 150, "150 Mbps"),
        MBPS_200(200_000_000, 200, "200 Mbps"),
        MBPS_250(250_000_000, 250, "250 Mbps"),
        MBPS_300(300_000_000, 300, "300 Mbps"),
        MBPS_350(350_000_000, 350, "350 Mbps");

        public final int targetBitsPerSecond;
        public final int mbps;
        public final String label;

        VideoBitrateOption(int targetBitsPerSecond, int mbps, String label) {
            this.targetBitsPerSecond = targetBitsPerSecond;
            this.mbps = mbps;
            this.label = label;
        }
    }

    public enum RegionAnimationOption {
        SHOW,
        HIDE
    }

    public enum BitrateModeOption {
        VBR,
        CQ
    }

    public enum ColorDepthOption {
        BIT_8,
        BIT_10
    }

    public enum VideoFpsOption {
        FPS_30(30),
        FPS_60(60);

        public final int fps;

        VideoFpsOption(int fps) {
            this.fps = fps;
        }
    }

    public enum GopIntervalOption {
        SEC_1(1.0f),
        SEC_0_5(0.5f);

        public final float seconds;

        GopIntervalOption(float seconds) {
            this.seconds = seconds;
        }
    }

    public enum ColorRangeOption {
        LIMITED,
        FULL
    }

    public enum AudioFormatOption {
        MONO_AAC,
        MONO_MP3
    }

    public enum RecordingPhase {
        IDLE,
        RECORDING,
        PAUSED,
        FINALIZING
    }
}
