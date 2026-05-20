package com.example.unmarkeddetector.domain.usecase

import com.example.unmarkeddetector.domain.model.AlertEvent
import com.example.unmarkeddetector.domain.model.PlateRecord
import com.example.unmarkeddetector.domain.repository.AlertDispatcher
import javax.inject.Inject

class TriggerAlertUseCase @Inject constructor(
    private val alertDispatcher: AlertDispatcher
) {
    suspend operator fun invoke(record: PlateRecord, confidence: Float): AlertEvent {
        val event = AlertEvent(
            plateRecord = record,
            confidence = confidence,
            detectedAt = System.currentTimeMillis()
        )
        alertDispatcher.dispatch(event)
        return event
    }
}
