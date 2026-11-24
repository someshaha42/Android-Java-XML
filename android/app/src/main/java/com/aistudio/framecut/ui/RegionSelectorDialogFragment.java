package com.aistudio.framecut.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.aistudio.framecut.R;
import com.aistudio.framecut.databinding.DialogRegionSelectorBinding;
import com.aistudio.framecut.model.NormalizedRegion;
import com.aistudio.framecut.model.PixelRegion;
import com.aistudio.framecut.region.RegionSelectionManager;
import java.util.Locale;

public final class RegionSelectorDialogFragment extends DialogFragment {
    public interface OnRegionConfirmedListener {
        void onRegionConfirmed(NormalizedRegion region);
    }

    private static final String ARG_LEFT = "left";
    private static final String ARG_TOP = "top";
    private static final String ARG_RIGHT = "right";
    private static final String ARG_BOTTOM = "bottom";
    private static final String ARG_SCREEN_W = "screen_w";
    private static final String ARG_SCREEN_H = "screen_h";

    private DialogRegionSelectorBinding binding;
    private OnRegionConfirmedListener callback;
    private int screenW = 1080;
    private int screenH = 2400;

    public static RegionSelectorDialogFragment newInstance(
            NormalizedRegion region,
            int screenW,
            int screenH) {
        RegionSelectorDialogFragment frag = new RegionSelectorDialogFragment();
        Bundle args = new Bundle();
        args.putFloat(ARG_LEFT, region.left);
        args.putFloat(ARG_TOP, region.top);
        args.putFloat(ARG_RIGHT, region.right);
        args.putFloat(ARG_BOTTOM, region.bottom);
        args.putInt(ARG_SCREEN_W, screenW);
        args.putInt(ARG_SCREEN_H, screenH);
        frag.setArguments(args);
        return frag;
    }

    public void setOnRegionConfirmedListener(OnRegionConfirmedListener listener) {
        this.callback = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = DialogRegionSelectorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        NormalizedRegion initial = NormalizedRegion.DEFAULT;
        if (args != null) {
            initial = new NormalizedRegion(
                    args.getFloat(ARG_LEFT, 0.1f),
                    args.getFloat(ARG_TOP, 0.2f),
                    args.getFloat(ARG_RIGHT, 0.9f),
                    args.getFloat(ARG_BOTTOM, 0.7f)
            );
            screenW = Math.max(128, args.getInt(ARG_SCREEN_W, 1080));
            screenH = Math.max(128, args.getInt(ARG_SCREEN_H, 2400));
        }

        binding.selectorEditorView.setCanvasAspectRatio((float) screenW / (float) screenH);
        binding.selectorEditorView.setMinSpan(RegionSelectionManager.MIN_SPAN_FRAC);
        binding.selectorEditorView.setRegion(initial);
        updateCoordsLabel(initial);

        binding.selectorEditorView.setOnRegionChangeListener(this::updateCoordsLabel);

        binding.chipSelFree.setOnClickListener(v -> applyPreset(null, binding.chipSelFree));
        binding.chipSel169.setOnClickListener(v -> applyPreset(16f / 9f, binding.chipSel169));
        binding.chipSel916.setOnClickListener(v -> applyPreset(9f / 16f, binding.chipSel916));
        binding.chipSel11.setOnClickListener(v -> applyPreset(1f, binding.chipSel11));

        binding.btnSelReset.setOnClickListener(v -> {
            binding.selectorEditorView.setLockedAspectRatio(null);
            binding.selectorEditorView.setRegion(NormalizedRegion.DEFAULT);
            updateCoordsLabel(NormalizedRegion.DEFAULT);
            highlightChip(binding.chipSelFree);
        });

        binding.btnSelCancel.setOnClickListener(v -> dismiss());
        binding.btnSelConfirm.setOnClickListener(v -> {
            if (callback != null) {
                callback.onRegionConfirmed(
                        RegionSelectionManager.normalize(binding.selectorEditorView.getRegion()));
            }
            dismiss();
        });
    }

    private void applyPreset(@Nullable Float ratio, TextView activeChip) {
        highlightChip(activeChip);
        binding.selectorEditorView.setLockedAspectRatio(ratio);
        if (ratio != null) {
            NormalizedRegion updated = RegionSelectionManager.applyAspectRatioPreset(
                    binding.selectorEditorView.getRegion(), ratio, screenW, screenH);
            binding.selectorEditorView.setRegion(updated);
            updateCoordsLabel(updated);
        }
    }

    private void highlightChip(TextView active) {
        TextView[] all = {binding.chipSelFree, binding.chipSel169, binding.chipSel916, binding.chipSel11};
        for (TextView chip : all) {
            chip.setBackgroundResource(chip == active ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        }
    }

    private void updateCoordsLabel(NormalizedRegion region) {
        PixelRegion px = RegionSelectionManager.toPixelRegion(region, screenW, screenH);
        binding.selectorCoordsText.setText(String.format(
                Locale.US,
                "%d x %d px  ·  (%d, %d)",
                px.width, px.height, px.left, px.top
        ));
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
