package com.motrix.android.core.engine.model

data class TaskInfo(
    val gid: String,
    val name: String,
    val state: TaskState,
    val totalLength: Long = 0,
    val completedLength: Long = 0,
    val uploadLength: Long = 0,
    val downloadSpeed: Long = 0,
    val uploadSpeed: Long = 0,
    val connections: Int = 0,
    val numSeeders: Int = 0,
    val dir: String = "",
    val files: List<TaskFileInfo> = emptyList(),
    val bittorrent: BtInfo? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = 0,
    val completedAt: Long? = null
)
