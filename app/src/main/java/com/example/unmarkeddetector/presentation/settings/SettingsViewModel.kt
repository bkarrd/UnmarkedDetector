package com.example.unmarkeddetector.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unmarkeddetector.BuildConfig
import com.example.unmarkeddetector.domain.repository.PlateRepository
import com.example.unmarkeddetector.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val plateRepository: PlateRepository
) : ViewModel() {

    sealed interface SyncResult {
        data class Success(val records: Int) : SyncResult
        data class Error(val message: String?) : SyncResult
    }

    private val _syncResult = MutableSharedFlow<SyncResult>(extraBufferCapacity = 1)
    val syncResult: SharedFlow<SyncResult> = _syncResult.asSharedFlow()

    val settings = settingsRepository.observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = settingsRepository.currentSettings()
        )

    val databaseVersion: String = BuildConfig.PLATE_DB_VERSION

    fun updateAlertVolume(volume: Int) {
        viewModelScope.launch {
            settingsRepository.updateAlertVolume(volume)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVibrationEnabled(enabled)
        }
    }

    fun syncDatabase() {
        viewModelScope.launch {
            runCatching { plateRepository.syncFromRemote() }
                .onSuccess { count -> _syncResult.tryEmit(SyncResult.Success(count)) }
                .onFailure { error -> _syncResult.tryEmit(SyncResult.Error(error.message)) }
        }
    }
}
