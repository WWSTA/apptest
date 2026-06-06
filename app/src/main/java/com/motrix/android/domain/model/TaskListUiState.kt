package com.motrix.android.domain.model

import com.motrix.android.core.engine.model.GlobalStat

data class TaskListUiState(
    val activeTasks: List<TaskUiModel> = emptyList(),
    val waitingTasks: List<TaskUiModel> = emptyList(),
    val completedTasks: List<TaskUiModel> = emptyList(),
    val errorTasks: List<TaskUiModel> = emptyList(),
    val globalStat: GlobalStat = GlobalStat(),
    val selectedTab: TaskTab = TaskTab.ACTIVE,
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadSpeedHistory: List<Long> = emptyList(),
    val uploadSpeedHistory: List<Long> = emptyList(),
)
