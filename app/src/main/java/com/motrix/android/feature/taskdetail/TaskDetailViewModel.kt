package com.motrix.android.feature.taskdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motrix.android.core.common.util.FormatUtils
import com.motrix.android.core.engine.DownloadEngine
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.domain.model.TaskUiModel
import com.motrix.android.domain.repository.TaskRepository
import com.motrix.android.domain.usecase.PauseTaskUseCase
import com.motrix.android.domain.usecase.RemoveTaskUseCase
import com.motrix.android.domain.usecase.ResumeTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class TaskDetailUiState(
    val task: TaskUiModel? = null,
    val fileList: List<FileItemUi> = emptyList(),
    val connectionInfo: ConnectionInfo? = null,
    val speedHistory: List<Long> = emptyList(),
    val errorMessage: String? = null,
    val isLoading: Boolean = true,
)

data class FileItemUi(
    val index: Int,
    val path: String,
    val size: String,
    val progress: Float,
    val isSelected: Boolean,
)

data class ConnectionInfo(
    val seeders: Int,
    val connections: Int,
    val uploadSpeed: String,
    val downloadSpeed: String,
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val downloadEngine: DownloadEngine,
    private val pauseTaskUseCase: PauseTaskUseCase,
    private val resumeTaskUseCase: ResumeTaskUseCase,
    private val removeTaskUseCase: RemoveTaskUseCase,
) : ViewModel() {

    private val gid: String = savedStateHandle["gid"] ?: ""

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    private val speedHistoryBuffer = MutableList(60) { 0L }

    init {
        loadTaskDetail()
    }

    private fun loadTaskDetail() {
        if (gid.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid task ID") }
            return
        }

        viewModelScope.launch {
            taskRepository.getTasks(TaskState.ACTIVE)
                .catch { Timber.e(it, "Failed to observe task detail") }
                .collect { tasks ->
                    val entity = tasks.find { it.gid == gid }
                    if (entity != null) {
                        updateStateFromEntity(entity)
                    }
                }
        }

        viewModelScope.launch {
            taskRepository.getTasks(TaskState.COMPLETED)
                .catch { Timber.e(it, "Failed to observe completed tasks") }
                .collect { tasks ->
                    val entity = tasks.find { it.gid == gid }
                    if (entity != null) {
                        updateStateFromEntity(entity)
                    }
                }
        }

        viewModelScope.launch {
            taskRepository.getTasks(TaskState.ERROR)
                .catch { Timber.e(it, "Failed to observe error tasks") }
                .collect { tasks ->
                    val entity = tasks.find { it.gid == gid }
                    if (entity != null) {
                        updateStateFromEntity(entity)
                    }
                }
        }

        viewModelScope.launch {
            val entity = taskRepository.getTask(gid)
            if (entity != null) {
                updateStateFromEntity(entity)
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Task not found") }
            }
        }
    }

    private fun updateStateFromEntity(entity: com.motrix.android.core.database.entity.TaskEntity) {
        val total = entity.totalLength.coerceAtLeast(1L)
        val progressValue = entity.completedLength.toFloat() / total
        val remainingBytes = entity.totalLength - entity.completedLength
        val remainingTime = if (entity.downloadSpeed > 0) {
            FormatUtils.formatDuration(remainingBytes / entity.downloadSpeed)
        } else null

        val taskUiModel = TaskUiModel(
            gid = entity.gid,
            name = entity.name,
            state = TaskState.valueOf(entity.state),
            progress = progressValue.coerceIn(0f, 1f),
            totalSize = FormatUtils.formatFileSize(entity.totalLength),
            downloadedSize = FormatUtils.formatFileSize(entity.completedLength),
            speed = FormatUtils.formatSpeed(entity.downloadSpeed),
            remainingTime = remainingTime,
            connections = entity.connections,
            seeders = entity.numSeeders,
            isTorrent = entity.type == "magnet" || entity.type == "torrent",
        )

        synchronized(speedHistoryBuffer) {
            speedHistoryBuffer.add(entity.downloadSpeed)
            if (speedHistoryBuffer.size > 60) speedHistoryBuffer.removeFirst()
        }

        _uiState.update { state ->
            state.copy(
                task = taskUiModel,
                connectionInfo = ConnectionInfo(
                    seeders = entity.numSeeders,
                    connections = entity.connections,
                    uploadSpeed = FormatUtils.formatSpeed(entity.uploadSpeed),
                    downloadSpeed = FormatUtils.formatSpeed(entity.downloadSpeed),
                ),
                speedHistory = speedHistoryBuffer.toList(),
                isLoading = false,
                errorMessage = if (entity.state == TaskState.ERROR.name) {
                    entity.errorMessage
                } else null,
            )
        }
    }

    fun onPauseTask() {
        viewModelScope.launch {
            pauseTaskUseCase(gid)
                .onFailure { Timber.e(it, "Pause task failed: $gid") }
        }
    }

    fun onResumeTask() {
        viewModelScope.launch {
            resumeTaskUseCase(gid)
                .onFailure { Timber.e(it, "Resume task failed: $gid") }
        }
    }

    fun onRemoveTask(withFile: Boolean = false) {
        viewModelScope.launch {
            removeTaskUseCase(gid, withFile)
                .onFailure { Timber.e(it, "Remove task failed: $gid") }
        }
    }

    fun onToggleFileSelection(fileIndex: Int, selected: Boolean) {
        viewModelScope.launch {
            downloadEngine.changeTaskOption(
                gid,
                mapOf("select-file" to fileIndex.toString()),
            ).onFailure { Timber.e(it, "Toggle file selection failed") }
        }
    }
}
