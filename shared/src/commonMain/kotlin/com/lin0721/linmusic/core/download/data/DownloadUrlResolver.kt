package com.lin0721.linmusic.core.download.data

import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.DOWNLOAD_QUALITY_LEVELS
import com.lin0721.linmusic.core.model.qualityRank
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.data.SongUrlItem
import com.lin0721.linmusic.core.player.data.isTrialAudio
import com.lin0721.linmusic.core.plugin.PluginDownloadLink
import com.lin0721.linmusic.core.plugin.PluginTrackQuery
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val TAG = "DownloadUrlResolver"

private val LOSSLESS_AND_ABOVE = setOf("lossless", "hires", "jyeffect", "sky", "jymaster")
private val COMPRESSED_ENCODE_TYPES = setOf("mp3", "aac", "m4a")
private val FILE_TYPES = setOf("mp3", "flac", "m4a", "aac", "ogg", "wav")

// 插件没给音质时按最低档记录，避免之后选高音质被判为已下载
private const val UNKNOWN_PLUGIN_LEVEL = "standard"

// RFC 7230 的 token 字符；OkHttp 遇到非法请求头会直接抛异常
private val HEADER_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
private const val MAX_HEADERS = 32
// 由宿主或 OkHttp 管理；Accept-Encoding 会关掉自动解压，存下压缩后的字节
private val RESERVED_HEADERS = setOf(
    "range", "accept-encoding", "host", "connection", "content-length", "transfer-encoding", "te", "upgrade"
)
private const val MAX_HEADER_VALUE_LENGTH = 4096

// size 为 0 表示未知，由下载器按 Content-Length 校验
data class ResolvedDownload(
    val url: String,
    val encodeType: String,
    val size: Long,
    val level: String,
    val headers: Map<String, String>,
    val fromPlugin: Boolean
)

const val PLUGIN_NOT_BETTER = "插件提供的音质不高于已下载的文件，已保留原文件"

// 插件来源不高于已有文件时不下载，下载完成后旧文件会被删除
fun ResolvedDownload.notBetterThan(existing: DownloadRecord?): Boolean =
    fromPlugin && existing != null && qualityRank(existing.quality) >= qualityRank(level)

sealed interface DownloadUrlOutcome {
    data class Ready(val download: ResolvedDownload) : DownloadUrlOutcome
    data class Rejected(val reason: String) : DownloadUrlOutcome
}

