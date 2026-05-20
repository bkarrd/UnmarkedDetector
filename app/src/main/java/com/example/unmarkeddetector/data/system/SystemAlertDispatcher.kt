package com.example.unmarkeddetector.data.system

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.unmarkeddetector.MainActivity
import com.example.unmarkeddetector.R
import com.example.unmarkeddetector.di.ApplicationScope
import com.example.unmarkeddetector.domain.model.AlertEvent
import com.example.unmarkeddetector.domain.repository.AlertDispatcher
import com.example.unmarkeddetector.domain.repository.SettingsRepository
import com.example.unmarkeddetector.presentation.alert.AlertOverlayController
import com.example.unmarkeddetector.util.ALERT_NOTIFICATION_CHANNEL_ID
import com.example.unmarkeddetector.util.createAlertNotificationChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SystemAlertDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val overlayController: AlertOverlayController,
    @ApplicationScope private val applicationScope: CoroutineScope
) : AlertDispatcher {

    companion object {
        private const val TAG = "SystemAlertDispatcher"
    }

    override suspend fun dispatch(event: AlertEvent) {
        val settings = settingsRepository.currentSettings()

        notifyAlert(event)

        runCatching {
            withContext(Dispatchers.Main) {
                overlayController.show(event, ::reportFalsePositiveInternal)
            }
        }.onFailure { throwable ->
            Log.e(TAG, "Overlay alert failed, continuing with notification/audio fallback", throwable)
        }

        if (settings.vibrationEnabled) {
            vibrate()
        }
        playTone(settings.alertVolume)
    }

    override suspend fun reportFalsePositive(event: AlertEvent) {
        reportFalsePositiveInternal(event)
    }

    private fun reportFalsePositiveInternal(@Suppress("UNUSED_PARAMETER") event: AlertEvent) {
        applicationScope.launch(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.alert_reported),
                Toast.LENGTH_LONG
            ).show()
            // TODO(v1.0): POST false positive reports to backend moderation endpoint.
        }
    }

    private fun notifyAlert(event: AlertEvent) {
        context.createAlertNotificationChannel()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            event.detectedAt.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = context.getString(
            R.string.alert_notification_body,
            event.plateRecord.brand,
            event.plateRecord.model,
            event.plateRecord.plate
        )
        val notification = NotificationCompat.Builder(context, ALERT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.alert_notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        notificationManager.notify(event.detectedAt.toInt(), notification)
    }

    private fun playTone(volume: Int) {
        applicationScope.launch(Dispatchers.Default) {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, volume.coerceIn(0, 100))
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
            delay(850)
            toneGenerator.release()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(VibratorManager::class.java)
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 220), -1))
    }
}
