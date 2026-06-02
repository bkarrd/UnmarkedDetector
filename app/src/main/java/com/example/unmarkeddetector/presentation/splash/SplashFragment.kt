package com.example.unmarkeddetector.presentation.splash

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.navigation.fragment.findNavController
import com.example.unmarkeddetector.R
import com.example.unmarkeddetector.databinding.FragmentSplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashFragment : Fragment(R.layout.fragment_splash) {

    private val viewModel: SplashViewModel by viewModels()
    private var binding: FragmentSplashBinding? = null
    private val animators = mutableListOf<ObjectAnimator>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSplashBinding.bind(view)

        startAnimations()
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1600L)
            val destination = if (viewModel.shouldOpenDrivingScreen()) {
                R.id.action_splashFragment_to_mainDrivingFragment
            } else {
                R.id.action_splashFragment_to_onboardingFragment
            }
            viewLifecycleOwner.withResumed {
                if (findNavController().currentDestination?.id == R.id.splashFragment) {
                    findNavController().navigate(destination)
                }
            }
        }
    }

    override fun onDestroyView() {
        animators.forEach { it.cancel() }
        animators.clear()
        binding = null
        super.onDestroyView()
    }

    private fun startAnimations() {
        binding?.apply {
            pulseRing(primaryRing, 1f, 1.16f, 0L)
            pulseRing(secondaryRing, 0.92f, 1.24f, 280L)
            bounceDot(dotOne, 0L)
            bounceDot(dotTwo, 180L)
            bounceDot(dotThree, 360L)
        }
    }

    private fun pulseRing(view: View, startScale: Float, endScale: Float, delayMs: Long) {
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, startScale, endScale),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, startScale, endScale),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.25f, 0.9f, 0.25f)
        ).apply {
            duration = 1900L
            startDelay = delayMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        animators += animator
    }

    private fun bounceDot(view: View, delayMs: Long) {
        view.doOnLayout {
            val animator = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -14f, 0f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.25f, 1f, 0.25f)
            ).apply {
                duration = 1050L
                startDelay = delayMs
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            animators += animator
        }
    }
}
