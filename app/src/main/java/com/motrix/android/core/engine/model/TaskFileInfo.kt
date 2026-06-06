package com.motrix.android.core.engine.model

data class TaskFileInfo(
    val index: Int,
    val path: String,
    val length: Long = 0,
    val completedLength: Long = 0,
    val selected: Boolean = true
)
