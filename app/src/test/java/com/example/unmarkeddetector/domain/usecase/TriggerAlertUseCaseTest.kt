package com.example.unmarkeddetector.domain.usecase

import com.example.unmarkeddetector.domain.model.AlertEvent
import com.example.unmarkeddetector.domain.model.PlateRecord
import com.example.unmarkeddetector.domain.repository.AlertDispatcher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TriggerAlertUseCaseTest {

    private var dispatchedEvent: AlertEvent? = null

    private val dispatcher = object : AlertDispatcher {
        override suspend fun dispatch(event: AlertEvent) {
            dispatchedEvent = event
        }

    }

    private val useCase = TriggerAlertUseCase(dispatcher)

    @Test
    fun `dispatches alert event for matched plate`() = runTest {
        val record = PlateRecord("WPI2C88", "Toyota", "Camry", "mazowieckie", 1L, 1)

        val event = useCase(record, 0.91f)

        assertThat(dispatchedEvent?.plateRecord?.plate).isEqualTo("WPI2C88")
        assertThat(event.confidence).isEqualTo(0.91f)
    }
}
