package com.example.unmarkeddetector.detection

import com.example.unmarkeddetector.domain.model.DetectionResult
import com.example.unmarkeddetector.domain.model.PlateRecord
import com.example.unmarkeddetector.domain.repository.PlateRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlateSessionManager @Inject constructor(
    private val plateRepository: PlateRepository
) {

    data class AlertMatch(
        val record: PlateRecord,
        val confidence: Float
    )

    data class ScanResult(
        val detectedPlates: List<String>,
        val newUniquePlates: List<String>,
        val alertMatches: List<AlertMatch>,
        val totalUniqueInSession: Int
    )

    private val sessionPlates = linkedSetOf<String>()
    private val alertCooldowns = mutableMapOf<String, Long>()
    private val alertCooldownMs = 60_000L

    suspend fun processScan(detectedPlates: List<DetectionResult>): ScanResult {
        val now = System.currentTimeMillis()
        val normalizedDetections = detectedPlates
            .groupBy { it.plate }
            .mapValues { (_, matches) -> matches.maxBy { it.confidence } }
            .values
            .sortedByDescending { it.confidence }

        val newUnique = mutableListOf<String>()
        val alertMatches = mutableListOf<AlertMatch>()

        normalizedDetections.forEach { detection ->
            if (sessionPlates.add(detection.plate)) {
                newUnique += detection.plate
            }

            val lastAlertAt = alertCooldowns[detection.plate] ?: 0L
            if (now - lastAlertAt < alertCooldownMs) return@forEach

            val record = plateRepository.findByPlate(detection.plate) ?: return@forEach
            alertMatches += AlertMatch(record = record, confidence = detection.confidence)
            alertCooldowns[detection.plate] = now
        }

        return ScanResult(
            detectedPlates = normalizedDetections.map { it.plate },
            newUniquePlates = newUnique,
            alertMatches = alertMatches,
            totalUniqueInSession = sessionPlates.size
        )
    }

    fun resetSession() {
        sessionPlates.clear()
        alertCooldowns.clear()
    }

    fun getSessionPlates(): List<String> = sessionPlates.toList()
}
