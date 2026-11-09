package com.lin0721.linmusic.desktop.data.download

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
            assertEquals(2, tag.getFields("TXXX").size)
        }
    }

    @Test
    fun vorbisRoundTrip() {
        val tag = VorbisCommentTag.createNewTag()
        tag.writeSongId(789L)
        assertEquals(789L, tag.readSongId())
    }
}
