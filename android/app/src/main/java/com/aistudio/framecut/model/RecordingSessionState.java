package com.aistudio.framecut.model;

import android.net.Uri;
import com.aistudio.framecut.model.RecorderEnums.RecordingMode;
import com.aistudio.framecut.model.RecorderEnums.RecordingPhase;

public final class RecordingSessionState {
    public RecordingPhase phase = RecordingPhase.IDLE;
    public long elapsedMillis = 0L;
    public RecordingMode activeMode = RecordingMode.FULL_SCREEN;
    public String activeAudioLabel = "Internal Audio";
    public String activeEncoderName = "H.265/HEVC";
    public int activeBitrateMbps = 150;
    public String activeResolutionLabel = "";
    public PixelRegion activeRegion = null;
    public boolean showRegionBorder = false;
    public Uri lastSavedUri = null;
    public String lastSavedName = null;
    public String warningMessage = null;
    public String errorMessage = null;

    public RecordingSessionState copy() {
        RecordingSessionState c = new RecordingSessionState();
        c.phase = this.phase;
        c.elapsedMillis = this.elapsedMillis;
        c.activeMode = this.activeMode;
        c.activeAudioLabel = this.activeAudioLabel;
        c.activeEncoderName = this.activeEncoderName;
        c.activeBitrateMbps = this.activeBitrateMbps;
        c.activeResolutionLabel = this.activeResolutionLabel;
        c.activeRegion = this.activeRegion;
        c.showRegionBorder = this.showRegionBorder;
        c.lastSavedUri = this.lastSavedUri;
        c.lastSavedName = this.lastSavedName;
        c.warningMessage = this.warningMessage;
        c.errorMessage = this.errorMessage;
        return c;
    }
}
