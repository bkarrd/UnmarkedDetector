package com.example.unmarkeddetector.detection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class ImagePreprocessor @Inject constructor() {

    fun enhanceForOCR(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        1.45f, 0f, 0f, 0f, -18f,
                        0f, 1.45f, 0f, 0f, -18f,
                        0f, 0f, 1.45f, 0f, -18f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    fun cropCenter(bitmap: Bitmap, zoomFactor: Float): Bitmap {
        if (zoomFactor <= 1f) return bitmap
        val newWidth = (bitmap.width / zoomFactor).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height / zoomFactor).toInt().coerceAtLeast(1)
        val x = ((bitmap.width - newWidth) / 2).coerceAtLeast(0)
        val y = ((bitmap.height - newHeight) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(bitmap, x, y, newWidth, newHeight)
        return Bitmap.createScaledBitmap(cropped, bitmap.width, bitmap.height, true)
    }

    /**
     * Wycina prostokąt tablicy z pełnej klatki (min. 32 px na bok dla modelu CRNN).
     * Przy szerokości lub wysokości &lt; 40 px dodaje 25% marginesu z każdej strony (padding).
     */
    fun cropPlate(fullBitmap: Bitmap, plateRect: Rect): Bitmap? = cropPlateForMlKit(fullBitmap, plateRect)

    @Deprecated("Użyj cropPlate", ReplaceWith("cropPlate(fullBitmap, plateRect)"))
    fun cropPlateForMlKit(fullBitmap: Bitmap, plateRect: Rect): Bitmap? {
        var left = plateRect.left
        var top = plateRect.top
        var right = plateRect.right
        var bottom = plateRect.bottom
        var w = right - left
        var h = bottom - top

        // ── NOWE: wymuś orientację poziomą (tablica zawsze szersza niż wysoka) ──
        if (h > w) {
            // Zamień wymiary i wycentruj
            val centerX = (left + right) / 2
            val centerY = (top + bottom) / 2
            val halfW = h / 2  // nowa szerokość = stara wysokość
            val halfH = (w * 0.35f).toInt().coerceAtLeast(20)  // nowa wysokość = ~35% starej szerokości
            left = (centerX - halfW).coerceAtLeast(0)
            top = (centerY - halfH).coerceAtLeast(0)
            right = (centerX + halfW).coerceAtMost(fullBitmap.width)
            bottom = (centerY + halfH).coerceAtMost(fullBitmap.height)
            w = right - left
            h = bottom - top
        }

        if (w < 40 || h < 40) {
            val padX = (w * 0.25f).roundToInt().coerceAtLeast(1)
            val padY = (h * 0.25f).roundToInt().coerceAtLeast(1)
            left = (left - padX).coerceAtLeast(0)
            top = (top - padY).coerceAtLeast(0)
            right = (right + padX).coerceAtMost(fullBitmap.width)
            bottom = (bottom + padY).coerceAtMost(fullBitmap.height)
            w = right - left
            h = bottom - top
        }
        // reszta funkcji bez zmian...
        return try {
            val bmp = Bitmap.createBitmap(fullBitmap, left, top, w, h)
            if (bmp.width < 32 || bmp.height < 32) {
                Log.w(LprDebug.TAG, "c) crop OCR odrzucony: ${bmp.width}x${bmp.height} < 32px")
                bmp.recycle()
                return null
            }
            Log.d(LprDebug.TAG, "c) crop OCR final: ${bmp.width}x${bmp.height} (srcRect=$left,$top,$right,$bottom)")
            bmp
        } catch (e: Exception) {
            Log.w(LprDebug.TAG, "c) błąd createBitmap crop", e)
            null
        }
    }

    /**
     * Bezpieczne wycinanie regionu z bitmapy z walidacją minimalnego rozmiaru.
     *
     * Logika:
     * 1. Jeśli szerokość lub wysokość < 40px → dodaj 25% padding z każdej strony
     * 2. Jeśli po paddingu wciąż < 32px → zwróć null (ML Kit wymaga min 32px)
     * 3. Clamp do granic bitmapy
     *
     * @param fullBitmap Pełna bitmapa do wycinania
     * @param regionRect Prostokąt regionu do wycinania
     * @param minDimension Minimalna wymagana wymiar (domyślnie 32px dla ML Kit)
     * @param paddingPercent Procent paddingu do dodania gdy wymiar < 40px (domyślnie 25%)
     * @return Wycięty fragment bitmapy lub null jeśli wymiary są niewystarczające
     */
    fun cropRegionSafe(
        fullBitmap: Bitmap,
        regionRect: Rect,
        minDimension: Int = 32,
        paddingPercent: Float = 0.25f
    ): Bitmap? {
        if (regionRect.width() <= 0 || regionRect.height() <= 0) {
            Log.w(
                LprDebug.TAG,
                "cropRegionSafe: Invalid region rect: width=${regionRect.width()}, height=${regionRect.height()}"
            )
            return null
        }

        var left = regionRect.left
        var top = regionRect.top
        var right = regionRect.right
        var bottom = regionRect.bottom

        var width = right - left
        var height = bottom - top

        // Krok 1: Dodaj padding jeśli wymiary są małe
        if (width < 40 || height < 40) {
            val padX = (width * paddingPercent).roundToInt().coerceAtLeast(1)
            val padY = (height * paddingPercent).roundToInt().coerceAtLeast(1)

            left = (left - padX).coerceAtLeast(0)
            top = (top - padY).coerceAtLeast(0)
            right = (right + padX).coerceAtMost(fullBitmap.width)
            bottom = (bottom + padY).coerceAtMost(fullBitmap.height)

            width = right - left
            height = bottom - top

            Log.d(
                LprDebug.TAG,
                "cropRegionSafe: Added padding, new size: ${width}x${height}"
            )
        }

        // Krok 2: Walidacja minimalnego wymiaru
        if (width < minDimension || height < minDimension) {
            Log.w(
                LprDebug.TAG,
                "cropRegionSafe: Dimensions too small after padding: ${width}x${height} (min: $minDimension)"
            )
            return null
        }

        // Krok 3: Wytnij bitmapę
        return try {
            Bitmap.createBitmap(fullBitmap, left, top, width, height).also {
                Log.d(
                    LprDebug.TAG,
                    "cropRegionSafe: Success - cropped to ${it.width}x${it.height} from region ($left,$top,$right,$bottom)"
                )
            }
        } catch (e: Exception) {
            Log.w(
                LprDebug.TAG,
                "cropRegionSafe: Error creating bitmap from ($left,$top,$width,$height)",
                e
            )
            null
        }
    }
}
