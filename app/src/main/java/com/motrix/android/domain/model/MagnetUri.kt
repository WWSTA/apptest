package com.motrix.android.domain.model

data class MagnetUri(
    val infoHash: String,
    val displayName: String,
    val trackers: List<String> = emptyList(),
    val exactLength: Long = 0,
    val webSeeds: List<String> = emptyList()
)
