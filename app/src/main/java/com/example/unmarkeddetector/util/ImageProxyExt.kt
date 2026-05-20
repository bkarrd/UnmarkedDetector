package com.example.unmarkeddetector.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

fun ImageProxy.toBitmap(): Bitmap {
    val plane = planes.first()
    val buffer = plane.buffer
    buffer.rewind()

    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width

    val bitmap = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888
    )
    bitmap.copyPixelsFromBuffer(buffer)

    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
    if (bitmap != croppedBitmap) {
        bitmap.recycle()
    }

    if (imageInfo.rotationDegrees == 0) {
        return croppedBitmap
    }

    val matrix = Matrix().apply {
        postRotate(imageInfo.rotationDegrees.toFloat())
    }
    val rotated = Bitmap.createBitmap(
        croppedBitmap,
        0,
        0,
        croppedBitmap.width,
        croppedBitmap.height,
        matrix,
        true
    )
    if (rotated != croppedBitmap) {
        croppedBitmap.recycle()
    }
    return rotated
}
