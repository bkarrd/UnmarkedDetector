package com.example.unmarkeddetector.data.system.viofo

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViofoHttpSnapshotClient @Inject constructor() {

    companion object {
        const val DEFAULT_HOST = "http://192.168.1.254"
        const val SNAPSHOT_PATH = "/?custom=1&cmd=4002"
    }

    suspend fun fetchSnapshotJpeg(): ByteArray {
        // TODO(v1.0): implement Viofo A119 Mini 2 / A229 snapshot download over Wi-Fi.
        // TODO(v1.0): reuse the same OCR pipeline by converting JPEG snapshots into InputImage.
        error("Viofo integration is intentionally deferred to v1.0")
    }
}
