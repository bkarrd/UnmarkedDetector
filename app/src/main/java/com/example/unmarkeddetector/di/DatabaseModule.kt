package com.example.unmarkeddetector.di

import android.content.Context
import androidx.room.Room
import com.example.unmarkeddetector.data.local.UnmarkedDetectorDatabase
import com.example.unmarkeddetector.data.local.dao.PlateRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): UnmarkedDetectorDatabase {
        return Room.databaseBuilder(
            context,
            UnmarkedDetectorDatabase::class.java,
            "unmarked_detector.db"
        ).build()
    }

    @Provides
    fun providePlateRecordDao(database: UnmarkedDetectorDatabase): PlateRecordDao =
        database.plateRecordDao()
}
