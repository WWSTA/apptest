package com.motrix.android.core.common.extension

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> {
    return ThrottleLatestFlow(this, periodMs)
}

private class ThrottleLatestFlow<T>(
    private val upstream: Flow<T>,
    private val periodMs: Long
) : Flow<T> {

    override suspend fun collect(collector: FlowCollector<T>) {
        var lastEmitTime = 0L
        var pendingValue: T? = null
        var hasPending = false

        upstream.collect { value ->
            val now = System.currentTimeMillis()
            if (now - lastEmitTime >= periodMs) {
                lastEmitTime = now
                collector.emit(value)
                hasPending = false
            } else {
                pendingValue = value
                hasPending = true
            }
        }

        if (hasPending) {
            collector.emit(pendingValue as T)
        }
    }
}

fun <T> Flow<T>.retryWithDelay(
    delayMs: Long = 1000L,
    maxRetries: Int = 3
): Flow<T> {
    return flow {
        var retryCount = 0
        while (retryCount <= maxRetries) {
            try {
                collect { value ->
                    emit(value)
                }
                break
            } catch (e: Exception) {
                retryCount++
                if (retryCount > maxRetries) {
                    throw e
                }
                delay(delayMs * retryCount)
            }
        }
    }
}

fun <T1, T2, R> combineFlows(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    transform: suspend (T1, T2) -> R
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow1, flow2, transform)
}

fun <T1, T2, T3, R> combineFlows(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    transform: suspend (T1, T2, T3) -> R
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow1, flow2, flow3, transform)
}

fun <T1, T2, T3, T4, R> combineFlows(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    transform: suspend (T1, T2, T3, T4) -> R
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow1, flow2, flow3, flow4, transform)
}

fun <T1, T2, T3, T4, T5, R> combineFlows(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    transform: suspend (T1, T2, T3, T4, T5) -> R
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow1, flow2, flow3, flow4, flow5, transform)
}
