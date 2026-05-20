package com.example.unmarkeddetector.di

import com.example.unmarkeddetector.data.repository.PlateRepositoryImpl
import com.example.unmarkeddetector.data.repository.SettingsRepositoryImpl
import com.example.unmarkeddetector.data.system.SystemAlertDispatcher
import com.example.unmarkeddetector.domain.repository.AlertDispatcher
import com.example.unmarkeddetector.domain.repository.PlateRepository
import com.example.unmarkeddetector.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPlateRepository(
        impl: PlateRepositoryImpl
    ): PlateRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAlertDispatcher(
        impl: SystemAlertDispatcher
    ): AlertDispatcher
}
