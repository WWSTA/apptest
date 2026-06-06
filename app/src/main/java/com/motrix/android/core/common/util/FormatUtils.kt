package com.motrix.android.core.common.util

import android.icu.text.NumberFormat
import java.util.Locale

object FormatUtils {

    private val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 1
    }

    private val speedFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 1
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 0) return "0 B"

        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024L * 1024 -> "${numberFormat.format(bytes.toDouble() / 1024)} KB"
            bytes < 1024L * 1024 * 1024 -> "${numberFormat.format(bytes.toDouble() / (1024 * 1024))} MB"
            bytes < 1024L * 1024 * 1024 * 1024 -> "${numberFormat.format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
            else -> "${numberFormat.format(bytes.toDouble() / (1024L * 1024 * 1024 * 1024))} TB"
        }
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond < 0) return "0 B/s"

        return when {
            bytesPerSecond < 1024 -> "${bytesPerSecond} B/s"
            bytesPerSecond < 1024L * 1024 -> "${speedFormat.format(bytesPerSecond.toDouble() / 1024)} KB/s"
            bytesPerSecond < 1024L * 1024 * 1024 -> "${speedFormat.format(bytesPerSecond.toDouble() / (1024 * 1024))} MB/s"
            else -> "${speedFormat.format(bytesPerSecond.toDouble() / (1024 * 1024 * 1024))} GB/s"
        }
    }

    fun formatRemainingTime(completedLength: Long, totalLength: Long, speed: Long): String? {
        if (speed <= 0) return null
        if (totalLength <= 0) return null
        if (completedLength >= totalLength) return null

        val remainingBytes = totalLength - completedLength
        val remainingSeconds = remainingBytes / speed

        return formatDuration(remainingSeconds)
    }

    fun formatProgress(completedLength: Long, totalLength: Long): Float {
        if (totalLength <= 0) return 0.0f
        if (completedLength <= 0) return 0.0f
        if (completedLength >= totalLength) return 1.0f
        return (completedLength.toFloat() / totalLength.toFloat()).coerceIn(0.0f, 1.0f)
    }

    fun formatProgressPercent(progress: Float): String {
        val percent = (progress * 100).coerceIn(0f, 100f)
        return "${numberFormat.format(percent)}%"
    }

    internal fun formatDuration(seconds: Long): String {
        if (seconds < 0) return "--"

        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }
}
