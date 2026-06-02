package com.example.unmarkeddetector.presentation.alert

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.view.isVisible
import com.example.unmarkeddetector.R
import com.example.unmarkeddetector.databinding.ViewAlertOverlayBinding
import com.example.unmarkeddetector.di.ApplicationScope
import com.example.unmarkeddetector.domain.model.AlertEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Singleton
class AlertOverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null
    private var dismissJob: Job? = null

    fun show(event: AlertEvent) {
        if (!Settings.canDrawOverlays(context)) return

        dismiss()

        val themedContext = ContextThemeWrapper(context, R.style.Theme_UnmarkedDetector)
        val inflater = LayoutInflater.from(themedContext).cloneInContext(themedContext)
        val binding = ViewAlertOverlayBinding.inflate(inflater)
        val confidencePercent = (event.confidence * 100).toInt().coerceIn(0, 100)
        binding.titleText.text = context.getString(R.string.overlay_title)
        binding.plateText.text = formatPlateForDisplay(event.plateRecord.plate)
        binding.vehicleText.text = context.getString(
            R.string.overlay_vehicle,
            event.plateRecord.brand,
            event.plateRecord.model
        )
        binding.regionText.text = context.getString(R.string.overlay_region, event.plateRecord.region)
        binding.confidenceText.isVisible = true
        binding.confidenceText.text = context.getString(R.string.overlay_confidence, confidencePercent)
        binding.confidenceBar.progress = confidencePercent
        binding.closeButton.setOnClickListener { dismiss() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 48
        }

        overlayView = binding.root
        windowManager.addView(binding.root, params)
        dismissJob = applicationScope.launch {
            delay(6_000)
            dismiss()
        }
    }

    fun dismiss() {
        dismissJob?.cancel()
        dismissJob = null
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
    }

    private fun formatPlateForDisplay(plate: String): String {
        val sanitized = plate.filter { !it.isWhitespace() }
        if (sanitized.length < 6) return plate
        val prefixLength = if (sanitized.length >= 7 && sanitized.take(3).all { it.isLetter() }) {
            3
        } else {
            2
        }
        return sanitized.take(prefixLength) + " " + sanitized.drop(prefixLength)
    }
}
