package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.LoudnessPolicy
import kotlinx.coroutines.runBlocking
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.valuepair.TextEncoding
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopReplayGainReaderTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-rg-test").toFile()
    }

    @After
    fun tearDown() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    @Test
    fun readsId3v23AndId3v24ReplayGainTagsCaseInsensitively() {
        listOf(ID3v23Tag(), ID3v24Tag()).forEach { tag ->
            tag.setField(tag.createFrame("TXXX").apply {
                body = FrameBodyTXXX(TextEncoding.ISO_8859_1, "replaygain_track_gain", "-7.32 dB")
            })
            tag.setField(tag.createFrame("TXXX").apply {
                body = FrameBodyTXXX(TextEncoding.ISO_8859_1, "REPLAYGAIN_TRACK_PEAK", "0.95")
            })

            val loudness = DesktopReplayGainReader.readFromTag(tag)
            assertNotNull(loudness)
            assertEquals(-18.0 - (-7.32), loudness!!.lufs, 1e-4)
            assertEquals(0.95, loudness.peak!!, 1e-4)
        }
    }

    @Test
    fun readsVorbisAndFlacReplayGainAndOpusR128Tags() {
        val vorbis = VorbisCommentTag.createNewTag().apply {
            setField("REPLAYGAIN_TRACK_GAIN", "+2.10 dB")
            setField("REPLAYGAIN_TRACK_PEAK", "0.80")
        }
        val flac = FlacTag(vorbis, emptyList())
        val fromFlac = DesktopReplayGainReader.readFromTag(flac)!!
        assertEquals(-20.10, fromFlac.lufs, 1e-4)
        assertEquals(0.80, fromFlac.peak!!, 1e-4)

        val opus = VorbisCommentTag.createNewTag().apply {
            setField("R128_TRACK_GAIN", "-512")
        }
        val fromOpus = DesktopReplayGainReader.readFromTag(opus)!!
        assertEquals(-21.0, fromOpus.lufs, 1e-4)
        assertNull(fromOpus.peak)
        assertEquals(LoudnessPolicy.gainDb(-21.0, null), LoudnessPolicy.gainDb(fromOpus), 1e-4)
    }

    @Test
    fun rejectsRemoteAndMissingPathsAndAcceptsLocalOrFileUri() = runBlocking {
        val local = File(dir, "sample.mp3").apply { writeBytes(ByteArray(16)) }
        assertTrue(DesktopReplayGainReader.isLocalUri(local.absolutePath))
        assertTrue(DesktopReplayGainReader.isLocalUri(local.toURI().toString()))
        assertFalse(DesktopReplayGainReader.isLocalUri("http://127.0.0.1:8080/a.mp3"))
        assertFalse(DesktopReplayGainReader.isLocalUri("https://example.com/a.flac"))
        assertFalse(DesktopReplayGainReader.isLocalUri(File(dir, "missing.mp3").absolutePath))
        assertFalse(DesktopReplayGainReader.isLocalUri("   "))

        assertNull(DesktopReplayGainReader.trackLoudness("http://127.0.0.1:8080/a.mp3"))
        assertNull(DesktopReplayGainReader.trackLoudness(local.absolutePath))
    }
}
