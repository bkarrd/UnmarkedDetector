package com.example.unmarkeddetector.data.repository

import android.content.SharedPreferences
import com.example.unmarkeddetector.domain.model.AppSettings
import com.example.unmarkeddetector.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : SettingsRepository {

    private val settingsFlow = MutableStateFlow(loadSettings())

    override fun observeSettings(): Flow<AppSettings> = settingsFlow.asStateFlow()

    override fun currentSettings(): AppSettings = settingsFlow.value

    override suspend fun updateAlertVolume(volume: Int) {
        sharedPreferences.edit().putInt(KEY_ALERT_VOLUME, volume).apply()
        settingsFlow.value = settingsFlow.value.copy(alertVolume = volume)
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_VIBRATION, enabled).apply()
        settingsFlow.value = settingsFlow.value.copy(vibrationEnabled = enabled)
    }

    override suspend fun completeOnboarding() {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        settingsFlow.value = settingsFlow.value.copy(onboardingCompleted = true)
    }

    private fun loadSettings(): AppSettings = AppSettings(
        alertVolume = sharedPreferences.getInt(KEY_ALERT_VOLUME, 80),
        vibrationEnabled = sharedPreferences.getBoolean(KEY_VIBRATION, true),
        onboardingCompleted = sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    )

    private companion object {
        private const val KEY_ALERT_VOLUME = "alert_volume"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
