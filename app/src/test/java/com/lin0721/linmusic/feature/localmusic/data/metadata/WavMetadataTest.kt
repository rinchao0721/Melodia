package com.lin0721.linmusic.feature.localmusic.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WavMetadataTest {
    @Test
    fun `从扩展名识别 WAV 不要求真实文件路径`() {
        assertTrue(isWavAudio("09. Chicklet Hatching.WAV", null))
        assertTrue(isWavAudio("track.wave", "application/octet-stream"))
        assertFalse(isWavAudio("track.wav.mp3", null))
    }

    @Test
    fun `从 provider MIME 识别无扩展名的 WAV`() {
        listOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave", " AUDIO/X-WAV; codecs=1 ").forEach {
            assertTrue(isWavAudio(null, it))
        }
        assertFalse(isWavAudio(null, null))
    }

    @Test
    fun `非 WAV 格式不进入补读路径`() {
        listOf("mp3" to "audio/mpeg", "flac" to "audio/flac", "m4a" to "audio/mp4",
            "ogg" to "audio/ogg").forEach { (extension, mime) ->
            assertFalse(isWavAudio("audio.$extension", mime))
        }
    }

    @Test
    fun `旧 SAF 非 WAV 文件名跳过全库同步补读但不跳过不透明引用`() {
        assertTrue(canSkipWavRefresh("primary:Music/曲目.FLAC"))
        assertTrue(canSkipWavRefresh("primary:Music/曲目.mp3"))
        assertFalse(canSkipWavRefresh("primary:Music/曲目.WAV"))
        assertFalse(canSkipWavRefresh("primary:Music/曲目.wave"))
        assertFalse(canSkipWavRefresh("12345"))
        assertFalse(canSkipWavRefresh(null))
    }

    @Test
    fun `RIFF ID3 中文标签包含专辑歌手年份和曲碟号`() {
        assertEquals(WavMetadata("小鸡出壳", "舌头 / 合作者", "妈妈一起飞吧，妈妈一起摇滚吧", "舌头", 2014, 2009),
            wavMetadataFromProperties(mapOf(
                "TITLE" to arrayOf(" 小鸡出壳 "), "ARTIST" to arrayOf("舌头", "", "合作者"),
                "ALBUM" to arrayOf("妈妈一起飞吧，妈妈一起摇滚吧"), "ALBUMARTIST" to arrayOf("舌头"),
                "DATE" to arrayOf("2014-04-15"), "TRACKNUMBER" to arrayOf("9/10"), "DISCNUMBER" to arrayOf("2/2")
            )))
    }

    @Test
    fun `有效内嵌标签优先于系统代填的文件名与目录名`() {
        val system = WavMetadata("09. Chicklet Hatching", "<unknown>", "WavFixture")
        val embedded = WavMetadata("小鸡出壳", "舌头", "妈妈一起飞吧，妈妈一起摇滚吧")
        assertEquals(embedded, mergeWavMetadata(system, embedded))
    }

    @Test
    fun `只覆盖内嵌标签中确实存在的字段`() {
        val system = WavMetadata("文件名", "歌手", "专辑", "专辑歌手", 2001, 1002)
        assertEquals(system.copy(title = "内嵌歌名"), mergeWavMetadata(system, WavMetadata(title = "内嵌歌名")))
    }

    @Test
    fun `空标签与未知占位不能擦掉有效系统结果`() {
        val system = WavMetadata("Title", "Artist", "Album", "Credit", 2001, 1)
        assertEquals(system, mergeWavMetadata(system, WavMetadata(" ", "<unknown>", "<UNKNOWN>", "", 0, -1)))
        assertEquals(system, mergeWavMetadata(system, WavMetadata()))
    }

    @Test
    fun `读取失败保留原系统结果包括原占位以维持现有回退`() {
        val system = WavMetadata(" File title ", "<unknown>", "Music", null, null, null)
        assertSame(system, mergeWavMetadata(system, null))
    }

    @Test
    fun `无标签文件不生成空字符串或虚构信息`() {
        assertEquals(WavMetadata(), wavMetadataFromProperties(emptyMap()))
        assertEquals(WavMetadata(), wavMetadataFromProperties(mapOf("TITLE" to arrayOf(" ", "<unknown>"))))
    }

    @Test
    fun `Unknown 是合法歌名和专辑名`() {
        val tags = wavMetadataFromProperties(mapOf("TITLE" to arrayOf("Unknown"), "ALBUM" to arrayOf("Unknown Album")))
        assertEquals("Unknown", tags.title)
        assertEquals("Unknown Album", tags.album)
    }

    @Test
    fun `缺失碟号时曲号与现有 MediaStore 排序约定一致`() {
        val tags = wavMetadataFromProperties(mapOf("TRACKNUMBER" to arrayOf("03/50")))
        assertEquals(3, tags.trackNumber)
    }

    @Test
    fun `非法曲号与溢出碟号不写入数据库`() {
        listOf("0", "1000", "oops").forEach {
            assertNull(wavMetadataFromProperties(mapOf("TRACKNUMBER" to arrayOf(it))).trackNumber)
        }
        assertNull(wavMetadataFromProperties(mapOf("TRACKNUMBER" to arrayOf("1"),
            "DISCNUMBER" to arrayOf(Int.MAX_VALUE.toString()))).trackNumber)
    }

    @Test
    fun `DATE 缺失或无效时可以使用 YEAR`() {
        assertEquals(2002, wavMetadataFromProperties(mapOf("YEAR" to arrayOf("2002"))).year)
        assertEquals(2002, wavMetadataFromProperties(mapOf("DATE" to arrayOf("oops"), "YEAR" to arrayOf("2002"))).year)
        assertNull(wavMetadataFromProperties(mapOf("DATE" to arrayOf("0000"))).year)
    }
}
