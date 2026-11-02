package com.lin0721.linmusic.feature.localmusic.data.scan

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaStoreAlbumTest {
    @Test
    fun `真实 FLAC 专辑标签与目录同名仍保留`() {
        var reads = 0
        val album = resolveMediaStoreAlbum("50 Classical Masterworks", "/Music/50 Classical Masterworks") {
            reads++
            "50 Classical Masterworks"
        }
        assertEquals("50 Classical Masterworks", album)
        assertEquals(1, reads)
    }

    @Test
    fun `没有嵌入标签的目录占位仍为未知专辑`() {
        assertNull(resolveMediaStoreAlbum("Download", "/storage/emulated/0/Download") { null })
    }

    @Test
    fun `空白或 unknown 的嵌入标签不能确认目录占位`() {
        listOf("", "  ", "<unknown>", " <unknown> ").forEach { embedded ->
            assertNull(resolveMediaStoreAlbum("Music", "/Music") { embedded })
        }
    }

    @Test
    fun `系统尚未更新时使用文件中的真实专辑名`() {
        assertEquals("油漆匠", resolveMediaStoreAlbum("Music", "/Music") { "油漆匠" })
    }

    @Test
    fun `普通系统专辑名不打开文件`() {
        assertEquals("原始人爱空调协会", resolveMediaStoreAlbum(" 原始人爱空调协会 ", "/Music") {
            error("非歧义条目不应读取嵌入标签")
        })
    }

    @Test
    fun `空白或未知系统专辑名不打开文件`() {
        listOf(null, "", " ", "<unknown>").forEach { systemAlbum ->
            assertNull(resolveMediaStoreAlbum(systemAlbum, "/Music") {
                error("无系统专辑名不属于同名歧义")
            })
        }
    }

    @Test
    fun `缺少路径无法认定目录占位时沿用系统结果`() {
        assertEquals("Music", resolveMediaStoreAlbum("Music", null) { error("不应读取") })
    }

    @Test
    fun `确认后的真实标签与系统结果使用相同空白规则`() {
        assertEquals("油漆匠", resolveMediaStoreAlbum(" 油漆匠 ", "/Music/油漆匠") { " 油漆匠 " })
    }

    @Test
    fun `无权限的同名条目保持未知且不中止扫描`() {
        assertNull(resolveMediaStoreAlbum("Music", "/Music") { throw SecurityException("denied") })
    }

    @Test
    fun `损坏或无法读取的同名条目保持未知`() {
        assertNull(resolveMediaStoreAlbum("Music", "/Music") { throw IOException("corrupt") })
    }

    @Test
    fun `native 不可用时退回原有未知专辑规则`() {
        assertNull(resolveMediaStoreAlbum("Music", "/Music") { throw UnsatisfiedLinkError("unavailable") })
    }
}
