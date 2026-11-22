package com.lin0721.linmusic.core.platform

import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

// 跨平台强类型实体标识公共契约，规范字符串格式为 "${platform.key}:$rawId"
sealed interface PlatformEntityId {
    val platform: MusicPlatform
    val rawId: String

    val canonical: String
        get() = "${platform.key}:$rawId"

    fun neteaseIdOrNull(): Long? =
        if (platform == MusicPlatform.NETEASE) rawId.toLongOrNull() else null
}

private fun normalizePlatformRawId(platform: MusicPlatform, rawId: String): String {
    val trimmed = rawId.trim()
    val normalized = if (platform == MusicPlatform.KUGOU) trimmed.lowercase() else trimmed
    require(normalized.isNotBlank()) { "实体标识 rawId 不能为空 (platform=${platform.key})" }
    return normalized
}

private fun parsePlatformEntityParts(raw: String): Pair<MusicPlatform, String>? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val colonIndex = trimmed.indexOf(':')
    if (colonIndex >= 0) {
        val prefix = trimmed.substring(0, colonIndex).trim().lowercase()
        val idPart = trimmed.substring(colonIndex + 1).trim()
        val platform = MusicPlatform.fromKey(prefix) ?: return null
        if (idPart.isBlank()) return null
        val normalizedId = if (platform == MusicPlatform.KUGOU) idPart.lowercase() else idPart
        return platform to normalizedId
    }
    // 兼容旧版纯数字网易云 ID 字符串
    if (!trimmed.all { it.isDigit() }) return null
    if (trimmed.toLongOrNull() == null) return null
    return MusicPlatform.NETEASE to trimmed
}

private open class PlatformEntityIdSerializer<T : PlatformEntityId>(
    serialName: String,
    private val fromParts: (MusicPlatform, String) -> T,
    private val fromNeteaseLong: (Long) -> T,
    private val parseString: (String) -> T
) : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.canonical)
    }

    override fun deserialize(decoder: Decoder): T {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element !is JsonPrimitive) {
                throw SerializationException("实体标识必须为字符串或数字: $element")
            }
            if (element.isString) {
                return try {
                    parseString(element.content)
                } catch (e: IllegalArgumentException) {
                    throw SerializationException(e.message ?: "无效实体标识: ${element.content}", e)
                }
            }
            val numericId = element.longOrNull
                ?: throw SerializationException("无效数字实体标识: ${element.content}")
            if (numericId < 0L) {
                throw SerializationException("兼容旧版数字实体标识不能为负数: $numericId")
            }
            return fromNeteaseLong(numericId)
        }
        return try {
            parseString(decoder.decodeString())
        } catch (e: IllegalArgumentException) {
            throw SerializationException(e.message ?: "无效实体标识", e)
        }
    }
}

private object TrackIdSerializer : PlatformEntityIdSerializer<TrackId>(
    serialName = "com.lin0721.linmusic.core.platform.TrackId",
    fromParts = ::TrackId,
    fromNeteaseLong = TrackId::netease,
    parseString = TrackId::parse
)

private object PlaylistIdSerializer : PlatformEntityIdSerializer<PlaylistId>(
    serialName = "com.lin0721.linmusic.core.platform.PlaylistId",
    fromParts = ::PlaylistId,
    fromNeteaseLong = PlaylistId::netease,
    parseString = PlaylistId::parse
)

private object AlbumIdSerializer : PlatformEntityIdSerializer<AlbumId>(
    serialName = "com.lin0721.linmusic.core.platform.AlbumId",
    fromParts = ::AlbumId,
    fromNeteaseLong = AlbumId::netease,
    parseString = AlbumId::parse
)

private object ArtistIdSerializer : PlatformEntityIdSerializer<ArtistId>(
    serialName = "com.lin0721.linmusic.core.platform.ArtistId",
    fromParts = ::ArtistId,
    fromNeteaseLong = ArtistId::netease,
    parseString = ArtistId::parse
)

private object AccountIdSerializer : PlatformEntityIdSerializer<AccountId>(
    serialName = "com.lin0721.linmusic.core.platform.AccountId",
    fromParts = ::AccountId,
    fromNeteaseLong = AccountId::netease,
    parseString = AccountId::parse
)

