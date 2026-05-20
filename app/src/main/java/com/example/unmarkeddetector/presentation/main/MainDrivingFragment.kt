package com.example.unmarkeddetector.presentation.main

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.unmarkeddetector.R
import com.example.unmarkeddetector.databinding.FragmentMainDrivingBinding
import com.example.unmarkeddetector.detection.DetectionCoordinator
import com.example.unmarkeddetector.service.DetectionForegroundService
import com.example.unmarkeddetector.util.hasRequiredDetectionPermissions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainDrivingFragment : Fragment(R.layout.fragment_main_driving) {

    @Inject
    lateinit var detectionCoordinator: DetectionCoordinator

    private val viewModel: MainDrivingViewModel by viewModels()
    private var binding: FragmentMainDrivingBinding? = null
    private var scanAnimator: ObjectAnimator? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentMainDrivingBinding.bind(view)

        binding?.apply {
            previewView.implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
            detectionCoordinator.attachPreview(previewView)

            // Podpij GraphicOverlay do detektora
            detectionCoordinator.attachGraphicOverlay(graphicOverlay)

            homeButton.setOnClickListener {
                findNavController().navigate(R.id.action_mainDrivingFragment_to_onboardingFragment)
            }
            startButton.setOnClickListener { startDetection() }
            stopButton.setOnClickListener { stopDetection() }
            settingsButton.setOnClickListener {
                findNavController().navigate(R.id.action_mainDrivingFragment_to_settingsFragment)
            }
            uniquePlatesCard.setOnClickListener { showUniquePlatesDialog() }
            scanIntervalText.setOnClickListener {
                val intervalMs = detectionCoordinator.cycleScanInterval()
                Toast.makeText(
                    requireContext(),
                    "Interwal skanowania: %.1f s".format(intervalMs / 1000f),
                    Toast.LENGTH_SHORT
                ).show()
            }
            startScanLineAnimation()
        }

        bindPreviewToScreen()

        observeState()
    }

    override fun onDestroyView() {
        scanAnimator?.cancel()
        scanAnimator = null
        detectionCoordinator.detachPreview()
        binding = null
        super.onDestroyView()
    }

    private fun startDetection() {
        if (!requireContext().hasRequiredDetectionPermissions()) {
            Toast.makeText(requireContext(), R.string.missing_permissions_message, Toast.LENGTH_LONG)
                .show()
            findNavController().navigate(R.id.onboardingFragment)
            return
        }
        DetectionForegroundService.start(requireContext())
    }

    private fun stopDetection() {
        DetectionForegroundService.stop(requireContext())
    }

    private fun showUniquePlatesDialog() {
        val plates = viewModel.sessionState.value.uniquePlates
        val message = if (plates.isEmpty()) {
            getString(R.string.unique_plates_dialog_empty)
        } else {
            plates.joinToString(separator = "\n")
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.unique_plates_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.common_ok, null)
            .show()
    }

    private fun bindPreviewToScreen() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                detectionCoordinator.ensureCameraBound(
                    owner = requireActivity(),
                    includePreview = true
                )
            }.onFailure {
                Toast.makeText(
                    requireContext(),
                    "Nie udalo sie uruchomic podgladu kamery.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sessionState.collect { state ->
                    binding?.apply {
                        statusText.text = state.statusLabel
                        speedValueText.text = state.currentSpeedKmh.toInt().toString()
                        scannedCountText.text = state.uniquePlateCount.toString()
                        alertCountText.text = state.alertCount.toString()
                        lastPlateText.text = state.lastDetectedPlates
                            .take(3)
                            .joinToString(" ")
                            .ifBlank { "--" }
                        scanIntervalText.text = getString(
                            R.string.main_scan_interval,
                            state.currentScanIntervalMs
                        )
                        val dotColor = when {
                            state.canAnalyze -> requireContext().getColor(R.color.success_green)
                            state.serviceRunning -> requireContext().getColor(R.color.accent_orange)
                            else -> requireContext().getColor(R.color.accent_red)
                        }
                        statusDot.background.setColorFilter(dotColor, PorterDuff.Mode.SRC_IN)
                        val isRunning = state.serviceRunning && state.userEnabled
                        startButton.isEnabled = !isRunning
                        startButton.alpha = if (startButton.isEnabled) 1f else 0.45f
                        stopButton.isEnabled = isRunning
                        stopButton.alpha = if (stopButton.isEnabled) 1f else 0.45f
                        if (!state.serviceRunning) {
                            bindPreviewToScreen()
                        }
                    }
                }
            }
        }
    }

    private fun startScanLineAnimation() {
        binding?.scanZone?.doOnLayout { zone ->
            val line = binding?.scanLine ?: return@doOnLayout
            scanAnimator?.cancel()
            val travelDistance = (zone.height - line.height).coerceAtLeast(0).toFloat()
            scanAnimator = ObjectAnimator.ofFloat(line, View.TRANSLATION_Y, 0f, travelDistance).apply {
                duration = 1700L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                start()
            }
        }
    }
}
