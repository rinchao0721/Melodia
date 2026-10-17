package com.lin0721.linmusic.feature.localmusic.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AssociationBackupCodecTest {
    private fun record(
        songId: Long = 123L,
        uri: String = "content://old-device/music/7",
    ) = AssociationBackupRecord(
        online = AssociationOnlineSong(
            songId = songId,
            title = "Hello (Live)",
            artist = "Singer A",
            album = "Live Album",
            durationMs = 180_000L,
        ),
        local = AssociationLocalFile(
            uri = uri,
            fileName = "Hello (Live).flac",
            title = "Hello (Live)",
            artist = "Singer A",
            album = "Live Album",
            durationMs = 180_000L,
            sizeBytes = 7_000_000L,
        ),
    )

    private fun candidate(
        uri: String = "content://new-device/music/42",
        identity: String = "file:42",
    ) = AssociationLocalCandidate(
        uri = uri,
        identity = identity,
        fileName = "Hello (Live).flac",
        title = "Hello (Live)",
        artist = "Singer A",
        album = "Live Album",
        durationMs = 180_000L,
        sizeBytes = 7_000_000L,
    )

    private fun preview(
        records: List<AssociationBackupRecord> = listOf(record()),
        candidates: List<AssociationLocalCandidate> = listOf(candidate()),
        existing: Map<Long, String> = emptyMap(),
        aliases: Map<String, String> = emptyMap(),
    ): AssociationImportPreview = AssociationImportPlanner.preview(
        records = records,
        candidates = candidates,
        existing = existing,
        identityOfUri = { uri ->
            aliases[uri] ?: candidates.firstOrNull { it.uri == uri }?.identity
        },
    )

    private fun expectRejected(action: () -> Unit) {
        try {
            action()
            fail("Expected an invalid backup to be rejected")
        } catch (_: IllegalArgumentException) {
            // kotlinx.serialization and schema validation use this exception family.
        }
    }

    @Test
    fun `backup roundtrip preserves local evidence and export timestamp`() {
        val backup = AssociationBackup(exportedAtMs = 1_729_000_000_000L, records = listOf(record()))

        assertEquals(backup, AssociationBackupCodec.decode(AssociationBackupCodec.encode(backup)))
    }

    @Test
    fun `unreadable export record remains in the backup without lost metadata`() {
        val source = record().let {
            it.copy(local = it.local.copy(
                readableAtExport = false,
                exportIssue = "文件授权失效，保留原关联",
                sizeBytes = null,
                durationMs = null,
            ))
        }
        val backup = AssociationBackup(records = listOf(source))

        assertEquals(backup, AssociationBackupCodec.decode(AssociationBackupCodec.encode(backup)))
    }

    @Test
    fun `unknown format and schema version are rejected`() {
        val backup = AssociationBackup(records = listOf(record()))

        expectRejected { AssociationBackupCodec.encode(backup.copy(format = "unrelated-format")) }
        expectRejected { AssociationBackupCodec.encode(backup.copy(schemaVersion = 2)) }
        expectRejected {
            AssociationBackupCodec.decode("""{"format":"unrelated-format","records":[]}""")
        }
        expectRejected {
            AssociationBackupCodec.decode("""{"schemaVersion":2,"records":[]}""")
        }
    }

    @Test
    fun `unknown JSON fields and malformed JSON are rejected`() {
        expectRejected {
            AssociationBackupCodec.decode("""{"records":[],"unexpected":true}""")
        }
        expectRejected { AssociationBackupCodec.decode("{not-json}") }
    }

    @Test
    fun `duplicate online song IDs are rejected on export and import`() {
        val backup = AssociationBackup(records = listOf(record(), record(uri = "content://other/9")))
        expectRejected { AssociationBackupCodec.encode(backup) }

        expectRejected {
            AssociationBackupCodec.decode("""{"records":[{"online":{"songId":123},"local":{"uri":"content://file/1"}},{"online":{"songId":123},"local":{"uri":"content://file/2"}}]}""")
        }
    }

    @Test
    fun `nonpositive song IDs are rejected`() {
        for (invalidId in listOf(0L, -1L)) {
            expectRejected {
                AssociationBackupCodec.encode(AssociationBackup(records = listOf(record(songId = invalidId))))
            }
        }
    }

    @Test
    fun `negative durations and size are rejected`() {
        val source = record()
        val invalidRecords = listOf(
            source.copy(online = source.online.copy(durationMs = -1L)),
            source.copy(local = source.local.copy(durationMs = -1L)),
            source.copy(local = source.local.copy(sizeBytes = -1L)),
        )

        for (invalid in invalidRecords) {
            expectRejected { AssociationBackupCodec.encode(AssociationBackup(records = listOf(invalid))) }
        }
    }

    @Test
    fun `oversized metadata and more than ten thousand records are rejected`() {
        val source = record()
        expectRejected {
            AssociationBackupCodec.encode(AssociationBackup(records = listOf(
                source.copy(local = source.local.copy(title = "x".repeat(1_048_576))),
            )))
        }
        expectRejected {
            AssociationBackupCodec.encode(AssociationBackup(records =
                (1L..10_001L).map { record(songId = it) },
            ))
        }
    }

    @Test
    fun `input larger than twenty MiB is rejected before JSON parsing`() {
        val validJson = AssociationBackupCodec.encode(AssociationBackup(records = emptyList()))
        val oversizedValidJson = validJson + " ".repeat(20 * 1024 * 1024 + 1 - validJson.length)

        expectRejected { AssociationBackupCodec.decode(oversizedValidJson) }
    }

    @Test
    fun `unique complete local evidence can resolve a new device path`() {
        val result = preview()

        assertEquals("NEW", result.rows.single().status.name)
        assertEquals(candidate().uri, result.rows.single().resolvedUri)
        assertEquals(1, result.newCount)
        assertEquals(0, result.sameCount)
        assertEquals(0, result.conflictCount)
        assertEquals(0, result.unresolvedCount)
    }

    @Test
    fun `matching relies on backed up local evidence instead of online song metadata`() {
        val source = record().let {
            it.copy(online = it.online.copy(title = "Another name", artist = "Another artist", album = null))
        }

        assertEquals("NEW", preview(records = listOf(source)).rows.single().status.name)
    }

    @Test
    fun `reused URI does not override a unique complete file evidence match`() {
        val source = record()
        val changedAtOldUri = candidate(uri = source.local.uri, identity = "file:old")
            .copy(sizeBytes = 6_000_000L)
        val result = preview(candidates = listOf(changedAtOldUri, candidate()))

        assertEquals("NEW", result.rows.single().status.name)
        assertEquals(candidate().uri, result.rows.single().resolvedUri)
        assertEquals(1, result.newCount)

        val noCompleteMatch = preview(candidates = listOf(changedAtOldUri))
        assertEquals("UNRESOLVED", noCompleteMatch.rows.single().status.name)
        assertNull(noCompleteMatch.rows.single().resolvedUri)
        assertEquals(1, noCompleteMatch.unresolvedCount)
    }

    @Test
    fun `same URI still requires complete filename title artist duration and size evidence`() {
        val source = record()
        val incompleteEvidence = listOf(
            source.local.copy(fileName = null),
            source.local.copy(title = ""),
            source.local.copy(artist = " "),
            source.local.copy(durationMs = null),
            source.local.copy(durationMs = 0L),
            source.local.copy(sizeBytes = null),
            source.local.copy(sizeBytes = 0L),
        )

        for (local in incompleteEvidence) {
            val result = preview(
                records = listOf(source.copy(local = local)),
                candidates = listOf(candidate(uri = source.local.uri)),
            )
            assertEquals("UNRESOLVED", result.rows.single().status.name)
            assertNull(result.rows.single().resolvedUri)
        }
    }

    @Test
    fun `online information cannot fill missing local metadata during migration`() {
        val source = record().let {
            it.copy(local = it.local.copy(title = null, artist = null))
        }

        assertEquals("UNRESOLVED", preview(records = listOf(source)).rows.single().status.name)
    }

    @Test
    fun `different physical files with identical evidence remain ambiguous`() {
        val result = preview(candidates = listOf(
            candidate(uri = "content://new/music/42", identity = "file:42"),
            candidate(uri = "content://new/music/43", identity = "file:43"),
        ))

        assertEquals("UNRESOLVED", result.rows.single().status.name)
        assertNull(result.rows.single().resolvedUri)
    }

    @Test
    fun `reused same URI cannot disambiguate two physical files with identical evidence`() {
        val source = record()
        val result = preview(candidates = listOf(
            candidate(uri = source.local.uri, identity = "file:old"),
            candidate(uri = "content://new/music/43", identity = "file:43"),
        ))

        // Backups have no trusted device identity; the same MediaStore numeric ID on
        // another phone can refer to either recording/copy. Require a unique file.
        assertEquals("UNRESOLVED", result.rows.single().status.name)
        assertNull(result.rows.single().resolvedUri)
        assertEquals(1, result.unresolvedCount)
    }

    @Test
    fun `MediaStore and SAF aliases of one physical file do not create ambiguity`() {
        val result = preview(candidates = listOf(
            candidate(uri = "content://media/audio/42", identity = "file:42"),
            candidate(uri = "content://saf/document/42", identity = "file:42"),
        ))

        assertEquals("NEW", result.rows.single().status.name)
        assertTrue(result.rows.single().resolvedUri in setOf(
            "content://media/audio/42", "content://saf/document/42",
        ))
    }

    @Test
    fun `existing different file keeps its association and reports conflict`() {
        val existing = linkedMapOf(123L to "content://existing/music/9")
        val before = existing.toMap()
        val result = preview(existing = existing, aliases = mapOf("content://existing/music/9" to "file:9"))

        assertEquals("CONFLICT", result.rows.single().status.name)
        assertEquals(1, result.conflictCount)
        assertEquals(0, result.newCount)
        assertEquals(before, existing)
    }

    @Test
    fun `same song ID and physical file are idempotent across URI aliases`() {
        val result = preview(
            existing = mapOf(123L to "content://saf/document/42"),
            aliases = mapOf("content://saf/document/42" to "file:42"),
        )

        assertEquals("SAME", result.rows.single().status.name)
        assertEquals(1, result.sameCount)
        assertEquals(0, result.newCount)
        assertEquals(0, result.conflictCount)
    }

    @Test
    fun `a physical file already associated with another song ID reports conflict`() {
        val result = preview(
            existing = mapOf(456L to "content://saf/document/42"),
            aliases = mapOf("content://saf/document/42" to "file:42"),
        )

        assertEquals("CONFLICT", result.rows.single().status.name)
        assertEquals(0, result.newCount)
    }

    @Test
    fun `two new song IDs for the same physical file conflict for both rows`() {
        val result = preview(records = listOf(record(songId = 123L), record(songId = 456L)))

        assertEquals(listOf("CONFLICT", "CONFLICT"), result.rows.map { it.status.name })
        assertEquals(2, result.conflictCount)
        assertEquals(0, result.newCount)
    }

    @Test
    fun `unreadable export rows stay unresolved even with unique matching evidence`() {
        val source = record().let {
            it.copy(local = it.local.copy(readableAtExport = false, exportIssue = "Permission lost"))
        }
        val result = preview(records = listOf(source))

        assertEquals("UNRESOLVED", result.rows.single().status.name)
        assertNull(result.rows.single().resolvedUri)
        assertEquals(1, result.unresolvedCount)
    }

    @Test
    fun `normalized fullwidth case and whitespace variations preserve complete evidence`() {
        val source = record().let {
            it.copy(local = it.local.copy(
                fileName = "  Ｈｅｌｌｏ   (Ｌｉｖｅ).ＦＬＡＣ  ",
                title = "  Ｈｅｌｌｏ   (Ｌｉｖｅ)  ",
                artist = "  Ｓｉｎｇｅｒ   Ａ  ",
                album = "  Ｌｉｖｅ   Ａｌｂｕｍ  ",
            ))
        }

        assertEquals("NEW", preview(records = listOf(source)).rows.single().status.name)
    }

    @Test
    fun `album disagreement including one missing album stays unresolved`() {
        val source = record()
        for (album in listOf(null, "", "Studio Album")) {
            val result = preview(records = listOf(source.copy(local = source.local.copy(album = album))))
            assertEquals("UNRESOLVED", result.rows.single().status.name)
        }
    }

    @Test
    fun `albums missing on both sides can still match complete required evidence`() {
        val source = record().let { it.copy(local = it.local.copy(album = null)) }
        val result = preview(records = listOf(source), candidates = listOf(candidate().copy(album = "  ")))

        assertEquals("NEW", result.rows.single().status.name)
    }

    @Test
    fun `version suffixes and file size changes prevent a migration match`() {
        val differentFiles = listOf(
            candidate().copy(fileName = "Hello.flac"),
            candidate().copy(title = "Hello"),
            candidate().copy(artist = "Singer A, Singer B"),
            candidate().copy(sizeBytes = 7_000_001L),
        )

        for (other in differentFiles) {
            assertEquals("UNRESOLVED", preview(candidates = listOf(other)).rows.single().status.name)
        }
    }

    @Test
    fun `duration tolerance includes one second and excludes larger differences`() {
        assertEquals("NEW", preview(candidates = listOf(candidate().copy(durationMs = 181_000L)))
            .rows.single().status.name)
        assertEquals("NEW", preview(candidates = listOf(candidate().copy(durationMs = 179_000L)))
            .rows.single().status.name)
        assertEquals("UNRESOLVED", preview(candidates = listOf(candidate().copy(durationMs = 181_001L)))
            .rows.single().status.name)
        assertEquals("UNRESOLVED", preview(candidates = listOf(candidate().copy(durationMs = 178_999L)))
            .rows.single().status.name)
    }

    @Test
    fun `missing candidate does not discard its backup row`() {
        val result = preview(candidates = emptyList())

        assertEquals(1, result.rows.size)
        assertEquals("UNRESOLVED", result.rows.single().status.name)
        assertEquals(1, result.unresolvedCount)
        assertNull(result.rows.single().resolvedUri)
    }
}
