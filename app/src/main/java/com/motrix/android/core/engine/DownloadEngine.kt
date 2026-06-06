package com.motrix.android.core.engine

import com.motrix.android.core.engine.model.EngineConfig
import com.motrix.android.core.engine.model.GlobalStat
import com.motrix.android.core.engine.model.HttpTaskRequest
import com.motrix.android.core.engine.model.MagnetTaskRequest
import com.motrix.android.core.engine.model.TaskEvent
import com.motrix.android.core.engine.model.TaskInfo
import com.motrix.android.core.engine.model.TaskStatus
import com.motrix.android.core.engine.model.TorrentTaskRequest
import kotlinx.coroutines.flow.Flow

interface DownloadEngine {
    suspend fun initialize(config: EngineConfig): Result<Unit>
    suspend fun shutdown(): Result<Unit>
    suspend fun addHttpTask(request: HttpTaskRequest): Result<String>
    suspend fun addMagnetTask(request: MagnetTaskRequest): Result<String>
    suspend fun addTorrentTask(request: TorrentTaskRequest): Result<String>
    suspend fun pauseTask(gid: String): Result<Unit>
    suspend fun resumeTask(gid: String): Result<Unit>
    suspend fun removeTask(gid: String, removeFile: Boolean): Result<Unit>
    suspend fun pauseAll(): Result<Unit>
    suspend fun resumeAll(): Result<Unit>
    suspend fun getTaskStatus(gid: String): Result<TaskStatus>
    suspend fun getGlobalStat(): Result<GlobalStat>
    suspend fun getActiveTasks(): Result<List<TaskInfo>>
    suspend fun getWaitingTasks(offset: Int, limit: Int): Result<List<TaskInfo>>
    suspend fun getStoppedTasks(offset: Int, limit: Int): Result<List<TaskInfo>>
    suspend fun changeGlobalOption(options: Map<String, String>): Result<Unit>
    suspend fun changeTaskOption(gid: String, options: Map<String, String>): Result<Unit>
    fun observeTaskEvents(): Flow<TaskEvent>
}
