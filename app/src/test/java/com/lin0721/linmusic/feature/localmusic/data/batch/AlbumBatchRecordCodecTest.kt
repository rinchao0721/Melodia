package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AlbumBatchRecordCodecTest {
    private val album = Album(1535592L, "For Emma, Forever Ago", "cover", listOf(Artist(29337L, "Bon Iver")))
    private fun record(key: String = "For Emma, Forever Ago|Bon Iver") = AlbumBatchRecord(
        localAlbumKey = key, fingerprint = "stable-full-library-fingerprint", status = AlbumBatchStatus.REVIEW,
        candidates = listOf(album), selected = album, reason = "歌曲有待核对", linkedSongs = 7,
        totalSongs = 9, updatedAtMs = 1_792_000_000_000L,
    )

    private fun entry(record: AlbumBatchRecord) =
        AlbumBatchRecordCodec.keyFor(record.localAlbumKey) to AlbumBatchRecordCodec.encode(record)

    @Test
    fun `settled report roundtrip preserves candidates selection and progress`() {
        val original = record()
        assertEquals(mapOf(original.localAlbumKey to original), AlbumBatchRecordCodec.decodeEntries(mapOf(entry(original))))
    }

    @Test
    fun `every persisted status is a settled outcome and survives restart decode`() {
        assertEquals(setOf("UNPROCESSED", "REVIEW", "MATCHED", "NO_MATCH", "ERROR"),
            AlbumBatchStatus.entries.map { it.name }.toSet())
        AlbumBatchStatus.entries.forEach { status ->
            val original = record().copy(status = status)
            assertEquals(original, AlbumBatchRecordCodec.decodeEntries(mapOf(entry(original)))[original.localAlbumKey])
        }
    }

    @Test
    fun `malformed record or unknown future status does not erase other albums`() {
        val original = record()
        val future = AlbumBatchRecordCodec.encode(record("future")).replace("\"REVIEW\"", "\"SEARCHING\"")
        val entries = mapOf(entry(original),
            AlbumBatchRecordCodec.keyFor("future") to future,
            AlbumBatchRecordCodec.keyFor("broken") to "{broken")
        assertEquals(mapOf(original.localAlbumKey to original), AlbumBatchRecordCodec.decodeEntries(entries))
    }

    @Test
    fun `unknown fields are accepted without changing a saved result`() {
        val original = record()
        val encoded = AlbumBatchRecordCodec.encode(original).replaceFirst("{", "{\"futureSetting\":true,")
        assertEquals(mapOf(original.localAlbumKey to original), AlbumBatchRecordCodec.decodeEntries(
            mapOf(AlbumBatchRecordCodec.keyFor(original.localAlbumKey) to encoded)))
    }

    @Test
    fun `entry key must correspond to original album key inside the payload`() {
        val original = record()
        assertTrue(AlbumBatchRecordCodec.decodeEntries(mapOf(
            AlbumBatchRecordCodec.keyFor("wrong album") to AlbumBatchRecordCodec.encode(original),
            "unrelated_preference" to AlbumBatchRecordCodec.encode(original),
        )).isEmpty())
        val keys = listOf("artist/album", "artist\\album", "artist:album", "\"},{\"x", "专辑🎵")
        assertEquals(keys.size, keys.map(AlbumBatchRecordCodec::keyFor).toSet().size)
    }

    @Test
    fun `invalid counts or identity are rejected before replacing the previous result`() {
        val original = record()
        val invalid = listOf(original.copy(localAlbumKey = ""), original.copy(fingerprint = " "),
            original.copy(totalSongs = -1), original.copy(linkedSongs = -1),
            original.copy(linkedSongs = 10), original.copy(updatedAtMs = -1),
            original.copy(candidates = listOf(Album(id = 0L))), original.copy(selected = Album(id = -1L)))
        invalid.forEach { value ->
            try {
                AlbumBatchRecordCodec.encode(value)
                fail("Invalid replacement must be rejected before persistence")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun `invalid stored counts are isolated from otherwise valid records`() {
        val original = record()
        val invalid = record("invalid").copy(linkedSongs = 99)
        assertEquals(mapOf(original.localAlbumKey to original), AlbumBatchRecordCodec.decodeEntries(mapOf(
            entry(original), AlbumBatchRecordCodec.keyFor(invalid.localAlbumKey) to Json.encodeToString(invalid))))
    }

    @Test
    fun `fingerprint ignores object insertion order including nested track metadata`() {
        val a = Json.parseToJsonElement("""{"album":"专辑","tracks":[{"title":"Song","duration":180000}],"artist":"Singer"}""")
        val b = Json.parseToJsonElement("""{"artist":"Singer","tracks":[{"duration":180000,"title":"Song"}],"album":"专辑"}""")
        assertEquals(AlbumBatchFingerprint.of(a), AlbumBatchFingerprint.of(b))
        assertTrue(AlbumBatchFingerprint.of(a).matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `fingerprint changes for any track metadata change or track order change`() {
        val a = Json.parseToJsonElement("""{"tracks":[{"title":"A","duration":180000},{"title":"B","duration":200000}]}""")
        val durationChanged = Json.parseToJsonElement("""{"tracks":[{"title":"A","duration":180001},{"title":"B","duration":200000}]}""")
        val orderChanged = Json.parseToJsonElement("""{"tracks":[{"title":"B","duration":200000},{"title":"A","duration":180000}]}""")
        assertNotEquals(AlbumBatchFingerprint.of(a), AlbumBatchFingerprint.of(durationChanged))
        assertNotEquals(AlbumBatchFingerprint.of(a), AlbumBatchFingerprint.of(orderChanged))
    }
}
