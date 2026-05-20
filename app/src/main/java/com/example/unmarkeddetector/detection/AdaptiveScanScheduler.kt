package com.example.unmarkeddetector.detection

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdaptiveScanScheduler @Inject constructor() {

    private var scanIntervalMs = 200L
    private var lastScanHadPlates = false
    private var consecutiveEmptyScans = 0
    private var lastScanAt = 0L
    private var manualIntervalMs: Long? = null

    fun shouldScan(now: Long = System.currentTimeMillis()): Boolean {
        return now - lastScanAt >= currentIntervalMs()
    }

    fun markScanStarted(now: Long = System.currentTimeMillis()) {
        lastScanAt = now
    }

    fun onScanCompleted(foundPlates: Boolean) {
        if (manualIntervalMs != null) {
            lastScanHadPlates = foundPlates
            if (foundPlates) {
                consecutiveEmptyScans = 0
            } else {
                consecutiveEmptyScans++
            }
            return
        }

        scanIntervalMs = when {
            foundPlates -> 100L
            lastScanHadPlates -> 120L
            consecutiveEmptyScans < 3 -> 180L
            consecutiveEmptyScans < 8 -> 260L
            else -> 400L
        }

        if (foundPlates) {
            consecutiveEmptyScans = 0
        } else {
            consecutiveEmptyScans++
        }
        lastScanHadPlates = foundPlates
    }

    fun currentIntervalMs(): Long = manualIntervalMs ?: scanIntervalMs

    fun cycleManualIntervalMs(): Long {
        val current = manualIntervalMs ?: currentIntervalMs()
        val next = when (current) {
            in Long.MIN_VALUE..100L -> 200L
            in 101L..200L -> 300L
            in 201L..300L -> 400L
            in 301L..400L -> 500L
            in 401L..500L -> 600L
            in 501L..600L -> 700L
            in 601L..700L -> 800L
            else -> 100L
        }
        manualIntervalMs = next
        return next
    }

    fun reset() {
        lastScanHadPlates = false
        consecutiveEmptyScans = 0
        lastScanAt = 0L
        if (manualIntervalMs == null) {
            scanIntervalMs = 200L
        }
    }
}
