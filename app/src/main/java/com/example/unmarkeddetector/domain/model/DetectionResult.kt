package com.example.unmarkeddetector.domain.model

data class DetectionResult(
    val plate: String,
    val confidence: Float,
    val detectedAt: Long
)

