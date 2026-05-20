package com.example.unmarkeddetector.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plate_records")
data class PlateRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "plate")
    val plate: String,
    @ColumnInfo(name = "brand")
    val brand: String,
    @ColumnInfo(name = "model")
    val model: String,
    @ColumnInfo(name = "region")
    val region: String,
    @ColumnInfo(name = "added_at")
    val addedAt: Long,
    @ColumnInfo(name = "confirmed_count")
    val confirmedCount: Int
)
