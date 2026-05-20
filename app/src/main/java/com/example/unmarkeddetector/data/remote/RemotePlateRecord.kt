package com.example.unmarkeddetector.data.remote

import com.example.unmarkeddetector.data.local.entity.PlateRecordEntity

data class RemotePlateRecord(
    val plate: String,
    val makeModel: String,
    val province: String
) {
    fun toEntity(addedAt: Long = System.currentTimeMillis()): PlateRecordEntity {
        val parts = makeModel.trim().split(Regex("\\s+"), limit = 2)
        return PlateRecordEntity(
            plate = plate.trim().uppercase(),
            brand = parts.getOrNull(0).orEmpty(),
            model = parts.getOrNull(1).orEmpty(),
            region = province.trim(),
            addedAt = addedAt,
            confirmedCount = 1
        )
    }
}
