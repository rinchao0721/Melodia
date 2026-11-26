package com.lin0721.linmusic.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverImageUrlTest {

    @Test
    fun `空字符串与纯空白返回空串`() {
        assertEquals("", resolveHighResCoverUrl(null))
        assertEquals("", resolveHighResCoverUrl(""))
        assertEquals("", resolveHighResCoverUrl("   \t\n "))
    }

    @Test
    fun `本地 URI 原样返回并去除首尾空白`() {
        assertEquals(
            "content://media/external/audio/albumart/12",
            resolveHighResCoverUrl("  content://media/external/audio/albumart/12  ")
        )
        assertEquals(
            "file:///storage/emulated/0/cover.jpg",
            resolveHighResCoverUrl("file:///storage/emulated/0/cover.jpg")
        )
        assertEquals(
            "android.resource://com.lin0721.linmusic/drawable/cover",
            resolveHighResCoverUrl(" android.resource://com.lin0721.linmusic/drawable/cover ")
        )
    }

    @Test
    fun `网易云移除 param 参数并保留其他查询参数`() {
        assertEquals(
            "https://p1.music.126.net/abc/123.jpg",
            resolveHighResCoverUrl("https://p1.music.126.net/abc/123.jpg")
        )
        assertEquals(
            "https://p1.music.126.net/abc/123.jpg",
            resolveHighResCoverUrl("https://p1.music.126.net/abc/123.jpg?param=400y400")
        )
        assertEquals(
            "https://p1.music.126.net/abc/123.jpg",
            resolveHighResCoverUrl("https://p1.music.126.net/abc/123.jpg?param=800y800")
        )
        assertEquals(
            "https://p1.music.126.net/abc/123.jpg?foo=1",
            resolveHighResCoverUrl("https://p1.music.126.net/abc/123.jpg?foo=1&param=300y300")
        )
        assertEquals(
            "https://p1.music.126.net/abc/123.jpg?foo=1&bar=2",
            resolveHighResCoverUrl("https://p1.music.126.net/abc/123.jpg?foo=1&param=400y400&bar=2")
        )
        assertEquals(
            "https://p1.music.126.net/abc/123.jpg?bar=2",
            resolveHighResCoverUrl("https://p1.music.126.net/abc/123.jpg?param=400y400&bar=2")
        )
    }

    @Test
    fun `酷狗替换 size 占位符与常见小尺寸路径为 1080`() {
        assertEquals(
            "https://imge.kugou.com/stdmusic/1080/20240101/cover.jpg",
            resolveHighResCoverUrl("https://imge.kugou.com/stdmusic/{size}/20240101/cover.jpg")
        )
        assertEquals(
            "https://imge.kugou.com/stdmusic/1080/20240101/cover.jpg",
            resolveHighResCoverUrl("https://imge.kugou.com/stdmusic/400/20240101/cover.jpg")
        )
        assertEquals(
            "https://imge.kugou.com/stdmusic/1080/20240101/cover.jpg",
            resolveHighResCoverUrl("https://imge.kugou.com/stdmusic/150/20240101/cover.jpg")
        )
        assertEquals(
            "https://imge.kugou.com/stdmusic/1080/20240101/cover.jpg",
            resolveHighResCoverUrl("https://imge.kugou.com/stdmusic/240/20240101/cover.jpg")
        )
    }

    @Test
    fun `QQ 音乐提升封面尺寸到 800x800`() {
        assertEquals(
            "https://y.gtimg.cn/music/photo_new/T002R800x800M000001abc.jpg",
            resolveHighResCoverUrl("https://y.gtimg.cn/music/photo_new/T002R300x300M000001abc.jpg")
        )
        assertEquals(
            "https://y.gtimg.cn/music/photo_new/T002R800x800M000001abc.jpg",
            resolveHighResCoverUrl("https://y.gtimg.cn/music/photo_new/T002R500x500M000001abc.jpg")
        )
        assertEquals(
            "https://y.gtimg.cn/music/photo_new/T002R800x800M000001abc.jpg",
            resolveHighResCoverUrl("https://y.gtimg.cn/music/photo_new/T002R150x150M000001abc.jpg")
        )
    }

    @Test
    fun `文件名清洗处理非法字符、超长截断与空值回退`() {
        assertEquals("Melodia_cover", sanitizeCoverFileName(null))
        assertEquals("Melodia_cover", sanitizeCoverFileName(""))
        assertEquals("Melodia_cover", sanitizeCoverFileName("   ///:::*?\"<>|   "))
        assertEquals("晴天_周杰伦", sanitizeCoverFileName("  晴天 / 周杰伦:*?\"<>|  "))

        val longTitle = "a".repeat(55)
        val sanitizedLong = sanitizeCoverFileName(longTitle)
        assertEquals(40, sanitizedLong.length)
        assertEquals("a".repeat(40), sanitizedLong)
    }

    @Test
    fun `CoverPreviewBounds 校验有效尺寸与中心坐标`() {
        val valid = CoverPreviewBounds(left = 10f, top = 20f, width = 100f, height = 200f)
        assertTrue(valid.isValid)
        assertEquals(60f, valid.centerX)
        assertEquals(120f, valid.centerY)

        assertTrue(CoverPreviewBounds(left = 0f, top = 0f, width = 1.01f, height = 1.01f).isValid)
        assertFalse(CoverPreviewBounds(left = 0f, top = 0f, width = 1f, height = 100f).isValid)
        assertFalse(CoverPreviewBounds(left = 0f, top = 0f, width = 100f, height = 1f).isValid)
        assertFalse(CoverPreviewBounds(left = 0f, top = 0f, width = 0f, height = 0f).isValid)
        assertFalse(CoverPreviewBounds(left = 0f, top = 0f, width = -10f, height = 50f).isValid)
    }

    @Test
    fun `CoverPreviewRequest 默认值与 sourceBounds 赋值`() {
        val defaultRequest = CoverPreviewRequest(url = "https://example.com/a.jpg")
        assertEquals("https://example.com/a.jpg", defaultRequest.url)
        assertEquals("", defaultRequest.title)
        assertNull(defaultRequest.sourceBounds)

        val bounds = CoverPreviewBounds(left = 12f, top = 34f, width = 80f, height = 80f)
        val withBounds = CoverPreviewRequest(
            url = "https://example.com/b.jpg",
            title = "封面",
            sourceBounds = bounds
        )
        assertEquals("https://example.com/b.jpg", withBounds.url)
        assertEquals("封面", withBounds.title)
        assertEquals(bounds, withBounds.sourceBounds)
    }
}
