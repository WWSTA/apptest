package com.motrix.android.feature.tasklist.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.motrix.android.core.common.util.FormatUtils

@Composable
fun SpeedChart(
    downloadSpeedHistory: List<Long>,
    uploadSpeedHistory: List<Long>,
    currentDownloadSpeed: Long,
    currentUploadSpeed: Long,
    modifier: Modifier = Modifier,
) {
    val downloadColor = MaterialTheme.colorScheme.primary
    val uploadColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val maxSpeed = remember(downloadSpeedHistory, uploadSpeedHistory) {
                val allSpeeds = downloadSpeedHistory + uploadSpeedHistory
                val max = allSpeeds.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                max.toFloat()
            }

            val chartWidth = size.width
            val chartHeight = size.height
            val pointCount = downloadSpeedHistory.size.coerceAtLeast(2)

            // Draw grid lines
            for (i in 0..3) {
                val y = chartHeight * i / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1f,
                )
            }

            // Draw download speed curve
            if (downloadSpeedHistory.size >= 2) {
                val downloadPath = createBezierPath(
                    data = downloadSpeedHistory,
                    maxWidth = chartWidth,
                    maxHeight = chartHeight,
                    maxValue = maxSpeed,
                )
                drawPath(
                    path = downloadPath,
                    color = downloadColor,
                    style = Stroke(width = 2.5f),
                )
            }

            // Draw upload speed curve
            if (uploadSpeedHistory.size >= 2) {
                val uploadPath = createBezierPath(
                    data = uploadSpeedHistory,
                    maxWidth = chartWidth,
                    maxHeight = chartHeight,
                    maxValue = maxSpeed,
                )
                drawPath(
                    path = uploadPath,
                    color = uploadColor,
                    style = Stroke(width = 2f),
                )
            }

            // Draw Y-axis labels
            val paint = androidx.compose.ui.graphics.Paint().asFrameworkPaint().apply {
                color = textColor.hashCode()
                textSize = 20f
            }
            for (i in 0..3) {
                val y = chartHeight * i / 3f
                val speedValue = (maxSpeed * (3 - i) / 3f).toLong()
                val label = FormatUtils.formatSpeed(speedValue)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    0f,
                    y + 12f,
                    paint,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\u2193 ${FormatUtils.formatSpeed(currentDownloadSpeed)}",
                style = MaterialTheme.typography.labelSmall,
                color = downloadColor,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "\u2191 ${FormatUtils.formatSpeed(currentUploadSpeed)}",
                style = MaterialTheme.typography.labelSmall,
                color = uploadColor,
            )
        }
    }
}

private fun createBezierPath(
    data: List<Long>,
    maxWidth: Float,
    maxHeight: Float,
    maxValue: Float,
): Path {
    val path = Path()
    if (data.size < 2) return path

    val stepX = maxWidth / (data.size - 1).coerceAtLeast(1)

    val points = data.mapIndexed { index, value ->
        val x = index * stepX
        val y = maxHeight - (value.toFloat() / maxValue.coerceAtLeast(1f)) * maxHeight
        Offset(x, y)
    }

    path.moveTo(points[0].x, points[0].y)

    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val controlX1 = prev.x + (curr.x - prev.x) / 3f
        val controlX2 = prev.x + 2f * (curr.x - prev.x) / 3f
        path.cubicTo(
            controlX1, prev.y,
            controlX2, curr.y,
            curr.x, curr.y,
        )
    }

    return path
}
