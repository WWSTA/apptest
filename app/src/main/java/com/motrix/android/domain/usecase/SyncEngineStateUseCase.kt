package com.motrix.android.domain.usecase

import com.motrix.android.domain.repository.TaskRepository
import timber.log.Timber
import javax.inject.Inject

class SyncEngineStateUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {

    suspend operator fun invoke() {
        Timber.d("Syncing engine state...")
        taskRepository.syncEngineState()
        Timber.d("Engine state sync completed")
    }
}
