package com.motrix.android.domain.model

data class TaskUiModel(
    val gid: String,
    val name: String,
    val state: String,
    val progress: Float,
    val totalSize: Long,
    val downloadedSize: Long,
    val speed: Long,
    val remainingTime: Long,
    val connections: Int,
    val seeders: Int,
    val isTorrent: Boolean
)
