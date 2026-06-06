package com.motrix.android.core.engine.model

data class MagnetTaskRequest(
    val magnetUri: String,
    val dir: String,
    val split: Int = 16,
    val selectFile: String? = null
)
