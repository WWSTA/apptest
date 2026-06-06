package com.motrix.android.domain.model

data class DownloadOptions(
    val dir: String? = null,
    val filename: String? = null,
    val split: Int? = null,
    val maxConnectionPerServer: Int? = null,
    val header: List<String> = emptyList(),
    val userAgent: String? = null,
    val referer: String? = null,
    val selectFile: String? = null
)
