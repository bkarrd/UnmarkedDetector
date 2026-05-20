package com.example.unmarkeddetector.detection

import android.graphics.Bitmap
import com.example.unmarkeddetector.domain.model.DetectionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class MultiScaleDetector @Inject constructor(
    private val preprocessor: ImagePreprocessor
) {

    companion object {
        private val QUICK_SCALES = listOf(1.0f, 1.35f)
        private val DEEP_SCALES = listOf(1.8f, 2.4f)
        private const val QUICK_MISS_THRESHOLD_FOR_DEEP_SCAN = 2
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var consecutiveQuickMisses = 0

    suspend fun detect(bitmap: Bitmap): List<DetectionResult> {
        val detectedAt = System.currentTimeMillis()
        val quickResults = detectWithScales(
            bitmap = bitmap,
            scales = QUICK_SCALES,
            detectedAt = detectedAt,
            enhance = false
        )
        if (quickResults.isNotEmpty()) {
            consecutiveQuickMisses = 0
            return quickResults
        }

        consecutiveQuickMisses++
        if (consecutiveQuickMisses < QUICK_MISS_THRESHOLD_FOR_DEEP_SCAN) {
            return emptyList()
        }
        consecutiveQuickMisses = 0

        return detectWithScales(
            bitmap = bitmap,
            scales = DEEP_SCALES,
            detectedAt = detectedAt,
            enhance = true
        )
    }

    fun reset() {
        consecutiveQuickMisses = 0
    }

    private suspend fun detectWithScales(
        bitmap: Bitmap,
        scales: List<Float>,
        detectedAt: Long,
        enhance: Boolean
    ): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()
        for (scale in scales) {
            val scaled = preprocessor.cropCenter(bitmap, scale)
            val processed = if (enhance) preprocessor.enhanceForOCR(scaled) else scaled
            val rawResults = runOCR(processed)
            detections += rawResults.map { plate ->
                DetectionResult(
                    plate = plate,
                    confidence = estimateConfidence(plate, scale, enhance),
                    detectedAt = detectedAt
                )
            }
            if (detections.isNotEmpty() && !enhance) {
                break
            }
        }

        return detections
            .groupBy { it.plate }
            .mapValues { (_, detections) -> detections.maxBy { it.confidence } }
            .values
            .sortedByDescending { it.confidence }
    }

    private suspend fun runOCR(bitmap: Bitmap): List<String> =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(PlateValidator.extractPlates(visionText.text))
                }
                .addOnFailureListener {
                    continuation.resume(emptyList())
                }
        }

    private fun estimateConfidence(plate: String, scale: Float, enhanced: Boolean): Float {
        var confidence = 0.86f
        if (scale > 1f) confidence += 0.04f
        if (scale >= 2f) confidence += 0.03f
        if (enhanced) confidence += 0.02f
        if (plate.length in 6..8) confidence += 0.03f
        return confidence.coerceAtMost(0.99f)
    }
}
