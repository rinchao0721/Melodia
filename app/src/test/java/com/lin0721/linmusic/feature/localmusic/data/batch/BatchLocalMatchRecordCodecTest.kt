package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchStatus
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class BatchLocalMatchRecordCodecTest {
    private val uri = "content://media/external/audio/media/9"
    private val legacyLocal = """{"mediaStoreId":9,"title":"五味瓶","artist":"测试歌手","durationMs":180000,"sizeBytes":2400000,"uri":"$uri","dateAddedMs":1,"source":"EXTERNAL"}"""
    private val result = """{"query":"五味瓶 测试歌手","status":"MATCHED","matchedTrack":{"id":123,"name":"五味瓶"}}"""

    @Test fun olderRowsMissingOptionalFileFieldsKeepTheirConfirmedResult() {
        val record = requireNotNull(BatchLocalMatchRecordCodec.decode(uri, legacyLocal, result))
        assertEquals(BatchLocalMatchStatus.MATCHED, record.result.status)
        assertEquals(123L, record.result.matchedTrack?.id)
        assertNull(record.local.albumArtist)
        assertNull(record.local.trackNumber)
        assertFalse(record.result.usesExistingSource)
    }

    @Test fun addedFieldsFromNewerVersionsDoNotHideKnownRows() {
        val local = legacyLocal.dropLast(1) + ",\"futureField\":{\"nested\":true}}"
        val newer = result.dropLast(1) + ",\"futureState\":7}"
        assertNotNull(BatchLocalMatchRecordCodec.decode(uri, local, newer))
    }

    @Test fun malformedRowDoesNotPreventReadingFollowingValidRows() {
        val entries = listOf("{truncated" to result, legacyLocal to result, legacyLocal to "not-json")
        val decoded = entries.mapNotNull { (local, status) -> BatchLocalMatchRecordCodec.decode(uri, local, status) }
        assertEquals(1, decoded.size)
        assertEquals(123L, decoded.single().result.matchedTrack?.id)
    }

    @Test fun mismatchedDatabaseKeyCannotTransferAnAssociationToAnotherFile() {
        assertNull(BatchLocalMatchRecordCodec.decode("content://media/external/audio/media/10", legacyLocal, result))
    }

    @Test fun unknownStatusIsUnresolvedInsteadOfBeingReportedAsSuccess() {
        assertNull(BatchLocalMatchRecordCodec.decode(uri, legacyLocal, result.replace("MATCHED", "FUTURE_STATE")))
    }

    @Test fun supportedDataRoundTripsWithoutLosingReviewEvidence() {
        val old = requireNotNull(BatchLocalMatchRecordCodec.decode(uri, legacyLocal, result))
        val expected = old.copy(local = old.local.copy(albumArtist = "合集歌手", trackNumber = 1002, year = 2026),
            result = old.result.copy(status = BatchLocalMatchStatus.NEEDS_CONFIRMATION,
                candidates = listOf(requireNotNull(old.result.matchedTrack)), reason = "需确认版本", updatedAtMs = 99L))
        assertEquals(expected, BatchLocalMatchRecordCodec.decode(uri,
            BatchLocalMatchRecordCodec.json.encodeToString(expected.local),
            BatchLocalMatchRecordCodec.json.encodeToString(expected.result)))
    }
}
