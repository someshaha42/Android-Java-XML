package com.aistudio.framecut.crop;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.view.Surface;
import com.aistudio.framecut.crop.CropModels.CropExecutionResult;
import com.aistudio.framecut.crop.CropModels.CropExportOptions;
import com.aistudio.framecut.crop.CropModels.CropPixelRect;
import com.aistudio.framecut.crop.CropModels.MediaInspectionResult;
import com.aistudio.framecut.crop.CropModels.ProgressListener;
import com.aistudio.framecut.encoder.GlRegionCropper;
import com.aistudio.framecut.muxer.MediaMuxerManager;
import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class VideoCropEngine {
    private VideoCropEngine() {}

    public static CropExecutionResult execute(
            Context context,
            MediaInspectionResult inspection,
            CropExportOptions options,
            ProgressListener listener) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String outName = "FrameCut_Crop_" + stamp + ".mp4";
        File tempOut = new File(context.getCacheDir(), outName);

        if (CropCoordinateMapper.isFullFrame(options.normalizedRegion)) {
            return remuxLossless(context, inspection, tempOut, outName, listener);
        }
        return transcodeCropped(context, inspection, options, tempOut, outName, listener);
    }

    private static CropExecutionResult remuxLossless(
            Context context,
            MediaInspectionResult inspection,
            File tempOut,
            String outName,
            ProgressListener listener) {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            if (listener != null) listener.onProgress(10, "Copying tracks 1:1 (Lossless)…");
            extractor.setDataSource(context, inspection.uri, null);
            muxer = new MediaMuxer(tempOut.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if (inspection.rotationDegrees != 0) {
                muxer.setOrientationHint(inspection.rotationDegrees);
            }

            int trackCount = extractor.getTrackCount();
            int[] dstTrackMap = new int[trackCount];
            for (int i = 0; i < trackCount; i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                dstTrackMap[i] = muxer.addTrack(fmt);
                extractor.selectTrack(i);
            }
            muxer.start();

            ByteBuffer buffer = ByteBuffer.allocate(512 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long durationUs = Math.max(1L, inspection.durationMs * 1000L);

            while (true) {
                if (listener != null && listener.isCancelled()) {
                    throw new RuntimeException("Cancelled.");
                }
                int trackIdx = extractor.getSampleTrackIndex();
                if (trackIdx < 0) break;
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = extractor.getSampleFlags();
                muxer.writeSampleData(dstTrackMap[trackIdx], buffer, info);
                if (listener != null) {
                    int pct = (int) Math.min(95L, 10L + (info.presentationTimeUs * 85L / durationUs));
                    listener.onProgress(pct, "Remuxing 1:1 original tracks…");
                }
                extractor.advance();
            }

            muxer.stop();
            muxer.release();
            muxer = null;

            Uri uri = MediaMuxerManager.publishVideoFileToMediaStore(context, tempOut, outName, "video/mp4");
            if (uri == null) {
                return new CropExecutionResult(false, null, null, null, "MediaStore publish failed.");
            }
            if (listener != null) listener.onProgress(100, "Saved 1:1 copy");
            return new CropExecutionResult(true, uri, outName, "1:1 Track Remux (Lossless)", null);
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            tempOut.delete();
            return new CropExecutionResult(false, null, null, null, t.getMessage());
        } finally {
            extractor.release();
            if (muxer != null) {
                try { muxer.release(); } catch (Exception ignored) {}
            }
        }
    }

    private static CropExecutionResult transcodeCropped(
            Context context,
            MediaInspectionResult inspection,
            CropExportOptions options,
            File tempOut,
            String outName,
            ProgressListener listener) {
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        GlRegionCropper glCropper = null;
        MediaMuxerManager muxerManager = null;

        try {
            if (listener != null) listener.onProgress(8, "Initializing GPU video cropper…");
            videoExtractor.setDataSource(context, inspection.uri, null);
            int srcVideoTrack = -1;
            MediaFormat srcVideoFormat = null;
            int srcAudioTrack = -1;
            MediaFormat srcAudioFormat = null;

            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                MediaFormat fmt = videoExtractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/") && srcVideoTrack < 0) {
                    srcVideoTrack = i;
                    srcVideoFormat = fmt;
                } else if (mime != null && mime.startsWith("audio/") && srcAudioTrack < 0) {
                    srcAudioTrack = i;
                    srcAudioFormat = fmt;
                }
            }
            if (srcVideoTrack < 0 || srcVideoFormat == null) {
                return new CropExecutionResult(false, null, null, null, "No video track found.");
            }
            videoExtractor.selectTrack(srcVideoTrack);

            CropPixelRect rect = CropCoordinateMapper.toPixelRect(
                    options.normalizedRegion,
                    inspection.displayWidth,
                    inspection.displayHeight,
                    true
            );

            boolean hasAudio = srcAudioTrack >= 0 && srcAudioFormat != null;
            muxerManager = new MediaMuxerManager(tempOut, hasAudio);
            int muxAudioTrack = hasAudio ? muxerManager.addAudioTrack(srcAudioFormat) : -1;

            String outMime = options.videoFallbackFormat.videoMime;
            MediaFormat encFmt = MediaFormat.createVideoFormat(outMime, rect.width, rect.height);
            encFmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            encFmt.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(1, options.videoBitrateMbps) * 1_000_000);
            encFmt.setInteger(MediaFormat.KEY_FRAME_RATE, Math.max(1, options.videoFps));
            encFmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            try {
                encoder = MediaCodec.createEncoderByType(outMime);
                encoder.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            } catch (Exception fallbackErr) {
                if (encoder != null) {
                    encoder.release();
                }
                outMime = MediaFormat.MIMETYPE_VIDEO_AVC;
                encFmt = MediaFormat.createVideoFormat(outMime, rect.width, rect.height);
                encFmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                encFmt.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(1, options.videoBitrateMbps) * 1_000_000);
                encFmt.setInteger(MediaFormat.KEY_FRAME_RATE, Math.max(1, options.videoFps));
                encFmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                encoder = MediaCodec.createEncoderByType(outMime);
                encoder.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            }

            Surface encSurface = encoder.createInputSurface();
            encoder.start();

            glCropper = new GlRegionCropper(
                    encSurface,
                    inspection.displayWidth,
                    inspection.displayHeight,
                    rect.width,
                    rect.height,
                    options.normalizedRegion
            );

            String decMime = srcVideoFormat.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(decMime);
            decoder.configure(srcVideoFormat, glCropper.getInputSurface(), null, 0);
            decoder.start();

            if (hasAudio) {
                audioExtractor.setDataSource(context, inspection.uri, null);
                audioExtractor.selectTrack(srcAudioTrack);
                ByteBuffer aBuf = ByteBuffer.allocate(128 * 1024);
                MediaCodec.BufferInfo aInfo = new MediaCodec.BufferInfo();
                while (true) {
                    int sz = audioExtractor.readSampleData(aBuf, 0);
                    if (sz < 0) break;
                    aInfo.offset = 0;
                    aInfo.size = sz;
                    aInfo.presentationTimeUs = audioExtractor.getSampleTime();
                    aInfo.flags = audioExtractor.getSampleFlags();
                    muxerManager.writeSampleData(muxAudioTrack, aBuf, aInfo);
                    audioExtractor.advance();
                }
            }

            boolean inputDone = false;
            boolean decoderDone = false;
            boolean encoderDone = false;
            int muxVideoTrack = -1;
            long durationUs = Math.max(1L, inspection.durationMs * 1000L);
            MediaCodec.BufferInfo decInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo encInfo = new MediaCodec.BufferInfo();

            while (!encoderDone) {
                if (listener != null && listener.isCancelled()) {
                    throw new RuntimeException("Cancelled.");
                }
                if (!inputDone) {
                    int inIdx = decoder.dequeueInputBuffer(5_000L);
                    if (inIdx >= 0) {
                        ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                        int sampleSize = inBuf != null ? videoExtractor.readSampleData(inBuf, 0) : -1;
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long pts = videoExtractor.getSampleTime();
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, pts, 0);
                            videoExtractor.advance();
                        }
                    }
                }

                if (!decoderDone) {
                    int decOut = decoder.dequeueOutputBuffer(decInfo, 5_000L);
                    if (decOut >= 0) {
                        boolean render = decInfo.size > 0;
                        decoder.releaseOutputBuffer(decOut, render);
                        if (render) {
                            glCropper.awaitAndRenderNextFrame(500L, decInfo.presentationTimeUs * 1000L);
                            if (listener != null) {
                                int pct = (int) Math.min(95L, 12L + (decInfo.presentationTimeUs * 82L / durationUs));
                                listener.onProgress(pct, "GPU cropping video frames…");
                            }
                        }
                        if ((decInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderDone = true;
                            encoder.signalEndOfInputStream();
                        }
                    }
                }

                int encOut = encoder.dequeueOutputBuffer(encInfo, 5_000L);
                if (encOut == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxVideoTrack < 0) {
                        muxVideoTrack = muxerManager.addVideoTrack(encoder.getOutputFormat());
                    }
                } else if (encOut >= 0) {
                    ByteBuffer outBuf = encoder.getOutputBuffer(encOut);
                    if ((encInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        encInfo.size = 0;
                    }
                    if (outBuf != null && encInfo.size > 0 && muxVideoTrack >= 0) {
                        outBuf.position(encInfo.offset);
                        outBuf.limit(encInfo.offset + encInfo.size);
                        muxerManager.writeSampleData(muxVideoTrack, outBuf, encInfo);
                    }
                    encoder.releaseOutputBuffer(encOut, false);
                    if ((encInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderDone = true;
                    }
                }
            }

            Uri uri = muxerManager.stopAndPublishToMediaStore(context, outName, "video/mp4");
            muxerManager = null;
            if (uri == null) {
                return new CropExecutionResult(false, null, null, null, "Could not publish cropped MP4.");
            }
            if (listener != null) listener.onProgress(100, "Saved cropped MP4");
            return new CropExecutionResult(
                    true,
                    uri,
                    outName,
                    options.videoFallbackFormat.label + " (" + rect.width + "x" + rect.height + ")",
                    null
            );
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            tempOut.delete();
            return new CropExecutionResult(false, null, null, null, t.getMessage());
        } finally {
            if (decoder != null) {
                try { decoder.stop(); } catch (Exception ignored) {}
                try { decoder.release(); } catch (Exception ignored) {}
            }
            if (encoder != null) {
                try { encoder.stop(); } catch (Exception ignored) {}
                try { encoder.release(); } catch (Exception ignored) {}
            }
            if (glCropper != null) {
                glCropper.release();
            }
            videoExtractor.release();
            audioExtractor.release();
        }
    }
}
