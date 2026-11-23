package com.lin0721.linmusic.core.platform

import com.lin0721.linmusic.core.model.QualityBadge
import com.lin0721.linmusic.core.model.qualityRank
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.Serializable

// 酷狗音乐曲目扩展元数据，保留播放鉴权、歌单删歌与换质所需标识
@Serializable
data class KugouTrackMeta(
    val hash: String,
    val mixSongId: Long? = null,
    val albumAudioId: Long? = null,
    val albumId: String? = null,
    val playlistFileId: Long? = null,
    val playlistAddTimeMs: Long? = null,
    val isCloudTrack: Boolean = false,
    val cloudAudioId: Long? = null,
    val cloudName: String? = null,
    val cloudKvId: Long? = null
)

// 跨平台统一音质档位枚举，串联网易音质码、酷狗音质码与 UI 徽标/排序
@Serializable
enum class UnifiedAudioQuality(
    val neteaseCode: String,
    val kugouCode: String,
    val displayName: String,
    val badgeLabel: String,
    val rank: Int
) {
    STANDARD("standard", "128", "标准", "Standard", 0),
    HIGHER("higher", "128", "较高", "Higher", 1),
    EXHIGH("exhigh", "320", "极高", "ExHigh", 2),
    LOSSLESS("lossless", "flac", "无损 (FLAC)", "Lossless", 3),
    HIRES("hires", "high", "Hi-Res", "Hi-Res", 4),
    SURROUND("jyeffect", "viper_clear", "高清环绕声", "Surround", 5),
    IMMERSIVE("sky", "viper_atmos", "沉浸环绕声", "Immersive", 6),
    DOLBY("dolby", "viper_atmos", "杜比全景声", "Dolby", 7),
    MASTER("jymaster", "viper_tape", "超清母带", "Master", 8);

    fun toQualityBadge(): QualityBadge = when (this) {
        STANDARD, HIGHER -> QualityBadge.Standard
        EXHIGH -> QualityBadge.ExHigh
        LOSSLESS -> QualityBadge.Lossless
        HIRES -> QualityBadge.HiRes
        SURROUND -> QualityBadge.Surround
        IMMERSIVE -> QualityBadge.Immersive
        DOLBY -> QualityBadge.Dolby
        MASTER -> QualityBadge.Master
    }

    fun toNeteaseCode(): String = neteaseCode

    fun toKugouCode(): String = kugouCode

    // 与现有 DOWNLOAD_QUALITY_LEVELS 排序衔接（注意旧版列表不含 dolby，返回 -1）
    fun toLegacyQualityRank(): Int = qualityRank(neteaseCode)

    companion object {
        val ORDERED: List<UnifiedAudioQuality> = entries.sortedBy { it.rank }

        fun fromNeteaseCode(code: String?): UnifiedAudioQuality? {
            val key = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.neteaseCode == key }
        }

        // 对照 KuGouMusicApi/module/privilege_lite.js 档位定义映射
        fun fromKugouCode(code: String?): UnifiedAudioQuality? =
            when (code?.trim()?.lowercase()) {
                "128" -> STANDARD
                "320" -> EXHIGH
                "flac" -> LOSSLESS
                "high" -> HIRES
                "super", "viper_tape" -> MASTER
                "viper_clear" -> SURROUND
                "viper_atmos" -> IMMERSIVE
                else -> null
            }

        fun fromCode(code: String?, platform: MusicPlatform? = null): UnifiedAudioQuality? =
            when (platform) {
                MusicPlatform.KUGOU -> fromKugouCode(code) ?: fromNeteaseCode(code)
                MusicPlatform.NETEASE -> fromNeteaseCode(code) ?: fromKugouCode(code)
                else -> fromNeteaseCode(code) ?: fromKugouCode(code)
            }

        fun fromQualityBadge(badge: QualityBadge?): UnifiedAudioQuality? = when (badge) {
            QualityBadge.Standard -> STANDARD
            QualityBadge.ExHigh -> EXHIGH
            QualityBadge.Lossless -> LOSSLESS
            QualityBadge.HiRes -> HIRES
            QualityBadge.Surround -> SURROUND
            QualityBadge.Immersive -> IMMERSIVE
            QualityBadge.Dolby -> DOLBY
            QualityBadge.Master -> MASTER
            null -> null
        }
    }
}

@Serializable
data class UnifiedArtist(
    val id: ArtistId? = null,
    val name: String = "",
    val avatarUrl: String = "",
    val picUrl: String = ""
)

@Serializable
data class UnifiedAlbum(
    val id: AlbumId? = null,
    val name: String = "",
    val coverUrl: String = "",
    val artists: List<UnifiedArtist> = emptyList(),
    val publishTimeMs: Long = 0L
)

@Serializable
data class UnifiedCreator(
    val id: AccountId? = null,
    val nickname: String = "",
    val avatarUrl: String = ""
)

@Serializable
data class UnifiedTrack(
    val id: TrackId,
    val name: String,
    val artists: List<UnifiedArtist> = emptyList(),
    val artistNames: String = artists.joinToString(" / ") { it.name },
    val album: UnifiedAlbum? = null,
    val durationMs: Long = 0L,
    val coverUrl: String = "",
    val publishTimeMs: Long = 0L,
    val maxQuality: UnifiedAudioQuality? = null,
    val fee: Int = 0,
    val playable: Boolean = true,
    val kugouMeta: KugouTrackMeta? = null,
    val qqSongId: Long? = null,
    val localUri: String? = null
) {
    val platform: MusicPlatform
        get() = id.platform
}

@Serializable
data class UnifiedPlaylist(
    val id: PlaylistId,
    val name: String = "",
    val coverUrl: String = "",
    val description: String? = null,
    val playCount: Long = 0L,
    val subscribed: Boolean = false,
    val creator: UnifiedCreator? = null,
    val tracks: List<UnifiedTrack> = emptyList(),
    val trackCount: Int = tracks.size,
    val trackIds: List<TrackId> = emptyList(),
    val artists: List<UnifiedArtist> = emptyList(),
    val isPublic: Boolean = true,
    val isAlbum: Boolean = false,
    val tags: List<String> = emptyList(),
    val listVer: Long = 0L
) {
    val platform: MusicPlatform
        get() = id.platform
}

@Serializable
data class UnifiedRepliedComment(
    val author: UnifiedCreator? = null,
    val content: String? = null,
    val commentId: String? = null
)

@Serializable
data class UnifiedComment(
    val id: String,
    val platform: MusicPlatform,
    val author: UnifiedCreator = UnifiedCreator(),
    val content: String = "",
    val timeMs: Long = 0L,
    val timeText: String? = null,
    val likedCount: Int = 0,
    val liked: Boolean = false,
    val replyCount: Int = 0,
    val parentCommentId: String? = null,
    val repliedTo: List<UnifiedRepliedComment> = emptyList()
)

@Serializable
data class UnifiedPlaySource(
    val kind: Kind,
    val platform: MusicPlatform,
    val rawId: String,
    val name: String,
    val coverUrl: String = ""
) {
    enum class Kind { PLAYLIST, ALBUM, RANK, RADIO, LOCAL_FOLDER }
}
