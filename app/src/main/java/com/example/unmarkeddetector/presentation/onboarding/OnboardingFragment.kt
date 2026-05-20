package com.example.unmarkeddetector.presentation.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.unmarkeddetector.R
import com.example.unmarkeddetector.databinding.FragmentOnboardingBinding
import com.example.unmarkeddetector.presentation.common.snackbar
import com.example.unmarkeddetector.util.hasCameraPermission
import com.example.unmarkeddetector.util.hasFineLocationPermission
import com.example.unmarkeddetector.util.hasNotificationPermission
import com.example.unmarkeddetector.util.hasOverlayPermission
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {

    private val viewModel: OnboardingViewModel by viewModels()
    private var binding: FragmentOnboardingBinding? = null

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissionState()
        if (!requireContext().hasOverlayPermission()) {
            binding?.root?.snackbar(getString(R.string.onboarding_overlay_settings_hint))
            openOverlaySettings()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentOnboardingBinding.bind(view)

        binding?.apply {
            phoneModeCard.setOnClickListener { }
            viofoModeCard.setOnClickListener { showViofoInfoDialog() }
            rearViofoModeCard.setOnClickListener { showViofoInfoDialog() }
            viofoAffiliateCard.setOnClickListener { openViofoLink() }
            viofoLinkButton.setOnClickListener { openViofoLink() }
            grantPermissionsButton.setOnClickListener { requestMissingPermissions() }
            grantOverlayButton.setOnClickListener { openOverlaySettings() }
            batterySettingsButton.setOnClickListener { openBatterySettings() }
            continueButton.setOnClickListener {
                if (hasAllRequirements()) {
                    viewModel.completeOnboarding()
                    findNavController().navigate(R.id.action_onboardingFragment_to_mainDrivingFragment)
                } else {
                    root.snackbar(getString(R.string.missing_permissions_message))
                }
            }
        }

        refreshPermissionState()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun requestMissingPermissions() {
        val context = requireContext()
        val permissions = buildList {
            if (!context.hasCameraPermission()) {
                add(Manifest.permission.CAMERA)
            }
            if (!context.hasFineLocationPermission()) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !context.hasNotificationPermission()
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        when {
            permissions.isNotEmpty() -> permissionsLauncher.launch(permissions.toTypedArray())
            !context.hasOverlayPermission() -> {
                binding?.root?.snackbar(getString(R.string.onboarding_overlay_settings_hint))
                openOverlaySettings()
            }
            else -> refreshPermissionState()
        }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
        )
    }

    private fun openBatterySettings() {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun refreshPermissionState() {
        binding?.apply {
            val context = requireContext()
            cameraStatus.text = getString(
                if (context.hasCameraPermission()) {
                    R.string.permission_camera_granted
                } else {
                    R.string.permission_camera_missing
                }
            )
            locationStatus.text = getString(
                if (context.hasFineLocationPermission()) {
                    R.string.permission_location_granted
                } else {
                    R.string.permission_location_missing
                }
            )
            overlayStatus.text = getString(
                if (context.hasOverlayPermission()) {
                    R.string.permission_overlay_granted
                } else {
                    R.string.permission_overlay_missing
                }
            )
            notificationsStatus.text = getString(
                if (context.hasNotificationPermission()) {
                    R.string.permission_notifications_granted
                } else {
                    R.string.permission_notifications_missing
                }
            )
            continueButton.isEnabled = hasAllRequirements()
        }
    }

    private fun hasAllRequirements(): Boolean {
        val context = requireContext()
        return context.hasCameraPermission() &&
            context.hasFineLocationPermission() &&
            context.hasNotificationPermission() &&
            context.hasOverlayPermission()
    }

    private fun showViofoInfoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.onboarding_viofo_dialog_title)
            .setMessage(R.string.onboarding_viofo_dialog_message)
            .setPositiveButton(R.string.onboarding_viofo_dialog_link) { _, _ ->
                openViofoLink()
            }
            .setNegativeButton(R.string.common_ok, null)
            .show()
    }

    private fun openViofoLink() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.onboarding_viofo_link))))
    }
}
