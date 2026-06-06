package com.motrix.android.core.engine.model

sealed class ProcessState {
    data object Running : ProcessState()
    data object Stopped : ProcessState()
    data class Crashed(val exitCode: Int) : ProcessState()
}
