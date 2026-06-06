package com.motrix.android.core.engine.model

data class HttpTaskRequest(
    val url: String,
    val dir: String,
    val filename: String? = null,
    val split: Int = 16,
    val maxConnectionPerServer: Int = 16,
    val header: List<String> = emptyList(),
    val userAgent: String? = null,
    val referer: String? = null
)
