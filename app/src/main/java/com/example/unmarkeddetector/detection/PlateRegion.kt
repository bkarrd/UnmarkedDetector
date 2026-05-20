package com.example.unmarkeddetector.detection

import android.graphics.Rect

data class PlateRegion(
    val rect: Rect,
    val score: Float
)

data class PlateTrackSnapshot(
    val trackId: Int,
    val rect: Rect,
    val regionScore: Float,
    val consecutiveHits: Int,
    val shouldRunOcr: Boolean
)
