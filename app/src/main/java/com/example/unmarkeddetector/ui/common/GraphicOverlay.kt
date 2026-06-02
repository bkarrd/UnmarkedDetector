package com.example.unmarkeddetector.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Transparent overlay for CameraX PreviewView.
 *
 * The source-frame transform matches PreviewView.ScaleType.FILL_CENTER.
 */
class GraphicOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class SourceDetectionBox(
        val rect: RectF,
        val confidence: Float = 0.9f,
        val isAlert: Boolean = false,
        val label: String = "",
        val color: Int = Color.GREEN
    )

    data class GraphicConfig(
        val boundingBoxStrokeWidth: Float = 8f,
        val alertBoxColor: Int = Color.RED,
        val textSize: Float = 32f,
        val textColor: Int = Color.WHITE,
        val enableConfidenceLabel: Boolean = true,
        val labelBackground: Boolean = true,
        val labelBackgroundColor: Int = Color.BLACK
    )

    private data class OverlayState(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val sourceBoxes: List<SourceDetectionBox>
    )

    var config = GraphicConfig()
        set(value) {
            field = value
            postInvalidate()
        }

    @Volatile
    private var overlayState = OverlayState(0, 0, emptyList())

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
    }
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 200
    }
    private val transformedRect = RectF()
    private val labelTextBounds = Rect()

    fun setSourceDetectionBoxes(
        detections: List<SourceDetectionBox>,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        overlayState = OverlayState(sourceWidth, sourceHeight, detections.toList())
        postInvalidate()
    }

    fun clearDetectionBoxes() {
        overlayState = OverlayState(0, 0, emptyList())
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val state = overlayState
        state.sourceBoxes.forEach { box ->
            if (!sourceToScreenRect(
                sourceRect = box.rect,
                sourceWidth = state.sourceWidth,
                sourceHeight = state.sourceHeight,
                output = transformedRect
            )) return@forEach
            drawBox(canvas, transformedRect, box)
        }
    }

    private fun sourceToScreenRect(
        sourceRect: RectF,
        sourceWidth: Int,
        sourceHeight: Int,
        output: RectF
    ): Boolean {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val frameWidth = sourceWidth.toFloat()
        val frameHeight = sourceHeight.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f || frameWidth <= 0f || frameHeight <= 0f) {
            return false
        }

        val scaleFactor = max(viewWidth / frameWidth, viewHeight / frameHeight)
        val offsetX = (viewWidth - frameWidth * scaleFactor) / 2f
        val offsetY = (viewHeight - frameHeight * scaleFactor) / 2f
        output.set(
            sourceRect.left * scaleFactor + offsetX,
            sourceRect.top * scaleFactor + offsetY,
            sourceRect.right * scaleFactor + offsetX,
            sourceRect.bottom * scaleFactor + offsetY
        )
        return true
    }

    private fun drawBox(canvas: Canvas, rect: RectF, box: SourceDetectionBox) {
        if (rect.width() <= 0f || rect.height() <= 0f) return

        boxPaint.color = if (box.isAlert) config.alertBoxColor else box.color
        boxPaint.strokeWidth = config.boundingBoxStrokeWidth
        canvas.drawRect(rect, boxPaint)

        if (box.label.isNotBlank() || config.enableConfidenceLabel) {
            drawLabel(canvas, rect, box)
        }
    }

    private fun drawLabel(canvas: Canvas, rect: RectF, box: SourceDetectionBox) {
        val text = when {
            box.label.isNotBlank() && config.enableConfidenceLabel ->
                "${box.label} ${(box.confidence * 100).toInt()}%"
            box.label.isNotBlank() -> box.label
            config.enableConfidenceLabel -> "${(box.confidence * 100).toInt()}%"
            else -> return
        }

        labelTextPaint.textSize = config.textSize
        labelTextPaint.color = config.textColor

        val padding = 8f
        labelTextPaint.getTextBounds(text, 0, text.length, labelTextBounds)
        val labelLeft = rect.left.coerceAtLeast(0f)
        val labelBottom = (rect.top - 6f).coerceAtLeast(labelTextBounds.height() + padding * 2)
        val labelTop = labelBottom - labelTextBounds.height() - padding * 2
        val labelRight = labelLeft + labelTextBounds.width() + padding * 2

        if (config.labelBackground) {
            labelBackgroundPaint.color = config.labelBackgroundColor
            canvas.drawRect(labelLeft, labelTop, labelRight, labelBottom, labelBackgroundPaint)
        }
        canvas.drawText(text, labelLeft + padding, labelBottom - padding, labelTextPaint)
    }
}
