package com.aistudio.framecut.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;
import com.aistudio.framecut.model.RecorderEnums.BitrateModeOption;
import com.aistudio.framecut.model.RecorderEnums.ColorDepthOption;
import com.aistudio.framecut.model.RecorderEnums.ColorRangeOption;
import com.aistudio.framecut.model.RecordingSettings;
import com.aistudio.framecut.muxer.MediaMuxerManager;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class VideoEncoder {
    private final MediaMuxerManager muxerManager;
    private MediaCodec codec;
    private Surface inputSurface;
    private Thread drainThread;
    private int videoTrackIndex = -1;
    private String activeCodecLabel = "H.265/HEVC";
    private volatile boolean running = false;

    public VideoEncoder(MediaMuxerManager muxerManager) {
        this.muxerManager = muxerManager;
    }

    public Surface start(int width, int height, RecordingSettings settings) throws IOException {
        int evenW = (width % 2 == 0) ? width : Math.max(2, width - 1);
        int evenH = (height % 2 == 0) ? height : Math.max(2, height - 1);

        try {
            codec = configureCodec(MediaFormat.MIMETYPE_VIDEO_HEVC, evenW, evenH, settings);
            activeCodecLabel = "H.265/HEVC";
        } catch (Exception hevcError) {
            if (codec != null) {
                try { codec.release(); } catch (Exception ignored) {}
                codec = null;
            }
            codec = configureCodec(MediaFormat.MIMETYPE_VIDEO_AVC, evenW, evenH, settings);
            activeCodecLabel = "H.264/AVC";
        }

        inputSurface = codec.createInputSurface();
        codec.start();
        running = true;

        drainThread = new Thread(this::drainLoop, "FrameCut-VideoEncoder");
        drainThread.start();
        return inputSurface;
    }

    public String getActiveCodecLabel() {
        return activeCodecLabel;
    }

    public void stop() {
        running = false;
        if (codec != null) {
            try {
                codec.signalEndOfInputStream();
            } catch (Exception ignored) {
            }
        }
        if (drainThread != null) {
            try {
                drainThread.join(2500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            drainThread = null;
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignored) {}
            try { codec.release(); } catch (Exception ignored) {}
            codec = null;
        }
    }

    private MediaCodec configureCodec(String mime, int width, int height, RecordingSettings settings) throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, settings.getEffectiveVideoBitrateBps());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, settings.getEffectiveVideoFps());

        int iFrameInterval = Math.max(1, Math.round(settings.getEffectiveGopSeconds()));
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);

        int bitrateMode = settings.bitrateMode == BitrateModeOption.CQ
                ? MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ
                : MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, bitrateMode);

        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709);
        format.setInteger(
                MediaFormat.KEY_COLOR_RANGE,
                settings.colorRange == ColorRangeOption.FULL
                        ? MediaFormat.COLOR_RANGE_FULL
                        : MediaFormat.COLOR_RANGE_LIMITED
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && settings.colorDepth == ColorDepthOption.BIT_10
                && MediaFormat.MIMETYPE_VIDEO_HEVC.equals(mime)) {
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10);
        }

        MediaCodec mc = MediaCodec.createEncoderByType(mime);
        mc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return mc;
    }

    private void drainLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (running || codec != null) {
            if (codec == null) break;
            int outIdx;
            try {
                outIdx = codec.dequeueOutputBuffer(info, 10_000L);
            } catch (Exception e) {
                break;
            }
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!running) break;
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (videoTrackIndex < 0) {
                    videoTrackIndex = muxerManager.addVideoTrack(codec.getOutputFormat());
                }
            } else if (outIdx >= 0) {
                ByteBuffer buf = codec.getOutputBuffer(outIdx);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0;
                }
                if (buf != null && info.size > 0 && videoTrackIndex >= 0) {
                    buf.position(info.offset);
                    buf.limit(info.offset + info.size);
                    muxerManager.writeSampleData(videoTrackIndex, buf, info);
                }
                codec.releaseOutputBuffer(outIdx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }
}
