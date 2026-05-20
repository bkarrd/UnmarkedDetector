package com.example.unmarkeddetector.util

import android.graphics.Rect
import android.util.Log
import android.view.Surface
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import kotlin.math.roundToInt

/**
 * Utility do transformacji współrzędnych między różnymi przestrzeniami (coordinate spaces).
 *
 * Pipeline transformacji:
 * 1. YOLO_NORMALIZED [0-1] (wyjście modelu TFLite)
 * 2. MODEL_PIXELS (224x224 - rozmiar wejścia modelu)
 * 3. FRAME_PIXELS (rozmiar ImageProxy z kamery, np. 1440x1080)
 * 4. SCREEN_PIXELS (rozmiar ekranu telefonu, uwzględnia rotację)
 */
object CoordinateTransformer {
    private const val TAG = "CoordinateTransformer"
    private const val MODEL_SIZE = 224 // Rozmiar modelu TFLite (224x224)

    /**
     * Data class reprezentujący prostokąt z informacją o przestrzeni współrzędnych
     */
    data class CoordinateRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val space: CoordinateSpace
    ) {
        val width: Int
            get() = right - left
        val height: Int
            get() = bottom - top

        fun toAndroidRect(): Rect = Rect(left, top, right, bottom)

        override fun toString(): String =
            "Rect($left, $top, $right, $bottom) in ${space.name}"
    }

    /**
     * Enum przestrzeni współrzędnych
     */
    enum class CoordinateSpace {
        YOLO_NORMALIZED,     // [0.0, 1.0] z modelu YOLO
        MODEL_PIXELS,        // Piksele w modelu (224x224)
        FRAME_PIXELS,        // Piksele ImageProxy z kamery
        SCREEN_PIXELS        // Piksele ekranu (uwzględnia rotację)
    }

    /**
     * Konwertuj współrzędne YOLO znormalizowane [0-1] na prostokąt w pikselach ImageProxy.
     *
     * @param xCenter znormalizowany środek X [0-1]
     * @param yCenter znormalizowany środek Y [0-1]
     * @param width znormalizowana szerokość [0-1]
     * @param height znormalizowana wysokość [0-1]
     * @param frameWidth szerokość ImageProxy w pikselach
     * @param frameHeight wysokość ImageProxy w pikselach
     * @return Prostokąt w pikselach ImageProxy
     */
    fun yoloToFramePixels(
        xCenter: Float,
        yCenter: Float,
        width: Float,
        height: Float,
        frameWidth: Int,
        frameHeight: Int
    ): CoordinateRect {
        val cx = xCenter.coerceIn(0f, 1f)
        val cy = yCenter.coerceIn(0f, 1f)
        val w = width.coerceIn(0f, 1f)
        val h = height.coerceIn(0f, 1f)

        val left = ((cx - w / 2f) * frameWidth).roundToInt().coerceIn(0, frameWidth - 1)
        val top = ((cy - h / 2f) * frameHeight).roundToInt().coerceIn(0, frameHeight - 1)
        val right = ((cx + w / 2f) * frameWidth).roundToInt().coerceIn(left + 1, frameWidth)
        val bottom = ((cy + h / 2f) * frameHeight).roundToInt().coerceIn(top + 1, frameHeight)

        return CoordinateRect(left, top, right, bottom, CoordinateSpace.FRAME_PIXELS)
    }

    /**
     * Transformuj współrzędne z pikseli ImageProxy na piksele ekranu PreviewView.
     *
     * @param frameRect Prostokąt w pikselach ImageProxy
     * @param imageProxy ImageProxy z kamery (zawiera rotację)
     * @param previewView PreviewView wyświetlająca obraz
     * @return Prostokąt w pikselach ekranu
     */
    fun frameToScreenPixels(
        frameRect: CoordinateRect,
        imageProxy: ImageProxy,
        previewView: PreviewView
    ): CoordinateRect {
        require(frameRect.space == CoordinateSpace.FRAME_PIXELS) {
            "Expected FRAME_PIXELS but got ${frameRect.space}"
        }

        val imageWidth = imageProxy.width
        val imageHeight = imageProxy.height
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val screenWidth = previewView.width
        val screenHeight = previewView.height

        return transformCoordinates(
            rect = frameRect,
            srcWidth = imageWidth,
            srcHeight = imageHeight,
            dstWidth = screenWidth,
            dstHeight = screenHeight,
            rotationDegrees = rotationDegrees
        )
    }

    /**
     * Transformuj współrzędne z pikseli ImageProxy na piksele ekranu PreviewView.
     * Wersja uproszczona, gdy znane są wymiary z góry.
     *
     * @param frameRect Prostokąt w pikselach ImageProxy
     * @param frameWidth Szerokość ImageProxy
     * @param frameHeight Wysokość ImageProxy
     * @param screenWidth Szerokość ekranu (PreviewView)
     * @param screenHeight Wysokość ekranu (PreviewView)
     * @param rotationDegrees Rotacja obrazu (0, 90, 180, 270)
     * @return Prostokąt w pikselach ekranu
     */
    fun frameToScreenPixels(
        frameRect: CoordinateRect,
        frameWidth: Int,
        frameHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        rotationDegrees: Int = 0
    ): CoordinateRect {
        require(frameRect.space == CoordinateSpace.FRAME_PIXELS) {
            "Expected FRAME_PIXELS but got ${frameRect.space}"
        }

        return transformCoordinates(
            rect = frameRect,
            srcWidth = frameWidth,
            srcHeight = frameHeight,
            dstWidth = screenWidth,
            dstHeight = screenHeight,
            rotationDegrees = rotationDegrees
        )
    }

    /**
     * Bezpośrednia transformacja - wszystkie kroki w jednym
     */
    fun yoloToScreenPixels(
        xCenter: Float,
        yCenter: Float,
        width: Float,
        height: Float,
        imageProxy: ImageProxy,
        previewView: PreviewView
    ): CoordinateRect {
        val frameRect = yoloToFramePixels(
            xCenter = xCenter,
            yCenter = yCenter,
            width = width,
            height = height,
            frameWidth = imageProxy.width,
            frameHeight = imageProxy.height
        )
        return frameToScreenPixels(frameRect, imageProxy, previewView)
    }

    /**
     * Bezpośrednia transformacja - wszystkie kroki w jednym (wersja uproszczona)
     */
    fun yoloToScreenPixels(
        xCenter: Float,
        yCenter: Float,
        width: Float,
        height: Float,
        frameWidth: Int,
        frameHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        rotationDegrees: Int = 0
    ): CoordinateRect {
        val frameRect = yoloToFramePixels(
            xCenter = xCenter,
            yCenter = yCenter,
            width = width,
            height = height,
            frameWidth = frameWidth,
            frameHeight = frameHeight
        )
        return frameToScreenPixels(
            frameRect = frameRect,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            rotationDegrees = rotationDegrees
        )
    }

    /**
     * Wewnętrzna logika transformacji współrzędnych z obsługą rotacji
     */
    private fun transformCoordinates(
        rect: CoordinateRect,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        rotationDegrees: Int
    ): CoordinateRect {
        // Normalizuj rotację do [0, 360)
        val normalizedRotation = (rotationDegrees % 360 + 360) % 360

        // Załóż aspect ratio i wylicz skalowanie
        val srcAspect = srcWidth.toFloat() / srcHeight
        val dstAspect = dstWidth.toFloat() / dstHeight

        // Oblicz skalowanie i offset dla aspect ratio fit
        val (scaleX, scaleY, offsetX, offsetY) = calculateScaleAndOffset(
            srcAspect, dstAspect, srcWidth, srcHeight, dstWidth, dstHeight
        )

        // Transformuj punkt na podstawie rotacji
        return when (normalizedRotation) {
            0 -> transformWithoutRotation(rect, scaleX, scaleY, offsetX, offsetY)
            90 -> transformWith90Rotation(rect, scaleX, scaleY, offsetX, offsetY, srcWidth, srcHeight, dstWidth, dstHeight)
            180 -> transformWith180Rotation(rect, scaleX, scaleY, offsetX, offsetY, dstWidth, dstHeight)
            270 -> transformWith270Rotation(rect, scaleX, scaleY, offsetX, offsetY, srcWidth, srcHeight, dstWidth, dstHeight)
            else -> {
                Log.w(TAG, "Niestandartowa rotacja: $rotationDegrees stopni, uzycie 0")
                transformWithoutRotation(rect, scaleX, scaleY, offsetX, offsetY)
            }
        }
    }

    private fun calculateScaleAndOffset(
        srcAspect: Float,
        dstAspect: Float,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): Array<Float> {
        val scaleX: Float
        val scaleY: Float
        val offsetX: Float
        val offsetY: Float

        if (srcAspect > dstAspect) {
            // Źródło jest szersze - skaluj po wysokości
            scaleX = dstHeight.toFloat() / srcHeight
            scaleY = scaleX
            offsetX = (dstWidth - srcWidth * scaleX) / 2
            offsetY = 0f
        } else {
            // Źródło jest węższe - skaluj po szerokości
            scaleX = dstWidth.toFloat() / srcWidth
            scaleY = scaleX
            offsetX = 0f
            offsetY = (dstHeight - srcHeight * scaleY) / 2
        }

        return arrayOf(scaleX, scaleY, offsetX, offsetY)
    }

    private fun transformWithoutRotation(
        rect: CoordinateRect,
        scaleX: Float,
        scaleY: Float,
        offsetX: Float,
        offsetY: Float
    ): CoordinateRect {
        val left = (rect.left * scaleX + offsetX).roundToInt()
        val top = (rect.top * scaleY + offsetY).roundToInt()
        val right = (rect.right * scaleX + offsetX).roundToInt()
        val bottom = (rect.bottom * scaleY + offsetY).roundToInt()
        return CoordinateRect(left, top, right, bottom, CoordinateSpace.SCREEN_PIXELS)
    }

    private fun transformWith90Rotation(
        rect: CoordinateRect,
        scaleX: Float,
        scaleY: Float,
        offsetX: Float,
        offsetY: Float,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): CoordinateRect {
        // Rotacja 90 stopni: (x, y) -> (srcHeight - y, x)
        val left = ((srcHeight - rect.bottom) * scaleX + offsetX).roundToInt()
        val top = (rect.left * scaleY + offsetY).roundToInt()
        val right = ((srcHeight - rect.top) * scaleX + offsetX).roundToInt()
        val bottom = (rect.right * scaleY + offsetY).roundToInt()
        return CoordinateRect(left, top, right, bottom, CoordinateSpace.SCREEN_PIXELS)
    }

    private fun transformWith180Rotation(
        rect: CoordinateRect,
        scaleX: Float,
        scaleY: Float,
        offsetX: Float,
        offsetY: Float,
        dstWidth: Int,
        dstHeight: Int
    ): CoordinateRect {
        // Rotacja 180 stopni: (x, y) -> (srcWidth - x, srcHeight - y)
        val left = (dstWidth - rect.right * scaleX - offsetX).roundToInt()
        val top = (dstHeight - rect.bottom * scaleY - offsetY).roundToInt()
        val right = (dstWidth - rect.left * scaleX - offsetX).roundToInt()
        val bottom = (dstHeight - rect.top * scaleY - offsetY).roundToInt()
        return CoordinateRect(left, top, right, bottom, CoordinateSpace.SCREEN_PIXELS)
    }

    private fun transformWith270Rotation(
        rect: CoordinateRect,
        scaleX: Float,
        scaleY: Float,
        offsetX: Float,
        offsetY: Float,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): CoordinateRect {
        // Rotacja 270 stopni: (x, y) -> (y, srcWidth - x)
        val left = (rect.top * scaleX + offsetX).roundToInt()
        val top = ((srcWidth - rect.right) * scaleY + offsetY).roundToInt()
        val right = (rect.bottom * scaleX + offsetX).roundToInt()
        val bottom = ((srcWidth - rect.left) * scaleY + offsetY).roundToInt()
        return CoordinateRect(left, top, right, bottom, CoordinateSpace.SCREEN_PIXELS)
    }
}
