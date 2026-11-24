package com.aistudio.framecut.model;

public final class NormalizedRegion {
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public static final NormalizedRegion DEFAULT = new NormalizedRegion(0.1f, 0.2f, 0.9f, 0.7f);

    public NormalizedRegion(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
