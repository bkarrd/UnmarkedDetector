package com.example.unmarkeddetector.detection

import android.graphics.Rect

data class PlateRegion(
    val rect: Rect,
    val score: Float,
    /** Tekst z TFLite CRNN ([LicensePlateDetector.recognizePlateText]), jeśli dostępny. */
    val plateText: String? = null,
    val normalizedXCenter: Float? = null,
    val normalizedYCenter: Float? = null,
    val normalizedWidth: Float? = null,
    val normalizedHeight: Float? = null
)

data class PlateTrackSnapshot(
    val trackId: Int,
    val rect: Rect,
    val regionScore: Float,
    val consecutiveHits: Int,
    val shouldRunOcr: Boolean
)
