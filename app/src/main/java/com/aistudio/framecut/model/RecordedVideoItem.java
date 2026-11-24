package com.aistudio.framecut.model;

import android.net.Uri;

public final class RecordedVideoItem {
    public final long id;
    public final Uri contentUri;
    public final String displayName;
    public final int width;
    public final int height;
    public final long durationMs;
    public final long sizeBytes;
    public final long dateAddedSec;
    public final String mimeType;

    public RecordedVideoItem(
            long id,
            Uri contentUri,
            String displayName,
            int width,
            int height,
            long durationMs,
            long sizeBytes,
            long dateAddedSec,
            String mimeType) {
        this.id = id;
        this.contentUri = contentUri;
        this.displayName = displayName;
        this.width = width;
        this.height = height;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.dateAddedSec = dateAddedSec;
        this.mimeType = mimeType;
    }
}
