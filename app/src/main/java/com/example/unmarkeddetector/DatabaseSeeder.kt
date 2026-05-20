package com.example.unmarkeddetector

import com.example.unmarkeddetector.domain.repository.PlateRepository
import com.example.unmarkeddetector.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val plateRepository: PlateRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    fun seedIfNeeded() {
        applicationScope.launch {
            plateRepository.seedIfEmpty()
        }
    }
}

