package com.example.unmarkeddetector.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.unmarkeddetector.data.local.entity.PlateRecordEntity

@Dao
interface PlateRecordDao {

    @Query("SELECT * FROM plate_records WHERE plate = :plate LIMIT 1")
    suspend fun getByPlate(plate: String): PlateRecordEntity?

    @Query("SELECT COUNT(*) FROM plate_records")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PlateRecordEntity>)
}
