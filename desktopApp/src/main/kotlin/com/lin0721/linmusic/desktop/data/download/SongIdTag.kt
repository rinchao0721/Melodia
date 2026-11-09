package com.lin0721.linmusic.desktop.data.download

import com.lin0721.linmusic.core.download.recognition.SONG_ID_TAG
import com.lin0721.linmusic.core.download.recognition.parseSongIdTag
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.valuepair.TextEncoding
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag

private const val ID3_USER_TEXT_FRAME = "TXXX"

// 写入网易歌曲 id：ID3 用 TXXX 自定义帧，FLAC/OGG 用 Vorbis 注释，与 Android 端 TagLib 写法互通
internal fun Tag.writeSongId(songId: Long) {
    val value = songId.toString()
    when (this) {
        is AbstractID3v2Tag -> {
            val frame = createFrame(ID3_USER_TEXT_FRAME)
            frame.body = FrameBodyTXXX(TextEncoding.ISO_8859_1, SONG_ID_TAG, value)
            setField(frame)
        }
        is FlacTag -> setField(SONG_ID_TAG, value)
        is VorbisCommentTag -> setField(SONG_ID_TAG, value)
    }
}

internal fun Tag.readSongId(): Long? = when (this) {
    is AbstractID3v2Tag -> getFields(ID3_USER_TEXT_FRAME).asSequence()
        .mapNotNull { ((it as? AbstractID3v2Frame)?.body as? FrameBodyTXXX) }
        .firstOrNull { it.description == SONG_ID_TAG }
        ?.firstTextValue
        .let(::parseSongIdTag)
    is FlacTag -> parseSongIdTag(vorbisCommentTag?.getFirst(SONG_ID_TAG))
    is VorbisCommentTag -> parseSongIdTag(getFirst(SONG_ID_TAG))
    else -> null
}
