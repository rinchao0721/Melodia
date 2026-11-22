package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.kugou.KugouPlaybackService
import com.lin0721.linmusic.core.kugou.KugouSongUrlResult
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.domain.KrcLyricParser
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.source.MusicPlatform

// 跨平台播放/歌词/打卡所需的曲目补充元数据
data class PlaybackTrackMeta(
    val title: String = "",
    val artists: String = "",
    val albumName: String = "",
    val durationMs: Long = 0L,
    val kugouMeta: KugouTrackMeta? = null,
    val playSource: PlaySource? = null
)

// 跨平台播放能力抽象契约（与网易云 PlaybackRepository 并存，供 PlatformPlaybackRouter 统一路由）
interface PlatformPlaybackProvider {
    val platform: MusicPlatform

    suspend fun resolvePlayback(
        trackId: TrackId,
        preferredQuality: UnifiedAudioQuality = UnifiedAudioQuality.EXHIGH,
        meta: PlaybackTrackMeta? = null
    ): Result<SongPlaybackInfo>

    suspend fun getLyrics(
        trackId: TrackId,
        meta: PlaybackTrackMeta? = null
    ): Result<List<LyricLine>>

    suspend fun getRawLyrics(
        trackId: TrackId,
        meta: PlaybackTrackMeta? = null
    ): Result<String>

    suspend fun reportPlayStart(
        trackId: TrackId,
        meta: PlaybackTrackMeta? = null
    ): Result<Unit> = Result.success(Unit)

    suspend fun reportPlayEnd(
        trackId: TrackId,
        playedSeconds: Long,
        meta: PlaybackTrackMeta? = null
    ): Result<Unit> = Result.success(Unit)

    suspend fun getQualityLevels(tracks: List<TrackId>): Result<Map<TrackId, UnifiedAudioQuality>> =
        Result.success(emptyMap())
}

// 酷狗平台播放提供者：直链主链 /v6/priv_url -> 回退 /v5/url；歌词 search + download + KRC 解析；播放历史上传
class KugouPlaybackProvider(
    private val playbackService: KugouPlaybackService,
    private val lyricsCache: LyricsCache? = null
) : PlatformPlaybackProvider {

    override val platform: MusicPlatform = MusicPlatform.KUGOU

    override suspend fun resolvePlayback(
        trackId: TrackId,
        preferredQuality: UnifiedAudioQuality,
        meta: PlaybackTrackMeta?
    ): Result<SongPlaybackInfo> {
        if (trackId.platform != MusicPlatform.KUGOU) {
            return Result.failure(IllegalArgumentException("不支持的平台: ${trackId.platform.key}"))
        }
        val hash = (meta?.kugouMeta?.hash ?: trackId.rawId).trim().lowercase()
        val albumId = meta?.kugouMeta?.albumId
        val albumAudioId = meta?.kugouMeta?.albumAudioId

        val primaryResult = playbackService.getSongUrlNew(
            hash = hash,
            albumId = albumId,
            albumAudioId = albumAudioId,
            quality = preferredQuality
        )
        val primary = primaryResult.getOrNull()
        if (primary != null && primary.url.isNotBlank() && !primary.isFreeTrial) {
            return Result.success(primary.toPlaybackInfo())
        }

        // 主链路失败或返回空直链/试听时，回退 /v5/url
        val fallbackResult = playbackService.getSongUrlLegacy(
            hash = hash,
            albumId = albumId,
            quality = preferredQuality
        )
        val fallback = fallbackResult.getOrNull()
        val chosen = when {
            fallback != null && fallback.url.isNotBlank() && !fallback.isFreeTrial -> fallback
            primary != null && primary.url.isNotBlank() -> primary
            fallback != null && fallback.url.isNotBlank() -> fallback
            else -> null
        }

        return if (chosen != null) {
            Result.success(chosen.toPlaybackInfo())
        } else {
            val cause = fallbackResult.exceptionOrNull()
                ?: primaryResult.exceptionOrNull()
                ?: AppError.BizError(-1, "酷狗未能获取到可用播放直链")
            Result.failure(cause)
        }
    }

    override suspend fun getRawLyrics(
        trackId: TrackId,
        meta: PlaybackTrackMeta?
    ): Result<String> {
        if (trackId.platform != MusicPlatform.KUGOU) {
            return Result.failure(IllegalArgumentException("不支持的平台: ${trackId.platform.key}"))
        }

        lyricsCache?.getRaw(trackId, LyricsCache.Namespace.KRC)
            ?.takeIf { it.isNotBlank() }
            ?.let { return Result.success(it) }

        val hash = (meta?.kugouMeta?.hash ?: trackId.rawId).trim().lowercase()
        val keyword = listOfNotNull(
            meta?.artists?.trim()?.takeIf { it.isNotEmpty() },
            meta?.title?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" - ")

        val candidatesResult = playbackService.searchLyrics(
            hash = hash,
            keyword = keyword,
            durationMs = meta?.durationMs ?: 0L
        )
        val candidates = candidatesResult.getOrElse { return Result.failure(it) }
        val best = candidates.maxByOrNull { it.score }
            ?: return Result.failure(AppError.BizError(-1, "未找到酷狗歌词"))

        val preferredFmt = best.fmt.takeIf { it.equals("krc", true) || it.equals("lrc", true) } ?: "krc"
        val downloaded = playbackService.downloadLyrics(best.id, best.accessKey, preferredFmt)
        var text = downloaded.getOrNull()?.decodedContent.orEmpty()

        if (text.isBlank() && !preferredFmt.equals("lrc", ignoreCase = true)) {
            text = playbackService.downloadLyrics(best.id, best.accessKey, "lrc")
                .getOrNull()?.decodedContent.orEmpty()
        }

        if (text.isBlank()) {
            return Result.failure(downloaded.exceptionOrNull() ?: AppError.BizError(-1, "酷狗歌词下载内容为空"))
        }

        lyricsCache?.putRaw(trackId, text, LyricsCache.Namespace.KRC)
        return Result.success(text)
    }

    override suspend fun getLyrics(
        trackId: TrackId,
        meta: PlaybackTrackMeta?
    ): Result<List<LyricLine>> =
        getRawLyrics(trackId, meta).map { raw -> KrcLyricParser.parse(raw) }

    override suspend fun reportPlayStart(
        trackId: TrackId,
        meta: PlaybackTrackMeta?
    ): Result<Unit> = Result.success(Unit)

    override suspend fun reportPlayEnd(
        trackId: TrackId,
        playedSeconds: Long,
        meta: PlaybackTrackMeta?
    ): Result<Unit> {
        val mixSongId = meta?.kugouMeta?.mixSongId?.takeIf { it > 0L } ?: return Result.success(Unit)
        return playbackService.uploadPlayHistory(mixSongId = mixSongId, playedSeconds = playedSeconds)
    }

    override suspend fun getQualityLevels(tracks: List<TrackId>): Result<Map<TrackId, UnifiedAudioQuality>> = runCatching {
        val kugouTracks = tracks.filter { it.platform == MusicPlatform.KUGOU }.distinct()
        if (kugouTracks.isEmpty()) return@runCatching emptyMap()

        val result = LinkedHashMap<TrackId, UnifiedAudioQuality>(kugouTracks.size)
        for (chunk in kugouTracks.chunked(50)) {
            val privs = playbackService.getPrivilegeLite(chunk.map { it.rawId }).getOrThrow()
            privs.forEachIndexed { index, priv ->
                val trackId = chunk.firstOrNull { it.rawId.equals(priv.hash, ignoreCase = true) }
                    ?: chunk.getOrNull(index)
                    ?: return@forEachIndexed
                val quality = priv.highestQuality
                    ?: UnifiedAudioQuality.fromKugouCode(priv.bestFreeQuality)
                if (quality != null) {
                    result[trackId] = quality
                }
            }
        }
        result
    }

    private fun KugouSongUrlResult.toPlaybackInfo(): SongPlaybackInfo =
        SongPlaybackInfo(
            url = url,
            isFreeTrial = isFreeTrial,
            level = unifiedQuality?.neteaseCode ?: quality.takeIf { it.isNotBlank() },
            loudness = volume?.let { TrackLoudness(lufs = it, peak = volumePeak) }
        )
}

