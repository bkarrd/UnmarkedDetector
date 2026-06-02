package com.example.unmarkeddetector.detection

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.unmarkeddetector.domain.model.DetectionResult
import com.example.unmarkeddetector.domain.usecase.DetectPlateUseCase
import com.example.unmarkeddetector.util.toUprightBitmap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionPipeline @Inject constructor(
    private val preprocessor: ImagePreprocessor,
    private val plateRegionDetector: PlateRegionDetector,
    private val plateTrackManager: PlateTrackManager,
    private val fastPlateOcrRecognizer: FastPlateOcrRecognizer,
    private val detectPlateUseCase: DetectPlateUseCase,
    private val sessionManager: PlateSessionManager,
    private val visualizationManager: VisualizationManager
) {

    companion object {
        private const val TAG = "DetectionPipeline"
        private const val MAX_OCR_TRACKS_PER_FRAME = 2
    }

    suspend fun processFrame(imageProxy: ImageProxy): PlateSessionManager.ScanResult {
        val analysisBitmap = runCatching { imageProxy.toUprightBitmap() }.getOrElse { error ->
            Log.e(TAG, "ImageAnalysis bitmap conversion failed", error)
            imageProxy.close()
            return sessionManager.processScan(emptyList())
        }
        imageProxy.close()

        return try {
            val startedAt = System.currentTimeMillis()
            Log.d(LprDebug.TAG, "Analysis frame: ${analysisBitmap.width}x${analysisBitmap.height}")

            val regions = plateRegionDetector.detect(analysisBitmap)
            val tracks = plateTrackManager.updateRegions(regions, startedAt)
            visualizationManager.publishPlateRegions(
                regions = regions,
                sourceWidth = analysisBitmap.width,
                sourceHeight = analysisBitmap.height
            )
            Log.d(
                LprDebug.TAG,
                "YOLO regions=${regions.size}: ${
                    regions.joinToString { region ->
                        "${region.rect} score=${"%.2f".format(region.score)}"
                    }
                }"
            )

            tracks.asSequence()
                .filter { it.shouldRunOcr }
                .take(MAX_OCR_TRACKS_PER_FRAME)
                .forEach { track ->
                    val crop = preprocessor.cropPlate(analysisBitmap, track.rect) ?: return@forEach
                    try {
                        Log.d(
                            LprDebug.TAG,
                            "Same-frame OCR source=${analysisBitmap.width}x${analysisBitmap.height}, " +
                                "cropRect=${track.rect}"
                        )
                        val detections = recognizeTrackCrop(crop, startedAt, track.regionScore)
                        plateTrackManager.registerRecognitions(track.trackId, detections, startedAt)
                    } finally {
                        crop.recycle()
                    }
                }

            val regionsForOverlay = regionsForVisualization(regions, tracks)
            if (regionsForOverlay != regions) {
                visualizationManager.publishPlateRegions(
                    regions = regionsForOverlay,
                    sourceWidth = analysisBitmap.width,
                    sourceHeight = analysisBitmap.height
                )
            }

            val detectedPlates = plateTrackManager.collectStableDetections(startedAt)
            sessionManager.processScan(detectedPlates).also { result ->
                Log.d(
                    TAG,
                    "Frame processed in ${System.currentTimeMillis() - startedAt}ms, " +
                        "regions=${regions.size}, tracks=${tracks.size}, final=${result.detectedPlates.size}"
                )
            }
        } catch (error: Exception) {
            Log.e(TAG, "processFrame failed", error)
            sessionManager.processScan(emptyList())
        } finally {
            analysisBitmap.recycle()
        }
    }

    fun resetSession() {
        sessionManager.resetSession()
        plateTrackManager.reset()
    }

    fun releaseResources() {
        plateRegionDetector.close()
        fastPlateOcrRecognizer.close()
    }

    fun getSessionPlates(): List<String> = sessionManager.getSessionPlates()

    private suspend fun recognizeTrackCrop(
        crop: Bitmap,
        detectedAt: Long,
        regionScore: Float
    ): List<DetectionResult> {
        val candidate = fastPlateOcrRecognizer.recognize(crop) ?: return emptyList()
        return detectPlateUseCase(listOf(candidate), detectedAt).map { detection ->
            detection.copy(
                confidence = (detection.confidence * 0.85f + regionScore * 0.15f)
                    .coerceIn(0.72f, 0.99f)
            )
        }
    }

    private fun regionsForVisualization(
        regions: List<PlateRegion>,
        tracks: List<PlateTrackSnapshot>
    ): List<PlateRegion> {
        if (regions.isEmpty() || tracks.isEmpty()) return regions

        return regions.map { region ->
            val matchedTrack = tracks.firstOrNull { track ->
                plateTrackManager.leadingOcrText(track.trackId) != null &&
                    iou(region.rect, track.rect) > 0.2f
            }
            region.copy(plateText = matchedTrack?.let { plateTrackManager.leadingOcrText(it.trackId) })
        }
    }

    private fun iou(a: Rect, b: Rect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val intersection = (right - left).coerceAtLeast(0) * (bottom - top).coerceAtLeast(0)
        val union = a.width() * a.height() + b.width() * b.height() - intersection
        return intersection.toFloat() / (union + 1e-6f)
    }

}
