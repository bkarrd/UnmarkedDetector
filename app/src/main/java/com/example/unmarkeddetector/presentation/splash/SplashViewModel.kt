package com.example.unmarkeddetector.presentation.splash

import androidx.lifecycle.ViewModel
import com.example.unmarkeddetector.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    fun shouldOpenDrivingScreen(): Boolean = settingsRepository.currentSettings().onboardingCompleted
}