@Serializable(with = TrackIdSerializer::class)
class TrackId(
    override val platform: MusicPlatform,
    rawId: String
) : PlatformEntityId {
    override val rawId: String = normalizePlatformRawId(platform, rawId)

    operator fun component1(): MusicPlatform = platform
    operator fun component2(): String = rawId

    fun copy(platform: MusicPlatform = this.platform, rawId: String = this.rawId): TrackId =
        TrackId(platform, rawId)

    override fun equals(other: Any?): Boolean =
        this === other || (other is TrackId && platform == other.platform && rawId == other.rawId)

    override fun hashCode(): Int = 31 * platform.hashCode() + rawId.hashCode()

    override fun toString(): String = canonical

    companion object {
        fun netease(id: Long): TrackId {
            require(id >= 0L) { "网易云歌曲 ID 不能为负数: $id" }
            return TrackId(MusicPlatform.NETEASE, id.toString())
        }

        fun kugou(hash: String): TrackId = TrackId(MusicPlatform.KUGOU, hash)

        fun qq(songMid: String): TrackId = TrackId(MusicPlatform.QQ, songMid)

        fun local(uriOrKey: String): TrackId = TrackId(MusicPlatform.LOCAL, uriOrKey)

        fun of(platform: MusicPlatform, rawId: String): TrackId = TrackId(platform, rawId)

        fun parseOrNull(raw: String): TrackId? =
            parsePlatformEntityParts(raw)?.let { (platform, id) -> TrackId(platform, id) }

        fun parse(raw: String): TrackId =
            parseOrNull(raw) ?: throw IllegalArgumentException("无法解析 TrackId: '$raw'")
    }
}

@Serializable(with = PlaylistIdSerializer::class)
class PlaylistId(
    override val platform: MusicPlatform,
    rawId: String
) : PlatformEntityId {
    override val rawId: String = normalizePlatformRawId(platform, rawId)

    operator fun component1(): MusicPlatform = platform
    operator fun component2(): String = rawId

    fun copy(platform: MusicPlatform = this.platform, rawId: String = this.rawId): PlaylistId =
        PlaylistId(platform, rawId)

    override fun equals(other: Any?): Boolean =
        this === other || (other is PlaylistId && platform == other.platform && rawId == other.rawId)

    override fun hashCode(): Int = 31 * platform.hashCode() + rawId.hashCode()

    override fun toString(): String = canonical

    companion object {
        fun netease(id: Long): PlaylistId {
            require(id >= 0L) { "网易云歌单 ID 不能为负数: $id" }
            return PlaylistId(MusicPlatform.NETEASE, id.toString())
        }

        fun kugou(globalCollectionId: String): PlaylistId = PlaylistId(MusicPlatform.KUGOU, globalCollectionId)

        fun local(id: String): PlaylistId = PlaylistId(MusicPlatform.LOCAL, id)

        fun of(platform: MusicPlatform, rawId: String): PlaylistId = PlaylistId(platform, rawId)

        fun parseOrNull(raw: String): PlaylistId? =
            parsePlatformEntityParts(raw)?.let { (platform, id) -> PlaylistId(platform, id) }

        fun parse(raw: String): PlaylistId =
            parseOrNull(raw) ?: throw IllegalArgumentException("无法解析 PlaylistId: '$raw'")
    }
}

