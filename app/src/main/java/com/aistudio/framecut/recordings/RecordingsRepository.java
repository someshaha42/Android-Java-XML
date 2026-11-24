package com.aistudio.framecut.recordings;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import com.aistudio.framecut.model.RecordedVideoItem;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RecordingsRepository {
    private final Context appContext;

    public RecordingsRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public List<RecordedVideoItem> loadAll() {
        List<RecordedVideoItem> list = new ArrayList<>();
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.MIME_TYPE
        };
        String selection = MediaStore.Video.Media.DISPLAY_NAME + " LIKE ?";
        String[] args = {"FrameCut_%"};
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        try (Cursor c = appContext.getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                sortOrder)) {
            if (c == null) return list;
            int idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int wCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH);
            int hCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT);
            int durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            int sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
            int dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
            int mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE);

            while (c.moveToNext()) {
                long id = c.getLong(idCol);
                Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                list.add(new RecordedVideoItem(
                        id,
                        uri,
                        c.getString(nameCol),
                        c.getInt(wCol),
                        c.getInt(hCol),
                        c.getLong(durCol),
                        c.getLong(sizeCol),
                        c.getLong(dateCol),
                        c.getString(mimeCol)
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public boolean delete(RecordedVideoItem item) {
        try {
            return appContext.getContentResolver().delete(item.contentUri, null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "0 KB";
        double kb = 1024.0;
        double mb = kb * 1024.0;
        double gb = mb * 1024.0;
        if (bytes >= gb) return String.format(Locale.US, "%.2f GB", bytes / gb);
        if (bytes >= mb) return String.format(Locale.US, "%.1f MB", bytes / mb);
        return Math.max(1, Math.round(bytes / kb)) + " KB";
    }

    public static String formatDuration(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long mins = totalSec / 60L;
        long secs = totalSec % 60L;
        return String.format(Locale.US, "%d:%02d", mins, secs);
    }

    public static String formatDate(long epochSec) {
        return new SimpleDateFormat("MMM d, HH:mm", Locale.US).format(new Date(epochSec * 1000L));
    }
}
