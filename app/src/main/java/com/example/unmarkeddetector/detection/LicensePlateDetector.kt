package com.example.unmarkeddetector.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate

@Singleton
class LicensePlateDetector @Inject constructor(
    @ApplicationContext context: Context
) {

    data class Detection(val box: RectF, val confidence: Float)

    private data class Engine(
        val interpreter: Interpreter,
        val gpuDelegate: GpuDelegate?,
        val inputWidth: Int,
        val inputHeight: Int,
        val inputType: DataType
    )

    private data class LetterboxTransform(
        val scale: Float,
        val padX: Float,
        val padY: Float,
        val sourceWidth: Int,
        val sourceHeight: Int
    )

    companion object {
        private const val TAG = "LicensePlateDetector"
        private const val MODEL_ASSET = "license-plate-v1n-fp16.tflite"
        private const val DEFAULT_INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val IOU_THRESHOLD = 0.45f
    }

    private val appContext = context.applicationContext
    private val engineLock = Any()
    private var engine: Engine? = null

    fun detect(bitmap: Bitmap): List<Detection> {
        val engine = synchronized(engineLock) {
            engine ?: createEngine().also { engine = it }
        } ?: return emptyList()

        val (inputBitmap, transform) = letterbox(bitmap, engine.inputWidth, engine.inputHeight)
        return try {
            val input = bitmapToInputBuffer(inputBitmap, engine.inputType)
            val outputShape = engine.interpreter.getOutputTensor(0).shape()
            val output = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

            synchronized(engineLock) {
                engine.interpreter.run(input, output)
            }

            decodeOutput(output[0], transform, engine.inputWidth, engine.inputHeight)
        } catch (error: Throwable) {
            Log.e(TAG, "YOLOv11 inference failed", error)
            emptyList()
        } finally {
            inputBitmap.recycle()
        }
    }

    fun close() {
        synchronized(engineLock) {
            engine?.let { current ->
                runCatching { current.interpreter.close() }
                runCatching { current.gpuDelegate?.close() }
            }
            engine = null
        }
    }

    private fun createEngine(): Engine? {
        val model = loadModelFile(MODEL_ASSET) ?: return null
        createGpuEngine(model)?.let { return it }
        return createCpuEngine(model)
    }

    private fun createGpuEngine(model: MappedByteBuffer): Engine? {
        var delegate: GpuDelegate? = null
        var interpreter: Interpreter? = null
        return try {
            delegate = GpuDelegate(
                GpuDelegate.Options().also { options ->
                    options.setPrecisionLossAllowed(true)
                }
            )
            interpreter = Interpreter(
                model,
                Interpreter.Options()
                    .addDelegate(delegate)
                    .setNumThreads(4)
            )
            buildEngine(interpreter, delegate).also {
                Log.i(TAG, "YOLOv11 TFLite loaded on GPU")
            }
        } catch (error: Throwable) {
            Log.w(TAG, "GPU unavailable for YOLOv11, using CPU fallback: ${error.message}")
            runCatching { interpreter?.close() }
            runCatching { delegate?.close() }
            null
        }
    }

    private fun createCpuEngine(model: MappedByteBuffer): Engine? {
        var interpreter: Interpreter? = null
        return try {
            interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
            buildEngine(interpreter, null).also {
                Log.i(TAG, "YOLOv11 TFLite loaded on CPU")
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Cannot create YOLOv11 interpreter", error)
            runCatching { interpreter?.close() }
            null
        }
    }

    private fun buildEngine(interpreter: Interpreter, delegate: GpuDelegate?): Engine {
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        require(inputShape.size == 4) {
            "Unsupported YOLOv11 input shape: ${inputShape.contentToString()}"
        }

        val inputHeight = inputShape[1].takeIf { it > 0 } ?: DEFAULT_INPUT_SIZE
        val inputWidth = inputShape[2].takeIf { it > 0 } ?: DEFAULT_INPUT_SIZE
        val outputShape = interpreter.getOutputTensor(0).shape()
        Log.i(
            TAG,
            "YOLOv11 tensors: input=${inputShape.contentToString()} ${inputTensor.dataType()}, " +
                "output=${outputShape.contentToString()} ${interpreter.getOutputTensor(0).dataType()}"
        )

        return Engine(
            interpreter = interpreter,
            gpuDelegate = delegate,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            inputType = inputTensor.dataType()
        )
    }

    private fun letterbox(source: Bitmap, targetWidth: Int, targetHeight: Int): Pair<Bitmap, LetterboxTransform> {
        val scale = min(
            targetWidth.toFloat() / source.width.coerceAtLeast(1),
            targetHeight.toFloat() / source.height.coerceAtLeast(1)
        )
        val resizedWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val resizedHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val padX = (targetWidth - resizedWidth) / 2f
        val padY = (targetHeight - resizedHeight) / 2f

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val resized = Bitmap.createScaledBitmap(source, resizedWidth, resizedHeight, true)
        canvas.drawBitmap(resized, padX, padY, Paint(Paint.FILTER_BITMAP_FLAG))
        resized.recycle()

        return output to LetterboxTransform(
            scale = scale,
            padX = padX,
            padY = padY,
            sourceWidth = source.width,
            sourceHeight = source.height
        )
    }

    private fun bitmapToInputBuffer(bitmap: Bitmap, inputType: DataType): ByteBuffer {
        require(inputType == DataType.FLOAT32) {
            "Unsupported YOLOv11 input type: $inputType"
        }

        val buffer = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.forEach { pixel ->
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    private fun decodeOutput(
        output: Array<FloatArray>,
        transform: LetterboxTransform,
        inputWidth: Int,
        inputHeight: Int
    ): List<Detection> {
        val raw = when {
            output.size <= 16 && output.firstOrNull()?.size ?: 0 > output.size -> decodeChannelFirst(output)
            else -> decodeRowMajor(output)
        }

        val detections = raw.mapNotNull { candidate ->
            val confidence = candidate[4]
            if (!confidence.isFinite() || confidence < CONFIDENCE_THRESHOLD) return@mapNotNull null

            val rect = yoloXywhToSourceRect(
                xCenter = candidate[0],
                yCenter = candidate[1],
                boxWidth = candidate[2],
                boxHeight = candidate[3],
                confidence = confidence,
                transform = transform,
                inputWidth = inputWidth,
                inputHeight = inputHeight
            ) ?: return@mapNotNull null

            Detection(rect, confidence)
        }

        return applyNms(detections)
    }

    private fun decodeChannelFirst(output: Array<FloatArray>): List<FloatArray> {
        val channels = output.size
        val count = output[0].size
        if (channels < 5) return emptyList()

        return buildList {
            for (index in 0 until count) {
                val confidence = if (channels == 5) {
                    output[4][index]
                } else {
                    var best = 0f
                    for (channel in 4 until channels) {
                        best = max(best, output[channel][index])
                    }
                    best
                }
                add(
                    floatArrayOf(
                        output[0][index],
                        output[1][index],
                        output[2][index],
                        output[3][index],
                        confidence
                    )
                )
            }
        }
    }

    private fun decodeRowMajor(output: Array<FloatArray>): List<FloatArray> {
        return output.mapNotNull { row ->
            if (row.size < 5) return@mapNotNull null
            val confidence = if (row.size == 5) {
                row[4]
            } else {
                var best = 0f
                for (index in 4 until row.size) {
                    best = max(best, row[index])
                }
                best
            }
            floatArrayOf(row[0], row[1], row[2], row[3], confidence)
        }
    }

    private fun yoloXywhToSourceRect(
        xCenter: Float,
        yCenter: Float,
        boxWidth: Float,
        boxHeight: Float,
        confidence: Float,
        transform: LetterboxTransform,
        inputWidth: Int,
        inputHeight: Int
    ): RectF? {
        if (!xCenter.isFinite() || !yCenter.isFinite() || !boxWidth.isFinite() || !boxHeight.isFinite()) {
            return null
        }

        val normalized = xCenter in 0f..1.2f &&
            yCenter in 0f..1.2f &&
            boxWidth in 0f..1.2f &&
            boxHeight in 0f..1.2f

        val cx = if (normalized) xCenter * inputWidth else xCenter
        val cy = if (normalized) yCenter * inputHeight else yCenter
        val width = if (normalized) boxWidth * inputWidth else boxWidth
        val height = if (normalized) boxHeight * inputHeight else boxHeight

        val modelLeft = cx - width / 2f
        val modelTop = cy - height / 2f
        val modelRight = cx + width / 2f
        val modelBottom = cy + height / 2f

        val left = ((modelLeft - transform.padX) / transform.scale)
            .coerceIn(0f, transform.sourceWidth.toFloat())
        val top = ((modelTop - transform.padY) / transform.scale)
            .coerceIn(0f, transform.sourceHeight.toFloat())
        val right = ((modelRight - transform.padX) / transform.scale)
            .coerceIn(0f, transform.sourceWidth.toFloat())
        val bottom = ((modelBottom - transform.padY) / transform.scale)
            .coerceIn(0f, transform.sourceHeight.toFloat())

        if (right - left < 2f || bottom - top < 2f) return null
        if (!confidence.isFinite()) return null
        return RectF(left, top, right, bottom)
    }

    private fun applyNms(detections: List<Detection>): List<Detection> {
        if (detections.size <= 1) return detections

        val sorted = detections.sortedByDescending { it.confidence }
        val kept = mutableListOf<Detection>()
        sorted.forEach { candidate ->
            val duplicate = kept.any { existing -> iou(candidate.box, existing.box) > IOU_THRESHOLD }
            if (!duplicate) kept += candidate
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val union = a.width() * a.height() + b.width() * b.height() - intersection
        return intersection / (union + 1e-6f)
    }

    private fun loadModelFile(name: String): MappedByteBuffer? {
        return try {
            appContext.assets.openFd(name).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Missing model asset: $name", error)
            null
        }
    }
}
