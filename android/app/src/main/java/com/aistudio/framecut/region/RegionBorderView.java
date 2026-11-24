package com.aistudio.framecut.region;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.aistudio.framecut.R;

public class RegionBorderView extends View {
    private float leftFraction = 0.1f;
    private float topFraction = 0.2f;
    private float rightFraction = 0.9f;
    private float bottomFraction = 0.7f;

    private boolean showBorder = true;
    private boolean animate = false;
    private int scrimColor = Color.TRANSPARENT;
    private int neutralGuideColor = Color.TRANSPARENT;

    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint neutralPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float dashPx;
    private float gapPx;
    private float dashPhase = 0f;
    private ValueAnimator animator;

    public RegionBorderView(Context context) {
        super(context);
        init(context);
    }

    public RegionBorderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RegionBorderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        dashPx = 12f * density;
        gapPx = 8f * density;
        float strokePx = 2.5f * density;

        scrimPaint.setStyle(Paint.Style.FILL);

        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setColor(Color.argb(90, 0, 0, 0));
        shadowPaint.setStrokeWidth(strokePx + 2f * density);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(ContextCompat.getColor(context, R.color.fc_record_accent));
        borderPaint.setStrokeWidth(strokePx);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{dashPx, gapPx}, 0f));

        neutralPaint.setStyle(Paint.Style.STROKE);
        neutralPaint.setStrokeWidth(1.5f * density);
    }

    public void setRegionFractions(float left, float top, float right, float bottom) {
        this.leftFraction = left;
        this.topFraction = top;
        this.rightFraction = right;
        this.bottomFraction = bottom;
        invalidate();
    }

    public void setAppearance(boolean showBorder, boolean animate, int scrimColor, int neutralGuideColor, float strokeDp) {
        this.showBorder = showBorder;
        this.animate = animate;
        this.scrimColor = scrimColor;
        this.neutralGuideColor = neutralGuideColor;
        float density = getResources().getDisplayMetrics().density;
        float strokePx = strokeDp * density;
        borderPaint.setStrokeWidth(strokePx);
        shadowPaint.setStrokeWidth(strokePx + 2f * density);
        updateAnimator();
        invalidate();
    }

    private void updateAnimator() {
        if (showBorder && animate && isAttachedToWindow()) {
            if (animator == null) {
                animator = ValueAnimator.ofFloat(0f, -(dashPx + gapPx));
                animator.setDuration(900L);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(anim -> {
                    dashPhase = (float) anim.getAnimatedValue();
                    borderPaint.setPathEffect(new DashPathEffect(new float[]{dashPx, gapPx}, dashPhase));
                    invalidate();
                });
                animator.start();
            }
        } else if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimator();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;

        float l = RegionSelectionManager.clamp(leftFraction * w, 0f, w);
        float t = RegionSelectionManager.clamp(topFraction * h, 0f, h);
        float r = RegionSelectionManager.clamp(rightFraction * w, l, w);
        float b = RegionSelectionManager.clamp(bottomFraction * h, t, h);

        if (scrimColor != Color.TRANSPARENT) {
            scrimPaint.setColor(scrimColor);
            canvas.drawRect(0f, 0f, w, t, scrimPaint);
            canvas.drawRect(0f, b, w, h, scrimPaint);
            canvas.drawRect(0f, t, l, b, scrimPaint);
            canvas.drawRect(r, t, w, b, scrimPaint);
        }

        if (showBorder) {
            canvas.drawRect(l, t, r, b, shadowPaint);
            canvas.drawRect(l, t, r, b, borderPaint);
        } else if (neutralGuideColor != Color.TRANSPARENT) {
            neutralPaint.setColor(neutralGuideColor);
            canvas.drawRect(l, t, r, b, neutralPaint);
        }
    }
}
