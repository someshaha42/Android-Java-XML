package com.aistudio.framecut.crop;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.exifinterface.media.ExifInterface;
import com.aistudio.framecut.crop.CropModels.CropMediaType;
import com.aistudio.framecut.crop.CropModels.MediaInspectionResult;
import java.io.InputStream;

public final class MediaCropInspector {
    private MediaCropInspector() {}

    public static MediaInspectionResult inspect(Context context, Uri uri, CropMediaType mediaType) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        String name = "media_file";
        long size = 0L;

        try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIdx = c.getColumnIndex(OpenableColumns.SIZE);
                if (nameIdx >= 0 && c.getString(nameIdx) != null) name = c.getString(nameIdx);
                if (sizeIdx >= 0) size = c.getLong(sizeIdx);
            }
        }
        String mime = resolver.getType(uri);

        if (mediaType == CropMediaType.PHOTO) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, opts);
            }
            int rotation = 0;
            try (InputStream is = resolver.openInputStream(uri)) {
                if (is != null) {
                    ExifInterface exif = new ExifInterface(is);
                    int orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                    );
                    if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                    else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                    else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
                }
            } catch (Exception ignored) {
            }

            int dispW = (rotation == 90 || rotation == 270) ? opts.outHeight : opts.outWidth;
            int dispH = (rotation == 90 || rotation == 270) ? opts.outWidth : opts.outHeight;
            return new MediaInspectionResult(
                    uri,
                    CropMediaType.PHOTO,
                    name,
                    mime != null ? mime : (opts.outMimeType != null ? opts.outMimeType : "image/jpeg"),
                    Math.max(1, dispW),
                    Math.max(1, dispH),
                    rotation,
                    size,
                    0L,
                    0,
                    0,
                    false,
                    false
            );
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int width = 1920;
        int height = 1080;
        int rotation = 0;
        long durationMs = 0L;
        int bitrate = 0;
        int fps = 30;
        boolean hasAudio = false;
        boolean isHdr = false;

        try {
            retriever.setDataSource(context, uri);
            String wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String brStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            String audioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);

            if (wStr != null) width = Integer.parseInt(wStr);
            if (hStr != null) height = Integer.parseInt(hStr);
            if (rotStr != null) rotation = Integer.parseInt(rotStr);
            if (durStr != null) durationMs = Long.parseLong(durStr);
            if (brStr != null) bitrate = Integer.parseInt(brStr);
            hasAudio = "yes".equalsIgnoreCase(audioStr);
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, uri, null);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                String trackMime = fmt.getString(MediaFormat.KEY_MIME);
                if (trackMime != null && trackMime.startsWith("video/")) {
                    if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = fmt.getInteger(MediaFormat.KEY_FRAME_RATE);
                    }
                    if (fmt.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                        int ct = fmt.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                        isHdr = (ct == MediaFormat.COLOR_TRANSFER_ST2084 || ct == MediaFormat.COLOR_TRANSFER_HLG);
                    }
                    break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            extractor.release();
        }

        int dispW = (rotation == 90 || rotation == 270) ? height : width;
        int dispH = (rotation == 90 || rotation == 270) ? width : height;

        return new MediaInspectionResult(
                uri,
                CropMediaType.VIDEO,
                name,
                mime != null ? mime : "video/mp4",
                Math.max(2, dispW),
                Math.max(2, dispH),
                rotation,
                size,
                durationMs,
                bitrate,
                Math.max(1, fps),
                hasAudio,
                isHdr
        );
    }
}
