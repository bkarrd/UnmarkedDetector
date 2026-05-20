package com.example.unmarkeddetector.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unmarkeddetector.detection.DetectionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainDrivingViewModel @Inject constructor(
    private val detectionCoordinator: DetectionCoordinator
) : ViewModel() {
    val sessionState = detectionCoordinator.sessionState
    val sessionCount = sessionState
        .map { it.uniquePlateCount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionState.value.uniquePlateCount)
    val lastDetectedPlates = sessionState
        .map { it.lastDetectedPlates }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionState.value.lastDetectedPlates)

    override fun onCleared() {
        if (!sessionState.value.serviceRunning) {
            detectionCoordinator.releasePlateTfliteResources()
        }
        super.onCleared()
    }
}
