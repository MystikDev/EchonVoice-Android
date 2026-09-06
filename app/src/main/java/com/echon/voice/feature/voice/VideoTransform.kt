package com.echon.voice.feature.voice

/** Zoom is relative to fit/fill; zooming out to 1 restores the complete viewport. */
internal data class VideoTransform(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun change(zoom: Float, panX: Float, panY: Float, width: Int, height: Int): VideoTransform {
        if (!zoom.isFinite() || zoom <= 0 || !panX.isFinite() || !panY.isFinite()) return this
        val nextScale = (scale * zoom).coerceIn(1f, 4f)
        val maxX = width.coerceAtLeast(0) * (nextScale - 1) / 2
        val maxY = height.coerceAtLeast(0) * (nextScale - 1) / 2
        return VideoTransform(nextScale, (x + panX).coerceIn(-maxX, maxX), (y + panY).coerceIn(-maxY, maxY))
    }
}
