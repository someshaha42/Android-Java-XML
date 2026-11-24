package com.aistudio.framecut.muxer;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class MediaMuxerManager {
    private final Object lock = new Object();
    private final File tempOutputFile;
    private MediaMuxer muxer;

    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private boolean expectAudioTrack;
    private boolean muxerStarted = false;
    private boolean released = false;

    public MediaMuxerManager(File tempOutputFile, boolean expectAudioTrack) throws IOException {
        this.tempOutputFile = tempOutputFile;
        this.expectAudioTrack = expectAudioTrack;
        this.muxer = new MediaMuxer(tempOutputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void disableAudioTrack() {
        synchronized (lock) {
            expectAudioTrack = false;
            maybeStartLocked();
        }
    }

    public int addVideoTrack(MediaFormat format) {
        synchronized (lock) {
            if (released) return -1;
            videoTrackIndex = muxer.addTrack(format);
            maybeStartLocked();
            lock.notifyAll();
            return videoTrackIndex;
        }
    }

    public int addAudioTrack(MediaFormat format) {
        synchronized (lock) {
            if (released) return -1;
            audioTrackIndex = muxer.addTrack(format);
            maybeStartLocked();
            lock.notifyAll();
            return audioTrackIndex;
        }
    }

    private void maybeStartLocked() {
        if (muxerStarted) return;
        boolean videoReady = videoTrackIndex >= 0;
        boolean audioReady = !expectAudioTrack || audioTrackIndex >= 0;
        if (videoReady && audioReady) {
            muxer.start();
            muxerStarted = true;
        }
    }

    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo info) {
        if (trackIndex < 0) return;
        synchronized (lock) {
            while (!muxerStarted && !released) {
                try {
                    lock.wait(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (muxerStarted && !released) {
                muxer.writeSampleData(trackIndex, byteBuf, info);
            }
        }
    }

    public Uri stopAndPublishToMediaStore(Context context, String displayName, String mimeType) {
        synchronized (lock) {
            released = true;
            lock.notifyAll();
            if (muxer != null) {
                try {
                    if (muxerStarted) muxer.stop();
                } catch (Exception ignored) {
                }
                try {
                    muxer.release();
                } catch (Exception ignored) {
                }
                muxer = null;
            }
        }
        return publishVideoFileToMediaStore(context, tempOutputFile, displayName, mimeType);
    }

    public static Uri publishVideoFileToMediaStore(
            Context context,
            File sourceFile,
            String displayName,
            String mimeType) {
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0) {
            return null;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Video.Media.MIME_TYPE, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FrameCut");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }

        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Uri itemUri = context.getContentResolver().insert(collection, values);
        if (itemUri == null) return null;

        try (FileInputStream in = new FileInputStream(sourceFile);
             OutputStream out = context.getContentResolver().openOutputStream(itemUri)) {
            if (out == null) return null;
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
        } catch (IOException e) {
            context.getContentResolver().delete(itemUri, null, null);
            return null;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            sourceFile.delete();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Video.Media.IS_PENDING, 0);
            context.getContentResolver().update(itemUri, done, null, null);
        }
        return itemUri;
    }
}
