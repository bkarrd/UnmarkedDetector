package com.example.unmarkeddetector.detection

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdaptiveScanScheduler @Inject constructor() {

    private var scanIntervalMs = 200L
    private var lastScanHadPlates = false
    private var consecutiveEmptyScans = 0
    private var lastScanAt = 0L

    fun shouldScan(now: Long = System.currentTimeMillis()): Boolean {
        return now - lastScanAt >= scanIntervalMs
    }

    fun markScanStarted(now: Long = System.currentTimeMillis()) {
        lastScanAt = now
    }

    fun onScanCompleted(foundPlates: Boolean) {
        // Automatic adaptive interval based on detection results
        scanIntervalMs = when {
            foundPlates -> 100L                    // Found plates: scan very frequently
            lastScanHadPlates -> 120L              // Just lost plates: still frequent
            consecutiveEmptyScans < 3 -> 180L      // Still trying
            consecutiveEmptyScans < 8 -> 260L      // Getting less likely
            else -> 400L                           // Very unlikely to find plates soon
        }

        if (foundPlates) {
            consecutiveEmptyScans = 0
        } else {
            consecutiveEmptyScans++
        }
        lastScanHadPlates = foundPlates
    }

    fun reset() {
        lastScanHadPlates = false
        consecutiveEmptyScans = 0
        lastScanAt = 0L
        scanIntervalMs = 200L
    }
}
