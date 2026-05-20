package com.example.unmarkeddetector.domain.repository

import com.example.unmarkeddetector.domain.model.AlertEvent

interface AlertDispatcher {
    suspend fun dispatch(event: AlertEvent)
    suspend fun reportFalsePositive(event: AlertEvent)
}

