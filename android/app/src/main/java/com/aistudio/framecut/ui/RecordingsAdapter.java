package com.aistudio.framecut.ui;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.aistudio.framecut.R;
import com.aistudio.framecut.databinding.ItemRecordingBinding;
import com.aistudio.framecut.model.RecordedVideoItem;
import com.aistudio.framecut.recordings.RecordingsRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.Holder> {
    public interface Listener {
        void onPlay(RecordedVideoItem item);
        void onShare(RecordedVideoItem item);
        void onDelete(RecordedVideoItem item);
    }

    private final List<RecordedVideoItem> items = new ArrayList<>();
    private final Listener listener;
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(2);

    public RecordingsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<RecordedVideoItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    public void release() {
        thumbExecutor.shutdownNow();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRecordingBinding binding = ItemRecordingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        RecordedVideoItem item = items.get(position);
        holder.binding.titleText.setText(item.displayName);
        String meta = RecordingsRepository.formatDuration(item.durationMs)
                + " · " + item.width + "x" + item.height
                + " · " + RecordingsRepository.formatSize(item.sizeBytes)
                + " · " + RecordingsRepository.formatDate(item.dateAddedSec);
        holder.binding.metaText.setText(meta);

        holder.binding.thumbImage.setImageResource(R.drawable.ic_film);
        holder.binding.thumbImage.setTag(item.id);
        ContentResolver resolver = holder.itemView.getContext().getContentResolver();
        thumbExecutor.execute(() -> {
            Bitmap bmp = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    bmp = resolver.loadThumbnail(item.contentUri, new Size(160, 160), null);
                } catch (Exception ignored) {
                }
            }
            if (bmp != null) {
                Bitmap finalBmp = bmp;
                holder.binding.thumbImage.post(() -> {
                    Object tag = holder.binding.thumbImage.getTag();
                    if (tag instanceof Long && ((Long) tag) == item.id) {
                        holder.binding.thumbImage.setImageBitmap(finalBmp);
                    }
                });
            }
        });

        holder.binding.btnPlay.setOnClickListener(v -> listener.onPlay(item));
        holder.binding.btnShare.setOnClickListener(v -> listener.onShare(item));
        holder.binding.btnDelete.setOnClickListener(v -> listener.onDelete(item));
        holder.itemView.setOnClickListener(v -> listener.onPlay(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemRecordingBinding binding;

        Holder(ItemRecordingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
