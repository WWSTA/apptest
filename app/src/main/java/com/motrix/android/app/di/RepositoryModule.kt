package com.motrix.android.app.di

import com.motrix.android.data.repository.SettingsRepositoryImpl
import com.motrix.android.data.repository.TaskRepositoryImpl
import com.motrix.android.domain.repository.SettingsRepository
import com.motrix.android.domain.repository.TaskRepository
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
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
