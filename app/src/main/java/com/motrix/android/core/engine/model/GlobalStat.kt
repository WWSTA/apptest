package com.motrix.android.core.engine.model

data class GlobalStat(
    val downloadSpeed: Long = 0,
    val uploadSpeed: Long = 0,
    val numActive: Int = 0,
    val numWaiting: Int = 0,
    val numStopped: Int = 0
)
