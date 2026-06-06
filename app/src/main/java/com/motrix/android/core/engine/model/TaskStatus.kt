package com.motrix.android.core.engine.model

data class TaskStatus(
    val gid: String,
    val state: TaskState,
    val totalLength: Long,
    val completedLength: Long,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val connections: Int,
    val numSeeders: Int,
    val errorCode: String?,
    val errorMessage: String?
)
