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
 * All source-frame transforms intentionally match PreviewView.ScaleType.FILL_CENTER:
 * scaleFactor = max(viewWidth / sourceWidth, viewHeight / sourceHeight)
 * offsetX = (viewWidth - sourceWidth * scaleFactor) / 2
 * offsetY = (viewHeight - sourceHeight * scaleFactor) / 2
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

    data class SourceDetectionBox(
        val rect: RectF,
        val confidence: Float = 0.9f,
        val isAlert: Boolean = false,
        val label: String = "",
        val color: Int = Color.GREEN
    )

    data class YoloDetection(
        val xCenter: Float,
        val yCenter: Float,
        val width: Float,
        val height: Float,
        val confidence: Float = 1f,
        val label: String = "",
        val isAlert: Boolean = false,
        val color: Int = Color.GREEN
    )

    data class GraphicConfig(
        val boundingBoxStrokeWidth: Float = 8f,
        val boundingBoxColor: Int = Color.GREEN,
        val alertBoxColor: Int = Color.RED,
        val textSize: Float = 32f,
        val textColor: Int = Color.WHITE,
        val confidenceThreshold: Float = 0.5f,
        val enableConfidenceLabel: Boolean = true,
        val labelBackground: Boolean = true,
        val labelBackgroundColor: Int = Color.BLACK
    )

    private data class OverlayBox(
        val rect: RectF,
        val confidence: Float,
        val isAlert: Boolean,
        val label: String,
        val color: Int
    )

    private data class OverlayState(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val screenBoxes: List<OverlayBox>,
        val sourceBoxes: List<SourceDetectionBox>,
        val yoloBoxes: List<YoloDetection>
    )

    var config = GraphicConfig()
        set(value) {
            field = value
            postInvalidate()
        }

    private val lock = Any()
    private val screenBoxes = mutableListOf<OverlayBox>()
    private val sourceBoxes = mutableListOf<SourceDetectionBox>()
    private val yoloBoxes = mutableListOf<YoloDetection>()

    private var sourceWidth = 0
    private var sourceHeight = 0

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.GREEN
        strokeWidth = 8f
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
    }

    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = 200
    }

    fun setAnalyzedFrameSize(width: Int, height: Int) {
        synchronized(lock) {
            sourceWidth = width
            sourceHeight = height
        }
        postInvalidate()
    }

    /**
     * Draws one normalized YOLO box [x_center, y_center, width, height] in the full
     * analyzed-frame coordinate system. Safe to call from CameraX background threads.
     */
    fun drawRect(
        normalizedXCenter: Float,
        normalizedYCenter: Float,
        normalizedWidth: Float,
        normalizedHeight: Float,
        confidence: Float = 1f,
        label: String = "",
        isAlert: Boolean = false,
        color: Int = Color.GREEN
    ) {
        synchronized(lock) {
            screenBoxes.clear()
            sourceBoxes.clear()
            yoloBoxes.clear()
            yoloBoxes += YoloDetection(
                xCenter = normalizedXCenter,
                yCenter = normalizedYCenter,
                width = normalizedWidth,
                height = normalizedHeight,
                confidence = confidence,
                label = label,
                isAlert = isAlert,
                color = color
            )
        }
        postInvalidate()
    }

    fun setYoloBoxes(
        detections: List<YoloDetection>,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        synchronized(lock) {
            this.sourceWidth = sourceWidth
            this.sourceHeight = sourceHeight
            screenBoxes.clear()
            sourceBoxes.clear()
            yoloBoxes.clear()
            yoloBoxes.addAll(detections)
        }
        postInvalidate()
    }

    fun setSourceDetectionBoxes(
        detections: List<SourceDetectionBox>,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        synchronized(lock) {
            this.sourceWidth = sourceWidth
            this.sourceHeight = sourceHeight
            screenBoxes.clear()
            sourceBoxes.clear()
            yoloBoxes.clear()
            sourceBoxes.addAll(detections)
        }
        postInvalidate()
    }

    fun setDetectionBoxes(detectionBoxes: List<DetectionBox>) {
        synchronized(lock) {
            yoloBoxes.clear()
            sourceBoxes.clear()
            screenBoxes.clear()
            detectionBoxes.mapTo(screenBoxes) { box ->
                OverlayBox(
                    rect = RectF(box.rect),
                    confidence = box.confidence,
                    isAlert = box.isAlert,
                    label = box.label,
                    color = box.color
                )
            }
        }
        postInvalidate()
    }

    fun addDetectionBox(box: DetectionBox) {
        synchronized(lock) {
            yoloBoxes.clear()
            sourceBoxes.clear()
            screenBoxes += OverlayBox(
                rect = RectF(box.rect),
                confidence = box.confidence,
                isAlert = box.isAlert,
                label = box.label,
                color = box.color
            )
        }
        postInvalidate()
    }

    fun clearDetectionBoxes() {
        synchronized(lock) {
            screenBoxes.clear()
            sourceBoxes.clear()
            yoloBoxes.clear()
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val state = synchronized(lock) {
            OverlayState(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                screenBoxes = screenBoxes.toList(),
                sourceBoxes = sourceBoxes.toList(),
                yoloBoxes = yoloBoxes.toList()
            )
        }

        state.screenBoxes.forEach { box ->
            drawBox(canvas, box)
        }

        state.sourceBoxes.forEach { sourceBox ->
            val rect = sourceToScreenRect(
                sourceRect = sourceBox.rect,
                sourceWidth = state.sourceWidth,
                sourceHeight = state.sourceHeight,
                overlayWidth = width,
                overlayHeight = height
            ) ?: return@forEach
            drawBox(
                canvas,
                OverlayBox(
                    rect = rect,
                    confidence = sourceBox.confidence,
                    isAlert = sourceBox.isAlert,
                    label = sourceBox.label,
                    color = sourceBox.color
                )
            )
        }

        state.yoloBoxes.forEach { detection ->
            val rect = normalizedYoloToScreenRect(
                normalizedXCenter = detection.xCenter,
                normalizedYCenter = detection.yCenter,
                normalizedWidth = detection.width,
                normalizedHeight = detection.height,
                sourceWidth = state.sourceWidth,
                sourceHeight = state.sourceHeight,
                overlayWidth = width,
                overlayHeight = height
            ) ?: return@forEach

            drawBox(
                canvas,
                OverlayBox(
                    rect = rect,
                    confidence = detection.confidence,
                    isAlert = detection.isAlert,
                    label = detection.label,
                    color = detection.color
                )
            )
        }
    }

    private fun normalizedYoloToScreenRect(
        normalizedXCenter: Float,
        normalizedYCenter: Float,
        normalizedWidth: Float,
        normalizedHeight: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        overlayWidth: Int,
        overlayHeight: Int
    ): RectF? {
        if (!normalizedXCenter.isFinite() || !normalizedYCenter.isFinite() ||
            !normalizedWidth.isFinite() || !normalizedHeight.isFinite()
        ) {
            return null
        }

        val frameWidth = sourceWidth.toFloat()
        val frameHeight = sourceHeight.toFloat()
        val boxWidth = normalizedWidth.coerceIn(0f, 1f)
        val boxHeight = normalizedHeight.coerceIn(0f, 1f)
        if (boxWidth <= 0f || boxHeight <= 0f) return null

        val xCenter = normalizedXCenter.coerceIn(0f, 1f)
        val yCenter = normalizedYCenter.coerceIn(0f, 1f)
        return sourceToScreenRect(
            sourceRect = RectF(
                (xCenter - boxWidth / 2f) * frameWidth,
                (yCenter - boxHeight / 2f) * frameHeight,
                (xCenter + boxWidth / 2f) * frameWidth,
                (yCenter + boxHeight / 2f) * frameHeight
            ),
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            overlayWidth = overlayWidth,
            overlayHeight = overlayHeight
        )
    }

    private fun sourceToScreenRect(
        sourceRect: RectF,
        sourceWidth: Int,
        sourceHeight: Int,
        overlayWidth: Int,
        overlayHeight: Int
    ): RectF? {
        val viewWidth = overlayWidth.toFloat()
        val viewHeight = overlayHeight.toFloat()
        val frameWidth = sourceWidth.toFloat()
        val frameHeight = sourceHeight.toFloat()

        if (viewWidth <= 0f || viewHeight <= 0f || frameWidth <= 0f || frameHeight <= 0f) {
            return null
        }
        if (!sourceRect.left.isFinite() || !sourceRect.top.isFinite() ||
            !sourceRect.right.isFinite() || !sourceRect.bottom.isFinite()
        ) {
            return null
        }

        val scaleFactor = max(viewWidth / frameWidth, viewHeight / frameHeight)
        val offsetX = (viewWidth - frameWidth * scaleFactor) / 2f
        val offsetY = (viewHeight - frameHeight * scaleFactor) / 2f

        return RectF(
            sourceRect.left * scaleFactor + offsetX,
            sourceRect.top * scaleFactor + offsetY,
            sourceRect.right * scaleFactor + offsetX,
            sourceRect.bottom * scaleFactor + offsetY
        )
    }

    private fun drawBox(canvas: Canvas, box: OverlayBox) {
        if (box.rect.width() <= 0f || box.rect.height() <= 0f) return

        boxPaint.color = if (box.isAlert) config.alertBoxColor else box.color
        boxPaint.strokeWidth = config.boundingBoxStrokeWidth
        canvas.drawRect(box.rect, boxPaint)

        if (box.label.isNotBlank() || config.enableConfidenceLabel) {
            drawLabel(canvas, box)
        }
    }

    private fun drawLabel(canvas: Canvas, box: OverlayBox) {
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
        val textBounds = Rect()
        labelTextPaint.getTextBounds(text, 0, text.length, textBounds)
        val labelLeft = box.rect.left.coerceAtLeast(0f)
        val labelBottom = (box.rect.top - 6f).coerceAtLeast(textBounds.height() + padding * 2)
        val labelTop = labelBottom - textBounds.height() - padding * 2
        val labelRight = labelLeft + textBounds.width() + padding * 2

        if (config.labelBackground) {
            labelBackgroundPaint.color = config.labelBackgroundColor
            labelBackgroundPaint.alpha = 200
            canvas.drawRect(labelLeft, labelTop, labelRight, labelBottom, labelBackgroundPaint)
        }

        canvas.drawText(text, labelLeft + padding, labelBottom - padding, labelTextPaint)
    }
}
