package com.example.unmarkeddetector.presentation.main

import androidx.lifecycle.ViewModel
import com.example.unmarkeddetector.detection.DetectionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainDrivingViewModel @Inject constructor(
    private val detectionCoordinator: DetectionCoordinator
) : ViewModel() {
    val sessionState = detectionCoordinator.sessionState

    override fun onCleared() {
        if (!sessionState.value.serviceRunning) {
            detectionCoordinator.releaseDetectionResources()
        }
        super.onCleared()
    }
}
