package com.example.unmarkeddetector.detection

import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.unmarkeddetector.domain.model.AlertEvent
import com.example.unmarkeddetector.domain.model.DetectionVisualizationData
import com.example.unmarkeddetector.domain.usecase.TriggerAlertUseCase
import com.example.unmarkeddetector.util.CoordinateTransformer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Rozszerzony analizator klatek z obsługą wizualizacji detekcji.
 *
 * Działanie:
 * 1. Analizuje klatkę z kamery
 * 2. Wykrywa tablice rejestracyjne
 * 3. Emituje dane do GraphicOverlay dla wizualizacji
 * 4. Zwraca wyniki skanowania i alertów
 */
class PlateFrameAnalyzer(
    private val detectionPipeline: DetectionPipeline,
    private val adaptiveScanScheduler: AdaptiveScanScheduler,
    private val triggerAlertUseCase: TriggerAlertUseCase,
    private val analysisScope: CoroutineScope,
    private val visualizationManager: VisualizationManager,
    private val canAnalyze: () -> Boolean,
    private val onScanResult: (PlateSessionManager.ScanResult, Long) -> Unit,
    private val onAlerts: (List<AlertEvent>) -> Unit,
    private val previewWidth: () -> Int = { 0 },
    private val previewHeight: () -> Int = { 0 }
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "PlateFrameAnalyzer"
    }

    private val inFlight = AtomicBoolean(false)
    private var lastVisualizationTime = 0L

    override fun analyze(image: ImageProxy) {
        if (!canAnalyze()) {
            image.close()
            return
        }

        val now = System.currentTimeMillis()
        if (!adaptiveScanScheduler.shouldScan(now) || !inFlight.compareAndSet(false, true)) {
            image.close()
            return
        }
        Log.d(
            LprDebug.TAG,
            "ImageProxy: ${image.width}x${image.height}, rot=${image.imageInfo.rotationDegrees}"
        )
        adaptiveScanScheduler.markScanStarted(now)

        analysisScope.launch {
            try {
                val scanResult = detectionPipeline.processFrame(image)
                adaptiveScanScheduler.onScanCompleted(scanResult.detectedPlates.isNotEmpty())

                if (scanResult.detectedPlates.isNotEmpty()) {
                    Log.d(TAG, "Plate detections=${scanResult.detectedPlates.joinToString()}")
                }

                // Wizualizacja detekcji
                updateVisualization(scanResult, image)

                onScanResult(scanResult, adaptiveScanScheduler.currentIntervalMs())

                if (scanResult.alertMatches.isNotEmpty()) {
                    val alerts = mutableListOf<AlertEvent>()
                    scanResult.alertMatches.forEach { match ->
                        Log.i(TAG, "Database match found for plate=${match.record.plate}")
                        alerts += triggerAlertUseCase(match.record, match.confidence)
                    }
                    onAlerts(alerts)

                    // Wizualizacja alertu
                    alerts.forEach { alert ->
                        val visualization = DetectionVisualizationData(
                            detectionRect = Rect(0, 0, 0, 0), // Będzie uaktualniony poniżej
                            confidence = alert.confidence,
                            isAlert = true,
                            label = "ALERT: ${alert.record.plate}",
                            color = Color.RED
                        )
                        // TODO: Zaktualizuj rect na bazie track'u
                    }
                }
            } catch (error: Exception) {
                adaptiveScanScheduler.onScanCompleted(foundPlates = false)
                Log.e(TAG, "Frame analysis failed", error)
            } finally {
                inFlight.set(false)
            }
        }
    }

    /**
     * Aktualizuj wizualizację na bazie wyników skanowania
     */
    private suspend fun updateVisualization(
        scanResult: PlateSessionManager.ScanResult,
        imageProxy: ImageProxy
    ) {
        val now = System.currentTimeMillis()

        // Throttle vizualizację - nie aktualizuj za często
        if (now - lastVisualizationTime < 50) {
            return
        }
        lastVisualizationTime = now

        val pWidth = previewWidth()
        val pHeight = previewHeight()

        if (pWidth <= 0 || pHeight <= 0) {
            // PreviewView nie ma jeszcze wymiarów
            return
        }

        // Wyczyść poprzednie wizualizacje
        visualizationManager.clearVisualization()

        // Dla każdej detekcji transformuj współrzędne i wyświetl
        scanResult.detectedPlates.forEach { plate ->
            try {
                // TODO: Uzyskaj współrzędne rect z PlateTrackManager
                // Tutaj bym potrzebował dostępu do trackowanego regionu
                // na razie jest placeholder
                Log.d(TAG, "Visualization for plate: ${plate.plate}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to visualize plate", e)
            }
        }
    }

    /**
     * Transformuj współrzędne regionu tablicy z pikseli ImageProxy na piksele ekranu
     */
    private fun transformRegionToScreen(
        regionRect: Rect,
        imageProxy: ImageProxy,
        previewWidth: Int,
        previewHeight: Int
    ): Rect {
        val coordRect = CoordinateTransformer.CoordinateRect(
            left = regionRect.left,
            top = regionRect.top,
            right = regionRect.right,
            bottom = regionRect.bottom,
            space = CoordinateTransformer.CoordinateSpace.FRAME_PIXELS
        )

        val screenRect = CoordinateTransformer.frameToScreenPixels(
            frameRect = coordRect,
            frameWidth = imageProxy.width,
            frameHeight = imageProxy.height,
            screenWidth = previewWidth,
            screenHeight = previewHeight,
            rotationDegrees = imageProxy.imageInfo.rotationDegrees
        )

        return screenRect.toAndroidRect()
    }
}
