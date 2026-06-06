package com.motrix.android.feature.tasklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motrix.android.core.common.util.FormatUtils
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.domain.model.TaskListUiState
import com.motrix.android.domain.model.TaskTab
import com.motrix.android.domain.model.TaskUiModel
import com.motrix.android.feature.tasklist.components.SpeedChart
import com.motrix.android.feature.tasklist.components.TaskCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListRoute(
    onNavigateToNewTask: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TaskListScreen(
        uiState = uiState,
        onTabSelected = viewModel::onTabSelected,
        onPauseTask = viewModel::onPauseTask,
        onResumeTask = viewModel::onResumeTask,
        onRemoveTask = viewModel::onRemoveTask,
        onPauseAll = viewModel::onPauseAll,
        onResumeAll = viewModel::onResumeAll,
        onRefresh = viewModel::onRefresh,
        onNavigateToNewTask = onNavigateToNewTask,
        onNavigateToTaskDetail = onNavigateToTaskDetail,
        onNavigateToSettings = onNavigateToSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskListScreen(
    uiState: TaskListUiState,
    onTabSelected: (TaskTab) -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToNewTask: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    var isDarkTheme by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Motrix",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = { isDarkTheme = !isDarkTheme }) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme",
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToNewTask,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New download",
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PrimaryTabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                ) {
                    TaskTab.entries.forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { onTabSelected(tab) },
                            text = {
                                Text(
                                    text = when (tab) {
                                        TaskTab.ACTIVE -> "Active"
                                        TaskTab.COMPLETED -> "Completed"
                                        TaskTab.ERROR -> "Error"
                                    },
                                )
                            },
                        )
                    }
                }

                val currentTasks = when (uiState.selectedTab) {
                    TaskTab.ACTIVE -> uiState.activeTasks
                    TaskTab.COMPLETED -> uiState.completedTasks
                    TaskTab.ERROR -> uiState.errorTasks
                }

                if (currentTasks.isEmpty()) {
                    EmptyState(
                        tab = uiState.selectedTab,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                    ) {
                        items(
                            items = currentTasks,
                            key = { it.gid },
                        ) { task ->
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = spring(
                                        dampingRatio = 0.8f,
                                        stiffness = 380f,
                                    ),
                                ) + fadeIn(
                                    animationSpec = spring(
                                        dampingRatio = 0.8f,
                                        stiffness = 380f,
                                    ),
                                ),
                                exit = slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = spring(
                                        dampingRatio = 0.8f,
                                        stiffness = 380f,
                                    ),
                                ) + fadeOut(
                                    animationSpec = spring(
                                        dampingRatio = 0.8f,
                                        stiffness = 380f,
                                    ),
                                ),
                            ) {
                                TaskCard(
                                    task = task,
                                    onPause = { onPauseTask(task.gid) },
                                    onResume = { onResumeTask(task.gid) },
                                    onRemove = { onRemoveTask(task.gid) },
                                    onClick = { onNavigateToTaskDetail(task.gid) },
                                )
                            }
                        }
                    }
                }

                SpeedChart(
                    downloadSpeedHistory = uiState.downloadSpeedHistory,
                    uploadSpeedHistory = uiState.uploadSpeedHistory,
                    currentDownloadSpeed = uiState.globalStat.downloadSpeed,
                    currentUploadSpeed = uiState.globalStat.uploadSpeed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    tab: TaskTab,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when (tab) {
                    TaskTab.ACTIVE -> "No active downloads"
                    TaskTab.COMPLETED -> "No completed downloads"
                    TaskTab.ERROR -> "No errors"
                },
                fontSize = 16.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (tab) {
                    TaskTab.ACTIVE -> "Tap + to start a new download"
                    TaskTab.COMPLETED -> "Downloads will appear here when finished"
                    TaskTab.ERROR -> "Failed downloads will appear here"
                },
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
