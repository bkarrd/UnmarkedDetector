package com.example.unmarkeddetector.data.repository

import com.example.unmarkeddetector.data.local.entity.PlateRecordEntity
import com.example.unmarkeddetector.domain.model.PlateRecord

fun PlateRecordEntity.toDomain(): PlateRecord = PlateRecord(
    plate = plate,
    brand = brand,
    model = model,
    region = region,
    addedAt = addedAt,
    confirmedCount = confirmedCount
)
