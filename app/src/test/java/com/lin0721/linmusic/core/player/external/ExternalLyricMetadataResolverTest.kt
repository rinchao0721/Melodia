package com.lin0721.linmusic.core.player.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLyricMetadataResolverTest {

    @Test
    fun `切歌尚未同步到ExternalLyricCoordinator时保留新MediaItem原始标题且不注入旧歌歌词`() {
        val resolved = resolveExternalLyricMetadata(
            mediaId = "2002",
            originalTitle = "荒唐谣",
            currentSongId = 1001L,
            isBluetoothLyricEnabled = true,
            isLyricInfoEnabled = false,
            displayedTitle = "残忍的缠绵 - 上一首歌词行",
            currentLyricInfoJson = """{"songId":"1001"}""",
            sessionGeneration = 3
        )

        assertEquals("荒唐谣", resolved.title)
        assertNull(resolved.lyricInfoJson)
        assertNull(resolved.discSubtitle)
    }

    @Test
    fun `未开启蓝牙歌词时即使currentSongId落后也始终使用MediaItem自身标题`() {
        val resolved = resolveExternalLyricMetadata(
            mediaId = "2002",
            originalTitle = "荒唐谣",
            currentSongId = 1001L,
            isBluetoothLyricEnabled = false,
            isLyricInfoEnabled = false,
            displayedTitle = "残忍的缠绵",
            currentLyricInfoJson = "",
            sessionGeneration = 1
        )

        assertEquals("荒唐谣", resolved.title)
        assertNull(resolved.lyricInfoJson)
        assertNull(resolved.discSubtitle)
    }

    @Test
    fun `曲目ID匹配且开启蓝牙歌词未开启lyricInfo时使用当前歌词标题`() {
        val resolved = resolveExternalLyricMetadata(
            mediaId = "2002",
            originalTitle = "荒唐谣",
            currentSongId = 2002L,
            isBluetoothLyricEnabled = true,
            isLyricInfoEnabled = false,
            displayedTitle = "若无人应我唱无妨都无妨",
            currentLyricInfoJson = "",
            sessionGeneration = 4
        )

        assertEquals("若无人应我唱无妨都无妨", resolved.title)
        assertNull(resolved.lyricInfoJson)
        assertNull(resolved.discSubtitle)
    }

    @Test
    fun `曲目ID匹配且开启lyricInfo时保留原始标题并附带lyricInfo与版本号`() {
        val json = """{"songId":"2002","songName":"荒唐谣"}"""
        val resolved = resolveExternalLyricMetadata(
            mediaId = "2002",
            originalTitle = "荒唐谣",
            currentSongId = 2002L,
            isBluetoothLyricEnabled = true,
            isLyricInfoEnabled = true,
            displayedTitle = "若无人应我唱无妨都无妨",
            currentLyricInfoJson = json,
            sessionGeneration = 5
        )

        assertEquals("荒唐谣", resolved.title)
        assertEquals(json, resolved.lyricInfoJson)
        assertEquals("lyric_5_${json.hashCode()}", resolved.discSubtitle)
    }
}
