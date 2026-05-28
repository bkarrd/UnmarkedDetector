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
import kotlin.math.min
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate

/**
 * Detekcja: [PlateDetectionModel.tflite] — SSD MobileNet aboerzel (300×300).
 * OCR: [glpr-model.tflite] — CRNN (128×64, skala szarości).
 */
class LicensePlateDetector(context: Context) {

    data class Detection(
        val box: RectF,
        val confidence: Float,
        val plateText: String? = null
    )

    companion object {
        private const val TAG = "LicensePlateDetector"

        /** Rozmiar wejścia PlateDetectionModel.tflite (aboerzel). */
        const val IMG_SIZE = 300

        private const val DETECTION_MODEL = "PlateDetectionModel.tflite"
        private const val RECOGNITION_MODEL = "glpr-model.tflite"

        private const val DETECTION_SCORE_THRESHOLD = 0.15f
        private const val DETECTION_IMAGE_MEAN = 128.0f
        private const val DETECTION_IMAGE_STD = 128.0f

        private const val RECOGNITION_WIDTH = 128
        private const val RECOGNITION_HEIGHT = 64
        private const val RECOGNITION_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÜ0123456789- "
    }

    private data class DetectionEngine(
        val interpreter: Interpreter,
        val gpuDelegate: GpuDelegate?,
        val tensorLayout: SsdTensorLayout
    )

    private data class SsdTensorLayout(
        val locationsIndex: Int,
        val classesIndex: Int,
        val scoresIndex: Int,
        val numDetectionsIndex: Int,
        val maxDetections: Int
    )

    private data class RecognitionEngine(
        val interpreter: Interpreter,
        val gpuDelegate: GpuDelegate?,
        val textSteps: Int,
        val alphabetSize: Int
    )

    private val appContext = context.applicationContext
    private val engineLock = Any()
    private var detectionEngine: DetectionEngine? = null
    private var recognitionEngine: RecognitionEngine? = null
    private var recognitionUnavailableLogged = false

    init {
        synchronized(engineLock) {
            detectionEngine = createDetectionEngine()
            recognitionEngine = createRecognitionEngine()
        }
    }

    /**
     * Uruchamia SSD na bitmapie. Zwraca ramki w układzie współrzędnych [bitmap]
     * (wejście najlepiej 300×300 — przy większym obrazie wynik jest skalowany).
     */
    fun detect(bitmap: Bitmap): List<Detection> {
        val engine = synchronized(engineLock) {
            detectionEngine ?: createDetectionEngine().also { detectionEngine = it }
        } ?: return emptyList()

        val modelBitmap = if (bitmap.width == IMG_SIZE && bitmap.height == IMG_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true)
        }
        val recycleModelBitmap = modelBitmap !== bitmap

        val layout = engine.tensorLayout
        val maxDetections = layout.maxDetections
        val outputLocations = Array(1) { Array(maxDetections) { FloatArray(4) } }
        val outputClasses = Array(1) { FloatArray(maxDetections) }
        val outputScores = Array(1) { FloatArray(maxDetections) }
        val numDetections = FloatArray(1)

        val outputs = mapOf(
            layout.locationsIndex to outputLocations,
            layout.classesIndex to outputClasses,
            layout.scoresIndex to outputScores,
            layout.numDetectionsIndex to numDetections
        )

        return try {
            val input = bitmapToDetectionByteBuffer(modelBitmap)
            engine.interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)

