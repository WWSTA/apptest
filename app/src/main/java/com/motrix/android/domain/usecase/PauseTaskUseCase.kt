package com.motrix.android.domain.usecase

import com.motrix.android.domain.repository.TaskRepository
import timber.log.Timber
import javax.inject.Inject

class PauseTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {

    suspend operator fun invoke(gid: String): Result<Unit> {
        if (gid.isBlank()) {
            return Result.failure(IllegalArgumentException("GID cannot be empty"))
        }
        Timber.d("Pausing task: $gid")
        return taskRepository.pauseTask(gid)
    }
}
