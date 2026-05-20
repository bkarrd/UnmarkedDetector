package com.example.unmarkeddetector.data.repository

import com.example.unmarkeddetector.BuildConfig
import com.example.unmarkeddetector.data.local.dao.PlateRecordDao
import com.example.unmarkeddetector.data.local.seed.SamplePlateSeed
import com.example.unmarkeddetector.data.remote.SupabasePlateRemoteDataSource
import com.example.unmarkeddetector.domain.model.PlateRecord
import com.example.unmarkeddetector.domain.repository.PlateRepository
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class PlateRepositoryImpl @Inject constructor(
    private val plateRecordDao: PlateRecordDao,
    private val supabaseRemoteDataSource: SupabasePlateRemoteDataSource
) : PlateRepository {

    companion object {
        private const val TAG = "PlateRepository"
    }

    override suspend fun seedIfEmpty() {
        if (plateRecordDao.count() == 0) {
            Log.i(TAG, "Lokalna baza pusta — próba pobrania rekordów z Supabase (seed)...")
            val remoteCount = runCatching { syncFromRemote() }
                .onFailure {
                    Log.w(
                        TAG,
                        "Pobranie z Supabase przy starcie nie powiodło się — używam przykładowych danych lokalnych",
                        it
                    )
                }
                .getOrDefault(0)
            if (remoteCount == 0) {
                Log.i(TAG, "Wstawiam przykładową bazę lokalną (${SamplePlateSeed.records.size} rekordów)")
                plateRecordDao.insertAll(SamplePlateSeed.records)
            } else {
                Log.i(TAG, "Seed z Supabase: zapisano $remoteCount rekordów")
            }
        }
    }

    override suspend fun syncFromRemote(): Int {
        Log.i(TAG, "Synchronizacja lokalnej bazy z Supabase (pobranie + zapis Room)...")
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val records = supabaseRemoteDataSource.fetchPlateRecords()
                .map { it.toEntity() }
            if (records.isNotEmpty()) {
                plateRecordDao.insertAll(records)
                val ms = SystemClock.elapsedRealtime() - startedAt
                Log.i(TAG, "Synchronizacja OK: zapisano ${records.size} rekordów do Room (łącznie ${ms}ms)")
            } else {
                Log.w(TAG, "Supabase zwrócił 0 rekordów — lokalna tabela nie została nadpisana")
            }
            records.size
        } catch (e: Exception) {
            val ms = SystemClock.elapsedRealtime() - startedAt
            Log.e(TAG, "Synchronizacja z Supabase przerwana po ${ms}ms: ${e.message}", e)
            throw e
        }
    }

    override suspend fun findByPlate(plate: String): PlateRecord? {
        return plateRecordDao.getByPlate(plate)?.toDomain()
    }

    override fun getDatabaseVersion(): Flow<String> = flowOf(BuildConfig.PLATE_DB_VERSION)
}
