package com.motrix.android.core.engine.model

data class TorrentTaskRequest(
    val torrentPath: String,
    val dir: String,
    val split: Int = 16,
    val selectFile: String? = null
)
