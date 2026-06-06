package com.motrix.android.core.engine.model

data class EngineConfig(
    val downloadDir: String,
    val maxConcurrentDownloads: Int = 5,
    val maxConnectionPerServer: Int = 16,
    val split: Int = 16,
    val maxOverallDownloadLimit: String = "0",
    val maxDownloadLimit: String = "0",
    val continueDownload: Boolean = true,
    val enableDht: Boolean = true,
    val btEnableLpd: Boolean = true,
    val btEnablePeerExchange: Boolean = true,
    val listenPort: Int = 6881,
    val rpcSecret: String = "",
    val userAgent: String = ""
)
