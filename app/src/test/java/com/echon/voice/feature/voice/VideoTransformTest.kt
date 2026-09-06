package com.echon.voice.feature.voice

import org.junit.Assert.*
import org.junit.Test

class VideoTransformTest {
    @Test fun zoomAndPanStayWithinViewport() {
        val zoomed = VideoTransform().change(2f, 5000f, -5000f, 1000, 600)
        assertEquals(VideoTransform(2f, 500f, -300f), zoomed)
        assertEquals(4f, zoomed.change(10f, 0f, 0f, 1000, 600).scale)
    }
    @Test fun zoomOutRestoresWholeFrameAndCentersIt() {
        assertEquals(VideoTransform(), VideoTransform(3f, 200f, 100f).change(0.1f, 100f, 100f, 600, 1000))
    }
    @Test fun invalidGesturesDoNotCorruptRendering() {
        for (zoom in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
            assertEquals(VideoTransform(), VideoTransform().change(zoom, 1f, 1f, 100, 100))
        }
    }
    @Test fun portraitAndLandscapeClampIndependently() {
        assertEquals(VideoTransform(2f, 300f, 500f), VideoTransform().change(2f, 999f, 999f, 600, 1000))
        assertEquals(VideoTransform(2f, 500f, 300f), VideoTransform().change(2f, 999f, 999f, 1000, 600))
    }
}
