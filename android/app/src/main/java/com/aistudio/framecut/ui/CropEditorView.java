package com.aistudio.framecut.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.aistudio.framecut.R;
import com.aistudio.framecut.model.NormalizedRegion;
import com.aistudio.framecut.region.RegionSelectionManager;

public class CropEditorView extends View {
    public interface OnRegionChangeListener {
        void onRegionChanged(NormalizedRegion region);
    }

    private enum DragMode {
        NONE, MOVE,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        LEFT, TOP, RIGHT, BOTTOM
    }

    private Bitmap previewBitmap;
    private float canvasAspectRatio = 9f / 16f;
    private NormalizedRegion region = NormalizedRegion.DEFAULT;
    private float minSpan = 0.08f;
    private Float lockedAspectRatio = null;
    private OnRegionChangeListener listener;

    private final RectF stageRect = new RectF();
    private final Paint stageBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private DragMode activeMode = DragMode.NONE;
    private float startTouchX;
    private float startTouchY;
    private NormalizedRegion startRegion;
    private float handleRadiusPx;
    private float touchSlopPx;

    public CropEditorView(Context context) {
        super(context);
        init(context);
    }

    public CropEditorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CropEditorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float d = context.getResources().getDisplayMetrics().density;
        handleRadiusPx = 9f * d;
        touchSlopPx = 28f * d;

        stageBgPaint.setColor(Color.parseColor("#111827"));
        stageBgPaint.setStyle(Paint.Style.FILL);

