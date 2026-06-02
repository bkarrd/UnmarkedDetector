package com.example.unmarkeddetector.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.unmarkeddetector.MainActivity
import com.example.unmarkeddetector.R
import com.example.unmarkeddetector.detection.DetectionCoordinator
import com.example.unmarkeddetector.domain.model.DetectionSessionState
import com.example.unmarkeddetector.util.createDetectionNotificationChannel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetectionForegroundService : LifecycleService() {

    companion object {
        private const val TAG = "DetectionService"
        private const val ACTION_START = "com.example.unmarkeddetector.action.START"
        private const val ACTION_STOP = "com.example.unmarkeddetector.action.STOP"
        private const val NOTIFICATION_ID = 2201
        private const val BACKGROUND_RESTRICTION_CHECK_INTERVAL_MS = 30_000L
        const val NOTIFICATION_CHANNEL_ID = "detection_service"

        fun createIntent(context: Context, action: String = ACTION_START): Intent {
            return Intent(context, DetectionForegroundService::class.java).apply {
                this.action = action
            }
        }

        fun start(context: Context) {
            context.startForegroundService(createIntent(context, ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(createIntent(context, ACTION_STOP))
        }
    }

    @Inject
    lateinit var detectionCoordinator: DetectionCoordinator

    @Inject
    lateinit var drivingSpeedMonitor: DrivingSpeedMonitor

    private var speedCollectionJob: Job? = null
    private var notificationCollectionJob: Job? = null
    private var screenOffSuppressionJob: Job? = null
    private var backgroundRestrictionJob: Job? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> startScreenOffTimer()
                Intent.ACTION_SCREEN_ON -> cancelScreenOffTimer()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createDetectionNotificationChannel()
        registerReceiver(
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_STOP -> stopDetection()
            else -> startDetection()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenStateReceiver)
        speedCollectionJob?.cancel()
        notificationCollectionJob?.cancel()
        screenOffSuppressionJob?.cancel()
        backgroundRestrictionJob?.cancel()
        detectionCoordinator.setServiceRunning(false)
        detectionCoordinator.setUserEnabled(false)
        detectionCoordinator.setBackgroundRestricted(false)
        detectionCoordinator.releaseCamera(owner = this)
        if (!detectionCoordinator.hasAttachedPreview()) {
            detectionCoordinator.releaseDetectionResources()
        }
        super.onDestroy()
    }

    private fun startDetection() {
        Log.i(TAG, "Starting detection session")
        startForeground(NOTIFICATION_ID, buildNotification(detectionCoordinator.sessionState.value))
        detectionCoordinator.resetSessionCounters()
        detectionCoordinator.setServiceRunning(true)
        detectionCoordinator.setUserEnabled(true)
        startBackgroundRestrictionMonitor()
        lifecycleScope.launch {
            runCatching {
                val includePreview = detectionCoordinator.hasAttachedPreview()
                Log.i(TAG, "Binding camera to foreground service, includePreview=$includePreview")
                detectionCoordinator.ensureCameraBound(
                    owner = this@DetectionForegroundService,
                    includePreview = includePreview
                )
            }.onFailure { error ->
                Log.e(TAG, "Failed to bind service camera", error)
            }
        }

        speedCollectionJob?.cancel()
        speedCollectionJob = lifecycleScope.launch {
            drivingSpeedMonitor.observeSpeedKmh().collectLatest { speed ->
                Log.v(TAG, "GPS speed update: $speed km/h")
                detectionCoordinator.updateSpeed(speed)
            }
        }

        notificationCollectionJob?.cancel()
        notificationCollectionJob = lifecycleScope.launch {
            detectionCoordinator.sessionState.collectLatest { state ->
                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    private fun stopDetection() {
        Log.i(TAG, "Stopping detection session")
        speedCollectionJob?.cancel()
        notificationCollectionJob?.cancel()
        screenOffSuppressionJob?.cancel()
        backgroundRestrictionJob?.cancel()
        detectionCoordinator.setServiceRunning(false)
        detectionCoordinator.setUserEnabled(false)
        detectionCoordinator.updateSpeed(0f)
        detectionCoordinator.setScreenOffTooLong(false)
        detectionCoordinator.setBackgroundRestricted(false)
        detectionCoordinator.releaseCamera(owner = this)
        if (!detectionCoordinator.hasAttachedPreview()) {
            detectionCoordinator.releaseDetectionResources()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startScreenOffTimer() {
        screenOffSuppressionJob?.cancel()
        screenOffSuppressionJob = lifecycleScope.launch {
            delay(30 * 60 * 1000L)
            detectionCoordinator.setScreenOffTooLong(true)
        }
    }

    private fun cancelScreenOffTimer() {
        screenOffSuppressionJob?.cancel()
        detectionCoordinator.setScreenOffTooLong(false)
    }

    private fun startBackgroundRestrictionMonitor() {
        backgroundRestrictionJob?.cancel()
        backgroundRestrictionJob = lifecycleScope.launch {
            while (true) {
                val restricted = isBackgroundExecutionRestricted()
                detectionCoordinator.setBackgroundRestricted(restricted)
                if (restricted) {
                    Log.w(TAG, "Background execution is restricted by system battery settings")
                }
                delay(BACKGROUND_RESTRICTION_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun isBackgroundExecutionRestricted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val activityManager = getSystemService(ActivityManager::class.java)
        return activityManager?.isBackgroundRestricted == true
    }

    private fun buildNotification(state: DetectionSessionState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            200,
            createIntent(this, ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val batterySettingsIntent = PendingIntent.getActivity(
            this,
            300,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inactive = state.serviceRunning && !state.canAnalyze
        val title = getString(
            if (inactive) {
                R.string.service_notification_title_inactive
            } else {
                R.string.service_notification_title
            }
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_camera)
            .setContentTitle(title)
            .setContentText(state.statusLabel)
            .setStyle(NotificationCompat.BigTextStyle().bigText(state.statusLabel))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.service_action_stop),
                stopIntent
            )

        if (state.backgroundRestricted) {
            builder.addAction(
                android.R.drawable.ic_menu_manage,
                getString(R.string.service_action_battery_settings),
                batterySettingsIntent
            )
        }

        return builder.build()
    }

}