@Serializable(with = AlbumIdSerializer::class)
class AlbumId(
    override val platform: MusicPlatform,
    rawId: String
) : PlatformEntityId {
    override val rawId: String = normalizePlatformRawId(platform, rawId)

    operator fun component1(): MusicPlatform = platform
    operator fun component2(): String = rawId

    fun copy(platform: MusicPlatform = this.platform, rawId: String = this.rawId): AlbumId =
        AlbumId(platform, rawId)

    override fun equals(other: Any?): Boolean =
        this === other || (other is AlbumId && platform == other.platform && rawId == other.rawId)

    override fun hashCode(): Int = 31 * platform.hashCode() + rawId.hashCode()

    override fun toString(): String = canonical

    companion object {
        fun netease(id: Long): AlbumId {
            require(id >= 0L) { "网易云专辑 ID 不能为负数: $id" }
            return AlbumId(MusicPlatform.NETEASE, id.toString())
        }

        fun kugou(albumId: String): AlbumId = AlbumId(MusicPlatform.KUGOU, albumId)

        fun local(id: String): AlbumId = AlbumId(MusicPlatform.LOCAL, id)

        fun of(platform: MusicPlatform, rawId: String): AlbumId = AlbumId(platform, rawId)

        fun parseOrNull(raw: String): AlbumId? =
            parsePlatformEntityParts(raw)?.let { (platform, id) -> AlbumId(platform, id) }

        fun parse(raw: String): AlbumId =
            parseOrNull(raw) ?: throw IllegalArgumentException("无法解析 AlbumId: '$raw'")
    }
}

@Serializable(with = ArtistIdSerializer::class)
class ArtistId(
    override val platform: MusicPlatform,
    rawId: String
) : PlatformEntityId {
    override val rawId: String = normalizePlatformRawId(platform, rawId)

    operator fun component1(): MusicPlatform = platform
    operator fun component2(): String = rawId

    fun copy(platform: MusicPlatform = this.platform, rawId: String = this.rawId): ArtistId =
        ArtistId(platform, rawId)

    override fun equals(other: Any?): Boolean =
        this === other || (other is ArtistId && platform == other.platform && rawId == other.rawId)

    override fun hashCode(): Int = 31 * platform.hashCode() + rawId.hashCode()

    override fun toString(): String = canonical

    companion object {
        fun netease(id: Long): ArtistId {
            require(id >= 0L) { "网易云歌手 ID 不能为负数: $id" }
            return ArtistId(MusicPlatform.NETEASE, id.toString())
        }

        fun kugou(singerId: String): ArtistId = ArtistId(MusicPlatform.KUGOU, singerId)

        fun local(id: String): ArtistId = ArtistId(MusicPlatform.LOCAL, id)

        fun of(platform: MusicPlatform, rawId: String): ArtistId = ArtistId(platform, rawId)

        fun parseOrNull(raw: String): ArtistId? =
            parsePlatformEntityParts(raw)?.let { (platform, id) -> ArtistId(platform, id) }

        fun parse(raw: String): ArtistId =
            parseOrNull(raw) ?: throw IllegalArgumentException("无法解析 ArtistId: '$raw'")
    }
}

@Serializable(with = AccountIdSerializer::class)
class AccountId(
    override val platform: MusicPlatform,
    rawId: String
) : PlatformEntityId {
    override val rawId: String = normalizePlatformRawId(platform, rawId)

    operator fun component1(): MusicPlatform = platform
    operator fun component2(): String = rawId

    fun copy(platform: MusicPlatform = this.platform, rawId: String = this.rawId): AccountId =
        AccountId(platform, rawId)

    override fun equals(other: Any?): Boolean =
        this === other || (other is AccountId && platform == other.platform && rawId == other.rawId)

    override fun hashCode(): Int = 31 * platform.hashCode() + rawId.hashCode()

    override fun toString(): String = canonical

    companion object {
        fun netease(id: Long): AccountId {
            require(id >= 0L) { "网易云账号 ID 不能为负数: $id" }
            return AccountId(MusicPlatform.NETEASE, id.toString())
        }

        fun kugou(userId: Long): AccountId {
            require(userId >= 0L) { "酷狗账号 ID 不能为负数: $userId" }
            return AccountId(MusicPlatform.KUGOU, userId.toString())
        }

        fun kugou(userId: String): AccountId = AccountId(MusicPlatform.KUGOU, userId)

        fun of(platform: MusicPlatform, rawId: String): AccountId = AccountId(platform, rawId)

        fun parseOrNull(raw: String): AccountId? =
            parsePlatformEntityParts(raw)?.let { (platform, id) -> AccountId(platform, id) }

        fun parse(raw: String): AccountId =
            parseOrNull(raw) ?: throw IllegalArgumentException("无法解析 AccountId: '$raw'")
    }
}
