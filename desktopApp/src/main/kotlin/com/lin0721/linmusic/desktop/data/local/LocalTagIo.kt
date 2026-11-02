package com.lin0721.linmusic.desktop.data.local

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

// 扫描时读到的元数据，读不出标签的格式只剩文件名推断的标题
data class LocalTagData(
    val title: String,
    val artist: String,
    val album: String,
    val year: Int,
    val trackNumber: Int,
    val durationMs: Long,
    val artwork: ByteArray?
)

// 标签编辑器读写的字段
data class LocalTagEdit(
    val title: String,
    val artist: String,
    val album: String,
    val year: String,
    val trackNumber: String,
    val lyrics: String
)

sealed interface CoverChange {
    data object Keep : CoverChange
    data object Remove : CoverChange
    class Replace(val jpeg: ByteArray) : CoverChange
}

internal const val UNKNOWN_ARTIST = "未知歌手"

object LocalTagIo {

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun read(file: File): LocalTagData {
        val audio = readAudio(file)
        val tag = audio?.tag
        val (fallbackArtist, fallbackTitle) = guessFromFileName(file)
        val durationMs = audio?.audioHeader?.let { header ->
            runCatching { (header.preciseTrackLength * 1000).toLong() }.getOrNull()
                ?: (header.trackLength * 1000L)
        } ?: 0L
        return LocalTagData(
            title = tag.field(FieldKey.TITLE).ifBlank { fallbackTitle },
            artist = tag.field(FieldKey.ARTIST).ifBlank { fallbackArtist ?: UNKNOWN_ARTIST },
            album = tag.field(FieldKey.ALBUM),
            year = tag.field(FieldKey.YEAR).take(4).toIntOrNull() ?: 0,
            trackNumber = tag.field(FieldKey.TRACK).substringBefore('/').trim().toIntOrNull() ?: 0,
            durationMs = durationMs.coerceAtLeast(0L),
            artwork = runCatching { tag?.firstArtwork?.binaryData }.getOrNull()?.takeIf { it.isNotEmpty() }
        )
    }

    fun readEmbeddedLyrics(file: File): String? =
        readAudio(file)?.tag.field(FieldKey.LYRICS).takeIf { it.isNotBlank() }

    // 读不出标签时返回 null，编辑器据此提示该格式不支持编辑
    fun readEditable(file: File): LocalTagEdit? {
        val audio = readAudio(file) ?: return null
        val tag = audio.tag
        val (fallbackArtist, fallbackTitle) = guessFromFileName(file)
        return LocalTagEdit(
            title = tag.field(FieldKey.TITLE).ifBlank { fallbackTitle },
            artist = tag.field(FieldKey.ARTIST).ifBlank { fallbackArtist.orEmpty() },
            album = tag.field(FieldKey.ALBUM),
            year = tag.field(FieldKey.YEAR),
            trackNumber = listOf(tag.field(FieldKey.TRACK), tag.field(FieldKey.TRACK_TOTAL))
                .filter { it.isNotEmpty() }
                .joinToString("/"),
            lyrics = tag.field(FieldKey.LYRICS)
        )
    }

    fun readArtwork(file: File): ByteArray? =
        runCatching { readAudio(file)?.tag?.firstArtwork?.binaryData }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun write(file: File, edit: LocalTagEdit, cover: CoverChange): Result<Unit> = runCatching {
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        tag.putOrDelete(FieldKey.TITLE, edit.title)
        tag.putOrDelete(FieldKey.ARTIST, edit.artist)
        tag.putOrDelete(FieldKey.ALBUM, edit.album)
        tag.putOrDelete(FieldKey.YEAR, edit.year)
        // “3/12” 拆成音轨号与总数，部分格式的音轨号字段只接受数字
        val trackParts = edit.trackNumber.split('/')
        tag.putOrDelete(FieldKey.TRACK, trackParts.getOrNull(0).orEmpty())
        tag.putOrDelete(FieldKey.TRACK_TOTAL, trackParts.getOrNull(1).orEmpty())
        tag.putOrDelete(FieldKey.LYRICS, edit.lyrics)
        when (cover) {
            CoverChange.Keep -> Unit
            CoverChange.Remove -> tag.deleteArtworkField()
            is CoverChange.Replace -> {
                val artwork = ArtworkFactory.getNew().apply {
                    binaryData = cover.jpeg
                    mimeType = "image/jpeg"
                    pictureType = 3
                    description = "Front Cover"
                }
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
        }
        audio.commit()
    }

    private fun readAudio(file: File): AudioFile? = runCatching { AudioFileIO.read(file) }.getOrNull()

    private fun Tag?.field(key: FieldKey): String =
        this?.let { runCatching { it.getFirst(key) }.getOrNull() }?.trim().orEmpty()

    private fun Tag.putOrDelete(key: FieldKey, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            if (hasField(key)) deleteField(key)
        } else {
            setField(key, trimmed)
        }
    }

    // “歌手 - 标题.mp3” 拆成两段，否则整段作标题
    internal fun guessFromFileName(file: File): Pair<String?, String> {
        val base = file.nameWithoutExtension.trim()
        val separator = base.indexOf(" - ")
        if (separator <= 0 || separator + 3 >= base.length) return null to base
        return base.substring(0, separator).trim() to base.substring(separator + 3).trim()
    }
}
