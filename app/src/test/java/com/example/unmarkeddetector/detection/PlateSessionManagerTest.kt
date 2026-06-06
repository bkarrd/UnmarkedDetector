package com.example.unmarkeddetector.detection

import com.example.unmarkeddetector.domain.model.DetectionResult
import com.example.unmarkeddetector.domain.model.PlateRecord
import com.example.unmarkeddetector.domain.repository.PlateRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlateSessionManagerTest {

    private val repository = object : PlateRepository {
        override suspend fun seedIfEmpty() = Unit

        override suspend fun syncFromRemote(): Int = 0

        override suspend fun findByPlate(plate: String): PlateRecord? {
            return if (plate == "RZE2A63") {
                PlateRecord("RZE2A63", "Opel", "Insignia", "podkarpackie", 1L, 1)
            } else {
                null
            }
        }

        override fun getDatabaseVersion(): Flow<String> = emptyFlow()
    }

    private val manager = PlateSessionManager(repository)

    @Test
    fun `counts only unique plates in session`() = runTest {
        val first = manager.processScan(
            listOf(
                DetectionResult("RZE2A63", 0.95f, 1L),
                DetectionResult("RZE2A63", 0.91f, 1L),
                DetectionResult("WPI2C88", 0.93f, 1L)
            )
        )
        val second = manager.processScan(
            listOf(
                DetectionResult("RZE2A63", 0.92f, 2L),
                DetectionResult("WPI2C88", 0.93f, 2L)
            )
        )

        assertThat(first.totalUniqueInSession).isEqualTo(2)
        assertThat(first.newUniquePlates).containsExactly("RZE2A63", "WPI2C88")
        assertThat(second.totalUniqueInSession).isEqualTo(2)
        assertThat(second.newUniquePlates).isEmpty()
    }

    @Test
    fun `applies alert cooldown for same plate`() = runTest {
        val first = manager.processScan(listOf(DetectionResult("RZE2A63", 0.95f, 1L)))
        val second = manager.processScan(listOf(DetectionResult("RZE2A63", 0.96f, 2L)))

        assertThat(first.alertMatches).hasSize(1)
        assertThat(second.alertMatches).isEmpty()
    }

    @Test
    fun `reports observed plate regions even before accepted OCR`() = runTest {
        val result = manager.processScan(
            detectedPlates = emptyList(),
            observedPlateRegionCount = 2
        )

        assertThat(result.detectedPlates).isEmpty()
        assertThat(result.totalUniqueInSession).isEqualTo(0)
        assertThat(result.observedPlateRegionCount).isEqualTo(2)
    }
}
