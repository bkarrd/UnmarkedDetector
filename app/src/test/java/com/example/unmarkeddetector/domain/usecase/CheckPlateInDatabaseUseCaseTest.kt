package com.example.unmarkeddetector.domain.usecase

import com.example.unmarkeddetector.domain.model.PlateRecord
import com.example.unmarkeddetector.domain.repository.PlateRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CheckPlateInDatabaseUseCaseTest {

    private val repository = object : PlateRepository {
        val lookups = mutableListOf<String>()

        override suspend fun seedIfEmpty() = Unit

        override suspend fun syncFromRemote(): Int = 0

        override suspend fun findByPlate(plate: String): PlateRecord? {
            lookups += plate
            return if (plate == "WPI2C88") {
                PlateRecord("WPI2C88", "Toyota", "Camry", "mazowieckie", 1L, 1)
            } else if (plate == "RZE2A63") {
                PlateRecord("RZE2A63", "Opel", "Insignia", "podkarpackie", 1L, 1)
            } else {
                null
            }
        }

        override fun getDatabaseVersion(): Flow<String> = emptyFlow()
    }

    private val useCase = CheckPlateInDatabaseUseCase(repository)

    @Test
    fun `normalizes whitespace and uppercases lookup`() = runTest {
        val result = useCase("wpi 2c88")

        assertThat(repository.lookups.first()).isEqualTo("WPI2C88")
        assertThat(result?.brand).isEqualTo("Toyota")
    }

    @Test
    fun `returns null when plate is not in database`() = runTest {
        val result = useCase("ABC12345")

        assertThat(result).isNull()
    }

    @Test
    fun `tries common ocr correction variants when exact lookup misses`() = runTest {
        val result = useCase("R2E2A63")

        assertThat(repository.lookups).contains("RZE2A63")
        assertThat(result?.plate).isEqualTo("RZE2A63")
    }
}
