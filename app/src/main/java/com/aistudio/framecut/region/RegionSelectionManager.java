package com.aistudio.framecut.region;

import com.aistudio.framecut.model.NormalizedRegion;
import com.aistudio.framecut.model.PixelRegion;
import com.aistudio.framecut.model.RecorderEnums.OrientationMode;

public final class RegionSelectionManager {
    public static final float MIN_SPAN_FRAC = 0.12f;
    public static final int MIN_PIXEL_SPAN = 128;

    private RegionSelectionManager() {}

    public static float clamp(float val, float min, float max) {
        if (min > max) return min;
        return Math.min(Math.max(val, min), max);
    }

    public static int clampInt(int val, int min, int max) {
        if (min > max) return min;
        return Math.min(Math.max(val, min), max);
    }

    public static int alignEven(int value) {
        int v = value;
        return (v % 2 == 0) ? v : Math.max(2, v - 1);
    }

    public static NormalizedRegion normalize(NormalizedRegion region) {
        if (region == null) return NormalizedRegion.DEFAULT;
        float left = clamp(region.left, 0f, 1f - MIN_SPAN_FRAC);
        float top = clamp(region.top, 0f, 1f - MIN_SPAN_FRAC);
        float right = clamp(region.right, left + MIN_SPAN_FRAC, 1f);
        float bottom = clamp(region.bottom, top + MIN_SPAN_FRAC, 1f);
        return new NormalizedRegion(left, top, right, bottom);
    }

    public static int[] resolveFullScreenDimensions(int rawWidth, int rawHeight, OrientationMode mode) {
        int evenW = alignEven(Math.max(128, rawWidth));
        int evenH = alignEven(Math.max(128, rawHeight));
        if (mode == OrientationMode.PORTRAIT) {
            return new int[]{Math.min(evenW, evenH), Math.max(evenW, evenH)};
        }
        return new int[]{Math.max(evenW, evenH), Math.min(evenW, evenH)};
    }

    public static PixelRegion toPixelRegion(NormalizedRegion normalized, int screenWidth, int screenHeight) {
        int safeScreenW = alignEven(Math.max(MIN_PIXEL_SPAN, screenWidth));
        int safeScreenH = alignEven(Math.max(MIN_PIXEL_SPAN, screenHeight));
        NormalizedRegion clean = normalize(normalized);

        int left = clampInt(alignEven(Math.round(clean.left * safeScreenW)), 0, safeScreenW - MIN_PIXEL_SPAN);
        int top = clampInt(alignEven(Math.round(clean.top * safeScreenH)), 0, safeScreenH - MIN_PIXEL_SPAN);
        int right = clampInt(alignEven(Math.round(clean.right * safeScreenW)), left + MIN_PIXEL_SPAN, safeScreenW);
        int bottom = clampInt(alignEven(Math.round(clean.bottom * safeScreenH)), top + MIN_PIXEL_SPAN, safeScreenH);

        if ((right - left) % 2 != 0) {
            right = (right > left + MIN_PIXEL_SPAN) ? right - 1 : Math.min(safeScreenW, right + 1);
        }
        if ((bottom - top) % 2 != 0) {
            bottom = (bottom > top + MIN_PIXEL_SPAN) ? bottom - 1 : Math.min(safeScreenH, bottom + 1);
        }

        return new PixelRegion(left, top, right, bottom, safeScreenW, safeScreenH);
    }

    public static NormalizedRegion applyAspectRatioPreset(
            NormalizedRegion current,
            float targetRatio,
            float screenW,
            float screenH) {
        float centerX = (current.left + current.right) * 0.5f;
        float centerY = (current.top + current.bottom) * 0.5f;
        float curW = (current.right - current.left) * screenW;

        float targetW = curW;
        float targetH = targetW / targetRatio;
        if (targetH > screenH * 0.85f) {
            targetH = screenH * 0.85f;
            targetW = targetH * targetRatio;
        }
        if (targetW > screenW * 0.9f) {
            targetW = screenW * 0.9f;
            targetH = targetW / targetRatio;
        }

        float halfFracW = (targetW / Math.max(1f, screenW)) * 0.5f;
        float halfFracH = (targetH / Math.max(1f, screenH)) * 0.5f;
        float clampedCx = clamp(centerX, halfFracW, 1f - halfFracW);
        float clampedCy = clamp(centerY, halfFracH, 1f - halfFracH);

        return normalize(new NormalizedRegion(
                clampedCx - halfFracW,
                clampedCy - halfFracH,
                clampedCx + halfFracW,
                clampedCy + halfFracH
        ));
    }
}
