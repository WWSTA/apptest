package com.motrix.android.app.di

import com.motrix.android.core.engine.Aria2Engine
import com.motrix.android.core.engine.DownloadEngine
import com.motrix.android.core.engine.model.EngineConfig
import com.motrix.android.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideEngineConfig(settingsRepository: SettingsRepository): EngineConfig {
        return runBlocking {
            runCatching {
                settingsRepository.getEngineConfig().first()
            }.getOrDefault(EngineConfig(downloadDir = SettingsRepository.DEFAULT_DOWNLOAD_DIR))
        }
    }

    @Provides
    @Singleton
    fun provideDownloadEngine(aria2Engine: Aria2Engine): DownloadEngine {
        return aria2Engine
    }
}
