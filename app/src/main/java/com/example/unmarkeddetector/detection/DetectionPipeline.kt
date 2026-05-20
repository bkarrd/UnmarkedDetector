package com.example.unmarkeddetector.detection

import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.unmarkeddetector.domain.model.DetectionResult
import com.example.unmarkeddetector.domain.model.RecognizedTextCandidate
import com.example.unmarkeddetector.domain.usecase.DetectPlateUseCase
import com.example.unmarkeddetector.util.toBitmap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionPipeline @Inject constructor(
    private val preprocessor: ImagePreprocessor,
    private val plateRegionDetector: PlateRegionDetector,
    private val plateTrackManager: PlateTrackManager,
    private val mlKitPlateRecognizer: MlKitPlateRecognizer,
    private val multiScaleDetector: MultiScaleDetector,
    private val detectPlateUseCase: DetectPlateUseCase,
    private val sessionManager: PlateSessionManager
) {

    companion object {
        private const val TAG = "DetectionPipeline"
    }

    private var framesWithoutRegions = 0

    suspend fun processFrame(imageProxy: ImageProxy): PlateSessionManager.ScanResult {
        val bitmap = runCatching { imageProxy.toBitmap() }.getOrElse { err ->
            Log.e(TAG, "toBitmap() nie powiodło się", err)
            imageProxy.close()
            return sessionManager.processScan(emptyList())
        }
        return try {
            Log.d(LprDebug.TAG, "a) klatka kamery (bitmap): ${bitmap.width}x${bitmap.height}")
            val startedAt = System.currentTimeMillis()
            val regions = plateRegionDetector.detect(bitmap)
            if (regions.isEmpty()) {
                framesWithoutRegions++
            } else {
                framesWithoutRegions = 0
            }
            val tracks = plateTrackManager.updateRegions(regions, startedAt)

            tracks.filter { it.shouldRunOcr }.take(3).forEach { track ->
                val crop = preprocessor.cropPlateForMlKit(bitmap, track.rect) ?: return@forEach
                val detections = recognizeTrackCrop(crop, startedAt, track.regionScore)
                plateTrackManager.registerRecognitions(track.trackId, detections, startedAt)
            }

            val trackedDetections = plateTrackManager.collectStableDetections(startedAt)
            val fallbackDetections = if (shouldRunFallback(regions, trackedDetections)) {
                multiScaleDetector.detect(bitmap).also { detections ->
                    if (detections.isNotEmpty()) {
                        Log.d(TAG, "Fallback OCR detections=${detections.joinToString { it.plate }}")
                    } else {
                        Log.v(TAG, "Fallback OCR returned no detections")
                    }
                }
            } else {
                emptyList()
            }

            val detectedPlates = (trackedDetections + fallbackDetections)
                .groupBy { it.plate }
                .mapValues { (_, matches) -> matches.maxBy { it.confidence } }
                .values
                .sortedByDescending { it.confidence }

            sessionManager.processScan(detectedPlates).also { result ->
                val elapsedMs = System.currentTimeMillis() - startedAt
                Log.d(
                    TAG,
                    "Frame processed in ${elapsedMs}ms, regions=${regions.size}, tracks=${tracks.size}, tracked=${trackedDetections.size}, fallback=${fallbackDetections.size}, final=${result.detectedPlates.size}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "processFrame failed", e)
            sessionManager.processScan(emptyList())
        } finally {
            imageProxy.close()
            bitmap.recycle()
        }
    }

    fun resetSession() {
        sessionManager.resetSession()
        plateTrackManager.reset()
        multiScaleDetector.reset()
        framesWithoutRegions = 0
    }

    fun releasePlateTfliteResources() {
        plateRegionDetector.close()
    }

    fun getSessionPlates(): List<String> = sessionManager.getSessionPlates()

    private suspend fun recognizeTrackCrop(
        crop: android.graphics.Bitmap,
        detectedAt: Long,
        regionScore: Float
    ): List<DetectionResult> {
        val rawCandidates = mlKitPlateRecognizer.recognize(crop)
        val detections = toDetectionResults(rawCandidates, detectedAt, regionScore)
        if (detections.isNotEmpty()) return detections

        val enhancedCrop = preprocessor.enhanceForOCR(crop)
        val enhancedCandidates = mlKitPlateRecognizer.recognize(enhancedCrop)
        return toDetectionResults(enhancedCandidates, detectedAt, regionScore)
    }

    private fun toDetectionResults(
        candidates: List<RecognizedTextCandidate>,
        detectedAt: Long,
        regionScore: Float
    ): List<DetectionResult> {
        return detectPlateUseCase(candidates, detectedAt)
            .map { detection ->
                detection.copy(
                    confidence = (detection.confidence * 0.82f + regionScore * 0.18f)
                        .coerceIn(0.72f, 0.99f)
                )
            }
    }

    private fun shouldRunFallback(
        regions: List<PlateRegion>,
        trackedDetections: List<DetectionResult>
    ): Boolean {
        if (trackedDetections.isNotEmpty()) return false
        if (regions.isEmpty()) return true
        return framesWithoutRegions >= 2
    }
}
