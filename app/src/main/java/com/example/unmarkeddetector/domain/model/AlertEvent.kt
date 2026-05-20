package com.example.unmarkeddetector.domain.model

data class AlertEvent(
    val plateRecord: PlateRecord,
    val confidence: Float,
    val detectedAt: Long
)

