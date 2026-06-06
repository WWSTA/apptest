package com.motrix.android.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.motrix.android.core.database.MotrixDatabase
import com.motrix.android.core.database.dao.FileDao
import com.motrix.android.core.database.dao.SettingsDao
import com.motrix.android.core.database.dao.TaskDao
import com.motrix.android.core.database.dao.TrackerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "motrix_settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMotrixDatabase(@ApplicationContext context: Context): MotrixDatabase {
        val db = Room.databaseBuilder(
            context,
            MotrixDatabase::class.java,
            "motrix.db"
        )
            .fallbackToDestructiveMigration()
            .build()
        MotrixDatabase.initialize(db)
        return db
    }

    @Provides
    fun provideTaskDao(database: MotrixDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideFileDao(database: MotrixDatabase): FileDao {
        return database.fileDao()
    }

    @Provides
    fun provideSettingsDao(database: MotrixDatabase): SettingsDao {
        return database.settingsDao()
    }

    @Provides
    fun provideTrackerDao(database: MotrixDatabase): TrackerDao {
        return database.trackerDao()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
