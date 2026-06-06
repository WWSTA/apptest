package com.motrix.android.core.engine.model

sealed class TaskEvent {
    data class Started(val gid: String) : TaskEvent()
    data class Paused(val gid: String) : TaskEvent()
    data class Stopped(val gid: String) : TaskEvent()
    data class Completed(val gid: String) : TaskEvent()
    data class Error(val gid: String, val errorCode: String?, val errorMessage: String?) : TaskEvent()
    data class Progress(
        val gid: String,
        val completedLength: Long,
        val downloadSpeed: Long,
        val uploadSpeed: Long
    ) : TaskEvent()
}
