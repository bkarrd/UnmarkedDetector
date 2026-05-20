package com.example.unmarkeddetector.domain.model

import android.graphics.Rect

/**
 * Data class przenoszący dane do wizualizacji detekcji tablicy rejestracyjnej.
 * Używany do komunikacji między analizatorem klatki a GraphicOverlay.
 */
data class DetectionVisualizationData(
    /** Prostokąt tablicy w współrzędnych ekranu (piksele) */
    val detectionRect: Rect,

    /** Pewność detekcji [0.0, 1.0] */
    val confidence: Float,

    /** Czy detekcja wyzwala alert */
    val isAlert: Boolean = false,

    /** Label do wyświetlenia (np. tekst tablicy lub "ALERT") */
    val label: String = "",

    /** Timestamp detekcji w ms */
    val timestamp: Long = System.currentTimeMillis(),

    /** Kolor rysowania (domyślnie zielony dla normalnej detekcji) */
    val color: Int = android.graphics.Color.GREEN,

    /** Czy to nowa detekcja (pierwszy frame) */
    val isNew: Boolean = false,

    /** ID śledzenia (jeśli dostępne z PlateTrackManager) */
    val trackId: Int = -1
)
