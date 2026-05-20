package com.example.unmarkeddetector.detection

import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.unmarkeddetector.domain.model.DetectionVisualizationData
import com.example.unmarkeddetector.ui.common.GraphicOverlay
import com.example.unmarkeddetector.util.CoordinateTransformer
import kotlin.math.roundToInt

/**
 * Helper do transformacji danych detekcji tablicy na dane wizualizacyjne.
 * Obsługuje konwersję współrzędnych z różnych przestrzeni na ekran telefonu.
 */
object DetectionVisualizationHelper {
    private const val TAG = "DetectionVisHelper"

    /**
     * Transformuj prostokąt region tablicy z ImageProxy na współrzędne ekranu.
     *
     * @param regionRect Prostokąt w pikselach ImageProxy
     * @param imageProxy ImageProxy z kamery (zawiera rotację)
     * @param screenWidth Szerokość ekranu (PreviewView)
     * @param screenHeight Wysokość ekranu (PreviewView)
     * @return Prostokąt w pikselach ekranu lub null jeśli wymiary są niewystarczające
     */
    fun transformRegionToScreen(
        regionRect: Rect,
        imageProxy: ImageProxy,
        screenWidth: Int,
        screenHeight: Int
    ): Rect? {
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.w(TAG, "Invalid screen dimensions: ${screenWidth}x${screenHeight}")
            return null
        }

        if (regionRect.width() <= 0 || regionRect.height() <= 0) {
            Log.w(TAG, "Invalid region rect: width=${regionRect.width()}, height=${regionRect.height()}")
            return null
        }

        return try {
            val coordRect = CoordinateTransformer.CoordinateRect(
                left = regionRect.left,
                top = regionRect.top,
                right = regionRect.right,
                bottom = regionRect.bottom,
                space = CoordinateTransformer.CoordinateSpace.FRAME_PIXELS
            )

            val screenRect = CoordinateTransformer.frameToScreenPixels(
                frameRect = coordRect,
                frameWidth = imageProxy.width,
                frameHeight = imageProxy.height,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees
            )

            screenRect.toAndroidRect()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to transform region to screen", e)
            null
        }
    }

    /**
     * Transformuj znormalizowane współrzędne YOLO na współrzędne ekranu.
     *
     * @param xCenter Znormalizowany środek X [0-1]
     * @param yCenter Znormalizowany środek Y [0-1]
     * @param width Znormalizowana szerokość [0-1]
     * @param height Znormalizowana wysokość [0-1]
     * @param imageProxy ImageProxy z kamery
     * @param screenWidth Szerokość ekranu
     * @param screenHeight Wysokość ekranu
     * @return Prostokąt w pikselach ekranu lub null
     */
    fun transformYoloToScreen(
        xCenter: Float,
        yCenter: Float,
        width: Float,
        height: Float,
        imageProxy: ImageProxy,
        screenWidth: Int,
        screenHeight: Int
    ): Rect? {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return null
        }

        return try {
            val screenRect = CoordinateTransformer.yoloToScreenPixels(
                xCenter = xCenter,
                yCenter = yCenter,
                width = width,
                height = height,
                frameWidth = imageProxy.width,
                frameHeight = imageProxy.height,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees
            )

            screenRect.toAndroidRect()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to transform YOLO to screen", e)
            null
        }
    }

    /**
     * Utwórz DetectionBox do wizualizacji na podstawie danych detekcji.
     *
     * @param screenRect Prostokąt w pikselach ekranu
     * @param confidence Pewność detekcji [0-1]
     * @param isAlert Czy to alert
     * @param label Label do wyświetlenia
     * @return GraphicOverlay.DetectionBox lub null
     */
    fun createDetectionBox(
        screenRect: Rect?,
        confidence: Float,
        isAlert: Boolean = false,
        label: String = ""
    ): GraphicOverlay.DetectionBox? {
        if (screenRect == null || screenRect.width() <= 0 || screenRect.height() <= 0) {
            return null
        }

        val color = if (isAlert) Color.RED else Color.GREEN
        val displayLabel = if (label.isNotEmpty()) {
            if (confidence > 0) {
                "$label ${(confidence * 100).toInt()}%"
            } else {
                label
            }
        } else if (confidence > 0) {
            "${(confidence * 100).toInt()}%"
        } else {
            ""
        }

        return GraphicOverlay.DetectionBox(
            rect = screenRect,
            confidence = confidence.coerceIn(0f, 1f),
            isAlert = isAlert,
            label = displayLabel,
            color = color
        )
    }

    /**
     * Filtruj bounding boxy - usuń za małe lub poza ekranem.
     */
    fun filterValidBoxes(
        boxes: List<GraphicOverlay.DetectionBox>,
        screenWidth: Int,
        screenHeight: Int,
        minSize: Int = 20
    ): List<GraphicOverlay.DetectionBox> {
        return boxes.filter { box ->
            box.rect.width() >= minSize &&
            box.rect.height() >= minSize &&
            box.rect.left < screenWidth &&
            box.rect.top < screenHeight &&
            box.rect.right > 0 &&
            box.rect.bottom > 0
        }
    }
}
