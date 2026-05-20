package com.example.unmarkeddetector.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.unmarkeddetector.MainActivity
import com.example.unmarkeddetector.R
import com.example.unmarkeddetector.detection.DetectionCoordinator
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
        const val NOTIFICATION_CHANNEL_ID = "detection_service"

        fun createIntent(context: Context, action: String = ACTION_START): Intent {
            return Intent(context, DetectionForegroundService::class.java).apply {
                this.action = action
            }
        }

        fun start(context: Context) {
            val intent = createIntent(context, ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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
        detectionCoordinator.setServiceRunning(false)
        detectionCoordinator.setUserEnabled(false)
        detectionCoordinator.releaseCamera()
        detectionCoordinator.releasePlateTfliteResources()
        super.onDestroy()
    }

    private fun startDetection() {
        Log.i(TAG, "Starting detection session")
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_stopped)))
        detectionCoordinator.resetSessionCounters()
        detectionCoordinator.setServiceRunning(true)
        detectionCoordinator.setUserEnabled(true)
        if (!detectionCoordinator.hasAttachedPreview()) {
            lifecycleScope.launch {
                runCatching {
                    detectionCoordinator.ensureCameraBound(
                        owner = this@DetectionForegroundService,
                        includePreview = false
                    )
                }.onFailure { error ->
                    Log.e(TAG, "Failed to bind background camera", error)
                }
            }
        } else {
            Log.i(TAG, "Skipping service camera rebind because foreground preview is attached")
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
                notificationManager.notify(NOTIFICATION_ID, buildNotification(state.statusLabel))
            }
        }
    }

    private fun stopDetection() {
        Log.i(TAG, "Stopping detection session")
        speedCollectionJob?.cancel()
        notificationCollectionJob?.cancel()
        screenOffSuppressionJob?.cancel()
        detectionCoordinator.setServiceRunning(false)
        detectionCoordinator.setUserEnabled(false)
        detectionCoordinator.updateSpeed(0f)
        detectionCoordinator.setScreenOffTooLong(false)
        detectionCoordinator.releaseCamera()
        detectionCoordinator.releasePlateTfliteResources()
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

    private fun buildNotification(status: String): Notification {
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

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(status)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.service_action_stop),
                stopIntent
            )
            .build()
    }

}
