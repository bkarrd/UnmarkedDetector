package com.example.unmarkeddetector.detection

import android.graphics.Bitmap
import android.graphics.Rect
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class PlateRegionDetector @Inject constructor(
    private val detector: LicensePlateDetector
) {

    fun detect(bitmap: Bitmap): List<PlateRegion> {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return emptyList()
        }

        return detector.detect(bitmap)
            .mapNotNull { detection ->
                val rect = Rect(
                    detection.box.left.roundToInt().coerceIn(0, bitmap.width - 1),
                    detection.box.top.roundToInt().coerceIn(0, bitmap.height - 1),
                    detection.box.right.roundToInt().coerceIn(1, bitmap.width),
                    detection.box.bottom.roundToInt().coerceIn(1, bitmap.height)
                )
                if (rect.width() <= 1 || rect.height() <= 1) return@mapNotNull null

                PlateRegion(
                    rect = rect,
                    score = detection.confidence
                )
            }
    }

    fun close() = detector.close()
}