        scrimPaint.setColor(Color.argb(145, 5, 8, 15));
        scrimPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(ContextCompat.getColor(context, R.color.fc_primary));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2.5f * d);

        gridPaint.setColor(Color.argb(110, 255, 255, 255));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * d);

        handleFillPaint.setColor(Color.WHITE);
        handleFillPaint.setStyle(Paint.Style.FILL);

        handleStrokePaint.setColor(ContextCompat.getColor(context, R.color.fc_primary));
        handleStrokePaint.setStyle(Paint.Style.STROKE);
        handleStrokePaint.setStrokeWidth(2f * d);
    }

    public void setOnRegionChangeListener(OnRegionChangeListener listener) {
        this.listener = listener;
    }

    public void setPreviewBitmap(@Nullable Bitmap bitmap, float aspectRatio) {
        this.previewBitmap = bitmap;
        this.canvasAspectRatio = Math.max(0.2f, aspectRatio);
        invalidate();
    }

    public void setCanvasAspectRatio(float aspectRatio) {
        this.canvasAspectRatio = Math.max(0.2f, aspectRatio);
        invalidate();
    }

    public void setLockedAspectRatio(@Nullable Float ratio) {
        this.lockedAspectRatio = ratio;
    }

    public void setMinSpan(float minSpan) {
        this.minSpan = minSpan;
    }

    public void setRegion(NormalizedRegion region) {
        this.region = clamp(region);
        invalidate();
    }

    public NormalizedRegion getRegion() {
        return region;
    }

    private void updateStageRect() {
        float pad = handleRadiusPx + 6f;
        float availW = Math.max(20f, getWidth() - pad * 2f);
        float availH = Math.max(20f, getHeight() - pad * 2f);
        float w = availW;
        float h = w / canvasAspectRatio;
        if (h > availH) {
            h = availH;
            w = h * canvasAspectRatio;
        }
        float left = (getWidth() - w) * 0.5f;
        float top = (getHeight() - h) * 0.5f;
        stageRect.set(left, top, left + w, top + h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateStageRect();
        float w = stageRect.width();
        float h = stageRect.height();
        if (w <= 0f || h <= 0f) return;

        if (previewBitmap != null && !previewBitmap.isRecycled()) {
            canvas.drawBitmap(previewBitmap, null, stageRect, null);
        } else {
            canvas.drawRect(stageRect, stageBgPaint);
            // Subtle simulated device display grid
            canvas.drawLine(stageRect.left + w / 2f, stageRect.top, stageRect.left + w / 2f, stageRect.bottom, gridPaint);
            canvas.drawLine(stageRect.left, stageRect.top + h / 2f, stageRect.right, stageRect.top + h / 2f, gridPaint);
        }

        float l = stageRect.left + region.left * w;
        float t = stageRect.top + region.top * h;
        float r = stageRect.left + region.right * w;
        float b = stageRect.top + region.bottom * h;

        // Scrim outside crop box
        canvas.drawRect(stageRect.left, stageRect.top, stageRect.right, t, scrimPaint);
        canvas.drawRect(stageRect.left, b, stageRect.right, stageRect.bottom, scrimPaint);
        canvas.drawRect(stageRect.left, t, l, b, scrimPaint);
        canvas.drawRect(r, t, stageRect.right, b, scrimPaint);

        // Rule of thirds inside crop box
        float cw = r - l;
        float ch = b - t;
        canvas.drawLine(l + cw / 3f, t, l + cw / 3f, b, gridPaint);
        canvas.drawLine(l + 2f * cw / 3f, t, l + 2f * cw / 3f, b, gridPaint);
        canvas.drawLine(l, t + ch / 3f, r, t + ch / 3f, gridPaint);
        canvas.drawLine(l, t + 2f * ch / 3f, r, t + 2f * ch / 3f, gridPaint);

        canvas.drawRect(l, t, r, b, borderPaint);

        // 8 handles: 4 corners + 4 midpoints
        float mx = (l + r) * 0.5f;
        float my = (t + b) * 0.5f;
        float[][] pts = {
                {l, t}, {r, t}, {l, b}, {r, b},
                {mx, t}, {mx, b}, {l, my}, {r, my}
        };
        for (float[] pt : pts) {
            canvas.drawCircle(pt[0], pt[1], handleRadiusPx, handleFillPaint);
            canvas.drawCircle(pt[0], pt[1], handleRadiusPx, handleStrokePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        updateStageRect();
        float w = stageRect.width();
        float h = stageRect.height();
        if (w <= 0f || h <= 0f) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeMode = hitTest(x, y, w, h);
                startTouchX = x;
                startTouchY = y;
                startRegion = region;
                if (activeMode != DragMode.NONE && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return activeMode != DragMode.NONE;

            case MotionEvent.ACTION_MOVE:
                if (activeMode == DragMode.NONE || startRegion == null) return false;
                float dx = (x - startTouchX) / w;
                float dy = (y - startTouchY) / h;
                NormalizedRegion next = applyDrag(startRegion, dx, dy, activeMode);
                region = next;
                invalidate();
                if (listener != null) listener.onRegionChanged(region);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeMode = DragMode.NONE;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;

            default:
                return false;
        }
    }

    private DragMode hitTest(float x, float y, float w, float h) {
        float l = stageRect.left + region.left * w;
        float t = stageRect.top + region.top * h;
        float r = stageRect.left + region.right * w;
        float b = stageRect.top + region.bottom * h;
        float mx = (l + r) * 0.5f;
        float my = (t + b) * 0.5f;

        if (near(x, y, l, t)) return DragMode.TOP_LEFT;
        if (near(x, y, r, t)) return DragMode.TOP_RIGHT;
        if (near(x, y, l, b)) return DragMode.BOTTOM_LEFT;
        if (near(x, y, r, b)) return DragMode.BOTTOM_RIGHT;
        if (near(x, y, mx, t)) return DragMode.TOP;
        if (near(x, y, mx, b)) return DragMode.BOTTOM;
        if (near(x, y, l, my)) return DragMode.LEFT;
        if (near(x, y, r, my)) return DragMode.RIGHT;
        if (x >= l && x <= r && y >= t && y <= b) return DragMode.MOVE;
        return DragMode.NONE;
    }

    private boolean near(float x, float y, float tx, float ty) {
        return Math.hypot(x - tx, y - ty) <= touchSlopPx;
    }

    private NormalizedRegion applyDrag(NormalizedRegion s, float dx, float dy, DragMode mode) {
        float l = s.left;
        float t = s.top;
        float r = s.right;
        float b = s.bottom;

        if (mode == DragMode.MOVE) {
            float spanX = r - l;
            float spanY = b - t;
            float nl = RegionSelectionManager.clamp(l + dx, 0f, 1f - spanX);
            float nt = RegionSelectionManager.clamp(t + dy, 0f, 1f - spanY);
            return new NormalizedRegion(nl, nt, nl + spanX, nt + spanY);
        }

        if (mode == DragMode.LEFT || mode == DragMode.TOP_LEFT || mode == DragMode.BOTTOM_LEFT) {
            l = RegionSelectionManager.clamp(l + dx, 0f, r - minSpan);
        }
        if (mode == DragMode.RIGHT || mode == DragMode.TOP_RIGHT || mode == DragMode.BOTTOM_RIGHT) {
            r = RegionSelectionManager.clamp(r + dx, l + minSpan, 1f);
        }
        if (mode == DragMode.TOP || mode == DragMode.TOP_LEFT || mode == DragMode.TOP_RIGHT) {
            t = RegionSelectionManager.clamp(t + dy, 0f, b - minSpan);
        }
        if (mode == DragMode.BOTTOM || mode == DragMode.BOTTOM_LEFT || mode == DragMode.BOTTOM_RIGHT) {
            b = RegionSelectionManager.clamp(b + dy, t + minSpan, 1f);
        }

        if (lockedAspectRatio != null) {
            float targetFracRatio = lockedAspectRatio / Math.max(0.2f, canvasAspectRatio);
            float curW = r - l;
            float desiredH = curW / targetFracRatio;
            if (t + desiredH > 1f) {
                desiredH = 1f - t;
                curW = desiredH * targetFracRatio;
                r = RegionSelectionManager.clamp(l + curW, l + minSpan, 1f);
            }
            b = RegionSelectionManager.clamp(t + desiredH, t + minSpan, 1f);
        }

        return clamp(new NormalizedRegion(l, t, r, b));
    }

    private NormalizedRegion clamp(NormalizedRegion src) {
        if (src == null) return NormalizedRegion.DEFAULT;
        float l = RegionSelectionManager.clamp(src.left, 0f, 1f - minSpan);
        float t = RegionSelectionManager.clamp(src.top, 0f, 1f - minSpan);
        float r = RegionSelectionManager.clamp(src.right, l + minSpan, 1f);
        float b = RegionSelectionManager.clamp(src.bottom, t + minSpan, 1f);
        return new NormalizedRegion(l, t, r, b);
    }
}
