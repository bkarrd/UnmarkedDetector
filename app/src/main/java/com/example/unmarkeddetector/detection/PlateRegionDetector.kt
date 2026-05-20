package com.example.unmarkeddetector.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate

@Singleton
class PlateRegionDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imagePreprocessor: ImagePreprocessor
) {

    companion object {
        private const val TAG = "PlateRegionDetector"
        private const val MODEL_ASSET = "plate_detector_simple.tflite"
        private const val EXPECTED_SIZE = 224
    }

    private data class Engine(
        val interpreter: Interpreter,
        val gpuDelegate: GpuDelegate?,
        val nnApiDelegate: NnApiDelegate?,
        val inputFloatNhwc: Array<Array<Array<FloatArray>>>,
        val output: Any
    )

    private val engineLock = Any()
    private var engine: Engine? = null

    fun close() = synchronized(engineLock) {
        val e = engine ?: return@synchronized
        runCatching { e.interpreter.close() }
            .onFailure { Log.w(TAG, "close(): błąd przy zamykaniu Interpretera", it) }
        runCatching { e.gpuDelegate?.close() }
            .onFailure { Log.w(TAG, "close(): błąd przy zamykaniu GpuDelegate", it) }
        runCatching { e.nnApiDelegate?.close() }
            .onFailure { Log.w(TAG, "close(): błąd przy zamykaniu NnApiDelegate", it) }
        engine = null
        Log.d(TAG, "close(): zwolniono Interpreter i delegaty TFLite")
    }

    private fun runInference(bitmap: Bitmap): FloatArray? {
        val t0 = SystemClock.elapsedRealtime()
        val scaled = Bitmap.createScaledBitmap(bitmap, EXPECTED_SIZE, EXPECTED_SIZE, true)
        return try {
            synchronized(engineLock) {
                if (engine == null) {
                    engine = createEngine()
                }
                val eng = engine ?: return@synchronized null
                if (eng.interpreter.getInputTensor(0).dataType() != DataType.FLOAT32) {
                    Log.w(
                        TAG,
                        "Nieobsługiwany typ wejścia modelu: ${eng.interpreter.getInputTensor(0).dataType()}"
                    )
                    return@synchronized null
                }
                fillNhwcFromBitmap(scaled, eng.inputFloatNhwc)
                val tPrep = SystemClock.elapsedRealtime()
                eng.interpreter.run(eng.inputFloatNhwc, eng.output)
                val tEnd = SystemClock.elapsedRealtime()
                Log.d(
                    LprDebug.TAG,
                    "TFLite infer: total=${tEnd - t0} ms prep=${tPrep - t0} ms run=${tEnd - tPrep} ms"
                )
                outputToFloat4(eng.output)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Inferencja TFLite nie powiodła się", e)
            null
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }
    }

    fun detect(bitmap: Bitmap): List<PlateRegion> {
        val row = runInference(bitmap) ?: return emptyList()
        val xc = row[0]
        val yc = row[1]
        val w = row[2]
        val h = row[3]
        Log.d(LprDebug.TAG, "b) TFLite raw: x_center=$xc, y_center=$yc, width=$w, height=$h")
        if (!xc.isFinite() || !yc.isFinite() || !w.isFinite() || !h.isFinite()) {
            Log.v(TAG, "Model zwrócił nie-liczby — pomijam")
            return emptyList()
        }
        if (w <= 1e-6f || h <= 1e-6f) {
            return emptyList()
        }
        val rect = yoloNormToPixelRect(xc, yc, w, h, bitmap.width, bitmap.height) ?: return emptyList()
        if (rect.width() <= 0 || rect.height() <= 0) {
            return emptyList()
        }
        return listOf(PlateRegion(rect = rect, score = 0.92f))
    }

    fun detectPlate(bitmap: Bitmap): Bitmap? {
        val row = runInference(bitmap) ?: return null
        val xc = row[0]
        val yc = row[1]
        val w = row[2]
        val h = row[3]
        Log.d(LprDebug.TAG, "b) TFLite raw: x_center=$xc, y_center=$yc, width=$w, height=$h")
        if (!xc.isFinite() || !yc.isFinite() || !w.isFinite() || !h.isFinite()) {
            return null
        }
        if (w <= 1e-6f || h <= 1e-6f) {
            return null
        }
        val rect = yoloNormToPixelRect(xc, yc, w, h, bitmap.width, bitmap.height) ?: return null
        return imagePreprocessor.cropPlateForMlKit(bitmap, rect)
    }

    /**
     * YOLO [0,1]: środek i rozmiar względem bitmapy.
     * left = (x_center - width/2) * bitmap.width, itd.
     */
    private fun yoloNormToPixelRect(
        xCenter: Float,
        yCenter: Float,
        width: Float,
        height: Float,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Rect? {
        val cx = xCenter.coerceIn(0f, 1f)
        val cy = yCenter.coerceIn(0f, 1f)
        val bw = width.coerceIn(1e-6f, 1f)
        val bh = height.coerceIn(1e-6f, 1f)
        val left = ((cx - bw / 2f) * bitmapWidth).roundToInt().coerceIn(0, bitmapWidth - 1)
        val top = ((cy - bh / 2f) * bitmapHeight).roundToInt().coerceIn(0, bitmapHeight - 1)
        val right = ((cx + bw / 2f) * bitmapWidth).roundToInt().coerceIn(left + 1, bitmapWidth)
        val bottom = ((cy + bh / 2f) * bitmapHeight).roundToInt().coerceIn(top + 1, bitmapHeight)
        if (right <= left || bottom <= top) return null
        return Rect(left, top, right, bottom)
    }

    @Suppress("UNCHECKED_CAST")
    private fun outputToFloat4(output: Any): FloatArray? = when (output) {
        is FloatArray -> output
        is Array<*> -> (output as Array<FloatArray>)[0]
        else -> null
    }

    private fun createEngine(): Engine? {
        val modelBuffer = loadModelBuffer() ?: return null
        tryGpu(modelBuffer)?.let { return it }
        tryNnApi(modelBuffer)?.let { return it }
        return tryCpu(modelBuffer)
    }

    private fun loadModelBuffer(): MappedByteBuffer? {
        return try {
            context.assets.openFd(MODEL_ASSET).use { afd ->
                FileInputStream(afd.fileDescriptor).channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Brak modelu w assets: $MODEL_ASSET", e)
            null
        }
    }

    private fun tryGpu(modelBuffer: MappedByteBuffer): Engine? {
        var delegate: GpuDelegate? = null
        var interpreter: Interpreter? = null
        return try {
            delegate = GpuDelegate()
            val options = Interpreter.Options()
                .addDelegate(delegate)
                .setNumThreads(2)
            interpreter = Interpreter(modelBuffer, options)
            wrapEngine(interpreter, delegate, null)?.also {
                Log.i(TAG, "TFLite: GpuDelegate")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "GpuDelegate niedostępny, próba NNAPI: ${t.message}")
            runCatching { interpreter?.close() }
            runCatching { delegate?.close() }
            null
        }
    }

    private fun tryNnApi(modelBuffer: MappedByteBuffer): Engine? {
        var delegate: NnApiDelegate? = null
        var interpreter: Interpreter? = null
        return try {
            delegate = NnApiDelegate()
            val options = Interpreter.Options()
                .addDelegate(delegate)
                .setNumThreads(2)
            interpreter = Interpreter(modelBuffer, options)
            wrapEngine(interpreter, null, delegate)?.also {
                Log.i(TAG, "TFLite: NnApiDelegate")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "NNAPI niedostępny, użycie CPU: ${t.message}")
            runCatching { interpreter?.close() }
            runCatching { delegate?.close() }
            null
        }
    }

    private fun tryCpu(modelBuffer: MappedByteBuffer): Engine? {
        return try {
            val options = Interpreter.Options().setNumThreads(4)
            val interpreter = Interpreter(modelBuffer, options)
            wrapEngine(interpreter, null, null)?.also {
                Log.i(TAG, "TFLite: CPU")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Nie udało się utworzyć Interpretera (CPU)", t)
            null
        }
    }

    private fun wrapEngine(
        interpreter: Interpreter,
        gpuDelegate: GpuDelegate?,
        nnApiDelegate: NnApiDelegate?
    ): Engine? {
        val inShape = interpreter.getInputTensor(0).shape()
        val outShape = interpreter.getOutputTensor(0).shape()
        val inputNhwc = if (
            interpreter.getInputTensor(0).dataType() == DataType.FLOAT32 &&
            inShape.size == 4 &&
            inShape[0] == 1 &&
            inShape[1] == EXPECTED_SIZE &&
            inShape[2] == EXPECTED_SIZE &&
            inShape[3] == 3
        ) {
            Array(1) {
                Array(EXPECTED_SIZE) {
                    Array(EXPECTED_SIZE) {
                        FloatArray(3)
                    }
                }
            }
        } else {
            Log.w(
                TAG,
                "Oczekiwano wejścia [1,$EXPECTED_SIZE,$EXPECTED_SIZE,3], jest ${inShape.contentToString()}"
            )
            null
        }
        val output: Any? = when {
            outShape.contentEquals(intArrayOf(1, 4)) -> Array(1) { FloatArray(4) }
            outShape.contentEquals(intArrayOf(4)) -> FloatArray(4)
            else -> {
                Log.w(TAG, "Oczekiwano wyjścia [1,4] lub [4], jest ${outShape.contentToString()}")
                null
            }
        }
        if (inputNhwc == null || output == null) {
            interpreter.close()
            gpuDelegate?.close()
            nnApiDelegate?.close()
            return null
        }
        return Engine(interpreter, gpuDelegate, nnApiDelegate, inputNhwc, output)
    }

    private fun fillNhwcFromBitmap(
        scaled224: Bitmap,
        nhwc: Array<Array<Array<FloatArray>>>
    ) {
        val plane = nhwc[0]
        for (y in 0 until EXPECTED_SIZE) {
            for (x in 0 until EXPECTED_SIZE) {
                val px = scaled224.getPixel(x, y)
                plane[y][x][0] = Color.red(px) / 255f
                plane[y][x][1] = Color.green(px) / 255f
                plane[y][x][2] = Color.blue(px) / 255f
            }
        }
    }
}
