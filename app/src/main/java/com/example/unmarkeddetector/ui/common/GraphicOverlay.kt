package com.example.unmarkeddetector.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Custom View do rysowania bounding boxów na kanwie nakładanej na PreviewView.
 * Obsługuje rotację ekranu i transformacje współrzędnych.
 *
 * Współrzędne wejściowe mogą być w jednostkach screen-space (piksele ekranu).
 */
class GraphicOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DetectionBox(
        val rect: Rect,
        val confidence: Float = 0.9f,
        val isAlert: Boolean = false,
        val label: String = "",
        val color: Int = Color.GREEN
    )

    /**
     * Konfiguracja stylizacji rysowania
     */
    data class GraphicConfig(
        val boundingBoxStrokeWidth: Float = 4f,
        val boundingBoxColor: Int = Color.GREEN,
        val alertBoxColor: Int = Color.RED,
        val textSize: Float = 32f,
        val textColor: Int = Color.WHITE,
        val confidenceThreshold: Float = 0.5f,
        val enableConfidenceLabel: Boolean = true,
        val labelBackground: Boolean = true,
        val labelBackgroundColor: Int = Color.BLACK
    )

    companion object {
        private const val TAG = "GraphicOverlay"
    }

    // Konfiguracja
    var config = GraphicConfig()
        set(value) {
            field = value
            invalidate()
        }

    // Kolekcja bounding boxów do narysowania (thread-safe)
    private val detectionBoxes = mutableListOf<DetectionBox>()
    private val boxLock = Object()

    // Paints
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
    }

    /**
     * Ustaw bounding boxy do narysowania.
     * Metoda jest thread-safe.
     */
    fun setDetectionBoxes(boxes: List<DetectionBox>) {
        synchronized(boxLock) {
            detectionBoxes.clear()
            detectionBoxes.addAll(boxes)
        }
        postInvalidate() // Bezpieczne invalidate z dowolnego wątku
    }

    /**
     * Dodaj nowy bounding box do listy.
     */
    fun addDetectionBox(box: DetectionBox) {
        synchronized(boxLock) {
            detectionBoxes.add(box)
        }
        postInvalidate()
    }

    /**
     * Wyczyść wszystkie bounding boxy.
     */
    fun clearDetectionBoxes() {
        synchronized(boxLock) {
            detectionBoxes.clear()
        }
        postInvalidate()
    }

    /**
     * Uzyskaj kopię aktualnych bounding boxów.
     */
    fun getDetectionBoxes(): List<DetectionBox> {
        synchronized(boxLock) {
            return detectionBoxes.toList()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        synchronized(boxLock) {
            detectionBoxes.forEach { box ->
                drawBoundingBox(canvas, box)
            }
        }
    }

    private fun drawBoundingBox(canvas: Canvas, box: DetectionBox) {
        if (box.rect.width() <= 0 || box.rect.height() <= 0) {
            return
        }

        val color = if (box.isAlert) config.alertBoxColor else box.color
        boxPaint.color = color
        boxPaint.strokeWidth = config.boundingBoxStrokeWidth

        // Narysuj prostokąt
        canvas.drawRect(
            box.rect.left.toFloat(),
            box.rect.top.toFloat(),
            box.rect.right.toFloat(),
            box.rect.bottom.toFloat(),
            boxPaint
        )

        // Narysuj label jeśli jest dostępny
        if (box.label.isNotEmpty() || config.enableConfidenceLabel) {
            drawLabel(canvas, box, color)
        }
    }

    private fun drawLabel(canvas: Canvas, box: DetectionBox, boxColor: Int) {
        val labelHeight = 32f
        val labelPadding = 8f
        val labelMargin = 2f

        // Tekst labelu
        val text = if (box.label.isNotEmpty()) {
            if (config.enableConfidenceLabel) {
                "${box.label} ${(box.confidence * 100).toInt()}%"
            } else {
                box.label
            }
        } else if (config.enableConfidenceLabel) {
            "${(box.confidence * 100).toInt()}%"
        } else {
            return
        }

        labelTextPaint.textSize = config.textSize
        labelTextPaint.color = config.textColor

        val textBounds = android.graphics.Rect()
        labelTextPaint.getTextBounds(text, 0, text.length, textBounds)

        val labelLeft = box.rect.left + labelMargin
        val labelTop = (box.rect.top - labelHeight - labelMargin).coerceAtLeast(0f).toInt()
        val labelWidth = textBounds.width() + labelPadding * 2
        val labelRight = labelLeft + labelWidth
        val labelBottom = labelTop + labelHeight.toInt()

        // Rysuj tło labelu jeśli włączone
        if (config.labelBackground) {
            labelBackgroundPaint.color = config.labelBackgroundColor
            labelBackgroundPaint.alpha = 200 // Semi-transparent
            canvas.drawRect(
                labelLeft.toFloat(),
                labelTop.toFloat(),
                labelRight.toFloat(),
                labelBottom.toFloat(),
                labelBackgroundPaint
            )
        }

        // Rysuj tekst
        canvas.drawText(
            text,
            labelLeft + labelPadding,
            labelTop + labelHeight - labelPadding / 2,
            labelTextPaint
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
