package com.example.unmarkeddetector.detection

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.example.unmarkeddetector.di.ApplicationScope
import com.example.unmarkeddetector.domain.model.AlertEvent
import com.example.unmarkeddetector.domain.model.DetectionSessionState
import com.example.unmarkeddetector.domain.usecase.TriggerAlertUseCase
import com.example.unmarkeddetector.util.await
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Singleton
class DetectionCoordinator @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val detectionPipeline: DetectionPipeline,
    private val adaptiveScanScheduler: AdaptiveScanScheduler,
    private val triggerAlertUseCase: TriggerAlertUseCase,
    private val visualizationManager: VisualizationManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    companion object {
        private const val TAG = "DetectionCoordinator"
    }

    private val _sessionState = MutableStateFlow(DetectionSessionState())
    val sessionState: StateFlow<DetectionSessionState> = _sessionState.asStateFlow()

    private val _alerts = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 8)
    val alerts: SharedFlow<AlertEvent> = _alerts.asSharedFlow()

    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var previewView: PreviewView? = null
    private var boundOwner: LifecycleOwner? = null
    private var previewEnabledInBinding: Boolean = false

    suspend fun ensureCameraBound(
        owner: LifecycleOwner,
        includePreview: Boolean = previewView != null
    ) {
        if (
            boundOwner === owner &&
            cameraProvider != null &&
            previewEnabledInBinding == includePreview
        ) {
            Log.d(TAG, "Camera already bound for owner=${owner::class.java.simpleName}")
            previewUseCase?.let { preview ->
                previewView?.let { preview.setSurfaceProvider(it.surfaceProvider) }
            }
            return
        }

        boundOwner = owner
        val provider = withContext(Dispatchers.Default) {
            ProcessCameraProvider.getInstance(appContext).await()
        }
        cameraProvider = provider

        val preview = if (includePreview) {
            Preview.Builder().build().also { preview ->
                previewView?.let { preview.setSurfaceProvider(it.surfaceProvider) }
            }
        } else {
            null
        }
        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
        val imageAnalysis = analysisUseCase ?: ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(
                    analyzerExecutor,
                    PlateFrameAnalyzer(
                        detectionPipeline = detectionPipeline,
                        adaptiveScanScheduler = adaptiveScanScheduler,
                        triggerAlertUseCase = triggerAlertUseCase,
                        analysisScope = applicationScope,
                        canAnalyze = { sessionState.value.canAnalyze },
                        onScanResult = ::handleScanResult,
                        onAlerts = ::handleAlerts
                    )
                )
            }
        analysisUseCase = imageAnalysis

        provider.unbindAll()
        Log.i(
            TAG,
            "Binding camera to owner=${owner::class.java.simpleName}, previewEnabled=$includePreview, previewAttached=${previewView != null}"
        )
        if (preview != null) {
            provider.bindToLifecycle(
                owner,
                // TODO(v1.0): swap/extend this frame source with Viofo HTTP snapshots over Wi-Fi.
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } else {
            provider.bindToLifecycle(
                owner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                imageAnalysis
            )
        }

        previewUseCase = preview
        previewEnabledInBinding = includePreview
        Log.i(TAG, "Camera binding completed")
    }

    fun attachPreview(previewView: PreviewView) {
        this.previewView = previewView
        previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
        Log.d(TAG, "PreviewView attached to detection coordinator")
        if (previewUseCase == null && boundOwner != null) {
            applicationScope.launch {
                runCatching {
                    ensureCameraBound(boundOwner!!, includePreview = true)
                }.onFailure { error ->
                    Log.e(TAG, "Failed to rebind camera with preview", error)
                }
            }
        }
    }

    fun attachGraphicOverlay(overlay: com.example.unmarkeddetector.ui.common.GraphicOverlay) {
        visualizationManager.attachGraphicOverlay(overlay)
        Log.d(TAG, "GraphicOverlay attached to visualization manager")
    }

    fun detachGraphicOverlay() {
        visualizationManager.detachGraphicOverlay()
        Log.d(TAG, "GraphicOverlay detached from visualization manager")
    }

    fun detachPreview() {
        previewView = null
        previewEnabledInBinding = false
        detachGraphicOverlay()
    }

    fun hasAttachedPreview(): Boolean = previewView != null

    fun setServiceRunning(running: Boolean) {
        _sessionState.value = _sessionState.value.copy(serviceRunning = running)
    }

    fun setUserEnabled(enabled: Boolean) {
        _sessionState.value = _sessionState.value.copy(userEnabled = enabled)
    }

    fun updateSpeed(speedKmh: Float) {
        _sessionState.value = _sessionState.value.copy(currentSpeedKmh = speedKmh)
    }

    fun setScreenOffTooLong(suppressed: Boolean) {
        _sessionState.value = _sessionState.value.copy(screenOffTooLong = suppressed)
    }

    fun resetSessionCounters() {
        detectionPipeline.resetSession()
        adaptiveScanScheduler.reset()
        _sessionState.value = _sessionState.value.copy(
            uniquePlateCount = 0,
            uniquePlates = emptyList(),
            alertCount = 0,
            lastDetectedPlates = emptyList(),
            currentScanIntervalMs = adaptiveScanScheduler.currentIntervalMs(),
            lastDetectedPlate = null,
            lastAlertPlate = null
        )
    }

    fun cycleScanInterval(): Long {
        val nextInterval = adaptiveScanScheduler.cycleManualIntervalMs()
        _sessionState.value = _sessionState.value.copy(currentScanIntervalMs = nextInterval)
        return nextInterval
    }

    fun releaseCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        previewUseCase = null
        analysisUseCase = null
        boundOwner = null
        previewEnabledInBinding = false
        Log.i(TAG, "Camera released")
    }

    /** Zamyka interpreter tablicy TFLite i delegaty — po [releaseCamera], gdy detekcja nie jest już potrzebna. */
    fun releasePlateTfliteResources() {
        detectionPipeline.releasePlateTfliteResources()
    }

    private fun handleScanResult(
        result: PlateSessionManager.ScanResult,
        currentIntervalMs: Long
    ) {
        val current = _sessionState.value
        val bestDetection = result.detectedPlates.firstOrNull()
        _sessionState.value = current.copy(
            uniquePlateCount = result.totalUniqueInSession,
            uniquePlates = detectionPipeline.getSessionPlates(),
            lastDetectedPlates = result.detectedPlates,
            currentScanIntervalMs = currentIntervalMs,
            lastDetectedPlate = bestDetection ?: current.lastDetectedPlate
        )
        Log.d(TAG, "Detections accepted=${result.detectedPlates.size}, last=$bestDetection")
    }

    private fun handleAlerts(events: List<AlertEvent>) {
        if (events.isEmpty()) return

        _sessionState.value = _sessionState.value.copy(
            alertCount = _sessionState.value.alertCount + events.size,
            lastAlertPlate = events.last().plateRecord.plate
        )
        events.forEach { event ->
            _alerts.tryEmit(event)
            Log.i(TAG, "Alert emitted for plate=${event.plateRecord.plate}")
        }
    }
}
