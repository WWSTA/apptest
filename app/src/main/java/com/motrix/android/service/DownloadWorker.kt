package com.motrix.android.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.motrix.android.core.engine.Aria2ProcessManager
import com.motrix.android.core.engine.DownloadEngine
import com.motrix.android.core.engine.model.EngineConfig
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.domain.repository.TaskRepository
import com.motrix.android.domain.usecase.SyncEngineStateUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val downloadEngine: DownloadEngine,
    private val processManager: Aria2ProcessManager,
    private val taskRepository: TaskRepository,
    private val syncEngineStateUseCase: SyncEngineStateUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("DownloadWorker: Starting sync")

        return try {
            // Check if aria2c process is running, restart if needed
            if (!processManager.isAria2Running()) {
                Timber.d("DownloadWorker: Engine not running, checking for active tasks")
                val hasActiveTasks = checkForActiveTasks()
                if (hasActiveTasks) {
                    Timber.d("DownloadWorker: Active tasks found, restarting engine")
                    val config = EngineConfig(downloadDir = "")
                    downloadEngine.initialize(config)
                        .onFailure { Timber.e(it, "DownloadWorker: Failed to restart engine") }
                }
            }

            // Sync engine state with local DB
            syncEngineStateUseCase()
                .onFailure { Timber.e(it, "DownloadWorker: State sync failed") }

            // Start foreground service if there are active tasks
            val hasActive = checkForActiveTasks()
            if (hasActive) {
                DownloadService.start(appContext)
            }

            Timber.d("DownloadWorker: Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "DownloadWorker: Sync failed")
            Result.retry()
        }
    }

    private suspend fun checkForActiveTasks(): Boolean {
        return try {
            val activeTasks = taskRepository.getTasks(TaskState.ACTIVE)
            val waitingTasks = taskRepository.getTasks(TaskState.WAITING)
            val pausedTasks = taskRepository.getTasks(TaskState.PAUSED)
            val a = activeTasks.first()
            val w = waitingTasks.first()
            val p = pausedTasks.first()
            a.isNotEmpty() || w.isNotEmpty() || p.isNotEmpty()
        } catch (e: Exception) {
            Timber.e(e, "DownloadWorker: Failed to check active tasks")
            false
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "download_sync_work"

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun createOneTimeWorkRequest() = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .build()
    }
}