// 跨平台播放路由聚合器：根据 TrackId.platform 分发至对应的 PlatformPlaybackProvider
class PlatformPlaybackRouter(
    providers: List<PlatformPlaybackProvider>
) {
    private val providerMap: Map<MusicPlatform, PlatformPlaybackProvider> =
        providers.associateBy { it.platform }

    fun providerFor(platform: MusicPlatform): PlatformPlaybackProvider? = providerMap[platform]

    fun supports(platform: MusicPlatform): Boolean = platform in providerMap

    val supportedPlatforms: Set<MusicPlatform>
        get() = providerMap.keys

    suspend fun resolvePlayback(
        trackId: TrackId,
        preferredQuality: UnifiedAudioQuality = UnifiedAudioQuality.EXHIGH,
        meta: PlaybackTrackMeta? = null
    ): Result<SongPlaybackInfo> {
        val provider = providerFor(trackId.platform)
            ?: return Result.failure(UnsupportedOperationException("未注册平台播放提供者: ${trackId.platform.key}"))
        return provider.resolvePlayback(trackId, preferredQuality, meta)
    }

    suspend fun getLyrics(
        trackId: TrackId,
        meta: PlaybackTrackMeta? = null
    ): Result<List<LyricLine>> {
        val provider = providerFor(trackId.platform)
            ?: return Result.failure(UnsupportedOperationException("未注册平台播放提供者: ${trackId.platform.key}"))
        return provider.getLyrics(trackId, meta)
    }

    suspend fun getRawLyrics(
        trackId: TrackId,
        meta: PlaybackTrackMeta? = null
    ): Result<String> {
        val provider = providerFor(trackId.platform)
            ?: return Result.failure(UnsupportedOperationException("未注册平台播放提供者: ${trackId.platform.key}"))
        return provider.getRawLyrics(trackId, meta)
    }

    suspend fun reportPlayStart(
        trackId: TrackId,
        meta: PlaybackTrackMeta? = null
    ): Result<Unit> =
        providerFor(trackId.platform)?.reportPlayStart(trackId, meta) ?: Result.success(Unit)

    suspend fun reportPlayEnd(
        trackId: TrackId,
        playedSeconds: Long,
        meta: PlaybackTrackMeta? = null
    ): Result<Unit> =
        providerFor(trackId.platform)?.reportPlayEnd(trackId, playedSeconds, meta) ?: Result.success(Unit)

    suspend fun getQualityLevels(tracks: List<TrackId>): Result<Map<TrackId, UnifiedAudioQuality>> {
        if (tracks.isEmpty()) return Result.success(emptyMap())
        val merged = LinkedHashMap<TrackId, UnifiedAudioQuality>()
        var firstFailure: Throwable? = null
        for ((platform, group) in tracks.groupBy { it.platform }) {
            val provider = providerFor(platform) ?: continue
            provider.getQualityLevels(group)
                .onSuccess { merged.putAll(it) }
                .onFailure { if (firstFailure == null) firstFailure = it }
        }
        return if (merged.isEmpty() && firstFailure != null) {
            Result.failure(firstFailure!!)
        } else {
            Result.success(merged)
        }
    }
}
