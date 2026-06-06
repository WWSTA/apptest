package com.motrix.android.core.network.model

sealed class Aria2Event {
    data class DownloadStart(val gid: String) : Aria2Event()
    data class DownloadPause(val gid: String) : Aria2Event()
    data class DownloadStop(val gid: String) : Aria2Event()
    data class DownloadComplete(val gid: String) : Aria2Event()
    data class DownloadError(val gid: String) : Aria2Event()
    data class BtDownloadComplete(val gid: String) : Aria2Event()
}
