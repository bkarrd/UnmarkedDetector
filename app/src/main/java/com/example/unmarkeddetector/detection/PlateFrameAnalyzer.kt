package com.example.unmarkeddetector.detection

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.unmarkeddetector.domain.model.AlertEvent
import com.example.unmarkeddetector.domain.usecase.TriggerAlertUseCase
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PlateFrameAnalyzer(
    private val detectionPipeline: DetectionPipeline,
    private val adaptiveScanScheduler: AdaptiveScanScheduler,
    private val triggerAlertUseCase: TriggerAlertUseCase,
    private val analysisScope: CoroutineScope,
    private val canAnalyze: () -> Boolean,
    private val onScanResult: (PlateSessionManager.ScanResult, Long) -> Unit,
    private val onAlerts: (List<AlertEvent>) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "PlateFrameAnalyzer"
    }

    private val inFlight = AtomicBoolean(false)

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
                onScanResult(scanResult, adaptiveScanScheduler.currentIntervalMs())

                if (scanResult.alertMatches.isNotEmpty()) {
                    val alerts = mutableListOf<AlertEvent>()
                    scanResult.alertMatches.forEach { match ->
                        Log.i(TAG, "Database match found for plate=${match.record.plate}")
                        alerts += triggerAlertUseCase(match.record, match.confidence)
                    }
                    onAlerts(alerts)
                }
            } catch (error: Exception) {
                adaptiveScanScheduler.onScanCompleted(foundPlates = false)
                Log.e(TAG, "Frame analysis failed", error)
            } finally {
                inFlight.set(false)
            }
        }
    }
}
