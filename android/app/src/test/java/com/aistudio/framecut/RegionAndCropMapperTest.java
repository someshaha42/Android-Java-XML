package com.aistudio.framecut;

import com.aistudio.framecut.crop.CropCoordinateMapper;
import com.aistudio.framecut.crop.CropModels.CropPixelRect;
import com.aistudio.framecut.model.NormalizedRegion;
import com.aistudio.framecut.model.PixelRegion;
import com.aistudio.framecut.model.RecorderEnums.OrientationMode;
import com.aistudio.framecut.model.RecordingSettings;
import com.aistudio.framecut.region.RegionSelectionManager;
import org.junit.Test;

import static org.junit.Assert.*;

public class RegionAndCropMapperTest {

    @Test
    public void pixelRegionAlwaysEvenAligned() {
        NormalizedRegion reg = new NormalizedRegion(0.113f, 0.217f, 0.881f, 0.773f);
        PixelRegion px = RegionSelectionManager.toPixelRegion(reg, 1081, 2401);
        assertEquals(0, px.width % 2);
        assertEquals(0, px.height % 2);
        assertTrue(px.width >= RegionSelectionManager.MIN_PIXEL_SPAN);
        assertTrue(px.height >= RegionSelectionManager.MIN_PIXEL_SPAN);
    }

    @Test
    public void fullScreenOrientationResolvesCorrectly() {
        int[] portrait = RegionSelectionManager.resolveFullScreenDimensions(2400, 1080, OrientationMode.PORTRAIT);
        assertTrue(portrait[0] < portrait[1]);
        assertEquals(1080, portrait[0]);
        assertEquals(2400, portrait[1]);

        int[] landscape = RegionSelectionManager.resolveFullScreenDimensions(1080, 2400, OrientationMode.LANDSCAPE);
        assertTrue(landscape[0] > landscape[1]);
        assertEquals(2400, landscape[0]);
        assertEquals(1080, landscape[1]);
    }

    @Test
    public void fullFrameDetectionAndEvenCropRect() {
        assertTrue(CropCoordinateMapper.isFullFrame(new NormalizedRegion(0f, 0f, 1f, 1f)));
        assertFalse(CropCoordinateMapper.isFullFrame(new NormalizedRegion(0.1f, 0.1f, 0.9f, 0.9f)));

        CropPixelRect rect = CropCoordinateMapper.toPixelRect(
                new NormalizedRegion(0.15f, 0.25f, 0.85f, 0.75f),
                1920,
                1080,
                true
        );
        assertEquals(0, rect.left % 2);
        assertEquals(0, rect.top % 2);
        assertEquals(0, rect.width % 2);
        assertEquals(0, rect.height % 2);
    }

    @Test
    public void settingsCustomOverridesWinOverPresets() {
        RecordingSettings s = new RecordingSettings();
        assertEquals(150, s.getEffectiveVideoBitrateMbps());
        assertEquals(30, s.getEffectiveVideoFps());

        s.customVideoBitrateMbps = 225;
        s.customVideoFps = 90;
        assertEquals(225, s.getEffectiveVideoBitrateMbps());
        assertEquals(90, s.getEffectiveVideoFps());
    }
}
