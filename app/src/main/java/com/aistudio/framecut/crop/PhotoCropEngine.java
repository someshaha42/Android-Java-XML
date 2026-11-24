package com.aistudio.framecut.crop;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import com.aistudio.framecut.crop.CropModels.CropExecutionResult;
import com.aistudio.framecut.crop.CropModels.CropExportOptions;
import com.aistudio.framecut.crop.CropModels.CropPixelRect;
import com.aistudio.framecut.crop.CropModels.MediaInspectionResult;
import com.aistudio.framecut.crop.CropModels.PhotoFallbackFormat;
import com.aistudio.framecut.crop.CropModels.ProgressListener;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class PhotoCropEngine {
    private PhotoCropEngine() {}

    public static CropExecutionResult execute(
            Context context,
            MediaInspectionResult inspection,
            CropExportOptions options,
            ProgressListener listener) {
        try {
            if (listener != null) listener.onProgress(10, "Preparing photo crop…");
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

            if (CropCoordinateMapper.isFullFrame(options.normalizedRegion)) {
                String ext = guessExtension(inspection.mimeType);
                String outName = "FrameCut_Crop_" + stamp + ext;
                Uri outUri = createImageMediaStoreEntry(context, outName, inspection.mimeType);
                if (outUri == null) {
                    return new CropExecutionResult(false, null, null, null, "Could not create MediaStore output.");
                }
                try (InputStream in = context.getContentResolver().openInputStream(inspection.uri);
                     OutputStream out = context.getContentResolver().openOutputStream(outUri)) {
                    if (in == null || out == null) {
                        return new CropExecutionResult(false, null, null, null, "Failed to open stream.");
                    }
                    byte[] buf = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buf)) > 0) {
                        if (listener != null && listener.isCancelled()) {
                            context.getContentResolver().delete(outUri, null, null);
                            return new CropExecutionResult(false, null, null, null, "Cancelled.");
                        }
                        out.write(buf, 0, read);
                    }
                    out.flush();
                }
                finalizePending(context, outUri);
                if (listener != null) listener.onProgress(100, "Copied 1:1 original photo");
                return new CropExecutionResult(true, outUri, outName, "1:1 Original Copy (Lossless)", null);
            }

            if (listener != null) listener.onProgress(25, "Decoding source bitmap…");
            Bitmap raw;
            try (InputStream in = context.getContentResolver().openInputStream(inspection.uri)) {
                raw = BitmapFactory.decodeStream(in);
            }
            if (raw == null) {
                return new CropExecutionResult(false, null, null, null, "Unable to decode source photo.");
            }

            Bitmap oriented = raw;
            if (inspection.rotationDegrees != 0) {
                Matrix m = new Matrix();
                m.postRotate(inspection.rotationDegrees);
                oriented = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);
                if (oriented != raw) raw.recycle();
            }

            if (listener != null && listener.isCancelled()) {
                oriented.recycle();
                return new CropExecutionResult(false, null, null, null, "Cancelled.");
            }

            if (listener != null) listener.onProgress(55, "Cropping region…");
            CropPixelRect rect = CropCoordinateMapper.toPixelRect(
                    options.normalizedRegion,
                    oriented.getWidth(),
                    oriented.getHeight(),
                    false
            );
            Bitmap cropped = Bitmap.createBitmap(oriented, rect.left, rect.top, rect.width, rect.height);
            if (cropped != oriented) oriented.recycle();

            PhotoFallbackFormat fmt = options.photoFallbackFormat;
            String outName = "FrameCut_Crop_" + stamp + fmt.extension;
            Uri outUri = createImageMediaStoreEntry(context, outName, fmt.mimeType);
            if (outUri == null) {
                cropped.recycle();
                return new CropExecutionResult(false, null, null, null, "Could not create MediaStore output.");
            }

            if (listener != null) listener.onProgress(80, "Encoding " + fmt.label + "…");
            Bitmap.CompressFormat compressFormat;
            if (fmt == PhotoFallbackFormat.PNG) {
                compressFormat = Bitmap.CompressFormat.PNG;
            } else if (fmt == PhotoFallbackFormat.WEBP) {
                compressFormat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? Bitmap.CompressFormat.WEBP_LOSSY
                        : Bitmap.CompressFormat.WEBP;
            } else {
                compressFormat = Bitmap.CompressFormat.JPEG;
            }

            try (OutputStream out = context.getContentResolver().openOutputStream(outUri)) {
                if (out == null) {
                    cropped.recycle();
                    return new CropExecutionResult(false, null, null, null, "Failed to open output stream.");
                }
                cropped.compress(compressFormat, options.photoQuality, out);
                out.flush();
            } finally {
                cropped.recycle();
            }

            finalizePending(context, outUri);
            if (listener != null) listener.onProgress(100, "Saved cropped photo");
            return new CropExecutionResult(
                    true,
                    outUri,
                    outName,
                    fmt.label + " (" + rect.width + "x" + rect.height + ")",
                    null
            );
        } catch (Throwable t) {
            return new CropExecutionResult(false, null, null, null, t.getMessage());
        }
    }

    private static String guessExtension(String mimeType) {
        if (mimeType == null) return ".jpg";
        if (mimeType.contains("png")) return ".png";
        if (mimeType.contains("webp")) return ".webp";
        return ".jpg";
    }

    private static Uri createImageMediaStoreEntry(Context context, String name, String mime) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, mime);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FrameCut");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private static void finalizePending(Context context, Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
        }
    }
}
