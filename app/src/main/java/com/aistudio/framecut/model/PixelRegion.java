package com.aistudio.framecut.model;

public final class PixelRegion {
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;
    public final int screenWidth;
    public final int screenHeight;
    public final int width;
    public final int height;

    public PixelRegion(int left, int top, int right, int bottom, int screenWidth, int screenHeight) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.width = Math.max(2, right - left);
        this.height = Math.max(2, bottom - top);
    }
}
