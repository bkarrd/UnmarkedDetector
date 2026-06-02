package com.example.unmarkeddetector.detection

import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import com.example.unmarkeddetector.ui.common.GraphicOverlay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisualizationManager @Inject constructor() {

    companion object {
        private const val TAG = "VisualizationManager"
    }

    private var graphicOverlay: GraphicOverlay? = null

    fun attachGraphicOverlay(overlay: GraphicOverlay) {
        graphicOverlay = overlay
        Log.d(TAG, "GraphicOverlay attached")
    }

    fun detachGraphicOverlay() {
        graphicOverlay = null
        Log.d(TAG, "GraphicOverlay detached")
    }

    fun publishPlateRegions(
        regions: List<PlateRegion>,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        if (regions.isEmpty()) {
            clearVisualization()
            return
        }

        graphicOverlay?.setSourceDetectionBoxes(
            detections = regions.map { region ->
                GraphicOverlay.SourceDetectionBox(
                    rect = RectF(region.rect),
                    confidence = region.score,
                    label = region.plateText?.takeIf { it.isNotBlank() } ?: "tablica",
                    color = Color.GREEN
                )
            },
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight
        )
    }

    fun clearVisualization() {
        graphicOverlay?.clearDetectionBoxes()
    }
}
