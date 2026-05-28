package com.example.unmarkeddetector.detection

import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import com.example.unmarkeddetector.domain.model.DetectionVisualizationData
import com.example.unmarkeddetector.ui.common.GraphicOverlay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menedżer odpowiedzialny za przesyłanie danych wizualizacyjnych od detektora do GraphicOverlay.
 * Obsługuje transformacje współrzędnych i zarządzanie callback'ami.
 */
@Singleton
class VisualizationManager @Inject constructor() {

    companion object {
        private const val TAG = "VisualizationManager"
        private const val MAX_VISUALIZATION_DATA_BUFFER = 10
    }

    // Flow emitujący dane do wizualizacji
    private val _visualizationData = MutableSharedFlow<DetectionVisualizationData>(
        extraBufferCapacity = MAX_VISUALIZATION_DATA_BUFFER,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val visualizationData = _visualizationData.asSharedFlow()

    // Referencja do overlaya (jeśli dostępna)
    private var graphicOverlay: GraphicOverlay? = null

    /**
     * Zarejestruj GraphicOverlay do otrzymywania danych wizualizacyjnych
     */
    fun attachGraphicOverlay(overlay: GraphicOverlay) {
        graphicOverlay = overlay
        Log.d(TAG, "GraphicOverlay attached")
    }

    /**
     * Odrejestruj GraphicOverlay
     */
    fun detachGraphicOverlay() {
        graphicOverlay = null
        Log.d(TAG, "GraphicOverlay detached")
    }

    fun publishPlateRegions(
        regions: List<PlateRegion>,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        val detections = regions.map { region ->
            val label = region.plateText?.takeIf { it.isNotBlank() } ?: "tablica"
            GraphicOverlay.SourceDetectionBox(
                rect = RectF(region.rect),
                confidence = region.score,
                label = label,
                color = Color.GREEN
            )
        }

        if (detections.isEmpty()) {
            clearVisualization()
        } else {
            graphicOverlay?.setSourceDetectionBoxes(
                detections = detections,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            )
        }
    }

    fun publishYoloDetections(
        regions: List<PlateRegion>,
        sourceWidth: Int,
        sourceHeight: Int
    ) = publishPlateRegions(regions, sourceWidth, sourceHeight)

    /**
     * Wyślij dane wizualizacyjne do GraphicOverlay i flow'a
     */
    suspend fun publishVisualizationData(data: DetectionVisualizationData) {
        try {
            graphicOverlay?.addDetectionBox(
                GraphicOverlay.DetectionBox(
                    rect = data.detectionRect,
                    confidence = data.confidence,
                    isAlert = data.isAlert,
                    label = data.label,
                    color = data.color
                )
            )
            _visualizationData.emit(data)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish visualization data", e)
        }
    }

    /**
     * Wyczyść wszystkie bounding boxy z overlaya
     */
    fun clearVisualization() {
        graphicOverlay?.clearDetectionBoxes()
    }

    /**
     * Zaktualizuj konfigurację rysowania overlaya
     */
    fun updateOverlayConfig(config: GraphicOverlay.GraphicConfig) {
        graphicOverlay?.config = config
    }
}
