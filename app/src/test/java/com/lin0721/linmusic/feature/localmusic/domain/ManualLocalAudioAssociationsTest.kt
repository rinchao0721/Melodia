package com.lin0721.linmusic.feature.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLocalAudioAssociationsTest {
    private fun identity(uri: String): String? = when (uri) {
        "media:1", "saf:1" -> "file:1"
        "media:2" -> "file:2"
        else -> null
    }

    @Test
    fun `readable manual file exposes its confirmed online ID`() {
        assertEquals(mapOf("file:1" to 123L),
            ManualLocalAudioAssociations.uniqueByIdentity(mapOf(123L to "saf:1"), ::identity))
    }

    @Test
    fun `multiple online versions bound to one physical file remain ambiguous`() {
        assertTrue(ManualLocalAudioAssociations.uniqueByIdentity(
            mapOf(123L to "media:1", 456L to "saf:1"), ::identity).isEmpty())
    }

    @Test
    fun `unreadable files and nonpositive IDs do not become cloud associations`() {
        assertTrue(ManualLocalAudioAssociations.uniqueByIdentity(
            mapOf(123L to "missing", -5L to "media:1"), ::identity).isEmpty())
    }

    @Test
    fun `relink removes only old aliases of the selected file`() {
        val links = mapOf(123L to "media:1", 456L to "saf:1", 789L to "media:2", 900L to "missing")
        assertEquals(setOf(123L, 456L), ManualLocalAudioAssociations.idsForIdentity(links, "file:1", ::identity))
        assertEquals(mapOf("file:2" to 789L), ManualLocalAudioAssociations.uniqueByIdentity(
            links.filterKeys { it !in setOf(123L, 456L) }, ::identity))
    }

    @Test
    fun `manual save reuses inspected URI and opens each distinct other reference once`() {
        val calls = mutableListOf<String>()
        val links = mapOf(1L to "media:1", 2L to "saf:1", 3L to "saf:1", 4L to "media:2")
        val removed = ManualLocalAudioAssociations.idsForReadableFile(links, "media:1", "file:1") {
            calls += it
            identity(it)
        }
        assertEquals(setOf(1L, 2L, 3L), removed)
        assertEquals(listOf("saf:1", "media:2"), calls)
    }

    @Test
    fun `unreadable aliases are checked once and retained`() {
        val calls = mutableListOf<String>()
        val removed = ManualLocalAudioAssociations.idsForReadableFile(
            mapOf(1L to "media:1", 2L to "missing", 3L to "missing"), "media:1", "file:1"
        ) { calls += it; identity(it) }
        assertEquals(setOf(1L), removed)
        assertEquals(listOf("missing"), calls)
    }

    @Test
    fun `different saves revalidate changed physical aliases`() {
        var aliasIdentity: String? = "file:1"
        val links = mapOf(1L to "media:1", 2L to "saf:1")
        assertEquals(setOf(1L, 2L), ManualLocalAudioAssociations.idsForReadableFile(links, "media:1", "file:1") { aliasIdentity })
        aliasIdentity = "file:2"
        assertEquals(setOf(1L), ManualLocalAudioAssociations.idsForReadableFile(links, "media:1", "file:1") { aliasIdentity })
    }

    @Test
    fun `indexed title key preserves exact title comparison semantics`() {
        val samples = listOf(" ＦＯＲ  Emma\t", "for emma", "", "  ", "五味瓶", "01 五味瓶", "五味瓶 (Live)")
        samples.forEach { a -> samples.forEach { b ->
            val key = ManualLocalAudioAssociations.titleKey(a)
            assertEquals(PreferredLocalAudioMatcher.sameTitle(a, b), key.isNotEmpty() && key == ManualLocalAudioAssociations.titleKey(b))
        } }
    }

    @Test fun `exact URI references are indexed without file opens and remain ambiguous for multiple IDs`() {
        val result = ManualLocalAudioAssociations.confirmedIdsForFiles(
            mapOf(1L to "media:1", 2L to "media:2", 3L to "media:2"), emptyMap(),
            listOf("media:1" to "One", "media:2" to "Two")
        ) { error("Exact references do not need alias descriptor reads") }
        assertEquals(mapOf("media:1" to 1L, "media:2" to null), result)
    }

    @Test fun `physical alias lookup shares descriptor reads and accepts stripped numeric prefix`() {
        val calls = mutableListOf<String>()
        val result = ManualLocalAudioAssociations.confirmedIdsForFiles(
            mapOf(1L to "saf:1", 2L to "media:2"), mapOf(1L to "01 For Emma", 2L to "Other"),
            listOf("media:1" to "For Emma", "media:1" to "FOR EMMA")
        ) { calls += it; identity(it) }
        assertEquals(mapOf("media:1" to 1L), result)
        assertEquals(listOf("media:1", "saf:1"), calls)
    }

    @Test fun `unrelated title rows do not open any files`() {
        val links = (1L..4_351L).associateWith { "reference:$it" }
        val titles = links.keys.associateWith { "Other song $it" }
        assertEquals(mapOf("selected" to null), ManualLocalAudioAssociations.confirmedIdsForFiles(
            links, titles, listOf("selected" to "For Emma")
        ) { error("Unrelated titles should not incur descriptor I/O") })
    }

    @Test fun `same title multiple versions bound to a physical alias remain unresolved`() {
        assertEquals(mapOf("alias" to null), ManualLocalAudioAssociations.confirmedIdsForFiles(
            mapOf(1L to "media:1", 2L to "saf:1"), mapOf(1L to "For Emma", 2L to "For Emma"),
            listOf("alias" to "For Emma")
        ) { "file:1" })
    }

    @Test fun `concurrent reference changes check only new URIs and preserve redirected links`() {
        val calls = mutableListOf<String>()
        val previous = mapOf(1L to "media:1", 2L to "saf:1", 3L to "media:2")
        val current = mapOf(1L to "media:1", 2L to "media:2", 3L to "media:2", 4L to "saf:1")
        val ids = ManualLocalAudioAssociations.completeReplacementIds(previous, setOf(1L, 2L), current,
            "media:1", "file:1") { calls += it; identity(it) }
        assertEquals(setOf(1L, 4L), ids)
        assertEquals(listOf("media:2", "saf:1"), calls)
    }

    @Test fun `unchanged preflight plan requires no other descriptor reads inside edit`() {
        val links = mapOf(1L to "media:1", 2L to "saf:1", 3L to "media:2")
        assertEquals(setOf(1L, 2L), ManualLocalAudioAssociations.completeReplacementIds(
            links, setOf(1L, 2L), links, "media:1", "file:1"
        ) { error("No reference changed after the fresh preflight") })
    }
}
