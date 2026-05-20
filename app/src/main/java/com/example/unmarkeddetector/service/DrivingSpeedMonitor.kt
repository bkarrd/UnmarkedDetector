package com.example.unmarkeddetector.service

import android.annotation.SuppressLint
import android.content.Context
import com.example.unmarkeddetector.util.hasFineLocationPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class DrivingSpeedMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient
) {

    @SuppressLint("MissingPermission")
    fun observeSpeedKmh(): Flow<Float> = callbackFlow {
        if (!context.hasFineLocationPermission()) {
            trySend(0f)
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(1_500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val speedKmh = (result.lastLocation?.speed ?: 0f) * 3.6f
                trySend(speedKmh)
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(
            request,
            callback,
            context.mainLooper
        )

        awaitClose {
            fusedLocationProviderClient.removeLocationUpdates(callback)
        }
    }
}
