package com.lin0721.linmusic.feature.localmusic.data.batch

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BatchMatchRunnerTest {
    private data class Row(val id: Int, val status: Int = 0)
    private class FakeStore(initial: List<Row>) {
        val rows = initial.associateBy { it.id }.toMutableMap()
        val writes = mutableListOf<Row>()
        fun pending() = rows.values.firstOrNull { it.status == 0 }
        fun commit(row: Row) { rows[row.id] = row; writes += row }
    }

    @Test fun completedAndAlreadyLinkedRowsAreNeverQueriedAgain() = runTest {
        val store = FakeStore(listOf(Row(1, 1), Row(2, 3), Row(3), Row(4)))
        val queried = mutableListOf<Int>()
        BatchMatchRunner(store::pending, { row -> queried += row.id; BatchRunnerStep(row.copy(status = 1)) }, store::commit).run()
        assertEquals(listOf(3, 4), queried)
        assertEquals(2, store.writes.size)
    }

    @Test fun networkFailureIsCommittedAndRemainingRowsStayPending() = runTest {
        val store = FakeStore(listOf(Row(1), Row(2), Row(3)))
        val queried = mutableListOf<Int>()
        val message = BatchMatchRunner(store::pending, { row ->
            queried += row.id
            if (row.id == 2) BatchRunnerStep(row.copy(status = 2), halt = true, message = "429")
            else BatchRunnerStep(row.copy(status = 1))
        }, store::commit).run()
        assertEquals("429", message)
        assertEquals(listOf(1, 2), queried)
        assertEquals(0, store.rows.getValue(3).status)
        assertEquals(2, store.rows.getValue(2).status)
    }

    @Test fun explicitResumeRetriesOnlyFailedAndUnprocessedRows() = runTest {
        val store = FakeStore(listOf(Row(1, 1), Row(2, 2), Row(3)))
        store.rows[2] = Row(2) // explicit user resume resets ERROR
        val queried = mutableListOf<Int>()
        BatchMatchRunner(store::pending, { row -> queried += row.id; BatchRunnerStep(row.copy(status = 1)) }, store::commit).run()
        assertEquals(listOf(2, 3), queried)
        assertEquals(1, store.rows.getValue(1).status)
    }

    @Test fun requestsAreSerialAndSeparatedByAtLeastTwoSeconds() = runTest {
        val store = FakeStore(listOf(Row(1), Row(2), Row(3)))
        val startTimes = mutableListOf<Long>()
        var concurrent = 0
        BatchMatchRunner(store::pending, { row ->
            concurrent++
            assertEquals(1, concurrent)
            startTimes += testScheduler.currentTime
            delay(100)
            concurrent--
            BatchRunnerStep(row.copy(status = 1))
        }, store::commit).run()
        assertEquals(listOf(0L, 2_100L, 4_200L), startTimes)
    }

    @Test fun pauseDuringDelayDoesNotConsumeNextPendingRow() = runTest {
        val store = FakeStore(listOf(Row(1), Row(2)))
        val queried = mutableListOf<Int>()
        val task = launch {
            BatchMatchRunner(store::pending, { row -> queried += row.id; BatchRunnerStep(row.copy(status = 1)) }, store::commit).run()
        }
        runCurrent()
        task.cancelAndJoin()
        assertEquals(listOf(1), queried)
        assertEquals(0, store.rows.getValue(2).status)
    }

    @Test fun pauseDuringSearchLeavesThatRowPending() = runTest {
        val store = FakeStore(listOf(Row(1), Row(2)))
        val task = launch {
            BatchMatchRunner(store::pending, { row -> delay(10_000); BatchRunnerStep(row.copy(status = 1)) }, store::commit).run()
        }
        runCurrent()
        advanceTimeBy(100)
        task.cancelAndJoin()
        assertTrue(store.rows.values.all { it.status == 0 })
        assertTrue(store.writes.isEmpty())
    }

    @Test fun durableCompletedResultsSurviveNewRunnerInstance() = runTest {
        val original = FakeStore(listOf(Row(1), Row(2), Row(3)))
        BatchMatchRunner(original::pending, { row -> BatchRunnerStep(row.copy(status = 1), halt = true) }, original::commit).run()
        val restored = FakeStore(original.rows.values.toList())
        val queried = mutableListOf<Int>()
        BatchMatchRunner(restored::pending, { row -> queried += row.id; BatchRunnerStep(row.copy(status = 1)) }, restored::commit).run()
        assertEquals(listOf(2, 3), queried)
        assertEquals(1, restored.rows.getValue(1).status)
    }

    @Test fun emptyQueueMakesNoRequestsOrWrites() = runTest {
        val store = FakeStore(emptyList())
        val result = BatchMatchRunner(store::pending, { _: Row -> fail("Unexpected request"); BatchRunnerStep(Row(1)) }, store::commit).run()
        assertNull(result)
        assertTrue(store.writes.isEmpty())
    }
}
