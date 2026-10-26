package com.lin0721.linmusic.feature.localmusic.data

import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.data.scan.resolveArtistAndTitle
import com.lin0721.linmusic.feature.localmusic.data.scan.resolveMediaStoreAlbum
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibrarySyncTest {

    private fun entity(
        uri: String,
        title: String = "歌曲",
        source: LocalTrackSource = LocalTrackSource.EXTERNAL,
        modifiedMs: Long = 1L
    ) = LocalTrackEntity(
        uri = uri,
        mediaStoreId = null,
        songId = null,
        title = title,
        artist = "歌手",
        album = null,
        durationMs = 1000L,
        sizeBytes = 1L,
        path = null,
        dateAddedMs = 1L,
        dateModifiedMs = modifiedMs,
        source = source.name
    )

    // ======================= 增量同步 =======================

    @Test
    fun `未变化的条目不重复写入`() {
        val a = entity("a")
        val diff = computeLocalLibrarySyncDiff(listOf(a), listOf(a)) { true }
        assertTrue(diff.upserts.isEmpty())
        assertTrue(diff.deleteUris.isEmpty())
    }

    @Test
    fun `新增与修改过的条目写入`() {
        val old = entity("a", modifiedMs = 1L)
        val changed = entity("a", title = "新标题", modifiedMs = 2L)
        val added = entity("b")
        val diff = computeLocalLibrarySyncDiff(listOf(old), listOf(changed, added)) { true }
        assertEquals(listOf(changed, added), diff.upserts)
        assertTrue(diff.deleteUris.isEmpty())
    }

    @Test
    fun `MediaStore 扫描不到的条目删除`() {
        val gone = entity("gone")
        val diff = computeLocalLibrarySyncDiff(listOf(gone), emptyList()) { true }
        assertEquals(setOf("gone"), diff.deleteUris)
    }

    @Test
    fun `导入条目只在文件不可读时删除`() {
        val alive = entity("alive", source = LocalTrackSource.IMPORTED)
        val dead = entity("dead", source = LocalTrackSource.IMPORTED)
        val diff = computeLocalLibrarySyncDiff(listOf(alive, dead), emptyList()) { it.uri == "alive" }
        assertEquals(setOf("dead"), diff.deleteUris)
    }

    @Test
    fun `导入条目被 MediaStore 收录后以扫描结果覆盖`() {
        val imported = entity("same", source = LocalTrackSource.IMPORTED)
        val scanned = entity("same", source = LocalTrackSource.EXTERNAL)
        val diff = computeLocalLibrarySyncDiff(listOf(imported), listOf(scanned)) { false }
        assertEquals(listOf(scanned), diff.upserts)
        assertTrue(diff.deleteUris.isEmpty())
    }

    @Test
    fun `旧目录占位在重扫后更新即使文件修改日期未变`() {
        val old = entity("a").copy(album = "Download", path = "/Download/track.flac")
        val scanned = old.copy(album = resolveMediaStoreAlbum(old.album, "/Download") { null })
        val diff = computeLocalLibrarySyncDiff(listOf(old), listOf(scanned)) { true }
        assertEquals(listOf(old.copy(album = null)), diff.upserts)
        assertTrue(diff.deleteUris.isEmpty())
        assertEquals(old.dateModifiedMs, diff.upserts.single().dateModifiedMs)
    }

    @Test
    fun `重扫确认真实同名标签后结果稳定且不重复写入`() {
        val old = entity("a").copy(album = "50 Classical Masterworks", path = "/Music/50 Classical Masterworks/01.flac")
        val scanned = old.copy(album = resolveMediaStoreAlbum(old.album, "/Music/50 Classical Masterworks") { old.album })
        val diff = computeLocalLibrarySyncDiff(listOf(old), listOf(scanned)) { true }
        assertTrue(diff.upserts.isEmpty())
        assertTrue(diff.deleteUris.isEmpty())
    }

    // ======================= 歌手/标题拆分 =======================

    @Test
    fun `歌手缺失时从标题拆出歌手`() {
        assertEquals("茶太" to "Winter Bells", resolveArtistAndTitle("<unknown>", "茶太 - Winter Bells"))
    }

    @Test
    fun `歌手存在时不拆标题`() {
        assertEquals("茶太" to "A - B", resolveArtistAndTitle("茶太", "A - B"))
    }

    @Test
    fun `歌手缺失且标题不含分隔符时用未知艺术家`() {
        assertEquals("未知艺术家" to "Winter Bells", resolveArtistAndTitle("", "Winter Bells"))
    }
}
