package com.example.unmarkeddetector.detection

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class ImagePreprocessor @Inject constructor() {

    fun cropPlate(fullBitmap: Bitmap, plateRect: Rect): Bitmap? {
        if (fullBitmap.isRecycled || plateRect.width() <= 0 || plateRect.height() <= 0) {
            return null
        }
        if (
            plateRect.width() > fullBitmap.width * 0.50f ||
            plateRect.height() > fullBitmap.height * 0.18f
        ) {
            Log.d(LprDebug.TAG, "OCR crop rejected as oversized: rect=$plateRect")
            return null
        }

        val padX = (plateRect.width() * 0.14f).roundToInt().coerceAtLeast(2)
        val padY = (plateRect.height() * 0.14f).roundToInt().coerceAtLeast(2)
        val left = (plateRect.left - padX).coerceAtLeast(0)
        val top = (plateRect.top - padY).coerceAtLeast(0)
        val right = (plateRect.right + padX).coerceAtMost(fullBitmap.width)
        val bottom = (plateRect.bottom + padY).coerceAtMost(fullBitmap.height)
        val width = right - left
        val height = bottom - top

        if (width < 24 || height < 24) {
            Log.w(LprDebug.TAG, "OCR crop too small: ${width}x${height}")
            return null
        }

        return try {
            Bitmap.createBitmap(fullBitmap, left, top, width, height).also {
                Log.d(LprDebug.TAG, "OCR crop: ${it.width}x${it.height} from [$left,$top,$right,$bottom]")
            }
        } catch (error: Exception) {
            Log.w(LprDebug.TAG, "OCR crop failed", error)
            null
        }
    }
}
