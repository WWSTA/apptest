package com.motrix.android.feature.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motrix.android.core.common.util.FormatUtils
import com.motrix.android.core.engine.model.GlobalStat
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.domain.model.TaskListUiState
import com.motrix.android.domain.model.TaskTab
import com.motrix.android.domain.model.TaskUiModel
import com.motrix.android.domain.repository.TaskRepository
import com.motrix.android.domain.usecase.GetTasksUseCase
import com.motrix.android.domain.usecase.PauseTaskUseCase
import com.motrix.android.domain.usecase.RemoveTaskUseCase
import com.motrix.android.domain.usecase.ResumeTaskUseCase
import com.motrix.android.domain.usecase.SyncEngineStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val getTasksUseCase: GetTasksUseCase,
    private val pauseTaskUseCase: PauseTaskUseCase,
    private val resumeTaskUseCase: ResumeTaskUseCase,
    private val removeTaskUseCase: RemoveTaskUseCase,
    private val taskRepository: TaskRepository,
    private val syncEngineStateUseCase: SyncEngineStateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    private val speedHistory = MutableList(60) { 0L }
    private val uploadSpeedHistory = MutableList(60) { 0L }

    init {
        syncEngineState()
        observeTasks()
        observeGlobalStat()
    }

    private fun syncEngineState() {
        viewModelScope.launch {
            syncEngineStateUseCase()
                .onFailure { Timber.e(it, "Engine sync failed") }
        }
    }

    private fun observeTasks() {
        viewModelScope.launch {
            combine(
                taskRepository.getTasks(TaskState.ACTIVE),
                taskRepository.getTasks(TaskState.WAITING),
                taskRepository.getTasks(TaskState.PAUSED),
                taskRepository.getTasks(TaskState.COMPLETED),
                taskRepository.getTasks(TaskState.ERROR),
            ) { active, waiting, paused, completed, error ->
                val activeList = (active + waiting + paused).map { it.toUiModel() }
                val completedList = completed.map { it.toUiModel() }
                val errorList = error.map { it.toUiModel() }
                Triple(activeList, completedList, errorList)
            }
                .distinctUntilChanged()
                .catch { Timber.e(it, "Task observation failed") }
                .collect { (active, completed, error) ->
                    _uiState.update { state ->
                        state.copy(
                            activeTasks = active,
                            completedTasks = completed,
                            errorTasks = error,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private fun observeGlobalStat() {
        viewModelScope.launch {
            taskRepository.getGlobalStat()
                .catch { Timber.e(it, "Global stat observation failed") }
                .collect { stat ->
                    updateSpeedHistory(stat)
                    _uiState.update { state ->
                        state.copy(
                            globalStat = stat,
                            downloadSpeedHistory = speedHistory.toList(),
                            uploadSpeedHistory = uploadSpeedHistory.toList(),
                        )
                    }
                }
        }
    }

    private fun updateSpeedHistory(stat: GlobalStat) {
        synchronized(speedHistory) {
            speedHistory.add(stat.downloadSpeed)
            if (speedHistory.size > 60) speedHistory.removeFirst()
        }
        synchronized(uploadSpeedHistory) {
            uploadSpeedHistory.add(stat.uploadSpeed)
            if (uploadSpeedHistory.size > 60) uploadSpeedHistory.removeFirst()
        }
    }

    fun onTabSelected(tab: TaskTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onPauseTask(gid: String) {
        viewModelScope.launch {
            pauseTaskUseCase(gid)
                .onFailure { Timber.e(it, "Pause task failed: $gid") }
        }
    }

    fun onResumeTask(gid: String) {
        viewModelScope.launch {
            resumeTaskUseCase(gid)
                .onFailure { Timber.e(it, "Resume task failed: $gid") }
        }
    }

    fun onRemoveTask(gid: String, withFile: Boolean = false) {
        viewModelScope.launch {
            removeTaskUseCase(gid, withFile)
                .onFailure { Timber.e(it, "Remove task failed: $gid") }
        }
    }

    fun onPauseAll() {
        viewModelScope.launch {
            taskRepository.pauseAllTasks()
                .onFailure { Timber.e(it, "Pause all failed") }
        }
    }

    fun onResumeAll() {
        viewModelScope.launch {
            taskRepository.resumeAllTasks()
                .onFailure { Timber.e(it, "Resume all failed") }
        }
    }

    fun onRefresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        syncEngineState()
    }

    private fun com.motrix.android.core.database.entity.TaskEntity.toUiModel(): TaskUiModel {
        val total = totalLength.coerceAtLeast(1L)
        val progressValue = completedLength.toFloat() / total
        val remainingBytes = totalLength - completedLength
        val remainingTime = if (downloadSpeed > 0) {
            FormatUtils.formatDuration(remainingBytes / downloadSpeed)
        } else null

        return TaskUiModel(
            gid = gid,
            name = name,
            state = TaskState.valueOf(state),
            progress = progressValue.coerceIn(0f, 1f),
            totalSize = FormatUtils.formatFileSize(totalLength),
            downloadedSize = FormatUtils.formatFileSize(completedLength),
            speed = FormatUtils.formatSpeed(downloadSpeed),
            remainingTime = remainingTime,
            connections = connections,
            seeders = numSeeders,
            isTorrent = type == "magnet" || type == "torrent",
        )
    }
}
