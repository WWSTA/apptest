package com.motrix.android.core.engine

import com.motrix.android.core.engine.model.BtInfo
import com.motrix.android.core.engine.model.EngineConfig
import com.motrix.android.core.engine.model.GlobalStat
import com.motrix.android.core.engine.model.HttpTaskRequest
import com.motrix.android.core.engine.model.MagnetTaskRequest
import com.motrix.android.core.engine.model.TaskEvent
import com.motrix.android.core.engine.model.TaskFileInfo
import com.motrix.android.core.engine.model.TaskInfo
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.core.engine.model.TaskStatus
import com.motrix.android.core.engine.model.TorrentTaskRequest
import com.motrix.android.core.network.Aria2RpcClient
import com.motrix.android.core.network.WebSocketClient
import com.motrix.android.core.network.model.Aria2Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Aria2Engine @Inject constructor(
    private val rpcClient: Aria2RpcClient,
    private val processManager: Aria2ProcessManager,
    private val webSocketClient: WebSocketClient
) : DownloadEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var config: EngineConfig? = null
    private var eventCollectionJob: Job? = null

    private val _taskEvents = MutableSharedFlow<TaskEvent>(extraBufferCapacity = 128)
    private val taskEvents: SharedFlow<TaskEvent> = _taskEvents.asSharedFlow()

    override suspend fun initialize(config: EngineConfig): Result<Unit> {
        return try {
            this.config = config

            val processResult = processManager.startAria2Process(config)
            if (processResult.isFailure) {
                return Result.failure(processResult.exceptionOrNull()!!)
            }

            rpcClient.configure(config.rpcSecret)

            var retries = 0
            var connected = false
            while (retries < MAX_INIT_RETRIES && !connected) {
                val testResult = rpcClient.callString("aria2.getVersion", emptyList())
                if (testResult.isSuccess) {
                    connected = true
                    Timber.i("Aria2 RPC connected: ${testResult.getOrNull()}")
                } else {
                    retries++
                    Timber.w("RPC not ready, retry $retries/$MAX_INIT_RETRIES")
                    kotlinx.coroutines.delay(INIT_RETRY_DELAY_MS)
                }
            }

            if (!connected) {
                processManager.stopAria2Process()
                return Result.failure(IllegalStateException("Failed to connect to Aria2 RPC after $MAX_INIT_RETRIES retries"))
            }

            webSocketClient.connect()
            startEventCollection()

            Timber.i("Aria2Engine initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Aria2Engine")
            Result.failure(e)
        }
    }

    override suspend fun shutdown(): Result<Unit> {
        return try {
            eventCollectionJob?.cancel()
            eventCollectionJob = null
            webSocketClient.disconnect()
            processManager.stopAria2Process()
            Timber.i("Aria2Engine shut down successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to shutdown Aria2Engine")
            Result.failure(e)
        }
    }

    override suspend fun addHttpTask(request: HttpTaskRequest): Result<String> {
        return try {
            val params = buildList {
                add(listOf(request.url))
                add(buildTaskOptions(request))
            }
            rpcClient.callString("aria2.addUri", params)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add HTTP task")
            Result.failure(e)
        }
    }

    override suspend fun addMagnetTask(request: MagnetTaskRequest): Result<String> {
        return try {
            val params = buildList {
                add(listOf(request.magnetUri))
                add(buildMagnetOptions(request))
            }
            rpcClient.callString("aria2.addUri", params)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add magnet task")
            Result.failure(e)
        }
    }

    override suspend fun addTorrentTask(request: TorrentTaskRequest): Result<String> {
        return try {
            val torrentBytes = java.io.File(request.torrentPath).readBytes()
            val encodedTorrent = Base64.getEncoder().encodeToString(torrentBytes)

            val params = buildList {
                add(encodedTorrent)
                add(buildTorrentOptions(request))
            }
            rpcClient.callString("aria2.addTorrent", params)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add torrent task")
            Result.failure(e)
        }
    }

    override suspend fun pauseTask(gid: String): Result<Unit> {
        return rpcClient.callUnit("aria2.pause", listOf(gid))
    }

    override suspend fun resumeTask(gid: String): Result<Unit> {
        return rpcClient.callUnit("aria2.unpause", listOf(gid))
    }

    override suspend fun removeTask(gid: String, removeFile: Boolean): Result<Unit> {
        return if (removeFile) {
            rpcClient.callUnit("aria2.removeDownloadResult", listOf(gid))
        } else {
            rpcClient.callUnit("aria2.remove", listOf(gid))
        }
    }

    override suspend fun pauseAll(): Result<Unit> {
        return rpcClient.callUnit("aria2.pauseAll", emptyList())
    }

    override suspend fun resumeAll(): Result<Unit> {
        return rpcClient.callUnit("aria2.unpauseAll", emptyList())
    }

    override suspend fun getTaskStatus(gid: String): Result<TaskStatus> {
        return try {
            rpcClient.call("aria2.tellStatus", listOf(gid, STATUS_KEYS)) { element ->
                val obj = element.jsonObject
                parseTaskStatus(obj)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get task status for gid=$gid")
            Result.failure(e)
        }
    }

    override suspend fun getGlobalStat(): Result<GlobalStat> {
        return try {
            rpcClient.call("aria2.getGlobalStat", emptyList()) { element ->
                val obj = element.jsonObject
                GlobalStat(
                    downloadSpeed = obj["downloadSpeed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    uploadSpeed = obj["uploadSpeed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    numActive = obj["numActive"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    numWaiting = obj["numWaiting"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    numStopped = obj["numStopped"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get global stat")
            Result.failure(e)
        }
    }

    override suspend fun getActiveTasks(): Result<List<TaskInfo>> {
        return try {
            rpcClient.call("aria2.tellActive", listOf(TELL_KEYS)) { element ->
                parseTaskInfoList(element.jsonArray)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get active tasks")
            Result.failure(e)
        }
    }

    override suspend fun getWaitingTasks(offset: Int, limit: Int): Result<List<TaskInfo>> {
        return try {
            rpcClient.call("aria2.tellWaiting", listOf(offset, limit, TELL_KEYS)) { element ->
                parseTaskInfoList(element.jsonArray)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get waiting tasks")
            Result.failure(e)
        }
    }

    override suspend fun getStoppedTasks(offset: Int, limit: Int): Result<List<TaskInfo>> {
        return try {
            rpcClient.call("aria2.tellStopped", listOf(offset, limit, TELL_KEYS)) { element ->
                parseTaskInfoList(element.jsonArray)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get stopped tasks")
            Result.failure(e)
        }
    }

    override suspend fun changeGlobalOption(options: Map<String, String>): Result<Unit> {
        return rpcClient.callUnit("aria2.changeGlobalOption", listOf(options))
    }

    override suspend fun changeTaskOption(gid: String, options: Map<String, String>): Result<Unit> {
        return rpcClient.callUnit("aria2.changeOption", listOf(gid, options))
    }

    override fun observeTaskEvents(): Flow<TaskEvent> = taskEvents

    private fun startEventCollection() {
        eventCollectionJob = engineScope.launch {
            webSocketClient.observeEvents().collect { event ->
                val taskEvent = when (event) {
                    is Aria2Event.DownloadStart -> TaskEvent.Started(event.gid)
                    is Aria2Event.DownloadPause -> TaskEvent.Paused(event.gid)
                    is Aria2Event.DownloadStop -> TaskEvent.Stopped(event.gid)
                    is Aria2Event.DownloadComplete -> TaskEvent.Completed(event.gid)
                    is Aria2Event.DownloadError -> TaskEvent.Error(
                        gid = event.gid,
                        errorCode = "UNKNOWN",
                        errorMessage = "Download error"
                    )
                    is Aria2Event.BtDownloadComplete -> TaskEvent.Completed(event.gid)
                }
                _taskEvents.tryEmit(taskEvent)
            }
        }
    }

    private fun buildTaskOptions(request: HttpTaskRequest): Map<String, String> {
        val options = mutableMapOf<String, String>()
        options["dir"] = request.dir
        request.filename?.let { options["out"] = it }
        request.split?.let { options["split"] = it.toString() }
        request.maxConnectionPerServer?.let { options["max-connection-per-server"] = it.toString() }
        request.userAgent?.let { options["user-agent"] = it }
        request.referer?.let { options["referer"] = it }
        request.header?.takeIf { it.isNotEmpty() }?.let { headers ->
            options["header"] = headers.joinToString("\n")
        }
        return options
    }

    private fun buildMagnetOptions(request: MagnetTaskRequest): Map<String, String> {
        val options = mutableMapOf<String, String>()
        options["dir"] = request.dir
        request.selectFile?.let { options["select-file"] = it }
        return options
    }

    private fun buildTorrentOptions(request: TorrentTaskRequest): Map<String, String> {
        val options = mutableMapOf<String, String>()
        options["dir"] = request.dir
        request.selectFile?.let { options["select-file"] = it }
        return options
    }

    private fun parseTaskStatus(obj: JsonObject): TaskStatus {
        return TaskStatus(
            gid = obj["gid"]?.jsonPrimitive?.contentOrNull ?: "",
            state = parseTaskState(obj["status"]?.jsonPrimitive?.contentOrNull),
            totalLength = obj["totalLength"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            completedLength = obj["completedLength"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            downloadSpeed = obj["downloadSpeed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            uploadSpeed = obj["uploadSpeed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            connections = obj["connections"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            numSeeders = obj["numSeeders"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            errorCode = obj["errorCode"]?.jsonPrimitive?.contentOrNull,
            errorMessage = obj["errorMessage"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseTaskInfo(obj: JsonObject): TaskInfo {
        val btInfo = obj["bittorrent"]?.jsonObject?.let { btObj ->
            val announceList = btObj["announceList"]?.jsonArray?.flatMap { tier ->
                tier.jsonArray.map { it.jsonPrimitive.content }
            } ?: emptyList()

            BtInfo(
                announceList = announceList,
                comment = btObj["comment"]?.jsonPrimitive?.contentOrNull,
                creationDate = btObj["creationDate"]?.jsonPrimitive?.longOrNull,
                name = btObj["info"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            )
        }

        val files = obj["files"]?.jsonArray?.map { fileElement ->
            val fileObj = fileElement.jsonObject
            TaskFileInfo(
                index = fileObj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                path = fileObj["path"]?.jsonPrimitive?.contentOrNull ?: "",
                length = fileObj["length"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                completedLength = fileObj["completedLength"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                selected = fileObj["selected"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
            )
        } ?: emptyList()

        val name = btInfo?.name
            ?: files.firstOrNull()?.path?.substringAfterLast("/")
            ?: obj["gid"]?.jsonPrimitive?.contentOrNull
            ?: "Unknown"

        return TaskInfo(
            gid = obj["gid"]?.jsonPrimitive?.contentOrNull ?: "",
            name = name,
            state = parseTaskState(obj["status"]?.jsonPrimitive?.contentOrNull),
            totalLength = obj["totalLength"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            completedLength = obj["completedLength"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            uploadLength = obj["uploadLength"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            downloadSpeed = obj["downloadSpeed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            uploadSpeed = obj["uploadSpeed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            connections = obj["connections"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            numSeeders = obj["numSeeders"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            dir = obj["dir"]?.jsonPrimitive?.contentOrNull ?: "",
            files = files,
            bittorrent = btInfo,
            errorCode = obj["errorCode"]?.jsonPrimitive?.contentOrNull,
            errorMessage = obj["errorMessage"]?.jsonPrimitive?.contentOrNull,
            createdAt = System.currentTimeMillis(),
            completedAt = null
        )
    }

    private fun parseTaskInfoList(array: JsonArray): List<TaskInfo> {
        return array.map { element ->
            parseTaskInfo(element.jsonObject)
        }
    }

    private fun parseTaskState(status: String?): TaskState {
        return when (status) {
            "active" -> TaskState.ACTIVE
            "waiting" -> TaskState.WAITING
            "paused" -> TaskState.PAUSED
            "complete" -> TaskState.COMPLETED
            "error" -> TaskState.ERROR
            "removed" -> TaskState.REMOVED
            else -> TaskState.WAITING
        }
    }

    companion object {
        private const val MAX_INIT_RETRIES = 10
        private const val INIT_RETRY_DELAY_MS = 500L

        private val STATUS_KEYS = listOf(
            "gid", "status", "totalLength", "completedLength",
            "downloadSpeed", "uploadSpeed", "connections",
            "numSeeders", "errorCode", "errorMessage"
        )

        private val TELL_KEYS = listOf(
            "gid", "status", "totalLength", "completedLength",
            "uploadLength", "downloadSpeed", "uploadSpeed",
            "connections", "numSeeders", "dir", "files",
            "bittorrent", "errorCode", "errorMessage"
        )
    }
}
