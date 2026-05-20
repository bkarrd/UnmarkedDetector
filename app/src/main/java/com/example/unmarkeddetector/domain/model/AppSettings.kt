package com.example.unmarkeddetector.domain.model

data class AppSettings(
    val alertVolume: Int = 80,
    val vibrationEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false
)

