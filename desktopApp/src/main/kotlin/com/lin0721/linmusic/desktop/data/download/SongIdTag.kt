package com.lin0721.linmusic.desktop.data.download

import com.lin0721.linmusic.core.download.recognition.SONG_ID_TAG
import com.lin0721.linmusic.core.download.recognition.parseTrackIdTag
import com.lin0721.linmusic.core.platform.TrackId
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.valuepair.TextEncoding
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag

private const val ID3_USER_TEXT_FRAME = "TXXX"

// 写入跨平台歌曲标识：网易云保持纯数字以兼容旧版读取，其余平台写入 platform:rawId
internal fun Tag.writeTrackId(trackId: TrackId) {
    val value = trackId.neteaseIdOrNull()?.toString() ?: trackId.canonical
    writeRawSongIdTag(value)
}

// 写入网易歌曲 id：ID3 用 TXXX 自定义帧，FLAC/OGG 用 Vorbis 注释，与 Android 端 TagLib 写法互通
internal fun Tag.writeSongId(songId: Long) {
    writeRawSongIdTag(songId.toString())
}

private fun Tag.writeRawSongIdTag(value: String) {
    when (this) {
        is AbstractID3v2Tag -> {
            val encoding = if (value.all { it.code in 0x20..0x7E }) TextEncoding.ISO_8859_1 else TextEncoding.UTF_8
            val frame = createFrame(ID3_USER_TEXT_FRAME)
            frame.body = FrameBodyTXXX(encoding, SONG_ID_TAG, value)
            setField(frame)
        }
        is FlacTag -> setField(SONG_ID_TAG, value)
        is VorbisCommentTag -> setField(SONG_ID_TAG, value)
    }
}

internal fun Tag.readTrackId(): TrackId? = when (this) {
    is AbstractID3v2Tag -> getFields(ID3_USER_TEXT_FRAME).asSequence()
        .mapNotNull { ((it as? AbstractID3v2Frame)?.body as? FrameBodyTXXX) }
        .firstOrNull { it.description == SONG_ID_TAG }
        ?.firstTextValue
        .let(::parseTrackIdTag)
    is FlacTag -> parseTrackIdTag(vorbisCommentTag?.getFirst(SONG_ID_TAG))
    is VorbisCommentTag -> parseTrackIdTag(getFirst(SONG_ID_TAG))
    else -> null
}

internal fun Tag.readSongId(): Long? = readTrackId()?.neteaseIdOrNull()?.takeIf { it > 0L }
