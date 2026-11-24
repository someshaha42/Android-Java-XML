package com.aistudio.framecut.ui;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.MediaController;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.aistudio.framecut.databinding.ActivityRecordingsBinding;
import com.aistudio.framecut.databinding.DialogVideoPlayerBinding;
import com.aistudio.framecut.model.RecordedVideoItem;
import com.aistudio.framecut.recordings.RecordingsRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RecordingsActivity extends AppCompatActivity implements RecordingsAdapter.Listener {
    private ActivityRecordingsBinding binding;
    private RecordingsRepository repository;
    private RecordingsAdapter adapter;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new RecordingsRepository(this);
        adapter = new RecordingsAdapter(this);

        binding.toolbarRecordings.setNavigationOnClickListener(v -> finish());
        binding.recyclerRecordings.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRecordings.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecordings();
    }

    private void refreshRecordings() {
        ioExecutor.execute(() -> {
            List<RecordedVideoItem> list = repository.loadAll();
            runOnUiThread(() -> {
                if (binding == null) return;
                adapter.submitList(list);
                binding.recordingsCountText.setText(list.size() + (list.size() == 1 ? " video" : " videos"));
                binding.emptyRecordingsLayout.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                binding.recyclerRecordings.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    @Override
    public void onPlay(RecordedVideoItem item) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogVideoPlayerBinding playerBinding = DialogVideoPlayerBinding.inflate(getLayoutInflater());
        dialog.setContentView(playerBinding.getRoot());

        playerBinding.playerTitle.setText(item.displayName);
        playerBinding.playerMeta.setText(
                item.width + "x" + item.height + " · " + RecordingsRepository.formatDuration(item.durationMs)
        );

        MediaController controller = new MediaController(this);
        controller.setAnchorView(playerBinding.videoView);
        playerBinding.videoView.setMediaController(controller);
        playerBinding.videoView.setVideoURI(item.contentUri);
        playerBinding.videoView.setOnPreparedListener(mp -> playerBinding.videoView.start());

        playerBinding.btnPlayerClose.setOnClickListener(v -> {
            playerBinding.videoView.stopPlayback();
            dialog.dismiss();
        });
        playerBinding.btnPlayerShare.setOnClickListener(v -> onShare(item));
        dialog.setOnDismissListener(d -> playerBinding.videoView.stopPlayback());
        dialog.show();
    }

    @Override
    public void onShare(RecordedVideoItem item) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(item.mimeType != null ? item.mimeType : "video/mp4");
        share.putExtra(Intent.EXTRA_STREAM, item.contentUri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share recording"));
    }

    @Override
    public void onDelete(RecordedVideoItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Recording?")
                .setMessage("Remove \"" + item.displayName + "\" permanently from MediaStore?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> ioExecutor.execute(() -> {
                    repository.delete(item);
                    refreshRecordings();
                }))
                .show();
    }

    @Override
    protected void onDestroy() {
        adapter.release();
        ioExecutor.shutdown();
        super.onDestroy();
    }
}
