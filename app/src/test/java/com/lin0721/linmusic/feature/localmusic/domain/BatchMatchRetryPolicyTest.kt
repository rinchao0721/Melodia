package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.feature.localmusic.data.batch.BatchMatchRunner
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchRunnerStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchMatchRetryPolicyTest {
    private data class Row(val id: Int, val status: BatchLocalMatchStatus, val confirmedId: Long? = null)

    private fun MutableMap<Int, Row>.explicitStart() {
        keys.toList().forEach { id ->
            val row = getValue(id)
            this[id] = row.copy(status = BatchMatchRetryPolicy.statusForExplicitStart(row.status))
        }
    }

    @Test fun explicitRestartIncludesUnmatchedNeedsConfirmationAndErrorsButPreservesConfirmedSources() = runTest {
        val rows = listOf(
            Row(1, BatchLocalMatchStatus.PENDING), Row(2, BatchLocalMatchStatus.NO_MATCH),
            Row(3, BatchLocalMatchStatus.NEEDS_CONFIRMATION), Row(4, BatchLocalMatchStatus.ERROR),
            Row(5, BatchLocalMatchStatus.MATCHED, 500L), Row(6, BatchLocalMatchStatus.ALREADY_LINKED, 600L)
        ).associateBy { it.id }.toMutableMap()
        val before = rows.toMap()
        rows.explicitStart()
        val queried = mutableListOf<Int>()
        BatchMatchRunner(
            nextPending = { rows.values.firstOrNull { it.status == BatchLocalMatchStatus.PENDING } },
            process = { row -> queried += row.id; BatchRunnerStep(row.copy(status = BatchLocalMatchStatus.MATCHED, confirmedId = row.id.toLong())) },
            commit = { row -> rows[row.id] = row }, minimumIntervalMs = 0L
        ).run()
        assertEquals(listOf(1, 2, 3, 4), queried)
        assertEquals(before[5], rows[5])
        assertEquals(before[6], rows[6])
    }

    @Test fun unresolvedResultsAreAttemptedOncePerRunAndCanSucceedOnTheNextExplicitStart() = runTest {
        val rows = listOf(Row(1, BatchLocalMatchStatus.PENDING), Row(2, BatchLocalMatchStatus.PENDING),
            Row(3, BatchLocalMatchStatus.MATCHED, 300L)).associateBy { it.id }.toMutableMap()
        val firstPass = mutableListOf<Int>()
        rows.explicitStart()
        BatchMatchRunner(
            nextPending = { rows.values.firstOrNull { it.status == BatchLocalMatchStatus.PENDING } },
            process = { row ->
                firstPass += row.id
                check(firstPass.size <= 2) { "An unresolved result was incorrectly requeued inside one run" }
                BatchRunnerStep(row.copy(status = if (row.id == 1) BatchLocalMatchStatus.NO_MATCH else BatchLocalMatchStatus.NEEDS_CONFIRMATION))
            }, commit = { row -> rows[row.id] = row }, minimumIntervalMs = 0L
        ).run()
        assertEquals(listOf(1, 2), firstPass)
        assertTrue(BatchMatchRetryPolicy.shouldRetryOnStart(rows.getValue(1).status))
        assertTrue(BatchMatchRetryPolicy.shouldRetryOnStart(rows.getValue(2).status))
        val secondPass = mutableListOf<Int>()
        rows.explicitStart()
        BatchMatchRunner(
            nextPending = { rows.values.firstOrNull { it.status == BatchLocalMatchStatus.PENDING } },
            process = { row -> secondPass += row.id; BatchRunnerStep(row.copy(status = BatchLocalMatchStatus.MATCHED, confirmedId = row.id.toLong())) },
            commit = { row -> rows[row.id] = row }, minimumIntervalMs = 0L
        ).run()
        assertEquals(listOf(1, 2), secondPass)
        assertEquals(300L, rows.getValue(3).confirmedId)
        assertTrue(rows.values.all { it.status == BatchLocalMatchStatus.MATCHED })
    }

    @Test fun aHaltedErrorRetainsPendingWorkAndRetriesItOnExplicitResume() = runTest {
        val rows = listOf(Row(1, BatchLocalMatchStatus.PENDING), Row(2, BatchLocalMatchStatus.PENDING))
            .associateBy { it.id }.toMutableMap()
        rows.explicitStart()
        val message = BatchMatchRunner(
            nextPending = { rows.values.firstOrNull { it.status == BatchLocalMatchStatus.PENDING } },
            process = { row -> BatchRunnerStep(row.copy(status = BatchLocalMatchStatus.ERROR), halt = true, message = "429") },
            commit = { row -> rows[row.id] = row }, minimumIntervalMs = 0L
        ).run()
        assertEquals("429", message)
        assertEquals(BatchLocalMatchStatus.ERROR, rows.getValue(1).status)
        assertEquals(BatchLocalMatchStatus.PENDING, rows.getValue(2).status)
        rows.explicitStart()
        assertTrue(rows.values.all { it.status == BatchLocalMatchStatus.PENDING })
        assertFalse(BatchMatchRetryPolicy.shouldRetryOnStart(BatchLocalMatchStatus.MATCHED))
        assertFalse(BatchMatchRetryPolicy.shouldRetryOnStart(BatchLocalMatchStatus.ALREADY_LINKED))
    }
}
