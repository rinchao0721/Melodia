package com.lin0721.linmusic.feature.localmusic.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ManualAssociationCommitTest {
    @Test fun unreadableFileDoesNotUpdateReport() = runTest {
        var links = 0
        var reports = 0
        val result = commitManualAssociation(link = { links++; false }, updateReport = { reports++ })
        assertEquals(ManualAssociationCommitResult.Unreadable, result)
        assertEquals(1, links)
        assertEquals(0, reports)
    }

    @Test fun successfulReferenceAndReportAreWrittenOnceInOrder() = runTest {
        val writes = mutableListOf<String>()
        val result = commitManualAssociation(
            link = { writes += "reference"; true },
            updateReport = { writes += "report" }
        )
        assertEquals(ManualAssociationCommitResult.Saved(), result)
        assertEquals(listOf("reference", "report"), writes)
    }

    @Test fun reportFailureStillReturnsSavedWithoutRepeatingReferenceWrite() = runTest {
        var links = 0
        var reports = 0
        val diskFailure = IllegalStateException("report storage unavailable")
        val result = commitManualAssociation(
            link = { links++; true },
            updateReport = { reports++; throw diskFailure }
        )
        assertTrue(result is ManualAssociationCommitResult.Saved)
        assertSame(diskFailure, (result as ManualAssociationCommitResult.Saved).reportError)
        assertEquals(1, links)
        assertEquals(1, reports)
    }

    @Test fun completionWaitsUntilReportUpdateFinishes() = runTest {
        val releaseReport = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val completion = async {
            commitManualAssociation(
                link = { writes += "reference"; true },
                updateReport = { writes += "report started"; releaseReport.await(); writes += "report saved" }
            )
        }
        runCurrent()
        assertFalse(completion.isCompleted)
        assertEquals(listOf("reference", "report started"), writes)
        releaseReport.complete(Unit)
        assertEquals(ManualAssociationCommitResult.Saved(), completion.await())
        assertEquals(listOf("reference", "report started", "report saved"), writes)
    }

    @Test fun referenceWriteFailurePropagatesWithoutUpdatingReport() = runTest {
        val diskFailure = IllegalStateException("reference storage unavailable")
        var reports = 0
        try {
            commitManualAssociation(link = { throw diskFailure }, updateReport = { reports++ })
            fail("A failed reference write must not be reported as saved")
        } catch (error: IllegalStateException) {
            assertSame(diskFailure, error)
        }
        assertEquals(0, reports)
    }

    @Test fun cancellationDuringReferenceWritePropagatesWithoutUpdatingReport() = runTest {
        val cancellation = CancellationException("reference operation cancelled")
        var reports = 0
        try {
            commitManualAssociation(link = { throw cancellation }, updateReport = { reports++ })
            fail("Cancellation must propagate")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
        assertEquals(0, reports)
    }

    @Test fun reportCancellationIsNotConvertedToASavedWarning() = runTest {
        val cancellation = CancellationException("report operation cancelled")
        try {
            commitManualAssociation(link = { true }, updateReport = { throw cancellation })
            fail("Cancellation must propagate")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }

    @Test fun cancellingPendingReportNeverPublishesCompletion() = runTest {
        val releaseReport = CompletableDeferred<Unit>()
        var links = 0
        var completionPublished = false
        var reportCancelled = false
        val operation = launch {
            commitManualAssociation(link = { links++; true }, updateReport = {
                try {
                    releaseReport.await()
                } finally {
                    reportCancelled = true
                }
            })
            completionPublished = true
        }
        runCurrent()
        operation.cancelAndJoin()
        assertTrue(operation.isCancelled)
        assertTrue(reportCancelled)
        assertFalse(completionPublished)
        assertEquals(1, links)
    }
}
