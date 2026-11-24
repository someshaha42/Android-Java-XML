package com.aistudio.framecut.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.aistudio.framecut.R;
import com.aistudio.framecut.crop.CropCoordinateMapper;
import com.aistudio.framecut.crop.CropModels.*;
import com.aistudio.framecut.crop.MediaCropInspector;
import com.aistudio.framecut.crop.PhotoCropEngine;
import com.aistudio.framecut.crop.VideoCropEngine;
import com.aistudio.framecut.databinding.ActivityCropToolBinding;
import com.aistudio.framecut.model.NormalizedRegion;
import com.aistudio.framecut.recordings.RecordingsRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CropToolActivity extends AppCompatActivity {
    private ActivityCropToolBinding binding;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private MediaInspectionResult currentInspection;
    private Bitmap previewBitmap;
    private PhotoFallbackFormat photoFallbackFormat = PhotoFallbackFormat.JPEG;
    private VideoFallbackFormat videoFallbackFormat = VideoFallbackFormat.MP4_H264_AAC;
    private int photoQuality = 95;
    private final AtomicBoolean cancelledFlag = new AtomicBoolean(false);
    private CropExecutionResult lastResult;

    private final ActivityResultLauncher<String[]> photoPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) loadMedia(uri, CropMediaType.PHOTO);
            });

    private final ActivityResultLauncher<String[]> videoPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) loadMedia(uri, CropMediaType.VIDEO);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCropToolBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbarCropTool.setNavigationOnClickListener(v -> finish());
        setupListeners();
    }

    private void setupListeners() {
        binding.btnPickPhoto.setOnClickListener(v -> photoPicker.launch(new String[]{"image/*"}));
        binding.btnPickVideo.setOnClickListener(v -> videoPicker.launch(new String[]{"video/*"}));

        binding.cropEditorView.setMinSpan(CropCoordinateMapper.MIN_CROP_SPAN);
        binding.cropEditorView.setOnRegionChangeListener(region -> updateCropReadout());

        binding.btnCropResetFull.setOnClickListener(v -> {
            binding.cropEditorView.setLockedAspectRatio(null);
            binding.cropEditorView.setRegion(new NormalizedRegion(0f, 0f, 1f, 1f));
            highlightRatioChip(binding.chipRatioFree);
            updateCropReadout();
        });

        binding.chipRatioFree.setOnClickListener(v -> applyPreset(null, binding.chipRatioFree));
        binding.chipRatio169.setOnClickListener(v -> applyPreset(16f / 9f, binding.chipRatio169));
        binding.chipRatio916.setOnClickListener(v -> applyPreset(9f / 16f, binding.chipRatio916));
        binding.chipRatio43.setOnClickListener(v -> applyPreset(4f / 3f, binding.chipRatio43));
        binding.chipRatio34.setOnClickListener(v -> applyPreset(3f / 4f, binding.chipRatio34));
        binding.chipRatio11.setOnClickListener(v -> applyPreset(1f, binding.chipRatio11));

        binding.chipPhotoJpeg.setOnClickListener(v -> {
            photoFallbackFormat = PhotoFallbackFormat.JPEG;
            highlightPhotoChip(binding.chipPhotoJpeg);
        });
        binding.chipPhotoPng.setOnClickListener(v -> {
            photoFallbackFormat = PhotoFallbackFormat.PNG;
            highlightPhotoChip(binding.chipPhotoPng);
        });
        binding.chipPhotoWebp.setOnClickListener(v -> {
            photoFallbackFormat = PhotoFallbackFormat.WEBP;
            highlightPhotoChip(binding.chipPhotoWebp);
        });

        binding.seekPhotoQuality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                photoQuality = Math.max(50, progress);
                binding.photoQualityLabel.setText("Quality: " + photoQuality + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        binding.chipVideoH264.setOnClickListener(v -> {
            videoFallbackFormat = VideoFallbackFormat.MP4_H264_AAC;
            highlightVideoChip(binding.chipVideoH264);
        });
        binding.chipVideoHevc.setOnClickListener(v -> {
            videoFallbackFormat = VideoFallbackFormat.MP4_HEVC_AAC;
            highlightVideoChip(binding.chipVideoHevc);
        });

        binding.btnExportCrop.setOnClickListener(v -> confirmAndExecuteCrop());
        binding.btnCancelExport.setOnClickListener(v -> cancelledFlag.set(true));
        binding.btnShareResult.setOnClickListener(v -> {
            if (lastResult != null && lastResult.outputUri != null) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType(currentInspection != null && currentInspection.mediaType == CropMediaType.PHOTO
                        ? "image/*" : "video/mp4");
                share.putExtra(Intent.EXTRA_STREAM, lastResult.outputUri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Share cropped media"));
            }
        });
    }

    private void loadMedia(Uri uri, CropMediaType type) {
        worker.execute(() -> {
            try {
                MediaInspectionResult insp = MediaCropInspector.inspect(this, uri, type);
                Bitmap bmp = loadPreviewBitmap(uri, type, insp.rotationDegrees);
                runOnUiThread(() -> {
                    if (binding == null) return;
                    if (previewBitmap != null && !previewBitmap.isRecycled()) {
                        previewBitmap.recycle();
                    }
                    previewBitmap = bmp;
                    currentInspection = insp;
                    lastResult = null;
                    binding.cardExportResult.setVisibility(View.GONE);
                    renderInspectedMedia();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (binding == null) return;
                    binding.cardInspection.setVisibility(View.VISIBLE);
                    binding.inspectTitle.setText("Unable to inspect media");
                    binding.inspectDetails.setText(e.getMessage());
                });
            }
        });
    }

    @Nullable
    private Bitmap loadPreviewBitmap(Uri uri, CropMediaType type, int rotationDeg) {
        try {
            if (type == CropMediaType.PHOTO) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                Bitmap raw;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    raw = BitmapFactory.decodeStream(is, null, opts);
                }
                if (raw != null && rotationDeg != 0) {
                    Matrix m = new Matrix();
                    m.postRotate(rotationDeg);
                    Bitmap rotated = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);
                    if (rotated != raw) raw.recycle();
                    return rotated;
                }
                return raw;
            }
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, uri);
                return retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            } finally {
                try { retriever.release(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void renderInspectedMedia() {
        if (currentInspection == null) return;
        binding.cardInspection.setVisibility(View.VISIBLE);
        binding.cardCropStage.setVisibility(View.VISIBLE);
        binding.cardFallbackConfig.setVisibility(View.VISIBLE);

        binding.inspectTitle.setText(currentInspection.displayName);
        StringBuilder details = new StringBuilder();
        details.append(currentInspection.displayWidth)
                .append("x")
                .append(currentInspection.displayHeight)
                .append(" · ")
                .append(RecordingsRepository.formatSize(currentInspection.sizeBytes));
        if (currentInspection.mediaType == CropMediaType.VIDEO) {
            details.append(" · ")
                    .append(RecordingsRepository.formatDuration(currentInspection.durationMs))
                    .append(" · ")
                    .append(currentInspection.fps)
                    .append(" fps");
            if (currentInspection.isHdr) details.append(" · HDR");
        } else if (currentInspection.rotationDegrees != 0) {
            details.append(" · EXIF ").append(currentInspection.rotationDegrees).append("°");
        }
        binding.inspectDetails.setText(details.toString());

        float aspect = (float) currentInspection.displayWidth / (float) Math.max(1, currentInspection.displayHeight);
        binding.cropEditorView.setPreviewBitmap(previewBitmap, aspect);
        binding.cropEditorView.setLockedAspectRatio(null);
        binding.cropEditorView.setRegion(new NormalizedRegion(0.08f, 0.08f, 0.92f, 0.92f));
        highlightRatioChip(binding.chipRatioFree);

        boolean isPhoto = currentInspection.mediaType == CropMediaType.PHOTO;
        binding.photoFallbackGroup.setVisibility(isPhoto ? View.VISIBLE : View.GONE);
        binding.videoFallbackGroup.setVisibility(isPhoto ? View.GONE : View.VISIBLE);
        updateCropReadout();
    }

    private void applyPreset(@Nullable Float ratio, TextView active) {
        highlightRatioChip(active);
        binding.cropEditorView.setLockedAspectRatio(ratio);
        if (ratio != null && currentInspection != null) {
            NormalizedRegion next = CropCoordinateMapper.applyAspectRatio(
                    binding.cropEditorView.getRegion(),
                    ratio,
                    currentInspection.displayWidth,
                    currentInspection.displayHeight
            );
            binding.cropEditorView.setRegion(next);
            updateCropReadout();
        }
    }

    private void updateCropReadout() {
        if (currentInspection == null) return;
        NormalizedRegion r = binding.cropEditorView.getRegion();
        boolean full = CropCoordinateMapper.isFullFrame(r);
        CropPixelRect px = CropCoordinateMapper.toPixelRect(
                r,
                currentInspection.displayWidth,
                currentInspection.displayHeight,
                currentInspection.mediaType == CropMediaType.VIDEO
        );
        binding.cropReadoutText.setText(String.format(
                Locale.US,
                "%dx%d px (%s)",
                px.width,
                px.height,
                full ? "1:1 Original Copy" : "Cropped Output"
        ));
    }

    private void confirmAndExecuteCrop() {
        if (currentInspection == null) return;
        NormalizedRegion reg = binding.cropEditorView.getRegion();
        boolean full = CropCoordinateMapper.isFullFrame(reg);
        String msg = full
                ? "Selection matches the full frame. FrameCut will copy the original source 1:1 without re-encoding."
                : "FrameCut will encode the cropped region using your selected output profile and save to MediaStore.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Export Media")
                .setMessage(msg)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Export", (d, w) -> startExport(reg))
                .show();
    }

    private void startExport(NormalizedRegion reg) {
        int bitrateMbps = parseIntOrDefault(String.valueOf(binding.inputCropVideoBitrate.getText()), 24);
        int fps = parseIntOrDefault(String.valueOf(binding.inputCropVideoFps.getText()),
                currentInspection.fps > 0 ? currentInspection.fps : 30);

        CropExportOptions options = new CropExportOptions(
                reg,
                photoFallbackFormat,
                photoQuality,
                videoFallbackFormat,
                Math.max(1, bitrateMbps),
                Math.max(1, fps)
        );

        cancelledFlag.set(false);
        binding.cardExportProgress.setVisibility(View.VISIBLE);
        binding.cardExportResult.setVisibility(View.GONE);
        binding.btnExportCrop.setEnabled(false);

        ProgressListener listener = new ProgressListener() {
            @Override
            public void onProgress(int percent, String statusText) {
                runOnUiThread(() -> {
                    if (binding == null) return;
                    binding.progressBar.setProgress(percent);
                    binding.progressLabel.setText(statusText + " (" + percent + "%)");
                });
            }

            @Override
            public boolean isCancelled() {
                return cancelledFlag.get();
            }
        };

        worker.execute(() -> {
            CropExecutionResult res = currentInspection.mediaType == CropMediaType.PHOTO
                    ? PhotoCropEngine.execute(this, currentInspection, options, listener)
                    : VideoCropEngine.execute(this, currentInspection, options, listener);

            runOnUiThread(() -> {
                if (binding == null) return;
                binding.btnExportCrop.setEnabled(true);
                binding.cardExportProgress.setVisibility(View.GONE);
                binding.cardExportResult.setVisibility(View.VISIBLE);
                lastResult = res;

                if (res.success) {
                    binding.resultTitle.setText("Saved: " + res.outputName);
                    binding.resultDetails.setText("Pipeline: " + res.modeUsedLabel);
                    binding.btnShareResult.setVisibility(View.VISIBLE);
                } else {
                    binding.resultTitle.setText("Export Failed");
                    binding.resultDetails.setText(res.errorMessage != null ? res.errorMessage : "Unknown error");
                    binding.btnShareResult.setVisibility(View.GONE);
                }
            });
        });
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void highlightRatioChip(TextView active) {
        TextView[] chips = {
                binding.chipRatioFree, binding.chipRatio169, binding.chipRatio916,
                binding.chipRatio43, binding.chipRatio34, binding.chipRatio11
        };
        for (TextView c : chips) {
            c.setBackgroundResource(c == active ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        }
    }

    private void highlightPhotoChip(TextView active) {
        TextView[] chips = {binding.chipPhotoJpeg, binding.chipPhotoPng, binding.chipPhotoWebp};
        for (TextView c : chips) {
            c.setBackgroundResource(c == active ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        }
    }

    private void highlightVideoChip(TextView active) {
        TextView[] chips = {binding.chipVideoH264, binding.chipVideoHevc};
        for (TextView c : chips) {
            c.setBackgroundResource(c == active ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        }
    }

    @Override
    protected void onDestroy() {
        cancelledFlag.set(true);
        worker.shutdown();
        if (previewBitmap != null && !previewBitmap.isRecycled()) {
            previewBitmap.recycle();
        }
        super.onDestroy();
    }
}
