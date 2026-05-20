package com.example.unmarkeddetector.domain.repository

import com.example.unmarkeddetector.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    fun currentSettings(): AppSettings
    suspend fun updateAlertVolume(volume: Int)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun completeOnboarding()
}

