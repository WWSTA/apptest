package com.motrix.android.feature.taskdetail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.feature.tasklist.components.SpeedChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailRoute(
    gid: String,
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TaskDetailScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onPauseTask = viewModel::onPauseTask,
        onResumeTask = viewModel::onResumeTask,
        onRemoveTask = { viewModel.onRemoveTask(false) },
        onToggleFileSelection = viewModel::onToggleFileSelection,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailScreen(
    uiState: TaskDetailUiState,
    onNavigateBack: () -> Unit,
    onPauseTask: () -> Unit,
    onResumeTask: () -> Unit,
    onRemoveTask: () -> Unit,
    onToggleFileSelection: (Int, Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(32.dp),
            )
            return@Scaffold
        }

        if (uiState.errorMessage != null && uiState.task == null) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiState.errorMessage!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            return@Scaffold
        }

        val task = uiState.task ?: return@Scaffold

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Task name
            item {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Status and progress
            item {
                val progressAnimated by animateFloatAsState(
                    targetValue = task.progress,
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 700f,
                    ),
                    label = "detail_progress",
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = when (task.state) {
                                    TaskState.ACTIVE -> "Downloading"
                                    TaskState.WAITING -> "Waiting"
                                    TaskState.PAUSED -> "Paused"
                                    TaskState.COMPLETED -> "Completed"
                                    TaskState.ERROR -> "Error"
                                    TaskState.REMOVED -> "Removed"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = when (task.state) {
                                    TaskState.ACTIVE -> MaterialTheme.colorScheme.primary
                                    TaskState.COMPLETED -> Color(0xFF34C759)
                                    TaskState.ERROR -> MaterialTheme.colorScheme.error
                                    TaskState.PAUSED -> Color(0xFFFFCC00)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant,
                                },
                            )
                            Text(
                                text = "${(task.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { progressAnimated },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(MaterialTheme.shapes.extraSmall),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${task.downloadedSize} / ${task.totalSize}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (task.remainingTime != null) {
                                Text(
                                    text = task.remainingTime,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            // Connection info
            if (uiState.connectionInfo != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Connection Info",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                ConnectionInfoItem(
                                    label = "Seeders",
                                    value = uiState.connectionInfo.seeders.toString(),
                                )
                                ConnectionInfoItem(
                                    label = "Connections",
                                    value = uiState.connectionInfo.connections.toString(),
                                )
                                ConnectionInfoItem(
                                    label = "Download",
                                    value = uiState.connectionInfo.downloadSpeed,
                                )
                                ConnectionInfoItem(
                                    label = "Upload",
                                    value = uiState.connectionInfo.uploadSpeed,
                                )
                            }
                        }
                    }
                }
            }

            // Speed history chart
            if (uiState.speedHistory.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        SpeedChart(
                            downloadSpeedHistory = uiState.speedHistory,
                            uploadSpeedHistory = emptyList(),
                            currentDownloadSpeed = uiState.connectionInfo?.downloadSpeed?.let {
                                parseSpeedToLong(it)
                            } ?: 0L,
                            currentUploadSpeed = uiState.connectionInfo?.uploadSpeed?.let {
                                parseSpeedToLong(it)
                            } ?: 0L,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(8.dp),
                        )
                    }
                }
            }

            // File list
            if (uiState.fileList.isNotEmpty()) {
                item {
                    Text(
                        text = "Files",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(
                    items = uiState.fileList,
                    key = { it.index },
                ) { file ->
                    FileItemRow(
                        file = file,
                        onToggleSelection = { selected ->
                            onToggleFileSelection(file.index, selected)
                        },
                    )
                }
            }

            // Error info
            if (task.state == TaskState.ERROR && uiState.errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // Action buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (task.state) {
                        TaskState.ACTIVE, TaskState.WAITING -> {
                            Button(
                                onClick = onPauseTask,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pause")
                            }
                        }
                        TaskState.PAUSED -> {
                            Button(
                                onClick = onResumeTask,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Resume")
                            }
                        }
                        else -> { /* no primary action */ }
                    }

                    Button(
                        onClick = onRemoveTask,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileItemRow(
    file: FileItemUi,
    onToggleSelection: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.path.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${file.size} · ${(file.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!file.isSelected) {
                TextButton(onClick = { onToggleSelection(true) }) {
                    Text("Select")
                }
            } else {
                TextButton(onClick = { onToggleSelection(false) }) {
                    Text("Deselect")
                }
            }
        }
    }
}

private fun parseSpeedToLong(speedStr: String): Long {
    val num = speedStr.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
    return when {
        speedStr.contains("GB/s") -> (num * 1_000_000_000).toLong()
        speedStr.contains("MB/s") -> (num * 1_000_000).toLong()
        speedStr.contains("KB/s") -> (num * 1_000).toLong()
        else -> num.toLong()
    }
}
