package com.aistudio.framecut.region;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import com.aistudio.framecut.model.NormalizedRegion;

public final class RegionOverlayManager {
    private final Context appContext;
    private final WindowManager windowManager;
    private RegionBorderView activeBorderView;

    public RegionOverlayManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean canDrawOverlays() {
        return Settings.canDrawOverlays(appContext);
    }

    public void showRecordingBorder(NormalizedRegion region) {
        if (!canDrawOverlays() || windowManager == null) return;
        hideRecordingBorder();

        RegionBorderView view = new RegionBorderView(appContext);
        view.setRegionFractions(region.left, region.top, region.right, region.bottom);
        view.setAppearance(true, true, Color.TRANSPARENT, Color.TRANSPARENT, 3.0f);

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            windowManager.addView(view, params);
            activeBorderView = view;
        } catch (Exception ignored) {
            activeBorderView = null;
        }
    }

    public void hideRecordingBorder() {
        if (activeBorderView != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(activeBorderView);
            } catch (Exception ignored) {
            }
            activeBorderView = null;
        }
    }
}
