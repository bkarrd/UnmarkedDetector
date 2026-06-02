package com.example.unmarkeddetector.presentation.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
    ) { result ->
        permissionPreferences().edit().putBoolean(KEY_RUNTIME_PERMISSIONS_REQUESTED, true).apply()
        refreshPermissionState()

        val deniedPermissions = result.filterValues { granted -> !granted }.keys
        when {
            deniedPermissions.isEmpty() -> {
                binding?.root?.snackbar(getString(R.string.onboarding_permissions_granted))
            }
            deniedPermissions.any { permission ->
                !shouldShowRequestPermissionRationale(permission)
            } -> {
                showPermissionDeniedDialog()
            }
            else -> {
                binding?.root?.snackbar(getString(R.string.onboarding_permissions_denied_hint))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentOnboardingBinding.bind(view)

        binding?.apply {
            phoneModeCard.setOnClickListener { }
            viofoModeCard.setOnClickListener { showViofoInfoDialog() }
            rearViofoModeCard.setOnClickListener { showViofoInfoDialog() }
            grantPermissionsButton.setOnClickListener { requestMissingPermissions() }
            grantOverlayButton.setOnClickListener { showOverlayPermissionDialog() }
            batterySettingsButton.setOnClickListener { openBatterySettings() }
            continueButton.setOnClickListener {
                if (hasRequiredRuntimePermissions()) {
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
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !context.hasNotificationPermission()
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isEmpty()) {
            refreshPermissionState()
            binding?.root?.snackbar(getString(R.string.onboarding_permissions_granted))
            return
        }

        val requestedBefore =
            permissionPreferences().getBoolean(KEY_RUNTIME_PERMISSIONS_REQUESTED, false)
        val shouldShowRationale = permissions.any(::shouldShowRequestPermissionRationale)
        if (requestedBefore && !shouldShowRationale) {
            showPermissionDeniedDialog()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.onboarding_permissions_dialog_title)
            .setMessage(R.string.onboarding_permissions_dialog_message)
            .setPositiveButton(R.string.onboarding_permissions_dialog_continue) { _, _ ->
                permissionsLauncher.launch(permissions.toTypedArray())
            }
            .setNegativeButton(R.string.common_not_now, null)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.onboarding_permissions_denied_title)
            .setMessage(R.string.onboarding_permissions_denied_message)
            .setPositiveButton(R.string.onboarding_open_app_settings) { _, _ ->
                openApplicationSettings()
            }
            .setNegativeButton(R.string.common_not_now, null)
            .show()
    }

    private fun showOverlayPermissionDialog() {
        if (requireContext().hasOverlayPermission()) {
            refreshPermissionState()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.onboarding_overlay_dialog_title)
            .setMessage(R.string.onboarding_overlay_dialog_message)
            .setPositiveButton(R.string.onboarding_overlay_dialog_continue) { _, _ ->
                openOverlaySettings()
            }
            .setNegativeButton(R.string.common_not_now, null)
            .show()
    }

    private fun openOverlaySettings() {
        launchSettings(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
        )
    }

    private fun openBatterySettings() {
        launchSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openApplicationSettings() {
        launchSettings(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${requireContext().packageName}")
            )
        )
    }

    private fun launchSettings(intent: Intent) {
        runCatching {
            startActivity(intent)
        }.onFailure {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun refreshPermissionState() {
        binding?.apply {
            val context = requireContext()
            cameraStatus.renderPermissionStatus(context.hasCameraPermission())
            locationStatus.renderPermissionStatus(context.hasFineLocationPermission())
            notificationsStatus.renderPermissionStatus(context.hasNotificationPermission())
            overlayStatus.renderPermissionStatus(
                granted = context.hasOverlayPermission(),
                missingText = R.string.permission_overlay_optional
            )

            val runtimePermissionsGranted = hasRequiredRuntimePermissions()
            grantPermissionsButton.isEnabled = !runtimePermissionsGranted
            grantPermissionsButton.setText(
                if (runtimePermissionsGranted) {
                    R.string.onboarding_permissions_ready
                } else {
                    R.string.onboarding_grant_permissions
                }
            )
            grantOverlayButton.isEnabled = !context.hasOverlayPermission()
            grantOverlayButton.setText(
                if (context.hasOverlayPermission()) {
                    R.string.onboarding_overlay_ready
                } else {
                    R.string.onboarding_overlay_permission
                }
            )
            continueButton.isEnabled = runtimePermissionsGranted
        }
    }

    private fun TextView.renderPermissionStatus(
        granted: Boolean,
        missingText: Int = R.string.permission_required
    ) {
        setText(if (granted) R.string.permission_granted else missingText)
        setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (granted) R.color.success_green else R.color.accent_orange
            )
        )
    }

    private fun hasRequiredRuntimePermissions(): Boolean {
        val context = requireContext()
        return context.hasCameraPermission() &&
            context.hasFineLocationPermission() &&
            context.hasNotificationPermission()
    }

    private fun permissionPreferences() =
        requireContext().getSharedPreferences(PERMISSION_PREFERENCES, Context.MODE_PRIVATE)

    companion object {
        private const val PERMISSION_PREFERENCES = "permission_onboarding"
        private const val KEY_RUNTIME_PERMISSIONS_REQUESTED = "runtime_permissions_requested"
    }

    private fun showViofoInfoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.onboarding_viofo_dialog_title)
            .setMessage(R.string.onboarding_viofo_dialog_message)
            .setPositiveButton(R.string.common_ok, null)
            .show()
    }
}
