package com.aistudio.framecut.crop;

import android.net.Uri;
import com.aistudio.framecut.model.NormalizedRegion;

public final class CropModels {
    private CropModels() {}

    public enum CropMediaType {
        PHOTO,
        VIDEO
    }

    public enum CropAspectPreset {
        FREE("Free", null),
        RATIO_16_9("16:9", 16f / 9f),
        RATIO_9_16("9:16", 9f / 16f),
        RATIO_4_3("4:3", 4f / 3f),
        RATIO_3_4("3:4", 3f / 4f),
        RATIO_1_1("1:1", 1f);

        public final String label;
        public final Float aspectRatio;

        CropAspectPreset(String label, Float aspectRatio) {
            this.label = label;
            this.aspectRatio = aspectRatio;
        }
    }

    public enum PhotoFallbackFormat {
        JPEG("JPEG", "image/jpeg", ".jpg"),
        PNG("PNG", "image/png", ".png"),
        WEBP("WebP", "image/webp", ".webp");

        public final String label;
        public final String mimeType;
        public final String extension;

        PhotoFallbackFormat(String label, String mimeType, String extension) {
            this.label = label;
            this.mimeType = mimeType;
            this.extension = extension;
        }
    }

    public enum VideoFallbackFormat {
        MP4_H264_AAC("MP4 (H.264 + AAC)", "video/avc", ".mp4"),
        MP4_HEVC_AAC("MP4 (HEVC + AAC)", "video/hevc", ".mp4");

        public final String label;
        public final String videoMime;
        public final String extension;

        VideoFallbackFormat(String label, String videoMime, String extension) {
            this.label = label;
            this.videoMime = videoMime;
            this.extension = extension;
        }
    }

    public static final class MediaInspectionResult {
        public final Uri uri;
        public final CropMediaType mediaType;
        public final String displayName;
        public final String mimeType;
        public final int displayWidth;
        public final int displayHeight;
        public final int rotationDegrees;
        public final long sizeBytes;
        public final long durationMs;
        public final int bitrateBps;
        public final int fps;
        public final boolean hasAudio;
        public final boolean isHdr;

        public MediaInspectionResult(
                Uri uri,
                CropMediaType mediaType,
                String displayName,
                String mimeType,
                int displayWidth,
                int displayHeight,
                int rotationDegrees,
                long sizeBytes,
                long durationMs,
                int bitrateBps,
                int fps,
                boolean hasAudio,
                boolean isHdr) {
            this.uri = uri;
            this.mediaType = mediaType;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.displayWidth = displayWidth;
            this.displayHeight = displayHeight;
            this.rotationDegrees = rotationDegrees;
            this.sizeBytes = sizeBytes;
            this.durationMs = durationMs;
            this.bitrateBps = bitrateBps;
            this.fps = fps;
            this.hasAudio = hasAudio;
            this.isHdr = isHdr;
        }
    }

    public static final class CropPixelRect {
        public final int left;
        public final int top;
        public final int width;
        public final int height;

        public CropPixelRect(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }

    public static final class CropExportOptions {
        public final NormalizedRegion normalizedRegion;
        public final PhotoFallbackFormat photoFallbackFormat;
        public final int photoQuality;
        public final VideoFallbackFormat videoFallbackFormat;
        public final int videoBitrateMbps;
        public final int videoFps;

        public CropExportOptions(
                NormalizedRegion normalizedRegion,
                PhotoFallbackFormat photoFallbackFormat,
                int photoQuality,
                VideoFallbackFormat videoFallbackFormat,
                int videoBitrateMbps,
                int videoFps) {
            this.normalizedRegion = normalizedRegion;
            this.photoFallbackFormat = photoFallbackFormat;
            this.photoQuality = photoQuality;
            this.videoFallbackFormat = videoFallbackFormat;
            this.videoBitrateMbps = videoBitrateMbps;
            this.videoFps = videoFps;
        }
    }

    public static final class CropExecutionResult {
        public final boolean success;
        public final Uri outputUri;
        public final String outputName;
        public final String modeUsedLabel;
        public final String errorMessage;

        public CropExecutionResult(
                boolean success,
                Uri outputUri,
                String outputName,
                String modeUsedLabel,
                String errorMessage) {
            this.success = success;
            this.outputUri = outputUri;
            this.outputName = outputName;
            this.modeUsedLabel = modeUsedLabel;
            this.errorMessage = errorMessage;
        }
    }

    public interface ProgressListener {
        void onProgress(int percent, String statusText);
        boolean isCancelled();
    }
}
