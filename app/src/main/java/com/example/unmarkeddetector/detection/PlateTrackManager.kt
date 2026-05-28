package com.example.unmarkeddetector.detection

import android.graphics.Rect
import com.example.unmarkeddetector.domain.model.DetectionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlateTrackManager @Inject constructor() {

    private data class Track(
        val id: Int,
        var rect: Rect,
        var regionScore: Float,
        var lastSeenAt: Long,
        var consecutiveHits: Int = 1,
        var missedFrames: Int = 0,
        var lastOcrAt: Long = 0L,
        val ocrVotes: MutableMap<String, Float> = mutableMapOf(),
        var ocrObservations: Int = 0
    )

    private val tracks = linkedMapOf<Int, Track>()
    private var nextTrackId = 1

    fun updateRegions(regions: List<PlateRegion>, now: Long): List<PlateTrackSnapshot> {
        val availableTrackIds = tracks.keys.toMutableSet()
        val availableRegionIndexes = regions.indices.toMutableSet()
        val matches = mutableListOf<Pair<Int, Int>>()

        val candidates = buildList {
            regions.forEachIndexed { regionIndex, region ->
                tracks.values.forEach { track ->
                    add(
                        Triple(
                            track.id,
                            regionIndex,
                            matchScore(track.rect, region.rect)
                        )
                    )
                }
            }
        }.sortedByDescending { it.third }

        candidates.forEach { (trackId, regionIndex, score) ->
            if (score < 0.18f) return@forEach
            if (trackId !in availableTrackIds || regionIndex !in availableRegionIndexes) return@forEach
            matches += trackId to regionIndex
            availableTrackIds.remove(trackId)
            availableRegionIndexes.remove(regionIndex)
        }

        matches.forEach { (trackId, regionIndex) ->
            val track = tracks.getValue(trackId)
            val region = regions[regionIndex]
            track.rect = region.rect
            track.regionScore = region.score
            track.lastSeenAt = now
            track.consecutiveHits += 1
            track.missedFrames = 0
        }

        availableTrackIds.forEach { trackId ->
            val track = tracks[trackId] ?: return@forEach
            track.missedFrames += 1
            track.consecutiveHits = 0
        }

        availableRegionIndexes.forEach { regionIndex ->
            val region = regions[regionIndex]
            tracks[nextTrackId] = Track(
                id = nextTrackId,
                rect = region.rect,
                regionScore = region.score,
                lastSeenAt = now
            )
            nextTrackId += 1
        }

        tracks.entries.removeAll { (_, track) ->
            now - track.lastSeenAt > 600L || track.missedFrames >= 4
        }

        return tracks.values
            .sortedWith(
                compareByDescending<Track> { it.consecutiveHits }
                    .thenByDescending { it.regionScore }
            )
            .take(4)
            .map { track ->
                PlateTrackSnapshot(
                    trackId = track.id,
                    rect = track.rect,
                    regionScore = track.regionScore,
                    consecutiveHits = track.consecutiveHits,
                    shouldRunOcr = shouldRunOcr(track, now)
                )
            }
    }

    fun registerRecognitions(trackId: Int, detections: List<DetectionResult>, now: Long) {
        val track = tracks[trackId] ?: return
        track.lastOcrAt = now
        if (detections.isEmpty()) return

        track.ocrObservations += 1
        detections.forEach { detection ->
            track.ocrVotes[detection.plate] =
                (track.ocrVotes[detection.plate] ?: 0f) + detection.confidence
        }
    }

    fun collectStableDetections(now: Long): List<DetectionResult> {
        return tracks.values
            .filter { now - it.lastSeenAt <= 900L }
            .mapNotNull { track ->
                val sortedVotes = track.ocrVotes.entries.sortedByDescending { it.value }
                val best = sortedVotes.firstOrNull() ?: return@mapNotNull null
                val second = sortedVotes.getOrNull(1)?.value ?: 0f

                if (track.ocrObservations < 1) return@mapNotNull null

                if (track.ocrObservations == 1) {
                    if (best.value < 0.88f) return@mapNotNull null
                } else {
                    if (best.value < 0.68f) return@mapNotNull null
                }

                val ambiguous = second > 0f && (best.value - second) < 0.18f
                if (ambiguous && track.ocrObservations < 3) return@mapNotNull null

                DetectionResult(
                    plate = best.key,
                    confidence = (best.value / track.ocrObservations).coerceIn(0.72f, 0.99f),
                    detectedAt = now
                )
            }
            .groupBy { it.plate }
            .map { (_, detections) -> detections.maxBy { it.confidence } }
            .sortedByDescending { it.confidence }
    }

    fun leadingOcrText(trackId: Int): String? {
        val track = tracks[trackId] ?: return null
        return track.ocrVotes.maxByOrNull { it.value }?.key
    }

    fun reset() {
        tracks.clear()
        nextTrackId = 1
    }

    private fun shouldRunOcr(track: Track, now: Long): Boolean {
        if (track.consecutiveHits == 0) return false
        val ocrIntervalMs = if (track.ocrObservations >= 2) 220L else 90L
        return now - track.lastOcrAt >= ocrIntervalMs
    }

    private fun matchScore(first: Rect, second: Rect): Float {
        val iou = iou(first, second)
        if (iou > 0f) return iou

        val centerDistance = centerDistance(first, second)
        val maxDimension = maxOf(
            rectWidth(first),
            rectHeight(first),
            rectWidth(second),
            rectHeight(second)
        ).toFloat()
        return (1f - (centerDistance / (maxDimension * 3f)).coerceAtMost(1f)) * 0.15f
    }

    private fun centerDistance(first: Rect, second: Rect): Float {
        val dx = ((first.left + first.right) / 2f) - ((second.left + second.right) / 2f)
        val dy = ((first.top + first.bottom) / 2f) - ((second.top + second.bottom) / 2f)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun iou(first: Rect, second: Rect): Float {
        val left = maxOf(first.left, second.left)
        val top = maxOf(first.top, second.top)
        val right = minOf(first.right, second.right)
        val bottom = minOf(first.bottom, second.bottom)
        val intersectionWidth = (right - left).coerceAtLeast(0)
        val intersectionHeight = (bottom - top).coerceAtLeast(0)
        val intersectionArea = intersectionWidth * intersectionHeight
        if (intersectionArea == 0) return 0f
        val unionArea =
            rectWidth(first) * rectHeight(first) + rectWidth(second) * rectHeight(second) - intersectionArea
        return intersectionArea.toFloat() / unionArea.toFloat()
    }

    private fun rectWidth(rect: Rect): Int = rect.right - rect.left

    private fun rectHeight(rect: Rect): Int = rect.bottom - rect.top
}
