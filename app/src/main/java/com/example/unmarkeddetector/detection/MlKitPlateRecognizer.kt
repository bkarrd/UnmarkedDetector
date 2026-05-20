package com.example.unmarkeddetector.detection

import android.graphics.Bitmap
import android.util.Log
import com.example.unmarkeddetector.domain.model.RecognizedTextCandidate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.tasks.await

@Singleton
class MlKitPlateRecognizer @Inject constructor() {

    companion object {
        private const val TAG = "MlKitPlateRecognizer"
        /** ML Kit działa znacznie lepiej, gdy krótszy bok cropa ma co najmniej ~288 px. */
        private const val OCR_TARGET_MIN_SIDE = 288
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): List<RecognizedTextCandidate> {
        val scaled = upscaleForOcrIfNeeded(bitmap)
        val recycleScaled = scaled !== bitmap
        return try {
            val inputImage = InputImage.fromBitmap(scaled, 0)
            val text = recognizer.process(inputImage).await()
            Log.v(TAG, "ML Kit blocks=${text.textBlocks.size}, textLen=${text.text.length}")

            val rawCombined = text.text.trim()
            if (rawCombined.isNotBlank() && text.textBlocks.isEmpty()) {
                Log.d(LprDebug.TAG, "ML Kit: pełny tekst bez bloków: \"${rawCombined.take(120)}\"")
            }

            buildList {
                if (rawCombined.isNotBlank()) {
                    add(
                        RecognizedTextCandidate(
                            rawText = rawCombined,
                            confidence = heuristicConfidence(rawCombined, isCombined = true)
                        )
                    )
                }

                text.textBlocks.forEach { block ->
                    val blockText = block.text.trim()
                    if (blockText.isNotBlank()) {
                        add(
                            RecognizedTextCandidate(
                                rawText = blockText,
                                confidence = heuristicConfidence(blockText, isCombined = true)
                            )
                        )
                    }

                    block.lines.forEach { line ->
                        val lineText = line.text.trim()
                        if (lineText.isNotBlank()) {
                            add(
                                RecognizedTextCandidate(
                                    rawText = lineText,
                                    confidence = heuristicConfidence(lineText, isCombined = true)
                                )
                            )
                        }

                        if (line.elements.isNotEmpty()) {
                            val joined = line.elements.joinToString(separator = "") { it.text.trim() }
                            val spaced = line.elements.joinToString(separator = " ") { it.text.trim() }
                            if (joined.isNotBlank()) {
                                add(
                                    RecognizedTextCandidate(
                                        rawText = joined,
                                        confidence = heuristicConfidence(joined, isCombined = false)
                                    )
                                )
                            }
                            if (spaced.isNotBlank()) {
                                add(
                                    RecognizedTextCandidate(
                                        rawText = spaced,
                                        confidence = heuristicConfidence(spaced, isCombined = false)
                                    )
                                )
                            }
                        }
                    }
                }
            }
                .map { candidate ->
                    candidate.copy(rawText = PlateValidator.cleanOCRResult(candidate.rawText))
                }
                .filter { it.rawText.isNotBlank() }
                .filter { looksPlateLike(it.rawText) }
                .also { filtered ->
                    if (rawCombined.isNotBlank() && filtered.isEmpty()) {
                        Log.d(
                            LprDebug.TAG,
                            "ML Kit: odrzucono po filtrach looksPlateLike, cleaned=\"${
                                PlateValidator.cleanOCRResult(rawCombined).take(80)
                            }\""
                        )
                    }
                }
                .sortedByDescending { it.confidence }
                .distinctBy { it.rawText.uppercase().replace("\\s".toRegex(), "") }
                .take(12)
        } finally {
            if (recycleScaled) {
                scaled.recycle()
            }
        }
    }

    private fun upscaleForOcrIfNeeded(source: Bitmap): Bitmap {
        val minSide = min(source.width, source.height)
        if (minSide >= OCR_TARGET_MIN_SIDE) return source
        val scale = OCR_TARGET_MIN_SIDE.toFloat() / max(1, minSide)
        val nw = (source.width * scale).roundToInt().coerceIn(32, 4000)
        val nh = (source.height * scale).roundToInt().coerceIn(32, 4000)
        Log.d(LprDebug.TAG, "OCR upscale: ${source.width}x${source.height} -> ${nw}x${nh}")
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }

    private fun heuristicConfidence(text: String, isCombined: Boolean): Float {
        val normalized = text.uppercase().replace("\\s".toRegex(), "")
        var score = 0.62f
        if (normalized.length in 6..8) score += 0.14f
        if (normalized.length in 4..10) score += 0.04f
        if (normalized.any { it.isDigit() }) score += 0.08f
        if (normalized.count { it.isLetter() } >= 2) score += 0.08f
        if (normalized.matches(Regex("[A-Z0-9\\s-]+"))) score += 0.08f
        if (isCombined) score += 0.04f
        return score.coerceAtMost(0.96f)
    }

    private fun looksPlateLike(text: String): Boolean {
        val normalized = text.uppercase().replace("\\s".toRegex(), "")
        if (normalized.length !in 4..12) return false
        val digits = normalized.count { it.isDigit() }
        val letters = normalized.count { it.isLetter() }
        return digits >= 1 && letters >= 2
    }
}
