package com.example.unmarkeddetector.presentation.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.unmarkeddetector.R
import com.example.unmarkeddetector.databinding.FragmentSettingsBinding
import com.example.unmarkeddetector.presentation.settings.SettingsViewModel.SyncResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()
    private var binding: FragmentSettingsBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSettingsBinding.bind(view)

        binding?.apply {
            toolbarBack.setOnClickListener { findNavController().navigateUp() }
            databaseVersionText.text =
                getString(R.string.settings_database_version, viewModel.databaseVersion)
            alertVolumeSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) viewModel.updateAlertVolume(value.toInt())
            }
            vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setVibrationEnabled(isChecked)
            }
            checkUpdatesButton.setOnClickListener {
                viewModel.syncDatabase()
            }
            billingButton.setOnClickListener {
                Toast.makeText(requireContext(), R.string.settings_billing_todo, Toast.LENGTH_LONG).show()
            }
        }

        observeState()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect { settings ->
                        binding?.apply {
                            if (alertVolumeSlider.value.toInt() != settings.alertVolume) {
                                alertVolumeSlider.value = settings.alertVolume.toFloat()
                            }
                            alertVolumeValueText.text =
                                getString(R.string.settings_alert_volume_value, settings.alertVolume)
                            if (vibrationSwitch.isChecked != settings.vibrationEnabled) {
                                vibrationSwitch.isChecked = settings.vibrationEnabled
                            }
                        }
                    }
                }
                launch {
                    viewModel.syncResult.collect { result ->
                        val message = when (result) {
                            is SyncResult.Success -> getString(
                                R.string.settings_updates_synced,
                                result.records
                            )
                            is SyncResult.Error -> getString(
                                R.string.settings_updates_failed,
                                result.message ?: getString(R.string.settings_updates_failed_unknown)
                            )
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