            val numFound = numDetections[0].toInt().coerceIn(0, maxDetections)
            val scaleX = bitmap.width.toFloat() / IMG_SIZE
            val scaleY = bitmap.height.toFloat() / IMG_SIZE
            Log.d(TAG, "=== WYNIKI DETEKCJI: numFound=$numFound ===")
            for (i in 0 until numFound) {
                Log.d(TAG, "  det[$i]: score=${outputScores[0][i]} " +
                        "box=${outputLocations[0][i].contentToString()}")
            }
            buildList {
                for (i in 0 until numFound) {
                    val score = outputScores[0][i]
                    if (score < DETECTION_SCORE_THRESHOLD) continue

                    val loc = outputLocations[0][i]
                    // SSD TFOD: [ymin, xmin, ymax, xmax] znormalizowane 0–1 względem wejścia 300×300
                    val left = loc[1] * IMG_SIZE * scaleX
                    val top = loc[0] * IMG_SIZE * scaleY
                    val right = loc[3] * IMG_SIZE * scaleX
                    val bottom = loc[2] * IMG_SIZE * scaleY

                    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) continue
                    if (right <= left || bottom <= top) continue

                    add(Detection(RectF(left, top, right, bottom), score))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PlateDetectionModel inferencja nie powiodła się: ${e.message}")
            emptyList()
        } finally {
            if (recycleModelBitmap) {
                modelBitmap.recycle()
            }
        }

    }

    fun recognizePlateText(plateCrop: Bitmap): String? {
        val engine = synchronized(engineLock) {
            recognitionEngine ?: createRecognitionEngine().also { recognitionEngine = it }
        } ?: return null

        val inputBitmap = preprocessRecognitionInput(plateCrop)
        val recycleInput = inputBitmap !== plateCrop
        val output = Array(1) {
            Array(engine.textSteps) { FloatArray(engine.alphabetSize) }
        }

        return try {
            val input = bitmapToRecognitionByteBuffer(inputBitmap)
            engine.interpreter.run(input, output)
            decodeRecognitionOutput(output[0])
        } catch (e: Exception) {
            Log.e(TAG, "glpr-model inferencja nie powiodła się: ${e.message}")
            null
        } finally {
            if (recycleInput) {
                inputBitmap.recycle()
            }
        }
    }

    fun isRecognitionModelLoaded(): Boolean = recognitionEngine != null

    private fun bitmapToDetectionByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(IMG_SIZE * IMG_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(IMG_SIZE * IMG_SIZE)
        bitmap.getPixels(pixels, 0, IMG_SIZE, 0, 0, IMG_SIZE, IMG_SIZE)
        pixels.forEach { pixel ->
            buffer.putFloat((((pixel shr 16) and 0xFF) - DETECTION_IMAGE_MEAN) / DETECTION_IMAGE_STD)
            buffer.putFloat((((pixel shr 8) and 0xFF) - DETECTION_IMAGE_MEAN) / DETECTION_IMAGE_STD)
            buffer.putFloat(((pixel and 0xFF) - DETECTION_IMAGE_MEAN) / DETECTION_IMAGE_STD)
        }
        buffer.rewind()
        return buffer
    }

    private fun bitmapToRecognitionByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(RECOGNITION_WIDTH * RECOGNITION_HEIGHT * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(RECOGNITION_WIDTH * RECOGNITION_HEIGHT)
        bitmap.getPixels(pixels, 0, RECOGNITION_WIDTH, 0, 0, RECOGNITION_WIDTH, RECOGNITION_HEIGHT)
        pixels.forEach { pixel ->
            val gray = (
                Color.red(pixel) * 0.299f +
                    Color.green(pixel) * 0.587f +
                    Color.blue(pixel) * 0.114f
                ) / 255.0f
            buffer.putFloat(gray)
        }
        buffer.rewind()
        return buffer
    }

    private fun preprocessRecognitionInput(source: Bitmap): Bitmap {
        val scale = RECOGNITION_WIDTH.toFloat() / source.width.coerceAtLeast(1)
        val scaledHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, RECOGNITION_WIDTH, scaledHeight, true)

        val cropTop = if (scaled.height > RECOGNITION_HEIGHT) {
            (scaled.height - RECOGNITION_HEIGHT) / 2
        } else {
            0
        }
        val cropHeight = min(scaled.height, RECOGNITION_HEIGHT)

        val cropped = Bitmap.createBitmap(scaled, 0, cropTop, RECOGNITION_WIDTH, cropHeight)
        if (scaled !== source && scaled !== cropped) {
            scaled.recycle()
        }

        if (cropped.height == RECOGNITION_HEIGHT) {
            return cropped
        }

        val padded = Bitmap.createBitmap(RECOGNITION_WIDTH, RECOGNITION_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        val topPad = (RECOGNITION_HEIGHT - cropped.height) / 2f
        canvas.drawBitmap(cropped, 0f, topPad, Paint(Paint.FILTER_BITMAP_FLAG))
        if (cropped !== source) {
            cropped.recycle()
        }
        return padded
    }

    private fun decodeRecognitionOutput(timeSteps: Array<FloatArray>): String {
        val bestChars = IntArray(timeSteps.size)
        for (timeIndex in timeSteps.indices) {
            var maxIdx = -1
            var maxVal = -1f
            val step = timeSteps[timeIndex]
            for (charIndex in step.indices) {
                if (step[charIndex] > maxVal) {
                    maxVal = step[charIndex]
                    maxIdx = charIndex
                }
            }
            bestChars[timeIndex] = maxIdx
        }

        val collapsed = mutableListOf<Int>()
        for (charCode in bestChars) {
            if (charCode < 0) continue
            if (collapsed.isEmpty() || collapsed.last() != charCode) {
                collapsed.add(charCode)
            }
        }

        return buildString {
            collapsed.forEach { code ->
                if (code in RECOGNITION_ALPHABET.indices) {
                    append(RECOGNITION_ALPHABET[code])
                }
            }
        }.trim()
    }

    private fun createDetectionEngine(): DetectionEngine? {
        val model = loadModelFile(DETECTION_MODEL) ?: run {
            Log.e(TAG, "❌ Brak $DETECTION_MODEL w assets")
            return null
        }
        Log.i(TAG, "✅ Model $DETECTION_MODEL załadowany")

        val interpreter = try {
            Interpreter(model, Interpreter.Options().setNumThreads(2))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Interpreter nie uruchomiony: ${e.message}")
            return null
        }

        // ── LOGUJ WSZYSTKIE TENSORY WEJŚCIOWE ──
        Log.i(TAG, "=== TENSORY WEJŚCIOWE (${interpreter.inputTensorCount}) ===")
        for (i in 0 until interpreter.inputTensorCount) {
            val t = interpreter.getInputTensor(i)
            Log.i(TAG, "  Input[$i]: shape=${t.shape().contentToString()} " +
                    "dtype=${t.dataType()} name=${t.name()}")
        }

        // ── LOGUJ WSZYSTKIE TENSORY WYJŚCIOWE ──
        Log.i(TAG, "=== TENSORY WYJŚCIOWE (${interpreter.outputTensorCount}) ===")
        for (i in 0 until interpreter.outputTensorCount) {
            val t = interpreter.getOutputTensor(i)
            Log.i(TAG, "  Output[$i]: shape=${t.shape().contentToString()} " +
                    "dtype=${t.dataType()} name=${t.name()}")
        }

        return try {
            val layout = resolveSsdTensorLayout(interpreter)
            Log.i(TAG, "Layout: loc=${layout.locationsIndex} cls=${layout.classesIndex} " +
                    "sc=${layout.scoresIndex} numDet=${layout.numDetectionsIndex} " +
                    "maxDet=${layout.maxDetections}")
            DetectionEngine(interpreter, null, layout)
        } catch (e: Exception) {
            Log.e(TAG, "❌ resolveSsdTensorLayout failed: ${e.message}")
            null
        }
    }

    private fun resolveSsdTensorLayout(interpreter: Interpreter): SsdTensorLayout {
        var locationsIndex = -1
        var classesIndex = -1
        var scoresIndex = -1
        var numDetectionsIndex = -1
        var maxDetections = 10

        for (tensorIndex in 0 until interpreter.outputTensorCount) {
            val shape = interpreter.getOutputTensor(tensorIndex).shape()
            when {
                shape.size == 3 && shape.last() == 4 -> {
                    locationsIndex = tensorIndex
                    maxDetections = shape[1]
                }
                shape.contentEquals(intArrayOf(1)) -> numDetectionsIndex = tensorIndex
                shape.size == 2 && shape[0] == 1 -> {
                    if (classesIndex < 0) classesIndex = tensorIndex
                    else scoresIndex = tensorIndex  // ← przypisuje w kolejności napotkania
                }
            }
        }

        require(locationsIndex >= 0 && scoresIndex >= 0 && numDetectionsIndex >= 0) {
            "Nie rozpoznano tensorów wyjściowych $DETECTION_MODEL"
        }
        if (classesIndex < 0) classesIndex = scoresIndex

        return SsdTensorLayout(
            locationsIndex = locationsIndex,
            classesIndex = classesIndex,
            scoresIndex = scoresIndex,
            numDetectionsIndex = numDetectionsIndex,
            maxDetections = maxDetections
        )
    }

    private fun createRecognitionEngine(): RecognitionEngine? {
        val model = loadModelFile(RECOGNITION_MODEL) ?: run {
            if (!recognitionUnavailableLogged) {
                recognitionUnavailableLogged = true
                Log.w(TAG, "Brak $RECOGNITION_MODEL w assets")
            }
            return null
        }

        var delegate: GpuDelegate? = null
        var interpreter: Interpreter? = null
        return try {
            delegate = GpuDelegate(GpuDelegate.Options().also { it.setPrecisionLossAllowed(true) })
            interpreter = Interpreter(model, Interpreter.Options().addDelegate(delegate).setNumThreads(4))
            buildRecognitionEngine(interpreter, delegate)
        } catch (gpuError: Throwable) {
            runCatching { interpreter?.close() }
            runCatching { delegate?.close() }
            try {
                interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
                buildRecognitionEngine(interpreter, null)
            } catch (cpuError: Throwable) {
                Log.e(TAG, "Nie uruchomiono $RECOGNITION_MODEL: ${cpuError.message}")
                runCatching { interpreter?.close() }
                null
            }
        }
    }

    private fun buildRecognitionEngine(
        interpreter: Interpreter,
        delegate: GpuDelegate?
    ): RecognitionEngine {
        val shape = interpreter.getOutputTensor(0).shape()
        val textSteps = if (shape.size >= 2) shape[shape.size - 2] else 1
        val alphabetSize = shape.last()
        Log.i(TAG, "OCR glpr-model: wyjście ${shape.contentToString()}")
        return RecognitionEngine(interpreter, delegate, textSteps, alphabetSize)
    }

    private fun loadModelFile(name: String): MappedByteBuffer? {
        return try {
            appContext.assets.openFd(name).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Brak modelu $name: ${error.message}")
            null
        }
    }

    fun close() {
        synchronized(engineLock) {
            detectionEngine?.let {
                runCatching { it.interpreter.close() }
                runCatching { it.gpuDelegate?.close() }
            }
            detectionEngine = null
            recognitionEngine?.let {
                runCatching { it.interpreter.close() }
                runCatching { it.gpuDelegate?.close() }
            }
            recognitionEngine = null
        }
    }
}
