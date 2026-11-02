package com.lin0721.linmusic.feature.localmusic.data.metadata

import java.util.Locale

// 只补充 WAV 标签；时长仍由 Android 解码器提供，不用标签推断音频属性。
internal data class WavMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null
)

internal fun isWavAudio(fileName: String?, mimeType: String?): Boolean =
    fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in setOf("wav", "wave") ||
        mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) in
        setOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave")

// 已导入引用通常含文件名；常见其他音频格式无需在每次全库同步时再查询 provider。
internal fun canSkipWavRefresh(fileName: String?): Boolean =
    fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in
        setOf("mp3", "flac", "aac", "m4a", "ogg", "opus", "ape", "wma", "aiff")

internal fun cleanWavTag(value: String?): String? = value?.trim()?.takeIf {
    // Unknown 可以是真实歌名，只过滤 Android 明确使用的缺失值。
    it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true)
}

internal fun wavMetadataFromProperties(properties: Map<String, Array<String>>): WavMetadata {
    fun text(key: String): String? = properties[key]?.mapNotNull(::cleanWavTag)
        ?.takeIf { it.isNotEmpty() }?.joinToString(" / ")
    fun first(key: String): String? = properties[key]?.firstNotNullOfOrNull(::cleanWavTag)
    fun year(key: String): Int? = first(key)?.take(4)?.toIntOrNull()?.takeIf { it > 0 }
    val track = first("TRACKNUMBER")?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it in 1..999 }
    val disc = first("DISCNUMBER")?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 0
    val encodedTrack = track?.let { (disc.toLong() * 1000 + it).takeIf { value -> value <= Int.MAX_VALUE }?.toInt() }
    return WavMetadata(
        title = text("TITLE"),
        artist = text("ARTIST"),
        album = text("ALBUM"),
        albumArtist = text("ALBUMARTIST"),
        year = year("DATE") ?: year("YEAR"),
        trackNumber = encodedTrack
    )
}

// 系统可能把文件名当作 WAV 标题，不能只在系统值为空时才补读。仅用确实读到的标签覆盖。
internal fun mergeWavMetadata(system: WavMetadata, embedded: WavMetadata?): WavMetadata =
    if (embedded == null) system else WavMetadata(
        title = cleanWavTag(embedded.title) ?: system.title,
        artist = cleanWavTag(embedded.artist) ?: system.artist,
        album = cleanWavTag(embedded.album) ?: system.album,
        albumArtist = cleanWavTag(embedded.albumArtist) ?: system.albumArtist,
        year = embedded.year?.takeIf { it > 0 } ?: system.year,
        trackNumber = embedded.trackNumber?.takeIf { it > 0 } ?: system.trackNumber
    )
