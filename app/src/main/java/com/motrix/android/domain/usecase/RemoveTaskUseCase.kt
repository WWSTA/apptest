package com.motrix.android.domain.usecase

import com.motrix.android.domain.repository.TaskRepository
import timber.log.Timber
import javax.inject.Inject

class RemoveTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {

    suspend operator fun invoke(gid: String, withFile: Boolean): Result<Unit> {
        if (gid.isBlank()) {
            return Result.failure(IllegalArgumentException("GID cannot be empty"))
        }
        Timber.d("Removing task: $gid, withFile=$withFile")
        return taskRepository.removeTask(gid, withFile)
    }
}
