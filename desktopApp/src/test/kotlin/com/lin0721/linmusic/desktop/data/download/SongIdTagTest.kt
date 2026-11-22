package com.lin0721.linmusic.desktop.data.download

import com.lin0721.linmusic.core.download.recognition.SONG_ID_TAG
import com.lin0721.linmusic.core.platform.TrackId
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.valuepair.TextEncoding
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongIdTagTest {

    @Test
    fun id3RoundTripKeepsOtherUserFrames() {
        listOf(ID3v24Tag(), ID3v23Tag()).forEach { tag ->
            assertNull(tag.readSongId())
            val other = tag.createFrame("TXXX").apply { body = FrameBodyTXXX(TextEncoding.ISO_8859_1, "OTHER", "x") }
            tag.setField(other)
            tag.writeSongId(123L)
            tag.writeSongId(456L)
            assertEquals(456L, tag.readSongId())
            assertEquals(TrackId.netease(456L), tag.readTrackId())
            assertEquals(2, tag.getFields("TXXX").size)
        }
    }

    @Test
    fun vorbisRoundTrip() {
        val tag = VorbisCommentTag.createNewTag()
        tag.writeSongId(789L)
        assertEquals(789L, tag.readSongId())
        assertEquals(TrackId.netease(789L), tag.readTrackId())
    }

    @Test
    fun trackIdRoundTripSupportsKugouAndKeepsNeteasePlainNumeric() {
        val neteaseTag = ID3v24Tag()
        neteaseTag.writeTrackId(TrackId.netease(321L))
        assertEquals(321L, neteaseTag.readSongId())
        assertEquals(TrackId.netease(321L), neteaseTag.readTrackId())

        val kugouId = TrackId.kugou("B5E3CA48FC0D01F01DB0B6BBA570480F")
        listOf(ID3v24Tag(), ID3v23Tag()).forEach { tag ->
            tag.writeTrackId(kugouId)
            assertEquals(kugouId, tag.readTrackId())
            assertNull(tag.readSongId())
        }

        val vorbis = VorbisCommentTag.createNewTag()
        vorbis.writeTrackId(kugouId)
        assertEquals(kugouId, vorbis.readTrackId())
        assertNull(vorbis.readSongId())
        assertEquals(kugouId.canonical, vorbis.getFirst(SONG_ID_TAG))
    }
}
