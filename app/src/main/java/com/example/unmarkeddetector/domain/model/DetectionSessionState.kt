package com.example.unmarkeddetector.domain.model

data class DetectionSessionState(
    val serviceRunning: Boolean = false,
    val userEnabled: Boolean = false,
    val currentSpeedKmh: Float = 0f,
    val screenOffTooLong: Boolean = false,
    val backgroundRestricted: Boolean = false,
    val uniquePlateCount: Int = 0,
    val uniquePlates: List<String> = emptyList(),
    val alertCount: Int = 0,
    val lastDetectedPlates: List<String> = emptyList(),
    val lastDetectedPlate: String? = null,
    val lastAlertPlate: String? = null
) {
    val canAnalyze: Boolean
        get() = serviceRunning && userEnabled && !screenOffTooLong && !backgroundRestricted

    val statusLabel: String
        get() = when {
            !serviceRunning || !userEnabled -> "Wstrzymane"
            backgroundRestricted -> "Nieaktywny - ograniczone działanie w tle"
            screenOffTooLong -> "Pauza - ekran wygaszony ponad 30 min"
            else -> "Aktywny - skanowanie tablic"
        }
}
