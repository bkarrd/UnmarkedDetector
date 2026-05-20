package com.example.unmarkeddetector.detection

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlateTrackManagerTest {

    @Test
    fun `emits stable detection after repeated confirmations on same track`() {
        val manager = PlateTrackManager()
        val region = PlateRegion(Rect(100, 40, 320, 110), 0.92f)

        val firstTracks = manager.updateRegions(listOf(region), now = 1_000L)
        manager.registerRecognitions(
            trackId = firstTracks.first().trackId,
            detections = listOf(com.example.unmarkeddetector.domain.model.DetectionResult("RZE2A63", 0.94f, 1_000L)),
            now = 1_000L
        )

        val secondTracks = manager.updateRegions(listOf(region), now = 1_140L)
        manager.registerRecognitions(
            trackId = secondTracks.first().trackId,
            detections = listOf(com.example.unmarkeddetector.domain.model.DetectionResult("RZE2A63", 0.92f, 1_140L)),
            now = 1_140L
        )

        val stableDetections = manager.collectStableDetections(now = 1_200L)

        assertThat(stableDetections.map { it.plate }).containsExactly("RZE2A63")
    }

    @Test
    fun `does not emit detection after single uncertain observation`() {
        val manager = PlateTrackManager()
        val tracks = manager.updateRegions(
            regions = listOf(PlateRegion(Rect(80, 30, 280, 95), 0.88f)),
            now = 2_000L
        )
        manager.registerRecognitions(
            trackId = tracks.first().trackId,
            detections = listOf(com.example.unmarkeddetector.domain.model.DetectionResult("PO25Y7P", 0.79f, 2_000L)),
            now = 2_000L
        )

        val stableDetections = manager.collectStableDetections(now = 2_050L)

        assertThat(stableDetections).isEmpty()
    }
}
