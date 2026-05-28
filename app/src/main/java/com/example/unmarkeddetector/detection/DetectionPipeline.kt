package com.example.unmarkeddetector.detection

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.unmarkeddetector.domain.model.DetectionResult
import com.example.unmarkeddetector.domain.model.RecognizedTextCandidate
import com.example.unmarkeddetector.domain.usecase.DetectPlateUseCase
import com.example.unmarkeddetector.util.toRotatedBitmap
import javax.inject.Inject
import javax.inject.Singleton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Singleton
class DetectionPipeline @Inject constructor(
    private val preprocessor: ImagePreprocessor,
    private val plateRegionDetector: PlateRegionDetector,
    private val plateTrackManager: PlateTrackManager,
    private val detectPlateUseCase: DetectPlateUseCase,
    private val sessionManager: PlateSessionManager,
    private val visualizationManager: VisualizationManager
) {

    companion object {
        private const val TAG = "DetectionPipeline"
    }

    private var framesWithoutRegions = 0

    suspend fun processFrame(imageProxy: ImageProxy): PlateSessionManager.ScanResult {
        val originalBitmap = runCatching { imageProxy.toRotatedBitmap() }.getOrElse { err ->
            Log.e(TAG, "toBitmap() nie powiodło się", err)
            imageProxy.close()
            return sessionManager.processScan(emptyList())
        }

        // Skaluj w dół do max 1200px po dłuższym boku
        val workBitmap = if (originalBitmap.width > 1200 || originalBitmap.height > 1200) {
            val scale = 720f / maxOf(originalBitmap.width, originalBitmap.height)
            val w = (originalBitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (originalBitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(originalBitmap, w, h, true)
            originalBitmap.recycle()  // dopiero TERAZ niszczymy oryginał
            Log.d(TAG, "Bitmap przeskalowany → ${w}x${h}")
            scaled
        } else {
            originalBitmap
        }

        return try {
            Log.d(LprDebug.TAG, "a) klatka kamery (bitmap): ${workBitmap.width}x${workBitmap.height}")
            val startedAt = System.currentTimeMillis()
            var regions = plateRegionDetector.detect(workBitmap)  // ← workBitmap

            if (regions.isEmpty()) {
                framesWithoutRegions++
            } else {
                framesWithoutRegions = 0
            }

            val tracks = plateTrackManager.updateRegions(regions, startedAt)

            tracks.filter { it.shouldRunOcr }.take(3).forEach { track ->
                val crop = preprocessor.cropPlate(workBitmap, track.rect) ?: return@forEach  // ← workBitmap
                val detections = recognizeTrackCrop(crop, startedAt, track.regionScore)
                plateTrackManager.registerRecognitions(track.trackId, detections, startedAt)
            }

            regions = regionsForVisualization(regions, tracks)
            visualizationManager.publishPlateRegions(
                regions = regions,
                sourceWidth = workBitmap.width,   // ← workBitmap
                sourceHeight = workBitmap.height  // ← workBitmap
            )

            val trackedDetections = plateTrackManager.collectStableDetections(startedAt)
            val fallbackDetections = if (shouldRunFallback(regions, trackedDetections)) {
                runTfliteFallback(workBitmap, startedAt)  // ← workBitmap
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
                    "Frame processed in ${elapsedMs}ms, regions=${regions.size}, tracks=${tracks.size}, " +
                            "tracked=${trackedDetections.size}, fallback=${fallbackDetections.size}, " +
                            "final=${result.detectedPlates.size}, ocrModel=${plateRegionDetector.isRecognitionModelLoaded()}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "processFrame failed", e)
            sessionManager.processScan(emptyList())
        } finally {
            imageProxy.close()
            if (!workBitmap.isRecycled) workBitmap.recycle()
        }
    }

    fun resetSession() {
        sessionManager.resetSession()
        plateTrackManager.reset()
        framesWithoutRegions = 0
    }

    fun releasePlateTfliteResources() {
        plateRegionDetector.close()
    }

    fun getSessionPlates(): List<String> = sessionManager.getSessionPlates()

    private fun recognizeTrackCrop(
        crop: android.graphics.Bitmap,
        detectedAt: Long,
        regionScore: Float
    ): List<DetectionResult> {
        val rawCandidates = recognizeWithTflite(crop)
        val detections = toDetectionResults(rawCandidates, detectedAt, regionScore)
        if (detections.isNotEmpty()) return detections

        val enhancedCrop = preprocessor.enhanceForOCR(crop)
        val enhancedCandidates = recognizeWithTflite(enhancedCrop)
        if (enhancedCrop !== crop) {
            enhancedCrop.recycle()
        }
        return toDetectionResults(enhancedCandidates, detectedAt, regionScore)
    }

    private fun recognizeWithTflite(crop: android.graphics.Bitmap): List<RecognizedTextCandidate> {
        return recognizeWithMlKit(crop)
    }

    private fun recognizeWithMlKit(crop: android.graphics.Bitmap): List<RecognizedTextCandidate> {
        val result = mutableListOf<RecognizedTextCandidate>()
        val latch = CountDownLatch(1)

        val inputImage = InputImage.fromBitmap(crop, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text.trim()
                if (raw.isNotBlank()) {
                    Log.d("OCR_RAW", "ML Kit zwrócił: '$raw'")
                    val cleaned = PlateValidator.cleanOCRResult(raw)
                    val plates = PlateValidator.extractPlates(cleaned)
                    plates.forEach { plate ->
                        result += RecognizedTextCandidate(rawText = plate, confidence = 0.88f)
                    }
                    if (plates.isEmpty()) {
                        Log.w("OCR_RAW", "Validator odrzucił: '$cleaned'")
                    }
                }
                latch.countDown()
            }
            .addOnFailureListener { error ->
                Log.e("OCR_RAW", "ML Kit błąd: ${error.message}")
                latch.countDown()
            }

        latch.await(300L, TimeUnit.MILLISECONDS)
        return result
    }

    private fun runTfliteFallback(bitmap: android.graphics.Bitmap, detectedAt: Long): List<DetectionResult> {
        val regions = plateRegionDetector.detectCenterFrame(bitmap)
        if (regions.isEmpty()) {
            Log.v(TAG, "Fallback TFLite: brak regionów w centrum klatki")
            return emptyList()
        }

        val detections = mutableListOf<DetectionResult>()
        regions.take(2).forEach { region ->
            val crop = preprocessor.cropPlate(bitmap, region.rect) ?: return@forEach
            val ocrResults = recognizeTrackCrop(crop, detectedAt, region.score)
            detections += ocrResults
        }

        if (detections.isNotEmpty()) {
            Log.d(TAG, "Fallback TFLite detections=${detections.joinToString { it.plate }}")
        }
        return detections
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
            val text = matchedTrack?.let { plateTrackManager.leadingOcrText(it.trackId) }
            if (text.isNullOrBlank()) region else region.copy(plateText = text)
        }
    }

    private fun iou(a: android.graphics.Rect, b: android.graphics.Rect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val intersection = maxOf(0, right - left) * maxOf(0, bottom - top)
        val areaA = a.width() * a.height()
        val areaB = b.width() * b.height()
        return intersection.toFloat() / (areaA + areaB - intersection + 1e-6f)
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
