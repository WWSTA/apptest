package com.motrix.android.data.repository

import com.motrix.android.core.database.dao.FileDao
import com.motrix.android.core.database.dao.TaskDao
import com.motrix.android.core.database.entity.FileEntity
import com.motrix.android.core.database.entity.TaskEntity
import com.motrix.android.core.engine.DownloadEngine
import com.motrix.android.core.engine.model.GlobalStat
import com.motrix.android.core.engine.model.HttpTaskRequest
import com.motrix.android.core.engine.model.MagnetTaskRequest
import com.motrix.android.core.engine.model.TaskEvent
import com.motrix.android.core.engine.model.TaskInfo
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.core.engine.model.TorrentTaskRequest
import com.motrix.android.core.storage.FileStorageManager
import com.motrix.android.domain.model.DownloadOptions
import com.motrix.android.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val engine: DownloadEngine,
    private val taskDao: TaskDao,
    private val fileDao: FileDao,
    private val fileStorageManager: FileStorageManager
) : TaskRepository {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _globalStat = MutableStateFlow(GlobalStat(0L, 0L, 0, 0, 0))

    init {
        observeEngineEvents()
        observeEngineGlobalStat()
    }

    override fun getTasks(state: TaskState?): Flow<List<TaskEntity>> {
        return if (state != null) {
            taskDao.getTasksByState(listOf(state.name))
        } else {
            taskDao.getAllTasks()
        }
    }

    override suspend fun getTask(gid: String): TaskEntity? {
        return runCatching {
            taskDao.getTask(gid).first()
        }.getOrNull()
    }

    override suspend fun upsertTask(task: TaskEntity) {
        taskDao.upsertTask(task)
    }

    override suspend fun deleteTask(gid: String) {
        taskDao.deleteTask(gid)
        fileDao.deleteFilesForTask(gid)
    }

    override suspend fun addHttpDownload(url: String, options: DownloadOptions): Result<String> {
        return runCatching {
            val dir = options.dir ?: fileStorageManager.getDefaultDownloadDir()
            val request = HttpTaskRequest(
                url = url,
                dir = dir,
                filename = options.filename,
                split = options.split ?: 16,
                maxConnectionPerServer = options.maxConnectionPerServer ?: 16,
                header = options.header,
                userAgent = options.userAgent,
                referer = options.referer
            )
            val result = engine.addHttpTask(request).getOrThrow()
            Timber.d("HTTP download added, gid=$result")
            result
        }.onFailure { e ->
            Timber.e(e, "Failed to add HTTP download: $url")
        }
    }

    override suspend fun addMagnetDownload(magnetUri: String, options: DownloadOptions): Result<String> {
        return runCatching {
            val dir = options.dir ?: fileStorageManager.getDefaultDownloadDir()
            val request = MagnetTaskRequest(
                magnetUri = magnetUri,
                dir = dir,
                split = options.split ?: 16,
                selectFile = options.selectFile
            )
            val result = engine.addMagnetTask(request).getOrThrow()
            Timber.d("Magnet download added, gid=$result")
            result
        }.onFailure { e ->
            Timber.e(e, "Failed to add magnet download: $magnetUri")
        }
    }

    override suspend fun addTorrentDownload(torrentPath: String, options: DownloadOptions): Result<String> {
        return runCatching {
            val dir = options.dir ?: fileStorageManager.getDefaultDownloadDir()
            val request = TorrentTaskRequest(
                torrentPath = torrentPath,
                dir = dir,
                split = options.split ?: 16,
                selectFile = options.selectFile
            )
            val result = engine.addTorrentTask(request).getOrThrow()
            Timber.d("Torrent download added, gid=$result")
            result
        }.onFailure { e ->
            Timber.e(e, "Failed to add torrent download: $torrentPath")
        }
    }

    override suspend fun pauseTask(gid: String): Result<Unit> {
        return runCatching {
            engine.pauseTask(gid).getOrThrow()
            taskDao.updateTaskState(gid, TaskState.PAUSED.name, System.currentTimeMillis())
            Timber.d("Task paused: $gid")
        }.onFailure { e ->
            Timber.e(e, "Failed to pause task: $gid")
        }
    }

    override suspend fun resumeTask(gid: String): Result<Unit> {
        return runCatching {
            engine.resumeTask(gid).getOrThrow()
            taskDao.updateTaskState(gid, TaskState.ACTIVE.name, System.currentTimeMillis())
            Timber.d("Task resumed: $gid")
        }.onFailure { e ->
            Timber.e(e, "Failed to resume task: $gid")
        }
    }

    override suspend fun removeTask(gid: String, withFile: Boolean): Result<Unit> {
        return runCatching {
            engine.removeTask(gid, withFile).getOrThrow()
            if (withFile) {
                val task = getTask(gid)
                if (task != null) {
                    fileStorageManager.deleteFile("${task.dir}/${task.name}")
                }
            }
            taskDao.deleteTask(gid)
            fileDao.deleteFilesForTask(gid)
            Timber.d("Task removed: $gid, withFile=$withFile")
        }.onFailure { e ->
            Timber.e(e, "Failed to remove task: $gid")
        }
    }

    override suspend fun pauseAllTasks(): Result<Unit> {
        return runCatching {
            engine.pauseAll().getOrThrow()
            val activeTasks = taskDao.getTasksByState(listOf(TaskState.ACTIVE.name)).first()
            val now = System.currentTimeMillis()
            for (task in activeTasks) {
                taskDao.updateTaskState(task.gid, TaskState.PAUSED.name, now)
            }
            Timber.d("All tasks paused")
        }.onFailure { e ->
            Timber.e(e, "Failed to pause all tasks")
        }
    }

    override suspend fun resumeAllTasks(): Result<Unit> {
        return runCatching {
            engine.resumeAll().getOrThrow()
            val pausedTasks = taskDao.getTasksByState(listOf(TaskState.PAUSED.name)).first()
            val now = System.currentTimeMillis()
            for (task in pausedTasks) {
                taskDao.updateTaskState(task.gid, TaskState.ACTIVE.name, now)
            }
            Timber.d("All tasks resumed")
        }.onFailure { e ->
            Timber.e(e, "Failed to resume all tasks")
        }
    }

    override fun getGlobalStat(): Flow<GlobalStat> {
        return _globalStat.asStateFlow()
    }

    override suspend fun syncEngineState() {
        runCatching {
            Timber.d("Syncing engine state with database...")

            val activeTasks = engine.getActiveTasks().getOrDefault(emptyList())
            val waitingTasks = engine.getWaitingTasks(0, 100).getOrDefault(emptyList())
            val stoppedTasks = engine.getStoppedTasks(0, 100).getOrDefault(emptyList())

            val allEngineTasks = activeTasks + waitingTasks + stoppedTasks

            val engineGids = allEngineTasks.map { it.gid }.toSet()
            val dbTasks = taskDao.getTasksByState(
                listOf(
                    TaskState.ACTIVE.name,
                    TaskState.WAITING.name,
                    TaskState.PAUSED.name,
                    TaskState.COMPLETED.name,
                    TaskState.ERROR.name,
                    TaskState.REMOVED.name
                )
            ).first()

            for (engineTask in allEngineTasks) {
                val existing = dbTasks.find { it.gid == engineTask.gid }
                val entity = mapToEntity(engineTask, existing)
                taskDao.upsertTask(entity)

                syncFiles(engineTask)
            }

            for (dbTask in dbTasks) {
                if (dbTask.gid !in engineGids) {
                    val isTerminalState = dbTask.state == TaskState.COMPLETED.name ||
                            dbTask.state == TaskState.ERROR.name ||
                            dbTask.state == TaskState.REMOVED.name
                    if (!isTerminalState) {
                        taskDao.deleteTask(dbTask.gid)
                        fileDao.deleteFilesForTask(dbTask.gid)
                        Timber.d("Removed stale task from DB: ${dbTask.gid}")
                    }
                }
            }

            syncGlobalStat()

            Timber.d("Engine state synced. Engine tasks: ${allEngineTasks.size}, DB tasks: ${dbTasks.size}")
        }.onFailure { e ->
            Timber.e(e, "Failed to sync engine state")
        }
    }

    private suspend fun syncGlobalStat() {
        engine.getGlobalStat()
            .onSuccess { stat ->
                _globalStat.value = stat
            }
            .onFailure { e ->
                Timber.e(e, "Failed to get global stat from engine")
            }
    }

    private suspend fun syncFiles(taskInfo: TaskInfo) {
        val fileEntities = taskInfo.files.map { fileInfo ->
            FileEntity(
                taskGid = taskInfo.gid,
                fileIndex = fileInfo.index,
                path = fileInfo.path,
                length = fileInfo.length,
                completedLength = fileInfo.completedLength,
                selected = if (fileInfo.selected) 1 else 0
            )
        }
        fileDao.insertFiles(fileEntities)
    }

    private fun mapToEntity(taskInfo: TaskInfo, existing: TaskEntity?): TaskEntity {
        return TaskEntity(
            gid = taskInfo.gid,
            name = taskInfo.name,
            type = determineTaskType(taskInfo),
            state = taskInfo.state.name,
            url = existing?.url,
            dir = taskInfo.dir,
            totalLength = taskInfo.totalLength,
            completedLength = taskInfo.completedLength,
            downloadSpeed = taskInfo.downloadSpeed,
            uploadSpeed = taskInfo.uploadSpeed,
            connections = taskInfo.connections,
            numSeeders = taskInfo.numSeeders,
            errorCode = taskInfo.errorCode,
            errorMessage = taskInfo.errorMessage,
            userAgent = existing?.userAgent,
            referer = existing?.referer,
            headers = existing?.headers,
            createdAt = existing?.createdAt ?: taskInfo.createdAt,
            updatedAt = System.currentTimeMillis(),
            completedAt = taskInfo.completedAt ?: existing?.completedAt,
            isSelected = existing?.isSelected ?: 1
        )
    }

    private fun determineTaskType(taskInfo: TaskInfo): String {
        return when {
            taskInfo.bittorrent != null -> "torrent"
            else -> "http"
        }
    }

    private fun observeEngineEvents() {
        coroutineScope.launch {
            engine.observeTaskEvents()
                .catch { e ->
                    Timber.e(e, "Error observing engine events")
                }
                .collect { event ->
                    handleEngineEvent(event)
                }
        }
    }

    private suspend fun handleEngineEvent(event: TaskEvent) {
        when (event) {
            is TaskEvent.Started -> {
                Timber.d("Task started: ${event.gid}")
                taskDao.updateTaskState(event.gid, TaskState.ACTIVE.name, System.currentTimeMillis())
            }

            is TaskEvent.Paused -> {
                Timber.d("Task paused: ${event.gid}")
                taskDao.updateTaskState(event.gid, TaskState.PAUSED.name, System.currentTimeMillis())
            }

            is TaskEvent.Stopped -> {
                Timber.d("Task stopped: ${event.gid}")
                taskDao.updateTaskState(event.gid, TaskState.REMOVED.name, System.currentTimeMillis())
            }

            is TaskEvent.Completed -> {
                Timber.d("Task completed: ${event.gid}")
                val now = System.currentTimeMillis()
                val task = getTask(event.gid)
                if (task != null) {
                    taskDao.upsertTask(task.copy(state = TaskState.COMPLETED.name, completedAt = now, updatedAt = now))
                } else {
                    taskDao.updateTaskState(event.gid, TaskState.COMPLETED.name, now)
                }
            }

            is TaskEvent.Error -> {
                Timber.e("Task error: ${event.gid}, code=${event.errorCode}, msg=${event.errorMessage}")
                val now = System.currentTimeMillis()
                val task = getTask(event.gid)
                if (task != null) {
                    taskDao.upsertTask(
                        task.copy(
                            state = TaskState.ERROR.name,
                            errorCode = event.errorCode,
                            errorMessage = event.errorMessage,
                            updatedAt = now
                        )
                    )
                } else {
                    taskDao.updateTaskState(event.gid, TaskState.ERROR.name, now)
                }
            }

            is TaskEvent.Progress -> {
                val task = getTask(event.gid)
                if (task != null) {
                    taskDao.updateTaskProgress(
                        gid = event.gid,
                        completedLength = event.completedLength,
                        downloadSpeed = event.downloadSpeed,
                        uploadSpeed = event.uploadSpeed,
                        connections = task.connections,
                        numSeeders = task.numSeeders,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private fun observeEngineGlobalStat() {
        coroutineScope.launch {
            engine.observeTaskEvents()
                .catch { e ->
                    Timber.e(e, "Error in global stat observation")
                }
                .collect {
                    launch {
                        syncGlobalStat()
                    }
                }
        }
    }
}
