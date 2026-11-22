package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal data class KugouServiceSession(
    val userIdStr: String,
    val userIdLong: Long,
    val token: String
)

internal suspend fun requireActiveKugouSession(userPreferences: UserPreferences): KugouServiceSession {
    val account = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        ?: throw IllegalStateException("请先登录酷狗账号")
    val token = account.credentials["token"].orEmpty().trim()
    val userIdStr = account.credentials["userid"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: account.effectivePlatformUid.trim()
    val userIdLong = userIdStr.toLongOrNull() ?: 0L
    if (token.isEmpty() || userIdLong <= 0L) {
        throw IllegalStateException("酷狗登录凭证无效，请重新登录")
    }
    return KugouServiceSession(
        userIdStr = userIdLong.toString(),
        userIdLong = userIdLong,
        token = token
    )
}

internal suspend fun optionalKugouSession(userPreferences: UserPreferences): KugouServiceSession? {
    val account = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() ?: return null
    val token = account.credentials["token"].orEmpty().trim()
    val userIdStr = account.credentials["userid"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: account.effectivePlatformUid.trim()
    val userIdLong = userIdStr.toLongOrNull() ?: 0L
    return KugouServiceSession(
        userIdStr = if (userIdLong > 0L) userIdLong.toString() else "0",
        userIdLong = userIdLong,
        token = token
    )
}

internal fun ensureKugouBizSuccess(response: KugouRawResponse, fallbackMessage: String) {
    KugouRiskException.fromResponse(response)?.let { throw it }
    if (!response.isBizSuccess) {
        if (response.httpCode !in 200..299 && response.bodyJson == null) {
            throw AppError.NetworkError()
        }
        val code = response.errorCode.takeIf { it != 0 } ?: response.status
        throw AppError.BizError(code, response.errorMsg.ifBlank { fallbackMessage })
    }
}

internal fun normalizeKugouCoverUrl(raw: String?): String =
    raw.orEmpty()
        .trim()
        .replace("{size}", "400")
        .replace("http://", "https://")

internal fun normalizeKugouDurationMs(raw: Long): Long =
    if (raw in 1L..9_999L) raw * 1000L else raw

internal fun stripKugouEmTags(raw: String): String =
    raw.replace("<em>", "", ignoreCase = true)
        .replace("</em>", "", ignoreCase = true)
        .trim()

internal fun parseKugouDateTimeMs(raw: String): Long {
    val text = raw.trim()
    if (text.isEmpty()) return 0L
    text.toLongOrNull()?.let { numeric ->
        return if (numeric in 1L..9_999_999_999L) numeric * 1000L else numeric
    }
    val formatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
    )
    for (fmt in formatters) {
        val parsed = runCatching {
            if (text.length <= 10) {
                java.time.LocalDate.parse(text, fmt).atStartOfDay().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
            } else {
                LocalDateTime.parse(text, fmt).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
            }
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
}

internal fun resolveKugouMaxQualityFromObj(obj: JsonObject): UnifiedAudioQuality? {
    val highest = listOfNotNull(
        UnifiedAudioQuality.EXHIGH.takeIf { obj.string("320hash").isNotBlank() || obj.long("320filesize") > 0L },
        UnifiedAudioQuality.LOSSLESS.takeIf { obj.string("sqhash").isNotBlank() || obj.long("sqfilesize") > 0L },
        UnifiedAudioQuality.HIRES.takeIf {
            obj.string("highhash").isNotBlank() || obj.long("highfilesize") > 0L || obj.int("is_hires") == 1
        },
        UnifiedAudioQuality.STANDARD.takeIf { obj.string("hash").isNotBlank() }
    ).maxByOrNull { it.rank }
    if (highest != null) return highest
    val qualityText = obj.string("quality").takeIf { it.isNotBlank() }
    return UnifiedAudioQuality.fromKugouCode(qualityText)
}

internal fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
internal fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun JsonObject.int(key: String, defaultValue: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: defaultValue

internal fun JsonObject.long(key: String, defaultValue: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: defaultValue

internal fun JsonObject.bool(key: String, defaultValue: Boolean = false): Boolean {
    val p = this[key] as? JsonPrimitive ?: return defaultValue
    return p.booleanOrNull ?: (p.intOrNull == 1) ?: defaultValue
}

internal fun JsonObject.double(key: String, defaultValue: Double = 0.0): Double =
    (this[key] as? JsonPrimitive)?.doubleOrNull ?: defaultValue

internal fun JsonObject.firstNonBlank(vararg keys: String): String {
    for (k in keys) {
        val v = string(k).trim()
        if (v.isNotEmpty()) return v
    }
    return ""
}

internal fun JsonObject.firstPositiveLong(vararg keys: String): Long {
    for (k in keys) {
        val v = long(k, 0L)
        if (v > 0L) return v
    }
    return 0L
}

// 解析酷狗多种接口返回中的通用曲目节点（歌单/搜索/歌手单曲/专辑曲目）
internal fun parseKugouTrackItem(obj: JsonObject): KugouTrackItemDto? {
    val audioInfo = obj["audio_info"]?.jsonObjectOrNull()
    val transParam = obj["trans_param"]?.jsonObjectOrNull()
    val base = obj["base"]?.jsonObjectOrNull()
    val albumInfo = obj["album_info"]?.jsonObjectOrNull()

    val hash = listOf(
        obj.string("hash"),
        audioInfo?.string("hash").orEmpty(),
        obj.string("FileHash"),
        obj.string("320hash"),
        obj.string("sqhash")
    ).firstOrNull { it.isNotBlank() }?.trim()?.lowercase() ?: return null

    val rawName = listOf(
        base?.string("audio_name").orEmpty(),
        obj.string("songname"),
        obj.string("audio_name"),
        obj.string("SongName"),
        obj.string("name"),
        obj.string("filename")
    ).firstOrNull { it.isNotBlank() }.orEmpty()

    val rawSinger = listOf(
        base?.string("author_name").orEmpty(),
        obj.string("singername"),
        obj.string("author_name"),
        obj.string("SingerName")
    ).firstOrNull { it.isNotBlank() }.orEmpty()

    // 部分接口 name 形如 "歌手 - 歌名" 而 singername 为空
    val cleanTitle = stripKugouEmTags(rawName)
    val cleanSinger = stripKugouEmTags(rawSinger)
    val (resolvedName, resolvedSinger) = if (cleanSinger.isBlank() && cleanTitle.contains(" - ")) {
        val parts = cleanTitle.split(" - ", limit = 2)
        parts[1].trim() to parts[0].trim()
    } else {
        cleanTitle to cleanSinger
    }

    val singerList = buildList {
        val singersArr = obj["singers"]?.jsonArrayOrNull() ?: obj["authors"]?.jsonArrayOrNull()
        singersArr?.forEach { elem ->
            val sObj = elem.jsonObjectOrNull() ?: return@forEach
            val sName = stripKugouEmTags(sObj.firstNonBlank("name", "author_name"))
            if (sName.isNotBlank()) {
                add(
                    KugouTrackArtistDto(
                        id = sObj.firstNonBlank("id", "author_id"),
                        name = sName,
                        avatarUrl = normalizeKugouCoverUrl(sObj.firstNonBlank("avatar", "sizable_avatar"))
                    )
                )
            }
        }
        if (isEmpty() && resolvedSinger.isNotBlank()) {
            resolvedSinger.split("、", "/", ",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                add(KugouTrackArtistDto(name = it))
            }
        }
    }

    val albumId = obj.firstNonBlank("album_id", "AlbumID")
        .ifBlank { albumInfo?.firstNonBlank("album_id").orEmpty() }
    val albumName = stripKugouEmTags(
        obj.firstNonBlank("album_name", "albumname", "AlbumName")
            .ifBlank { albumInfo?.firstNonBlank("album_name").orEmpty() }
    )
    val albumAudioId = obj.firstPositiveLong("album_audio_id", "AlbumAudioID")
        .takeIf { it > 0L }
        ?: audioInfo?.firstPositiveLong("album_audio_id")
        ?: base?.firstPositiveLong("album_audio_id")
        ?: 0L
    val mixSongId = obj.firstPositiveLong("mixsongid", "MixSongID", "audio_id", "id")
        .takeIf { it > 0L }
        ?: audioInfo?.firstPositiveLong("mixsongid", "audio_id")
        ?: 0L
    val fileId = obj.firstPositiveLong("fileid", "file_id")

    val rawDur = obj.firstPositiveLong("timelen", "duration", "Duration", "time_length")
        .takeIf { it > 0L }
        ?: audioInfo?.firstPositiveLong("timelength", "duration")
        ?: 0L
    val durationMs = normalizeKugouDurationMs(rawDur)

    val coverUrl = normalizeKugouCoverUrl(
        listOf(
            transParam?.string("union_cover").orEmpty(),
            obj.firstNonBlank("cover", "pic", "image", "Image", "img", "sizable_cover"),
            albumInfo?.firstNonBlank("sizable_cover", "cover").orEmpty()
        ).firstOrNull { it.isNotBlank() }
    )

    val publishTimeMs = parseKugouDateTimeMs(obj.firstNonBlank("publish_date", "publish_time", "addtime"))
    val privilege = obj.int("privilege", -1)
    val fee = if (privilege != 0 && obj.int("pay_type", 0) > 0) 1 else 0

    return KugouTrackItemDto(
        hash = hash,
        name = resolvedName.ifBlank { hash },
        artists = singerList,
        albumId = albumId,
        albumName = albumName,
        albumAudioId = albumAudioId,
        mixSongId = mixSongId,
        fileId = fileId,
        durationMs = durationMs,
        coverUrl = coverUrl,
        maxQuality = resolveKugouMaxQualityFromObj(obj),
        fee = fee,
        playable = obj.int("status", 1) != 0,
        publishTimeMs = publishTimeMs
    )
}
