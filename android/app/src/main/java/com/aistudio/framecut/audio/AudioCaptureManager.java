package com.aistudio.framecut.audio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import com.aistudio.framecut.model.RecorderEnums.AudioSourceOption;
import com.aistudio.framecut.muxer.MediaMuxerManager;
import java.nio.ByteBuffer;

public final class AudioCaptureManager {
    private final Context appContext;
    private final MediaMuxerManager muxerManager;
    private final int sampleRateHz;
    private final int bitrateBps;
    private final AudioSourceOption sourceOption;
    private final MediaProjection mediaProjection;

    private AudioRecord audioRecord;
    private MediaCodec audioEncoder;
    private int audioTrackIndex = -1;
    private Thread captureThread;

    private volatile boolean running = false;
    private volatile boolean paused = false;
    private long pauseStartedUs = 0L;
    private long totalPausedUs = 0L;
    private long startTimeUs = -1L;

    public interface Callback {
        void onAudioWarning(String message);
    }

    public AudioCaptureManager(
            Context context,
            MediaMuxerManager muxerManager,
            AudioSourceOption sourceOption,
            MediaProjection mediaProjection,
            int sampleRateHz,
            int bitrateBps) {
        this.appContext = context.getApplicationContext();
        this.muxerManager = muxerManager;
        this.sourceOption = sourceOption;
        this.mediaProjection = mediaProjection;
        this.sampleRateHz = sampleRateHz;
        this.bitrateBps = bitrateBps;
    }

    @SuppressLint("MissingPermission")
    public boolean start(Callback callback) {
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBufSize = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat);
        if (minBufSize <= 0) {
            minBufSize = sampleRateHz / 5 * 2;
        }
        int bufferSize = Math.max(minBufSize * 2, 8192);

        try {
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(channelConfig)
                    .build();

            if (sourceOption == AudioSourceOption.INTERNAL) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                    AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build();
                    audioRecord = new AudioRecord.Builder()
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(bufferSize)
                            .setAudioPlaybackCaptureConfig(config)
                            .build();
                } else {
                    if (callback != null) {
                        callback.onAudioWarning("Internal audio capture requires Android 10+. Recording video only.");
                    }
                    muxerManager.disableAudioTrack();
                    return false;
                }
            } else {
                audioRecord = new AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .build();
                preferExternalMicIfAvailable(audioRecord);
            }

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                releaseAudioRecordOnly();
                if (callback != null) {
                    callback.onAudioWarning("Audio capture unavailable on this device. Recording video only.");
                }
                muxerManager.disableAudioTrack();
                return false;
            }

            MediaFormat encFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateHz, 1);
            encFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps);
            encFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);

            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            audioEncoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();

            audioRecord.startRecording();
            running = true;
            startTimeUs = System.nanoTime() / 1000L;

            final int readChunkSize = bufferSize / 2;
            captureThread = new Thread(() -> runCaptureLoop(readChunkSize), "FrameCut-AudioCapture");
            captureThread.start();
            return true;
        } catch (Throwable t) {
            releaseResources();
            if (callback != null) {
                callback.onAudioWarning("Audio initialization failed (" + t.getMessage() + "). Recording video only.");
            }
            muxerManager.disableAudioTrack();
            return false;
        }
    }

    private void preferExternalMicIfAvailable(AudioRecord record) {
        AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        AudioDeviceInfo[] inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS);
        AudioDeviceInfo preferred = null;
        for (AudioDeviceInfo info : inputs) {
            int type = info.getType();
            if (type == AudioDeviceInfo.TYPE_USB_HEADSET
                    || type == AudioDeviceInfo.TYPE_USB_DEVICE
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                preferred = info;
                break;
            }
        }
        if (preferred != null) {
            record.setPreferredDevice(preferred);
        }
    }

    public void setPaused(boolean pause) {
        if (this.paused == pause) return;
        long nowUs = System.nanoTime() / 1000L;
        if (pause) {
            pauseStartedUs = nowUs;
        } else if (pauseStartedUs > 0L) {
            totalPausedUs += Math.max(0L, nowUs - pauseStartedUs);
            pauseStartedUs = 0L;
        }
        this.paused = pause;
    }

    public void stop() {
        running = false;
        if (captureThread != null) {
            try {
                captureThread.join(1500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }
        releaseResources();
    }

    private void runCaptureLoop(int readChunkSize) {
        byte[] pcmBuffer = new byte[readChunkSize];
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while (running) {
            int bytesRead = audioRecord != null ? audioRecord.read(pcmBuffer, 0, pcmBuffer.length) : 0;
            if (paused) {
                drainEncoder(info, false);
                continue;
            }
            if (bytesRead > 0 && audioEncoder != null) {
                int inIdx = audioEncoder.dequeueInputBuffer(10_000L);
                if (inIdx >= 0) {
                    ByteBuffer inBuf = audioEncoder.getInputBuffer(inIdx);
                    if (inBuf != null) {
                        inBuf.clear();
                        int writeLen = Math.min(bytesRead, inBuf.remaining());
                        inBuf.put(pcmBuffer, 0, writeLen);
                        long ptsUs = Math.max(0L, (System.nanoTime() / 1000L) - startTimeUs - totalPausedUs);
                        audioEncoder.queueInputBuffer(inIdx, 0, writeLen, ptsUs, 0);
                    }
                }
            }
            drainEncoder(info, false);
        }

        if (audioEncoder != null) {
            int inIdx = audioEncoder.dequeueInputBuffer(10_000L);
            if (inIdx >= 0) {
                long ptsUs = Math.max(0L, (System.nanoTime() / 1000L) - startTimeUs - totalPausedUs);
                audioEncoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            drainEncoder(info, true);
        }
    }

    private void drainEncoder(MediaCodec.BufferInfo info, boolean waitForEos) {
        if (audioEncoder == null) return;
        int attempts = 0;
        while (true) {
            int outIdx = audioEncoder.dequeueOutputBuffer(info, waitForEos ? 10_000L : 0L);
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!waitForEos || attempts++ > 25) break;
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (audioTrackIndex < 0) {
                    audioTrackIndex = muxerManager.addAudioTrack(audioEncoder.getOutputFormat());
                }
            } else if (outIdx >= 0) {
                ByteBuffer outBuf = audioEncoder.getOutputBuffer(outIdx);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0;
                }
                if (outBuf != null && info.size > 0 && audioTrackIndex >= 0) {
                    outBuf.position(info.offset);
                    outBuf.limit(info.offset + info.size);
                    muxerManager.writeSampleData(audioTrackIndex, outBuf, info);
                }
                audioEncoder.releaseOutputBuffer(outIdx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    private void releaseAudioRecordOnly() {
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {
            }
            try {
                audioRecord.release();
            } catch (Exception ignored) {
            }
            audioRecord = null;
        }
    }

    private void releaseResources() {
        releaseAudioRecordOnly();
        if (audioEncoder != null) {
            try {
                audioEncoder.stop();
            } catch (Exception ignored) {
            }
            try {
                audioEncoder.release();
            } catch (Exception ignored) {
            }
            audioEncoder = null;
        }
    }
}
