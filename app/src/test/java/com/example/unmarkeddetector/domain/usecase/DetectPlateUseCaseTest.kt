package com.example.unmarkeddetector.domain.usecase

import com.example.unmarkeddetector.domain.model.RecognizedTextCandidate
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetectPlateUseCaseTest {

    private val useCase = DetectPlateUseCase()

    @Test
    fun `returns normalized unique plates above confidence threshold`() {
        val results = useCase(
            listOf(
                RecognizedTextCandidate("tablica wpi 2c88", 0.92f),
                RecognizedTextCandidate("WPI2C88", 0.95f),
                RecognizedTextCandidate("KR 123", 0.99f)
            ),
            detectedAt = 123L
        )

        assertThat(results).hasSize(1)
        assertThat(results.first().plate).isEqualTo("WPI2C88")
        assertThat(results.first().detectedAt).isEqualTo(123L)
    }

    @Test
    fun `rejects low confidence candidates`() {
        val results = useCase(
            listOf(RecognizedTextCandidate("KRA8M44", 0.79f))
        )

        assertThat(results).isEmpty()
    }

    @Test
    fun `rejects regular words that are not license plates`() {
        val results = useCase(
            listOf(
                RecognizedTextCandidate("TABLICA", 0.96f),
                RecognizedTextCandidate("POLSKIE", 0.97f),
                RecognizedTextCandidate("GENERATOR", 0.98f)
            )
        )

        assertThat(results).isEmpty()
    }
}
