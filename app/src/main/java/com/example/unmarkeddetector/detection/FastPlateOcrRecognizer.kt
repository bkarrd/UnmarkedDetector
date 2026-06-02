package com.example.unmarkeddetector.detection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.example.unmarkeddetector.domain.model.RecognizedTextCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor

@Singleton
class FastPlateOcrRecognizer @Inject constructor(
    @ApplicationContext context: Context
) {

    private enum class InputLayout {
        NHWC,
        NWCH,
        NCHW
    }

    private data class Engine(
        val interpreter: Interpreter,
        val backend: String,
        val inputLayout: InputLayout,
        val inputWidth: Int,
        val inputHeight: Int,
        val plateOutputIndex: Int,
        val plateSlots: Int,
        val regionOutputIndex: Int?
    )

    private data class DecodedPlate(
        val text: String,
        val confidence: Float,
        val characterSummary: String
    )

    companion object {
        private const val TAG = "FastPlateOCR"
        private const val MODEL_ASSET = "cct_s_v2_global_float32.tflite"
        private const val CHANNELS = 3
        private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_"
        private const val PAD_CHARACTER = '_'
        private val REGION_LABELS = listOf(
            "Albania", "Andorra", "Argentina", "Armenia", "Australia", "Austria",
            "Azerbaijan", "Bahrain", "Belarus", "Belgium", "Bosnia and Herzegovina",
            "Brazil", "Bulgaria", "Cambodia", "Canada", "Croatia", "Cyprus",
            "Czech Republic", "Denmark", "Estonia", "Finland", "France", "Georgia",
            "Germany", "Gibraltar", "Greece", "Guernsey", "Hungary", "Iceland",
            "Indonesia", "Ireland", "Israel", "Italy", "Latvia", "Liechtenstein",
            "Lithuania", "Luxembourg", "Malaysia", "Malta", "Mexico", "Moldova",
            "Monaco", "Montenegro", "Netherlands", "New Zealand", "North Macedonia",
            "Norway", "Poland", "Portugal", "Qatar", "Romania", "San Marino", "Serbia",
            "Singapore", "Slovakia", "Slovenia", "Spain", "Sweden", "Switzerland",
            "Thailand", "Turkey", "United States", "Ukraine", "United Kingdom",
            "Vietnam", "Unknown"
        )
    }

    private val appContext = context.applicationContext
    private val ocrExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fast-plate-ocr")
    }
    private val ocrDispatcher = ocrExecutor.asCoroutineDispatcher()
    private var engine: Engine? = null
    private var initializationFailed = false

    suspend fun recognize(crop: Bitmap): RecognizedTextCandidate? {
        if (crop.isRecycled || crop.width <= 0 || crop.height <= 0) return null
        return withContext(ocrDispatcher) {
            recognizeOnOcrThread(crop)
        }
    }

    fun close() {
        runCatching {
            ocrExecutor.submit {
                engine?.let { current ->
                    runCatching { current.interpreter.close() }
                    Log.i(TAG, "FastPlateOCR resources released")
                }
                engine = null
                initializationFailed = false
            }.get()
        }.onFailure { error ->
            Log.w(TAG, "Could not release FastPlateOCR resources cleanly", error)
        }
    }

    private fun recognizeOnOcrThread(crop: Bitmap): RecognizedTextCandidate? {
        if (initializationFailed) return null
        val current = engine ?: createEngine()?.also { engine = it } ?: run {
            initializationFailed = true
            return null
        }
        val startedAt = SystemClock.elapsedRealtime()
        val resized = Bitmap.createScaledBitmap(crop, current.inputWidth, current.inputHeight, true)

        return try {
            val input = bitmapToInputBuffer(resized, current.inputLayout)
            val plateOutput = outputBuffer(current.interpreter.getOutputTensor(current.plateOutputIndex))
            val outputs = mutableMapOf<Int, Any>(current.plateOutputIndex to plateOutput)
            val regionOutput = current.regionOutputIndex?.let { index ->
                outputBuffer(current.interpreter.getOutputTensor(index)).also { buffer ->
                    outputs[index] = buffer
                }
            }

            current.interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)

            val decoded = decodePlate(
                values = readOutputValues(plateOutput),
                slots = current.plateSlots
            )
            val region = regionOutput?.let { output ->
                decodeRegion(values = readOutputValues(output))
            }
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            Log.d(
                LprDebug.TAG,
                "FastPlateOCR result=\"${decoded.text}\" confidence=${format(decoded.confidence)} " +
                    "chars=[${decoded.characterSummary}] region=${region ?: "n/a"} " +
                    "crop=${crop.width}x${crop.height}->${current.inputWidth}x${current.inputHeight} " +
                    "layout=${current.inputLayout} backend=${current.backend} inference=${elapsedMs}ms"
            )

            decoded.text.takeIf { it.isNotBlank() }?.let { text ->
                RecognizedTextCandidate(
                    rawText = text,
                    confidence = decoded.confidence
                )
            }
        } catch (error: Throwable) {
            Log.e(TAG, "FastPlateOCR inference failed", error)
            null
        } finally {
            if (resized !== crop) resized.recycle()
        }
    }

    private fun createEngine(): Engine? {
        val model = loadModelFile() ?: return null
        return createCpuEngine(model)
    }

    private fun createCpuEngine(model: MappedByteBuffer): Engine? {
        var interpreter: Interpreter? = null
        return try {
            interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
            buildEngine(interpreter, backend = "CPU").also {
                Log.i(TAG, "FastPlateOCR TFLite loaded on CPU")
            }
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "Cannot create FastPlateOCR CPU engine. Verify that the bundled model is Android-compatible.",
                error
            )
            runCatching { interpreter?.close() }
            null
        }
    }

    private fun buildEngine(
        interpreter: Interpreter,
        backend: String
    ): Engine {
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        require(inputTensor.dataType() == DataType.UINT8) {
            "Unsupported FastPlateOCR input type: ${inputTensor.dataType()}"
        }

        val (layout, width, height) = detectInputLayout(inputShape)
        val plateOutputIndex = (0 until interpreter.outputTensorCount)
            .firstOrNull { index ->
                val shape = interpreter.getOutputTensor(index).shape()
                shape.size == 3 && shape.firstOrNull() == 1 && shape.lastOrNull() == ALPHABET.length
            }
            ?: error("Cannot find FastPlateOCR plate output")
        val plateTensor = interpreter.getOutputTensor(plateOutputIndex)
        val plateShape = plateTensor.shape()
        require(plateTensor.dataType() == DataType.FLOAT32) {
            "Unsupported FastPlateOCR plate output type: ${plateTensor.dataType()}"
        }

        val regionOutputIndex = (0 until interpreter.outputTensorCount)
            .firstOrNull { index ->
                if (index == plateOutputIndex) return@firstOrNull false
                val shape = interpreter.getOutputTensor(index).shape()
                shape.contentEquals(intArrayOf(1, REGION_LABELS.size))
            }
        val regionOutputType = regionOutputIndex?.let { interpreter.getOutputTensor(it).dataType() }
        require(regionOutputType == null || regionOutputType == DataType.FLOAT32) {
            "Unsupported FastPlateOCR region output type: $regionOutputType"
        }

        Log.i(
            TAG,
            "FastPlateOCR tensors: input=${inputShape.contentToString()} ${inputTensor.dataType()} " +
                "layout=$layout, plate=${plateShape.contentToString()} ${plateTensor.dataType()}, " +
                "region=${regionOutputIndex?.let { interpreter.getOutputTensor(it).shape().contentToString() } ?: "none"} " +
                "${regionOutputType ?: ""}"
        )
        return Engine(
            interpreter = interpreter,
            backend = backend,
            inputLayout = layout,
            inputWidth = width,
            inputHeight = height,
            plateOutputIndex = plateOutputIndex,
            plateSlots = plateShape[1],
            regionOutputIndex = regionOutputIndex
        )
    }

    private fun detectInputLayout(shape: IntArray): Triple<InputLayout, Int, Int> {
        require(shape.size == 4 && shape[0] == 1) {
            "Unsupported FastPlateOCR input shape: ${shape.contentToString()}"
        }
        return when {
            shape[3] == CHANNELS -> Triple(InputLayout.NHWC, shape[2], shape[1])
            shape[2] == CHANNELS -> Triple(InputLayout.NWCH, shape[1], shape[3])
            shape[1] == CHANNELS -> Triple(InputLayout.NCHW, shape[3], shape[2])
            else -> error("Unsupported FastPlateOCR channel layout: ${shape.contentToString()}")
        }
    }

    private fun bitmapToInputBuffer(bitmap: Bitmap, layout: InputLayout): ByteBuffer {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val input = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * CHANNELS)
            .order(ByteOrder.nativeOrder())

        fun putChannel(x: Int, y: Int, channel: Int) {
            val pixel = pixels[y * bitmap.width + x]
            val shift = when (channel) {
                0 -> 16
                1 -> 8
                else -> 0
            }
            input.put(((pixel shr shift) and 0xFF).toByte())
        }

        when (layout) {
            InputLayout.NHWC -> {
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        for (channel in 0 until CHANNELS) putChannel(x, y, channel)
                    }
                }
            }
            InputLayout.NWCH -> {
                for (x in 0 until bitmap.width) {
                    for (channel in 0 until CHANNELS) {
                        for (y in 0 until bitmap.height) putChannel(x, y, channel)
                    }
                }
            }
            InputLayout.NCHW -> {
                for (channel in 0 until CHANNELS) {
                    for (y in 0 until bitmap.height) {
                        for (x in 0 until bitmap.width) putChannel(x, y, channel)
                    }
                }
            }
        }
        input.rewind()
        return input
    }

    private fun outputBuffer(tensor: Tensor): ByteBuffer {
        return ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
    }

    private fun readOutputValues(buffer: ByteBuffer): FloatArray {
        buffer.rewind()
        return FloatArray(buffer.remaining() / 4) {
            buffer.float
        }
    }

    private fun decodePlate(values: FloatArray, slots: Int): DecodedPlate {
        require(values.size == slots * ALPHABET.length) {
            "Unexpected FastPlateOCR plate output size: ${values.size}"
        }

        val characters = CharArray(slots)
        val probabilities = FloatArray(slots)
        for (slot in 0 until slots) {
            var bestIndex = 0
            var bestProbability = Float.NEGATIVE_INFINITY
            for (classIndex in ALPHABET.indices) {
                val probability = values[slot * ALPHABET.length + classIndex]
                if (probability > bestProbability) {
                    bestProbability = probability
                    bestIndex = classIndex
                }
            }
            characters[slot] = ALPHABET[bestIndex]
            probabilities[slot] = bestProbability
        }

        val text = characters.concatToString().trimEnd(PAD_CHARACTER)
        val visibleProbabilities = probabilities.take(text.length)
        val confidence = visibleProbabilities
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
            ?: 0f
        val characterSummary = text.indices.joinToString { index ->
            "${text[index]}:${format(probabilities[index])}"
        }
        return DecodedPlate(text, confidence, characterSummary)
    }

    private fun decodeRegion(values: FloatArray): String? {
        if (values.size != REGION_LABELS.size) return null
        val index = values.indices.maxByOrNull { values[it] } ?: return null
        return "${REGION_LABELS[index]}(${format(values[index])})"
    }

    private fun loadModelFile(): MappedByteBuffer? {
        return try {
            appContext.assets.openFd(MODEL_ASSET).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Missing FastPlateOCR model asset: $MODEL_ASSET", error)
            null
        }
    }

    private fun format(value: Float): String = "%.3f".format(value)
}
