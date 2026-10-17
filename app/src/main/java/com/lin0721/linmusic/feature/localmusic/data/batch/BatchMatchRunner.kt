package com.lin0721.linmusic.feature.localmusic.data.batch

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay

internal data class BatchRunnerStep<T>(val row: T, val halt: Boolean = false, val message: String? = null)

/** A completed row is committed before advancing. Cancellation leaves unprocessed rows pending. */
internal class BatchMatchRunner<T>(
    private val nextPending: suspend () -> T?,
    private val process: suspend (T) -> BatchRunnerStep<T>,
    private val commit: suspend (T) -> Unit,
    private val minimumIntervalMs: Long = 2_000L
) {
    suspend fun run(): String? {
        var first = true
        while (true) {
            currentCoroutineContext().ensureActive()
            val row = nextPending() ?: return null
            // Delay after the previous result, so request starts are always at least two seconds apart.
            if (!first) delay(minimumIntervalMs)
            first = false
            currentCoroutineContext().ensureActive()
            val result = process(row)
            commit(result.row)
            if (result.halt) return result.message ?: "匹配已暂停"
        }
    }
}
