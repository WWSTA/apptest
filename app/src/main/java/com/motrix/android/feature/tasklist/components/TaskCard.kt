package com.motrix.android.feature.tasklist.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.domain.model.TaskUiModel
import kotlin.math.roundToInt

@Composable
fun TaskCard(
    task: TaskUiModel,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offsetX by remember(task.gid) { mutableFloatStateOf(0f) }
    val swipeThreshold = 200f

    val progressAnimated by animateFloatAsState(
        targetValue = task.progress,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = 700f,
        ),
        label = "progress_${task.gid}",
    )

    val progressColor by animateColorAsState(
        targetValue = when (task.state) {
            TaskState.ACTIVE -> MaterialTheme.colorScheme.primary
            TaskState.COMPLETED -> Color(0xFF34C759)
            TaskState.ERROR -> MaterialTheme.colorScheme.error
            TaskState.PAUSED -> Color(0xFFFFCC00)
            else -> MaterialTheme.colorScheme.outline,
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "progressColor_${task.gid}",
    )

    if (offsetX < -swipeThreshold) {
        onRemove()
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium),
    ) {
        // Delete background revealed on swipe
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.error)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onError,
            )
        }

        Card(
            onClick = onClick,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(task.gid) {
                    detectHorizontalDragGestures(
                        onDragCancel = {
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = offsetX + dragAmount
                            offsetX = newOffset.coerceIn(-swipeThreshold, 0f)
                        },
                        onDragEnd = {
                            if (offsetX < -swipeThreshold / 2) {
                                offsetX = -swipeThreshold
                            } else {
                                offsetX = 0f
                            }
                        },
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progressAnimated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "${task.downloadedSize} / ${task.totalSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (task.remainingTime != null) {
                            Text(
                                text = task.remainingTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (task.state == TaskState.ACTIVE || task.state == TaskState.PAUSED) {
                            Text(
                                text = task.speed,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (task.isTorrent && task.seeders > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${task.seeders} seeders",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        when (task.state) {
                            TaskState.ACTIVE, TaskState.WAITING -> {
                                IconButton(
                                    onClick = onPause,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = "Pause",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            TaskState.PAUSED -> {
                                IconButton(
                                    onClick = onResume,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            else -> { /* no action button */ }
                        }

                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
