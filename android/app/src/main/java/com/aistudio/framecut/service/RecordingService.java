package com.aistudio.framecut.service;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.aistudio.framecut.R;
import com.aistudio.framecut.audio.AudioCaptureManager;
import com.aistudio.framecut.capture.ScreenCaptureManager;
import com.aistudio.framecut.encoder.VideoEncoder;
import com.aistudio.framecut.model.PixelRegion;
import com.aistudio.framecut.model.RecorderEnums.AudioSourceOption;
import com.aistudio.framecut.model.RecorderEnums.RecordingMode;
import com.aistudio.framecut.model.RecorderEnums.RecordingPhase;
import com.aistudio.framecut.model.RecorderEnums.RegionAnimationOption;
import com.aistudio.framecut.model.RecordingSessionState;
import com.aistudio.framecut.model.RecordingSettings;
import com.aistudio.framecut.model.SettingsStore;
import com.aistudio.framecut.muxer.MediaMuxerManager;
import com.aistudio.framecut.region.RegionOverlayManager;
import com.aistudio.framecut.region.RegionSelectionManager;
import com.aistudio.framecut.ui.MainActivity;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RecordingService extends Service {
    public static final String ACTION_START = "com.aistudio.framecut.action.START";
    public static final String ACTION_PAUSE = "com.aistudio.framecut.action.PAUSE";
    public static final String ACTION_RESUME = "com.aistudio.framecut.action.RESUME";
    public static final String ACTION_STOP = "com.aistudio.framecut.action.STOP";
    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_DATA = "extra_result_data";

    private static final String CHANNEL_ID = "framecut_recording_channel";
    private static final int NOTIFICATION_ID = 4102;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private MediaProjection mediaProjection;
    private MediaMuxerManager muxerManager;
    private VideoEncoder videoEncoder;
    private AudioCaptureManager audioCaptureManager;
    private ScreenCaptureManager screenCaptureManager;
    private RegionOverlayManager regionOverlayManager;

    private RecordingSettings activeSettings;
    private String currentDisplayName;
    private long sessionActiveMs = 0L;
    private long segmentStartMs = 0L;
    private boolean isPaused = false;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            RecordingSessionState state = RecordingSessionBus.getInstance().getCurrentState();
            if (state.phase == RecordingPhase.RECORDING && !isPaused) {
                long now = System.currentTimeMillis();
                state.elapsedMillis = sessionActiveMs + Math.max(0L, now - segmentStartMs);
                RecordingSessionBus.getInstance().postState(state);
                mainHandler.postDelayed(this, 250L);
            }
        }
    };

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            stopAndFinalizeRecording();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        regionOverlayManager = new RegionOverlayManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        switch (action) {
            case ACTION_START:
                int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
                Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startRecordingPipeline(resultCode, data);
                } else {
                    stopSelf();
                }
                break;
            case ACTION_PAUSE:
                pauseRecording();
                break;
            case ACTION_RESUME:
                resumeRecording();
                break;
            case ACTION_STOP:
                stopAndFinalizeRecording();
                break;
            default:
                break;
        }
        return START_NOT_STICKY;
    }

    private void startRecordingPipeline(int resultCode, Intent resultData) {
        activeSettings = new SettingsStore(this).load();
        boolean includeMic = activeSettings.audioSource == AudioSourceOption.MICROPHONE;
        startForegroundWithType(buildNotification(RecordingPhase.RECORDING), includeMic);

        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) {
            failSession("MediaProjection permission was denied.");
            return;
        }
        mediaProjection.registerCallback(projectionCallback, mainHandler);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics = getResources().getDisplayMetrics();
        }

        int[] fullDims = RegionSelectionManager.resolveFullScreenDimensions(
                metrics.widthPixels,
                metrics.heightPixels,
                activeSettings.orientationMode
        );
        int screenW = fullDims[0];
        int screenH = fullDims[1];
        int densityDpi = Math.max(160, metrics.densityDpi);

        PixelRegion pixelRegion = null;
        int outW = screenW;
        int outH = screenH;
        if (activeSettings.recordingMode == RecordingMode.REGION) {
            pixelRegion = RegionSelectionManager.toPixelRegion(activeSettings.selectedRegion, screenW, screenH);
            outW = pixelRegion.width;
            outH = pixelRegion.height;
        }

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        currentDisplayName = "FrameCut_" + stamp + ".mp4";
        File tempMp4 = new File(getCacheDir(), currentDisplayName);

        try {
            muxerManager = new MediaMuxerManager(tempMp4, true);
            videoEncoder = new VideoEncoder(muxerManager);
            Surface encoderSurface = videoEncoder.start(outW, outH, activeSettings);

            audioCaptureManager = new AudioCaptureManager(
                    this,
                    muxerManager,
                    activeSettings.audioSource,
                    mediaProjection,
                    activeSettings.getEffectiveAudioSampleRateHz(),
                    activeSettings.getEffectiveAudioBitrateBps()
            );
            audioCaptureManager.start(warning -> {
                RecordingSessionState st = RecordingSessionBus.getInstance().getCurrentState();
                st.warningMessage = warning;
                RecordingSessionBus.getInstance().postState(st);
            });

            screenCaptureManager = new ScreenCaptureManager(mediaProjection);
            screenCaptureManager.start(
                    activeSettings.recordingMode,
                    screenW,
                    screenH,
                    densityDpi,
                    activeSettings.selectedRegion,
                    pixelRegion,
                    encoderSurface
            );

            boolean showOverlay = activeSettings.recordingMode == RecordingMode.REGION
                    && activeSettings.regionAnimation == RegionAnimationOption.SHOW;
            if (showOverlay && regionOverlayManager.canDrawOverlays()) {
                regionOverlayManager.showRecordingBorder(activeSettings.selectedRegion);
            }

            sessionActiveMs = 0L;
            segmentStartMs = System.currentTimeMillis();
            isPaused = false;

            RecordingSessionState state = new RecordingSessionState();
            state.phase = RecordingPhase.RECORDING;
            state.elapsedMillis = 0L;
            state.activeMode = activeSettings.recordingMode;
            state.activeAudioLabel = activeSettings.audioSource == AudioSourceOption.INTERNAL
                    ? getString(R.string.audio_internal)
                    : getString(R.string.audio_microphone);
            state.activeEncoderName = videoEncoder.getActiveCodecLabel();
            state.activeBitrateMbps = activeSettings.getEffectiveVideoBitrateMbps();
            state.activeResolutionLabel = outW + "x" + outH;
            state.activeRegion = pixelRegion;
            state.showRegionBorder = showOverlay;
            RecordingSessionBus.getInstance().postState(state);

            mainHandler.removeCallbacks(timerRunnable);
            mainHandler.post(timerRunnable);
        } catch (Throwable t) {
            failSession("Failed to initialize recorder: " + t.getMessage());
        }
    }

    private void pauseRecording() {
        if (isPaused) return;
        isPaused = true;
        sessionActiveMs += Math.max(0L, System.currentTimeMillis() - segmentStartMs);
        mainHandler.removeCallbacks(timerRunnable);

        if (screenCaptureManager != null) screenCaptureManager.setPaused(true);
        if (audioCaptureManager != null) audioCaptureManager.setPaused(true);

        RecordingSessionState state = RecordingSessionBus.getInstance().getCurrentState();
        state.phase = RecordingPhase.PAUSED;
        state.elapsedMillis = sessionActiveMs;
        RecordingSessionBus.getInstance().postState(state);
        updateNotification(RecordingPhase.PAUSED);
    }

    private void resumeRecording() {
        if (!isPaused) return;
        isPaused = false;
        segmentStartMs = System.currentTimeMillis();

        if (screenCaptureManager != null) screenCaptureManager.setPaused(false);
        if (audioCaptureManager != null) audioCaptureManager.setPaused(false);

        RecordingSessionState state = RecordingSessionBus.getInstance().getCurrentState();
        state.phase = RecordingPhase.RECORDING;
        RecordingSessionBus.getInstance().postState(state);
        updateNotification(RecordingPhase.RECORDING);
        mainHandler.post(timerRunnable);
    }

    private void stopAndFinalizeRecording() {
        mainHandler.removeCallbacks(timerRunnable);
        if (regionOverlayManager != null) {
            regionOverlayManager.hideRecordingBorder();
        }

        RecordingSessionState finalizing = RecordingSessionBus.getInstance().getCurrentState();
        if (finalizing.phase == RecordingPhase.FINALIZING || finalizing.phase == RecordingPhase.IDLE) {
            return;
        }
        finalizing.phase = RecordingPhase.FINALIZING;
        RecordingSessionBus.getInstance().postState(finalizing);
        updateNotification(RecordingPhase.FINALIZING);

        worker.execute(() -> {
            if (screenCaptureManager != null) {
                screenCaptureManager.stop();
                screenCaptureManager = null;
            }
            if (audioCaptureManager != null) {
                audioCaptureManager.stop();
                audioCaptureManager = null;
            }
            if (videoEncoder != null) {
                videoEncoder.stop();
                videoEncoder = null;
            }
            if (mediaProjection != null) {
                try {
                    mediaProjection.unregisterCallback(projectionCallback);
                    mediaProjection.stop();
                } catch (Exception ignored) {
                }
                mediaProjection = null;
            }

            Uri publishedUri = null;
            if (muxerManager != null) {
                publishedUri = muxerManager.stopAndPublishToMediaStore(
                        getApplicationContext(),
                        currentDisplayName != null ? currentDisplayName : "FrameCut_Capture.mp4",
                        "video/mp4"
                );
                muxerManager = null;
            }

            RecordingSessionState done = new RecordingSessionState();
            done.phase = RecordingPhase.IDLE;
            done.elapsedMillis = 0L;
            done.lastSavedUri = publishedUri;
            done.lastSavedName = currentDisplayName;
            if (publishedUri == null) {
                done.errorMessage = "Recording file could not be finalized.";
            }
            RecordingSessionBus.getInstance().postState(done);

            mainHandler.post(() -> {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            });
        });
    }

    private void failSession(String message) {
        if (regionOverlayManager != null) {
            regionOverlayManager.hideRecordingBorder();
        }
        RecordingSessionState state = new RecordingSessionState();
        state.phase = RecordingPhase.IDLE;
        state.errorMessage = message;
        RecordingSessionBus.getInstance().postState(state);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void startForegroundWithType(Notification notification, boolean includeMic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (includeMic) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(NOTIFICATION_ID, notification, type);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        ch.setDescription(getString(R.string.notification_channel_desc));
        nm.createNotificationChannel(ch);
    }

    private void updateNotification(RecordingPhase phase) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(phase));
        }
    }

    private Notification buildNotification(RecordingPhase phase) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_recording)
                .setContentTitle(getString(R.string.app_name))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        if (phase == RecordingPhase.FINALIZING) {
            builder.setContentText(getString(R.string.status_finalizing));
            return builder.build();
        }

        if (phase == RecordingPhase.PAUSED) {
            builder.setContentText(getString(R.string.status_paused));
            PendingIntent resumePi = PendingIntent.getService(
                    this, 1, new Intent(this, RecordingService.class).setAction(ACTION_RESUME),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_play, getString(R.string.action_resume), resumePi);
        } else {
            builder.setContentText(getString(R.string.status_recording));
            PendingIntent pausePi = PendingIntent.getService(
                    this, 2, new Intent(this, RecordingService.class).setAction(ACTION_PAUSE),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_pause, getString(R.string.action_pause), pausePi);
        }

        PendingIntent stopPi = PendingIntent.getService(
                this, 3, new Intent(this, RecordingService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(R.drawable.ic_stop, getString(R.string.action_stop), stopPi);
        return builder.build();
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(timerRunnable);
        if (regionOverlayManager != null) {
            regionOverlayManager.hideRecordingBorder();
        }
        worker.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
