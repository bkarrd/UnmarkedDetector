package com.example.unmarkeddetector.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.unmarkeddetector.data.local.dao.PlateRecordDao
import com.example.unmarkeddetector.data.local.entity.PlateRecordEntity

@Database(
    entities = [PlateRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UnmarkedDetectorDatabase : RoomDatabase() {
    abstract fun plateRecordDao(): PlateRecordDao
}
