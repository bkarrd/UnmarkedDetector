package com.example.unmarkeddetector.domain.model

data class PlateRecord(
    val plate: String,
    val brand: String,
    val model: String,
    val region: String,
    val addedAt: Long,
    val confirmedCount: Int
)

