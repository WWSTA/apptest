package com.motrix.android.domain.repository

import com.motrix.android.core.engine.model.GlobalStat
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.core.database.entity.TaskEntity
import com.motrix.android.domain.model.DownloadOptions
import kotlinx.coroutines.flow.Flow

interface TaskRepository {

    fun getTasks(state: TaskState? = null): Flow<List<TaskEntity>>

    suspend fun getTask(gid: String): TaskEntity?

    suspend fun upsertTask(task: TaskEntity)

    suspend fun deleteTask(gid: String)

    suspend fun addHttpDownload(url: String, options: DownloadOptions): Result<String>

    suspend fun addMagnetDownload(magnetUri: String, options: DownloadOptions): Result<String>

    suspend fun addTorrentDownload(torrentPath: String, options: DownloadOptions): Result<String>

    suspend fun pauseTask(gid: String): Result<Unit>

    suspend fun resumeTask(gid: String): Result<Unit>

    suspend fun removeTask(gid: String, withFile: Boolean): Result<Unit>

    suspend fun pauseAllTasks(): Result<Unit>

    suspend fun resumeAllTasks(): Result<Unit>

    fun getGlobalStat(): Flow<GlobalStat>

    suspend fun syncEngineState()
}
