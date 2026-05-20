package com.example.unmarkeddetector.domain.usecase

import com.example.unmarkeddetector.detection.PlateValidator
import com.example.unmarkeddetector.domain.model.DetectionResult
import com.example.unmarkeddetector.domain.model.RecognizedTextCandidate
import javax.inject.Inject

class DetectPlateUseCase @Inject constructor() {

    operator fun invoke(
        candidates: List<RecognizedTextCandidate>,
        detectedAt: Long = System.currentTimeMillis()
    ): List<DetectionResult> {
        return candidates
            .filter { it.confidence >= 0.80f }
            .flatMap { candidate ->
                PlateValidator.extractPlates(candidate.rawText).map { plate ->
                    DetectionResult(
                        plate = plate,
                        confidence = candidate.confidence,
                        detectedAt = detectedAt
                    )
                }
            }
            .groupBy { it.plate }
            .map { (_, detections) ->
                DetectionResult(
                    plate = detections.first().plate,
                    confidence = detections.maxOf { it.confidence },
                    detectedAt = detectedAt
                )
            }
            .sortedByDescending { it.confidence }
    }
}
