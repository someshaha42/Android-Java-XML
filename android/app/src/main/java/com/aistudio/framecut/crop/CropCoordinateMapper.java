package com.aistudio.framecut.crop;

import com.aistudio.framecut.crop.CropModels.CropPixelRect;
import com.aistudio.framecut.model.NormalizedRegion;
import com.aistudio.framecut.region.RegionSelectionManager;

public final class CropCoordinateMapper {
    public static final float FULL_FRAME_EPSILON = 0.005f;
    public static final float MIN_CROP_SPAN = 0.08f;

    private CropCoordinateMapper() {}

    public static NormalizedRegion clampRegion(NormalizedRegion region) {
        if (region == null) return new NormalizedRegion(0f, 0f, 1f, 1f);
        float l = RegionSelectionManager.clamp(region.left, 0f, 1f - MIN_CROP_SPAN);
        float t = RegionSelectionManager.clamp(region.top, 0f, 1f - MIN_CROP_SPAN);
        float r = RegionSelectionManager.clamp(region.right, l + MIN_CROP_SPAN, 1f);
        float b = RegionSelectionManager.clamp(region.bottom, t + MIN_CROP_SPAN, 1f);
        return new NormalizedRegion(l, t, r, b);
    }

    public static boolean isFullFrame(NormalizedRegion region) {
        NormalizedRegion c = clampRegion(region);
        return c.left <= FULL_FRAME_EPSILON
                && c.top <= FULL_FRAME_EPSILON
                && c.right >= (1f - FULL_FRAME_EPSILON)
                && c.bottom >= (1f - FULL_FRAME_EPSILON);
    }

    public static CropPixelRect toPixelRect(
            NormalizedRegion region,
            int mediaWidth,
            int mediaHeight,
            boolean evenDimensions) {
        NormalizedRegion c = clampRegion(region);
        int safeW = Math.max(4, mediaWidth);
        int safeH = Math.max(4, mediaHeight);

        int l = Math.round(c.left * safeW);
        int t = Math.round(c.top * safeH);
        int r = Math.round(c.right * safeW);
        int b = Math.round(c.bottom * safeH);

        int w = Math.max(16, r - l);
        int h = Math.max(16, b - t);

        if (evenDimensions) {
            l = RegionSelectionManager.alignEven(l);
            t = RegionSelectionManager.alignEven(t);
            w = RegionSelectionManager.alignEven(Math.min(w, safeW - l));
            h = RegionSelectionManager.alignEven(Math.min(h, safeH - t));
            w = Math.max(16, w);
            h = Math.max(16, h);
        } else {
            w = Math.min(w, safeW - l);
            h = Math.min(h, safeH - t);
        }
        return new CropPixelRect(l, t, w, h);
    }

    public static NormalizedRegion applyAspectRatio(
            NormalizedRegion current,
            float targetAspectRatio,
            int mediaWidth,
            int mediaHeight) {
        NormalizedRegion c = clampRegion(current);
        float cx = (c.left + c.right) * 0.5f;
        float cy = (c.top + c.bottom) * 0.5f;

        float curPxW = (c.right - c.left) * Math.max(1, mediaWidth);
        float curPxH = curPxW / targetAspectRatio;

        if (curPxH > mediaHeight * 0.92f) {
            curPxH = mediaHeight * 0.92f;
            curPxW = curPxH * targetAspectRatio;
        }
        if (curPxW > mediaWidth * 0.92f) {
            curPxW = mediaWidth * 0.92f;
            curPxH = curPxW / targetAspectRatio;
        }

        float halfW = (curPxW / Math.max(1f, (float) mediaWidth)) * 0.5f;
        float halfH = (curPxH / Math.max(1f, (float) mediaHeight)) * 0.5f;
        halfW = Math.min(0.5f, Math.max(MIN_CROP_SPAN * 0.5f, halfW));
        halfH = Math.min(0.5f, Math.max(MIN_CROP_SPAN * 0.5f, halfH));

        float clampedCx = RegionSelectionManager.clamp(cx, halfW, 1f - halfW);
        float clampedCy = RegionSelectionManager.clamp(cy, halfH, 1f - halfH);

        return clampRegion(new NormalizedRegion(
                clampedCx - halfW,
                clampedCy - halfH,
                clampedCx + halfW,
                clampedCy + halfH
        ));
    }
}
