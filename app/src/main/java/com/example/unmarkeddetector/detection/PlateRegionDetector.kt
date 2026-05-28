package com.example.unmarkeddetector.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Detekcja tablic przez niebieski pasek EU po lewej stronie.
 *
 * Algorytm:
 * 1. Konwersja do HSV – szukamy niebieskiego regionu (pasek UE)
 * 2. Kandydaci: pionowe niebieskie prostokąty o proporcjach paska EU
 * 3. Rozszerzenie w prawo o oczekiwaną szerokość tablicy (proporcja ~4.5:1)
 * 4. Walidacja regionu tablicy (białe tło, kontrast znaków)
 * 5. Fallback Sobel dla tablic bez widocznego paska (np. widok z tyłu)
 */
@Singleton
class PlateRegionDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imagePreprocessor: ImagePreprocessor
) {
    companion object {
        private const val TAG = "PlateRegionDetector"

        // HSV zakres niebieskiego paska EU
        // H: 100-135 (niebieski), S: 80-255, V: 40-220
        private const val HUE_MIN = 100
        private const val HUE_MAX = 135
        private const val SAT_MIN = 80
        private const val SAT_MAX = 255
        private const val VAL_MIN = 40
        private const val VAL_MAX = 220

        // Proporcje paska EU względem tablicy
        // Polska tablica: 520x114mm, pasek EU: ~40x114mm
        // plate_width / strip_width ≈ 13.0
        private const val PLATE_TO_STRIP_RATIO = 13.0f
        // Tolerancja ±40%
        private const val RATIO_TOLERANCE = 0.4f

        // Rozmiary paska EU jako % obrazu
        private const val STRIP_MIN_HEIGHT_RATIO = 0.01f  // min 1% wysokości
        private const val STRIP_MAX_HEIGHT_RATIO = 0.20f  // max 20% wysokości
        private const val STRIP_MAX_WIDTH_RATIO  = 0.08f  // max 8% szerokości (wąski!)
        private const val STRIP_MIN_ASPECT       = 1.5f   // wyższy niż szerszy

        // Fallback Sobel
        private const val SOBEL_MIN_ASPECT = 2.0f
        private const val SOBEL_MAX_ASPECT = 8.0f
        private const val SOBEL_MIN_W_RATIO = 0.05f
        private const val SOBEL_MAX_W_RATIO = 0.80f
        private const val SOBEL_MIN_H_RATIO = 0.01f
        private const val SOBEL_MAX_H_RATIO = 0.20f

        private const val MAX_CANDIDATES = 6
    }

    private val licensePlateDetector = LicensePlateDetector(context)

    fun detect(bitmap: Bitmap): List<PlateRegion> {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        return try {
            // Krok 1: próba detekcji przez niebieski pasek EU
            val blueResults = detectByBlueStrip(bitmap)

            // Krok 2: fallback Sobel jeśli niebieski pasek nie znaleziony
            val results = if (blueResults.isNotEmpty()) {
                Log.d(LprDebug.TAG, "BlueStrip hit: ${blueResults.size} candidates")
                blueResults
            } else {
                val sobel = detectBySobel(bitmap)
                Log.d(LprDebug.TAG, "BlueStrip miss → Sobel fallback: ${sobel.size}")
                sobel
            }
            results
        } catch (e: Throwable) {
            Log.e(TAG, "Detection failed", e)
            emptyList()
        }
    }

    fun detectCenterFrame(bitmap: Bitmap): List<PlateRegion> {
        if (bitmap.isRecycled) return emptyList()
        return try { detect(bitmap).take(3) } catch (e: Throwable) { emptyList() }
    }

    fun detectPlate(bitmap: Bitmap): Bitmap? {
        val best = detect(bitmap).maxByOrNull { it.score } ?: return null
        return imagePreprocessor.cropPlate(bitmap, best.rect)
    }

    fun recognizePlateText(crop: Bitmap): String? =
        licensePlateDetector.recognizePlateText(crop)

    fun isRecognitionModelLoaded(): Boolean =
        licensePlateDetector.isRecognitionModelLoaded()

    fun close() = licensePlateDetector.close()

    // ── 1. Detekcja przez niebieski pasek EU ─────────────────────────────────

    private fun detectByBlueStrip(src: Bitmap): List<PlateRegion> {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Maska niebieskiego w HSV
        val blueMask = BooleanArray(w * h)
        for (i in pixels.indices) {
            val (hue, sat, value) = rgbToHsv(pixels[i])
            blueMask[i] = hue in HUE_MIN..HUE_MAX &&
                    sat in SAT_MIN..SAT_MAX &&
                    value in VAL_MIN..VAL_MAX
        }

        // Znajdź pionowe kolumny z dużym zagęszczeniem niebieskiego
        val blueColDensity = FloatArray(w) { x ->
            var count = 0
            for (y in 0 until h) if (blueMask[y * w + x]) count++
            count.toFloat() / h
        }

        // Znajdź spójne pionowe niebieskie paski
        val strips = findBlueStrips(blueColDensity, blueMask, w, h)

        return strips.mapNotNull { strip ->
            buildPlateFromStrip(strip, w, h, pixels)
        }.sortedByDescending { it.score }
    }

    private data class BlueStrip(val x1: Int, val x2: Int, val y1: Int, val y2: Int)

    private fun findBlueStrips(
        density: FloatArray, mask: BooleanArray, w: Int, h: Int
    ): List<BlueStrip> {
        val minColDensity = 0.20f  // min 20% kolumny musi być niebieska
        val strips = mutableListOf<BlueStrip>()
        var inStrip = false
        var stripStart = 0

        for (x in 0 until w) {
            if (density[x] >= minColDensity && !inStrip) {
                inStrip = true
                stripStart = x
            } else if ((density[x] < minColDensity || x == w - 1) && inStrip) {
                inStrip = false
                val stripWidth = x - stripStart

                // Pasek EU jest wąski
                if (stripWidth.toFloat() / w > STRIP_MAX_WIDTH_RATIO) continue

                // Znajdź zakres Y dla tego paska
                var y1 = h; var y2 = 0
                for (xi in stripStart until x) {
                    for (y in 0 until h) {
                        if (mask[y * w + xi]) {
                            if (y < y1) y1 = y
                            if (y > y2) y2 = y
                        }
                    }
                }
                if (y2 <= y1) continue

                val stripHeight = (y2 - y1).toFloat()
                val heightRatio = stripHeight / h

                if (heightRatio < STRIP_MIN_HEIGHT_RATIO ||
                    heightRatio > STRIP_MAX_HEIGHT_RATIO) continue

                // Pasek musi być wyższy niż szeroki
                if (stripHeight / stripWidth < STRIP_MIN_ASPECT) continue

                strips += BlueStrip(stripStart, x, y1, y2)
            }
        }
        return strips
    }

    private fun buildPlateFromStrip(
        strip: BlueStrip, imgW: Int, imgH: Int, pixels: IntArray
    ): PlateRegion? {
        val stripW = (strip.x2 - strip.x1).toFloat()
        val stripH = (strip.y2 - strip.y1).toFloat()

        // Oczekiwana szerokość całej tablicy na podstawie proporcji paska
        val expectedPlateW = (stripW * PLATE_TO_STRIP_RATIO).roundToInt()

        // Tablica rozciąga się w prawo od paska
        val plateLeft = strip.x1
        val plateRight = (plateLeft + expectedPlateW).coerceAtMost(imgW)
        val plateTop = (strip.y1 - stripH * 0.15f).roundToInt().coerceAtLeast(0)
        val plateBottom = (strip.y2 + stripH * 0.15f).roundToInt().coerceAtMost(imgH)

        if (plateRight <= plateLeft || plateBottom <= plateTop) return null

        val actualW = (plateRight - plateLeft).toFloat()
        val actualH = (plateBottom - plateTop).toFloat()
        if (actualH <= 0) return null

        val aspect = actualW / actualH
        // Polska tablica ~4.56:1, z tolerancją
        if (aspect < 2.5f || aspect > 8.0f) return null

        // Walidacja: region po prawej stronie paska powinien być jasny (białe tło tablicy)
        val whiteScore = validateWhiteBackground(
            pixels, imgW,
            (strip.x2 + 2).coerceAtMost(imgW - 1),
            plateRight.coerceAtMost(imgW - 1),
            plateTop, plateBottom
        )
        if (whiteScore < 0.25f) return null  // za mało białego tła

        // Score bazowany na proporcji paska i białości tła
        val aspectScore = 1f - (abs(aspect - 4.56f) / 4.56f).coerceIn(0f, 1f)
        val centerY = (plateTop + plateBottom) / 2f / imgH
        val posScore = if (centerY > 0.3f) 0.8f else 0.5f
        val score = (aspectScore * 0.4f + whiteScore * 0.4f + posScore * 0.2f)
            .coerceIn(0.1f, 0.99f)

        Log.d(LprDebug.TAG,
            "BlueStrip candidate: x=${plateLeft}-${plateRight} " +
                    "y=${plateTop}-${plateBottom} aspect=${"%.2f".format(aspect)} " +
                    "white=${"%.2f".format(whiteScore)} score=${"%.2f".format(score)}")

        return PlateRegion(
            rect = Rect(plateLeft, plateTop, plateRight, plateBottom),
            score = score,
            plateText = null,
            normalizedXCenter = (plateLeft + actualW / 2) / imgW,
            normalizedYCenter = (plateTop + actualH / 2) / imgH,
            normalizedWidth = actualW / imgW,
            normalizedHeight = actualH / imgH
        )
    }

    /** Sprawdza czy region ma białe tło (tablica ma białe lub żółte tło). */
    private fun validateWhiteBackground(
        pixels: IntArray, imgW: Int,
        x1: Int, x2: Int, y1: Int, y2: Int
    ): Float {
        if (x2 <= x1 || y2 <= y1) return 0f
        var brightCount = 0; var total = 0
        val stepX = ((x2 - x1) / 10).coerceAtLeast(1)
        val stepY = ((y2 - y1) / 8).coerceAtLeast(1)
        for (y in y1 until y2 step stepY) {
            for (x in x1 until x2 step stepX) {
                if (x >= imgW) continue
                val px = pixels[y * imgW + x]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val brightness = (r * 0.299f + g * 0.587f + b * 0.114f)
                if (brightness > 140f) brightCount++
                total++
            }
        }
        return if (total > 0) brightCount.toFloat() / total else 0f
    }

    // ── 2. Fallback Sobel ────────────────────────────────────────────────────

    private fun detectBySobel(src: Bitmap): List<PlateRegion> {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = FloatArray(w * h) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            r * 0.299f + g * 0.587f + b * 0.114f
        }

        // Sobel – więcej wagi na X (pionowe krawędzie znaków)
        val edge = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = -gray[(y-1)*w+(x-1)] - 2*gray[y*w+(x-1)] - gray[(y+1)*w+(x-1)] +
                        gray[(y-1)*w+(x+1)] + 2*gray[y*w+(x+1)] + gray[(y+1)*w+(x+1)]
                val gy = -gray[(y-1)*w+(x-1)] - 2*gray[(y-1)*w+x] - gray[(y-1)*w+(x+1)] +
                        gray[(y+1)*w+(x-1)] + 2*gray[(y+1)*w+x] + gray[(y+1)*w+(x+1)]
                edge[y * w + x] = abs(gx) * 0.75f + abs(gy) * 0.25f
            }
        }

        val threshold = otsuThreshold(edge)
        val binary = BooleanArray(w * h) { edge[it] > threshold }

        val kernW = (w * 0.022f).toInt().coerceAtLeast(8).coerceAtMost(35)
        val kernH = (h * 0.006f).toInt().coerceAtLeast(3).coerceAtMost(10)
        val closed = morphDilateH(binary, w, h, kernW, kernH)

        return findAndScoreRects(closed, pixels, w, h)
            .sortedByDescending { it.score }
            .take(MAX_CANDIDATES)
    }

    private fun morphDilateH(
        binary: BooleanArray, w: Int, h: Int, kw: Int, kh: Int
    ): BooleanArray {
        val hk = kw / 2; val vk = kh / 2
        val step1 = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val x0 = (x - hk).coerceAtLeast(0); val x1 = (x + hk).coerceAtMost(w - 1)
            step1[y * w + x] = (x0..x1).any { binary[y * w + it] }
        }
        val step2 = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val y0 = (y - vk).coerceAtLeast(0); val y1 = (y + vk).coerceAtMost(h - 1)
            step2[y * w + x] = (y0..y1).any { step1[it * w + x] }
        }
        return step2
    }

    private fun findAndScoreRects(
        binary: BooleanArray, pixels: IntArray, w: Int, h: Int
    ): List<PlateRegion> {
        // Scanline grupowanie
        val minRun = (w * SOBEL_MIN_W_RATIO).toInt()
        val results = mutableListOf<PlateRegion>()
        val rowRanges = Array(h) { y ->
            val ranges = mutableListOf<Pair<Int,Int>>()
            var inRun = false; var rs = 0
            for (x in 0 until w) {
                val on = binary[y * w + x]
                if (on && !inRun) { inRun = true; rs = x }
                else if (!on && inRun) { inRun = false; if (x - rs >= minRun) ranges += rs to x - 1 }
            }
            if (inRun && w - rs >= minRun) ranges += rs to w - 1
            ranges
        }

        // Merge pionowy
        data class ActiveBlob(var x1: Int, var x2: Int, var y1: Int, var y2: Int)
        val active = mutableListOf<ActiveBlob>()
        val finished = mutableListOf<ActiveBlob>()

        for (y in 0 until h) {
            val newActive = mutableListOf<ActiveBlob>()
            val usedBlob = mutableSetOf<Int>()
            for ((rx1, rx2) in rowRanges[y]) {
                val match = active.indexOfFirst { b ->
                    b !in active.filterIndexed { i, _ -> i in usedBlob } &&
                            rx1 <= b.x2 + 4 && rx2 >= b.x1 - 4
                }
                if (match >= 0) {
                    usedBlob += match
                    active[match].apply {
                        x1 = minOf(x1, rx1); x2 = maxOf(x2, rx2); y2 = y
                    }
                    newActive += active[match]
                } else {
                    newActive += ActiveBlob(rx1, rx2, y, y)
                }
            }
            active.filter { it !in newActive }.forEach { finished += it }
            active.clear(); active += newActive
        }
        finished += active

        for (blob in finished) {
            val bw = (blob.x2 - blob.x1).toFloat()
            val bh = (blob.y2 - blob.y1).toFloat()
            if (bh <= 0f || bw <= 0f) continue
            val aspect = bw / bh
            val wRatio = bw / w
            val hRatio = bh / h
            if (aspect < SOBEL_MIN_ASPECT || aspect > SOBEL_MAX_ASPECT) continue
            if (wRatio < SOBEL_MIN_W_RATIO || wRatio > SOBEL_MAX_W_RATIO) continue
            if (hRatio < SOBEL_MIN_H_RATIO || hRatio > SOBEL_MAX_H_RATIO) continue

            // Walidacja białego tła
            val white = validateWhiteBackground(
                pixels, w, blob.x1, blob.x2, blob.y1, blob.y2)
            if (white < 0.20f) continue

            val aspectScore = 1f - (abs(aspect - 4.56f) / 4.56f).coerceIn(0f, 1f)
            val centerY = (blob.y1 + bh / 2) / h
            val posScore = if (centerY > 0.3f) 0.7f else 0.4f
            val score = (aspectScore * 0.5f + white * 0.3f + posScore * 0.2f)
                .coerceIn(0.05f, 0.99f)

            results += PlateRegion(
                rect = Rect(blob.x1.coerceAtLeast(0), blob.y1.coerceAtLeast(0),
                    blob.x2.coerceAtMost(w), blob.y2.coerceAtMost(h)),
                score = score, plateText = null,
                normalizedXCenter = (blob.x1 + bw/2) / w,
                normalizedYCenter = (blob.y1 + bh/2) / h,
                normalizedWidth = bw / w, normalizedHeight = bh / h
            )
        }
        return results
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun rgbToHsv(pixel: Int): Triple<Int, Int, Int> {
        val r = ((pixel shr 16) and 0xFF) / 255f
        val g = ((pixel shr 8) and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val delta = max - min
        val h = when {
            delta < 0.001f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * ((b - r) / delta + 2f)
            else     -> 60f * ((r - g) / delta + 4f)
        }.let { if (it < 0) it + 360f else it }
        val s = if (max < 0.001f) 0f else delta / max
        return Triple(
            (h / 2f).toInt().coerceIn(0, 179),   // OpenCV-style: 0-179
            (s * 255f).toInt().coerceIn(0, 255),
            (max * 255f).toInt().coerceIn(0, 255)
        )
    }

    private fun otsuThreshold(edge: FloatArray): Float {
        val maxVal = edge.maxOrNull() ?: return 128f
        if (maxVal <= 0f) return 128f
        val bins = 256; val hist = IntArray(bins)
        val scale = (bins - 1) / maxVal
        edge.forEach { hist[(it * scale).toInt().coerceIn(0, bins-1)]++ }
        val total = edge.size
        var sum = 0.0
        for (i in 0 until bins) sum += i * hist[i]
        var sumB = 0.0; var wB = 0; var best = 0.0; var bestT = 0f
        for (t in 0 until bins) {
            wB += hist[t]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += t * hist[t]
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; bestT = t / scale }
        }
        return bestT
    }
}