// 官方下载接口优先；拿不到完整链接且开启换源时询问插件下载源，宿主自身不做换源
class DownloadUrlResolver(
    private val official: suspend (songId: Long, level: String) -> SongDownloadUrlResponse,
    private val fallbackEnabled: suspend () -> Boolean = { false },
    private val plugins: suspend (query: PluginTrackQuery, level: String) -> PluginDownloadLink? = { _, _ -> null },
    private val platformResolver: (suspend (trackId: TrackId, level: String, kugouMeta: KugouTrackMeta?, query: PluginTrackQuery) -> SongPlaybackInfo?)? = null,
    private val isPlatformEnabled: suspend (MusicPlatform) -> Boolean = { true },
    // 部分声音/播客节目的 mainSong 在 download/url/v1 无直链，可回退到 player/url/v1
    private val playerUrlFallback: (suspend (songId: Long, level: String) -> SongUrlItem?)? = null,
    // 所选音质不可用时是否自动向下匹配实际可用的最高音质
    private val downgradeFallbackEnabled: suspend () -> Boolean = { false }
) {

    suspend fun resolve(
        songId: Long,
        level: String,
        query: PluginTrackQuery,
        preferPlayerFallback: Boolean = false
    ): DownloadUrlOutcome {
        if (downgradeFallbackEnabled()) {
            return resolveWithHighestAvailableFallback(songId, level, query)
        }
        val response = official(songId, level)
        val item = response.data
        val url = item?.url
        val reason = when {
            !response.isSuccess || item == null || url.isNullOrBlank() -> NO_URL
            item.isTrialAudio -> TRIAL_ONLY
            else -> {
                val encodeType = (item.type ?: item.encodeType)?.lowercase()
                // 无权限时服务端会静默降级为压缩格式
                if (level in LOSSLESS_AND_ABOVE && encodeType in COMPRESSED_ENCODE_TYPES) {
                    NEED_HIGHER_PLAN
                } else {
                    return DownloadUrlOutcome.Ready(
                        ResolvedDownload(url, encodeType ?: "mp3", item.size, item.level ?: level, emptyMap(), fromPlugin = false)
                    )
                }
            }
        }
        if (reason == NO_URL || preferPlayerFallback) {
            resolveFromPlayerFallback(songId, level)?.let { return DownloadUrlOutcome.Ready(it) }
        }
        AppLogger.w(TAG, "官方下载链接不可用 songId=$songId level=$level code=${response.code}: $reason")
        return resolveFromPlugins(reason, level, query, logKey = "songId=$songId")
    }

    // 当所选高音质在此媒体不可用时，允许向下匹配该媒体实际可用的最高音质（仍跳过试听片段）
    private suspend fun resolveWithHighestAvailableFallback(
        songId: Long,
        requestedLevel: String,
        query: PluginTrackQuery
    ): DownloadUrlOutcome {
        val (initialDownload, initialReason) = tryDirectTier(songId, requestedLevel, allowCompressedDowngrade = true)
        var best = initialDownload
        var lastReason = initialReason

        val requestedRank = qualityRank(requestedLevel)
        if (requestedRank > 0) {
            val currentRank = best?.let { qualityRank(it.level) } ?: -1
            for (rank in (requestedRank - 1) downTo (currentRank + 1)) {
                val candidateLevel = DOWNLOAD_QUALITY_LEVELS[rank]
                val (candidate, reason) = tryDirectTier(songId, candidateLevel, allowCompressedDowngrade = true)
                if (candidate != null) {
                    val candidateRank = qualityRank(candidate.level)
                    if (best == null || candidateRank > qualityRank(best.level)) {
                        best = candidate
                    }
                    // 已拿到的音质不低于当前尝试档位，无需继续向下试探
                    if (candidateRank >= rank) break
                } else if (lastReason == NO_URL && reason != NO_URL) {
                    lastReason = reason
                }
            }
        }

        if (best != null && (requestedRank < 0 || qualityRank(best.level) >= requestedRank)) {
            return DownloadUrlOutcome.Ready(best)
        }
        if (fallbackEnabled()) {
            val pluginDownload = resolvePluginCandidate(requestedLevel, query, logKey = "songId=$songId")
            if (pluginDownload != null && (best == null || qualityRank(pluginDownload.level) > qualityRank(best.level))) {
                return DownloadUrlOutcome.Ready(pluginDownload)
            }
        }
        best?.let { return DownloadUrlOutcome.Ready(it) }
        AppLogger.w(TAG, "全音质匹配失败 songId=$songId requested=$requestedLevel: $lastReason")
        return DownloadUrlOutcome.Rejected(lastReason)
    }

    private suspend fun tryDirectTier(
        songId: Long,
        level: String,
        allowCompressedDowngrade: Boolean
    ): Pair<ResolvedDownload?, String> {
        val response = runCatching { official(songId, level) }.getOrNull()
        val item = response?.data
        val url = item?.url
        var officialDownload: ResolvedDownload? = null
        val reason = when {
            response == null || !response.isSuccess || item == null || url.isNullOrBlank() -> NO_URL
            item.isTrialAudio -> TRIAL_ONLY
            else -> {
                val encodeType = (item.type ?: item.encodeType)?.lowercase()
                val rawLevel = item.level?.lowercase()?.takeIf { qualityRank(it) >= 0 }
                val effectiveLevel = rawLevel ?: if (encodeType in COMPRESSED_ENCODE_TYPES && level in LOSSLESS_AND_ABOVE) {
                    "exhigh"
                } else {
                    level
                }
                if (!allowCompressedDowngrade && level in LOSSLESS_AND_ABOVE && encodeType in COMPRESSED_ENCODE_TYPES) {
                    NEED_HIGHER_PLAN
                } else {
                    officialDownload = ResolvedDownload(
                        url = url,
                        encodeType = encodeType ?: if (effectiveLevel in LOSSLESS_AND_ABOVE) "flac" else "mp3",
                        size = item.size,
                        level = effectiveLevel,
                        headers = emptyMap(),
                        fromPlugin = false
                    )
                    ""
                }
            }
        }

        val needsPlayerCheck = officialDownload == null ||
            (allowCompressedDowngrade && qualityRank(officialDownload.level) < qualityRank(level))
        val playerDownload = if (needsPlayerCheck) {
            resolveFromPlayerFallback(songId, level, allowCompressedDowngrade = allowCompressedDowngrade)
        } else {
            null
        }

        val best = listOfNotNull(officialDownload, playerDownload)
            .maxByOrNull { qualityRank(it.level) }
        return best to (if (best != null) "" else reason)
    }

    private suspend fun resolveFromPlayerFallback(
        songId: Long,
        level: String,
        allowCompressedDowngrade: Boolean = false
    ): ResolvedDownload? {
        val resolver = playerUrlFallback ?: return null
        val playItem = try {
            resolver(songId, level)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "播放直链兜底查询失败 songId=$songId", e)
            null
        } ?: return null
        val playUrl = playItem.url?.takeIf { it.isNotBlank() && it.toHttpUrlOrNull() != null } ?: return null
        if (playItem.isTrialAudio) return null
        val rawLevel = playItem.level?.lowercase()?.takeIf { qualityRank(it) >= 0 }
        val encodeType = playItem.type?.lowercase()?.takeIf { it in FILE_TYPES }
            ?: inferEncodeTypeFromUrl(playUrl, rawLevel ?: level)
        if (!allowCompressedDowngrade && level in LOSSLESS_AND_ABOVE && encodeType in COMPRESSED_ENCODE_TYPES) {
            return null
        }
        val resolvedLevel = rawLevel ?: if (encodeType in COMPRESSED_ENCODE_TYPES && level in LOSSLESS_AND_ABOVE) {
            "exhigh"
        } else {
            level
        }
        return ResolvedDownload(
            url = playUrl,
            encodeType = encodeType,
            size = playItem.size,
            level = resolvedLevel,
            headers = emptyMap(),
            fromPlugin = false
        )
    }

    // 跨平台下载直链解析：网易云走官方下载接口；其余平台先查官方播放/下载链路，试听、关闭或失败时按设置走插件换源
    suspend fun resolvePlatform(
        trackId: TrackId,
        level: String,
        query: PluginTrackQuery,
        kugouMeta: KugouTrackMeta? = null
    ): DownloadUrlOutcome {
        if (trackId.platform == MusicPlatform.NETEASE) {
            val neteaseId = trackId.neteaseIdOrNull()
            if (neteaseId != null && neteaseId > 0L) {
                return resolve(neteaseId, level, query)
            }
        }

        if (!isPlatformEnabled(trackId.platform)) {
            AppLogger.i(TAG, "平台总开关已关闭，跳过官方下载解析 trackId=$trackId")
            return resolveFromPlugins(PLATFORM_DISABLED, level, query, logKey = "trackId=$trackId")
        }

        val allowDowngrade = downgradeFallbackEnabled()
        var (best, lastReason) = resolvePlatformDirectCandidate(trackId, level, kugouMeta, query)
        val requestedRank = qualityRank(level)

        if (allowDowngrade && requestedRank > 0 && (best == null || qualityRank(best.level) < requestedRank)) {
            val triedPlatformQualities = mutableSetOf<UnifiedAudioQuality?>()
            triedPlatformQualities += UnifiedAudioQuality.fromCode(level, trackId.platform)
            val currentRank = best?.let { qualityRank(it.level) } ?: -1
            for (rank in (requestedRank - 1) downTo (currentRank + 1)) {
                val candidateLevel = DOWNLOAD_QUALITY_LEVELS[rank]
                val mappedQuality = UnifiedAudioQuality.fromCode(candidateLevel, trackId.platform)
                if (!triedPlatformQualities.add(mappedQuality)) continue
                val (candidate, reason) = resolvePlatformDirectCandidate(trackId, candidateLevel, kugouMeta, query)
                if (candidate != null) {
                    val candidateRank = qualityRank(candidate.level)
                    if (best == null || candidateRank > qualityRank(best.level)) {
                        best = candidate
                    }
                    if (candidateRank >= rank) break
                } else if (lastReason == NO_URL && reason != NO_URL) {
                    lastReason = reason
                }
            }
        }

        if (best != null && (!allowDowngrade || requestedRank < 0 || qualityRank(best.level) >= requestedRank)) {
            return DownloadUrlOutcome.Ready(best)
        }
        if (fallbackEnabled()) {
            val pluginDownload = resolvePluginCandidate(level, query, logKey = "trackId=$trackId")
            if (pluginDownload != null && (best == null || qualityRank(pluginDownload.level) > qualityRank(best.level))) {
                return DownloadUrlOutcome.Ready(pluginDownload)
            }
        }
        best?.let { return DownloadUrlOutcome.Ready(it) }
        AppLogger.w(TAG, "跨平台官方链接不可用 trackId=$trackId level=$level: $lastReason")
        return DownloadUrlOutcome.Rejected(lastReason)
    }

    private suspend fun resolvePlatformDirectCandidate(
        trackId: TrackId,
        level: String,
        kugouMeta: KugouTrackMeta?,
        query: PluginTrackQuery
    ): Pair<ResolvedDownload?, String> {
        val officialInfo = try {
            platformResolver?.invoke(trackId, level, kugouMeta, query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "跨平台官方链接查询失败 trackId=$trackId", e)
            null
        }

        val officialUrl = officialInfo?.url?.takeIf { it.isNotBlank() && it.toHttpUrlOrNull() != null }
        if (officialInfo != null && officialUrl != null && !officialInfo.isFreeTrial) {
            val resolvedLevel = officialInfo.level?.lowercase()?.takeIf { qualityRank(it) >= 0 } ?: level
            val encodeType = inferEncodeTypeFromUrl(officialUrl, resolvedLevel)
            return ResolvedDownload(
                url = officialUrl,
                encodeType = encodeType,
                size = 0L,
                level = resolvedLevel,
                headers = emptyMap(),
                fromPlugin = false
            ) to ""
        }
        val reason = if (officialInfo?.isFreeTrial == true) TRIAL_ONLY else NO_URL
        return null to reason
    }

    private suspend fun resolveFromPlugins(
        reason: String,
        level: String,
        query: PluginTrackQuery,
        logKey: String
    ): DownloadUrlOutcome {
        if (!fallbackEnabled()) return DownloadUrlOutcome.Rejected(reason)
        return resolvePluginCandidate(level, query, logKey)?.let { DownloadUrlOutcome.Ready(it) }
            ?: DownloadUrlOutcome.Rejected(reason)
    }

    private suspend fun resolvePluginCandidate(
        level: String,
        query: PluginTrackQuery,
        logKey: String
    ): ResolvedDownload? {
        val link = try {
            plugins(query, level)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "插件下载源出错 $logKey", e)
            null
        }?.takeIf { it.url.toHttpUrlOrNull() != null } ?: return null
        return ResolvedDownload(
            url = link.url,
            encodeType = fileType(link),
            size = 0,
            level = link.quality?.lowercase()?.takeIf { qualityRank(it) >= 0 } ?: UNKNOWN_PLUGIN_LEVEL,
            headers = sanitizeHeaders(link.headers),
            fromPlugin = true
        )
    }

    private fun inferEncodeTypeFromUrl(url: String, level: String): String {
        val file = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        file.substringAfterLast('.', "").lowercase().takeIf { it in FILE_TYPES }?.let { return it }
        return if (level in LOSSLESS_AND_ABOVE) "flac" else "mp3"
    }

    // 用作扩展名，只接受已知格式；插件没给时从链接后缀推断
    private fun fileType(link: PluginDownloadLink): String {
        link.type?.trim()?.lowercase()?.takeIf { it in FILE_TYPES }?.let { return it }
        val file = link.url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        return file.substringAfterLast('.', "").lowercase().takeIf { it in FILE_TYPES } ?: "mp3"
    }

    // OkHttp 只接受制表符与可打印 ASCII 的取值
    private fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> = headers
        .filter { (name, value) ->
            HEADER_NAME.matches(name) && name.lowercase() !in RESERVED_HEADERS && !name.startsWith("if-", ignoreCase = true) &&
                value.length <= MAX_HEADER_VALUE_LENGTH && value.all { it == '\t' || it in ' '..'~' }
        }
        .entries.take(MAX_HEADERS)
        .associate { it.key to it.value }

    private companion object {
        const val NO_URL = "获取下载链接失败"
        const val TRIAL_ONLY = "该音质仅支持试听，需要 VIP/购买后才能完整下载"
        const val NEED_HIGHER_PLAN = "需要更高会员等级才能下载该音质"
        const val PLATFORM_DISABLED = "该平台总开关已关闭"
    }
}
