package com.motrix.android.domain.usecase

import com.motrix.android.core.database.entity.TaskEntity
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.domain.model.TaskUiModel
import com.motrix.android.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {

    operator fun invoke(state: TaskState? = null): Flow<List<TaskUiModel>> {
        return taskRepository.getTasks(state).map { tasks ->
            tasks.map { it.toUiModel() }
        }
    }

    private fun TaskEntity.toUiModel(): TaskUiModel {
        val progress = if (totalLength > 0) {
            (completedLength.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val isTorrent = type.equals("torrent", ignoreCase = true) ||
                type.equals("magnet", ignoreCase = true)

        val remainingTime = calculateRemainingTime(downloadSpeed, completedLength, totalLength)

        return TaskUiModel(
            gid = gid,
            name = name,
            state = state,
            progress = progress,
            totalSize = totalLength,
            downloadedSize = completedLength,
            speed = downloadSpeed,
            remainingTime = remainingTime,
            connections = connections,
            seeders = numSeeders,
            isTorrent = isTorrent
        )
    }

    private fun calculateRemainingTime(speed: Long, completed: Long, total: Long): Long {
        if (speed <= 0 || total <= 0) return -1L
        val remaining = total - completed
        if (remaining <= 0) return 0L
        return remaining / speed
    }
}
