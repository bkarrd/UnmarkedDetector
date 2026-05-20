package com.example.unmarkeddetector.domain.model

data class RecognizedTextCandidate(
    val rawText: String,
    val confidence: Float
)

