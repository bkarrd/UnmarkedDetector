package com.example.unmarkeddetector.data.remote

import android.os.SystemClock
import android.util.Log
import com.example.unmarkeddetector.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SupabasePlateRemoteDataSource @Inject constructor() {

    companion object {
        private const val TAG = "SupabasePlateRemote"
        private const val SELECT_COLUMNS = "plate,brand_model,province"
        private const val PAGE_SIZE = 1_000
    }

    suspend fun fetchPlateRecords(): List<RemotePlateRecord> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        require(baseUrl.isNotBlank() && anonKey.isNotBlank()) {
            "Supabase URL or anon key is not configured."
        }

        val host = runCatching { URL("$baseUrl/").host }.getOrElse { "(niepoprawny URL)" }
        val downloadStartedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "Pobieranie bazy tablic z Supabase: host=$host, rozmiar strony=$PAGE_SIZE")

        return@withContext try {
            val allRecords = mutableListOf<RemotePlateRecord>()
            var offset = 0
            var pageIndex = 0
            do {
                pageIndex++
                val pageStartedAt = SystemClock.elapsedRealtime()
                val page = fetchPage(baseUrl, anonKey, offset)
                val pageMs = SystemClock.elapsedRealtime() - pageStartedAt
                Log.i(
                    TAG,
                    "Strona $pageIndex: offset=$offset, rekordów=${page.size}, czas=${pageMs}ms, łącznie w pamięci=${allRecords.size + page.size}"
                )
                allRecords += page
                offset += PAGE_SIZE
            } while (page.size == PAGE_SIZE)

            val totalMs = SystemClock.elapsedRealtime() - downloadStartedAt
            Log.i(
                TAG,
                "Pobieranie z Supabase zakończone: stron=$pageIndex, rekordów=${allRecords.size}, czas całkowity=${totalMs}ms"
            )
            allRecords
        } catch (e: Exception) {
            val elapsedMs = SystemClock.elapsedRealtime() - downloadStartedAt
            Log.e(TAG, "Błąd pobierania bazy z Supabase po ${elapsedMs}ms: ${e.message}", e)
            throw e
        }
    }

    private fun fetchPage(
        baseUrl: String,
        anonKey: String,
        offset: Int
    ): List<RemotePlateRecord> {
        val url = URL(
            "$baseUrl/rest/v1/plate_records?select=$SELECT_COLUMNS&order=plate.asc&limit=$PAGE_SIZE&offset=$offset"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $anonKey")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val snippet = error.take(500)
                Log.e(
                    TAG,
                    "HTTP ${connection.responseCode} przy pobieraniu strony (offset=$offset). Odpowiedź: $snippet"
                )
                throw IllegalStateException(
                    "Supabase request failed with HTTP ${connection.responseCode}: $error"
                )
            }
            parseRecords(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRecords(body: String): List<RemotePlateRecord> {
        val records = JSONArray(body)
        return buildList {
            for (index in 0 until records.length()) {
                val item = records.getJSONObject(index)
                val plate = item.optCleanString("plate")
                val makeModel = item.optCleanString("brand_model")
                val province = item.optCleanString("province")
                if (plate == null || makeModel == null || province == null) {
                    Log.w(TAG, "Skipping incomplete plate record at index=$index")
                    continue
                }
                add(
                    RemotePlateRecord(
                        plate = plate,
                        makeModel = makeModel,
                        province = province
                    )
                )
            }
        }
    }

    private fun JSONObject.optCleanString(name: String): String? {
        if (isNull(name)) return null
        return optString(name).trim().ifBlank { null }
    }
}
