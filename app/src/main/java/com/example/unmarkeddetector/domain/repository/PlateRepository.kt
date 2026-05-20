package com.example.unmarkeddetector.domain.repository

import com.example.unmarkeddetector.domain.model.PlateRecord
import kotlinx.coroutines.flow.Flow

interface PlateRepository {
    suspend fun seedIfEmpty()
    suspend fun syncFromRemote(): Int
    suspend fun findByPlate(plate: String): PlateRecord?
    fun getDatabaseVersion(): Flow<String>
}
