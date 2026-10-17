package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.feature.localmusic.domain.BatchAssociationSaveStatus
import org.junit.Assert.*
import org.junit.Test

class BatchAssociationGuardTest {
    @Test fun permitsANewSongAndFile() {
        assertNull(batchAssociationGuard(10, "file:new", "new", emptySet(), mapOf(1L to "file:old")) { it })
    }
    @Test fun preservesExplicitExclusionEvenForAnOtherwiseUniqueMatch() {
        assertEquals(BatchAssociationSaveStatus.CONFLICT,
            batchAssociationGuard(10, "file:new", "new", setOf("10"), emptyMap()) { it }?.status)
    }
    @Test fun doesNotReplaceExistingSongWithAnotherFile() {
        assertEquals(BatchAssociationSaveStatus.CONFLICT,
            batchAssociationGuard(10, "file:new", "new", emptySet(), mapOf(10L to "file:old")) { it }?.status)
    }
    @Test fun sameUriAndSongIsAlreadyLinked() {
        assertEquals(BatchAssociationSaveStatus.ALREADY_LINKED,
            batchAssociationGuard(10, "file:same", "same", emptySet(), mapOf(10L to "file:same")) { null }?.status)
    }
    @Test fun differentUrisToSamePhysicalFileAndSongAreAlreadyLinked() {
        assertEquals(BatchAssociationSaveStatus.ALREADY_LINKED,
            batchAssociationGuard(10, "content:media", "inode1", emptySet(), mapOf(10L to "content:saf")) { "inode1" }?.status)
    }
    @Test fun samePhysicalFileCannotBeAutomaticallyAssignedToAnotherSong() {
        assertEquals(BatchAssociationSaveStatus.CONFLICT,
            batchAssociationGuard(10, "content:media", "inode1", emptySet(), mapOf(11L to "content:saf")) { "inode1" }?.status)
    }
    @Test fun unreadableExistingSongStillCannotBeOverwritten() {
        assertEquals(BatchAssociationSaveStatus.CONFLICT,
            batchAssociationGuard(10, "file:new", "new", emptySet(), mapOf(10L to "file:missing")) { null }?.status)
    }
    @Test fun unrelatedExcludedSongDoesNotPreventANewReference() {
        assertNull(batchAssociationGuard(10, "file:new", "new", setOf("11"), emptyMap()) { null })
    }
}
