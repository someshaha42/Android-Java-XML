package com.aistudio.framecut.capture;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.view.Surface;
import com.aistudio.framecut.encoder.GlRegionCropper;
import com.aistudio.framecut.model.NormalizedRegion;
import com.aistudio.framecut.model.PixelRegion;
import com.aistudio.framecut.model.RecorderEnums.RecordingMode;

public final class ScreenCaptureManager {
    private final MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private GlRegionCropper glCropper;
    private Thread glThread;

    private volatile boolean running = false;
    private volatile boolean paused = false;
    private long pauseStartedNs = 0L;
    private long totalPausedNs = 0L;

    public ScreenCaptureManager(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    public void start(
            RecordingMode mode,
            int screenWidth,
            int screenHeight,
            int densityDpi,
            NormalizedRegion region,
            PixelRegion pixelRegion,
            Surface encoderInputSurface) {
        running = true;
        if (mode == RecordingMode.REGION && pixelRegion != null) {
            glThread = new Thread(() -> {
                glCropper = new GlRegionCropper(
                        encoderInputSurface,
                        screenWidth,
                        screenHeight,
                        pixelRegion.width,
                        pixelRegion.height,
                        region
                );
                virtualDisplay = mediaProjection.createVirtualDisplay(
                        "FrameCut-RegionDisplay",
                        screenWidth,
                        screenHeight,
                        densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        glCropper.getInputSurface(),
                        null,
                        null
                );
                long baseNs = -1L;
                while (running) {
                    if (paused) {
                        try {
                            Thread.sleep(40L);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    long nowNs = System.nanoTime();
                    if (baseNs < 0L) baseNs = nowNs;
                    long adjustedPtsNs = Math.max(0L, nowNs - totalPausedNs);
                    glCropper.awaitAndRenderNextFrame(50L, adjustedPtsNs);
                }
                if (glCropper != null) {
                    glCropper.release();
                    glCropper = null;
                }
            }, "FrameCut-GlCropper");
            glThread.start();
        } else {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "FrameCut-FullDisplay",
                    screenWidth,
                    screenHeight,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    encoderInputSurface,
                    null,
                    null
            );
        }
    }

    public void setPaused(boolean pause) {
        if (this.paused == pause) return;
        long nowNs = System.nanoTime();
        if (pause) {
            pauseStartedNs = nowNs;
        } else if (pauseStartedNs > 0L) {
            totalPausedNs += Math.max(0L, nowNs - pauseStartedNs);
            pauseStartedNs = 0L;
        }
        this.paused = pause;
    }

    public void stop() {
        running = false;
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (glThread != null) {
            try {
                glThread.join(1500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            glThread = null;
        }
    }
}
