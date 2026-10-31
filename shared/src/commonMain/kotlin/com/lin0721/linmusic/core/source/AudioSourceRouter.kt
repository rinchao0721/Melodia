package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private const val TAG = "AudioSourceRouter"

// PlaybackRepository 装饰器，在官方接口失败或仅为试听片段时自动通过 UNM 服务换源
class AudioSourceRouter(
    private val delegate: PlaybackRepository,
    private val unmApiClient: UnmApiClient,
    private val sourcePreferences: SourcePreferences,
    private val settingsPreferences: SettingsPreferences,
    private val networkStateProvider: NetworkStateProvider,
    private val providers: List<AudioSourceProvider> = emptyList()
) : PlaybackRepository {

    override fun getSongPlaybackInfo(songId: Long): Flow<Result<SongPlaybackInfo>> = flow {
        val fallbackEnabled = sourcePreferences.fallbackEnabled.first()

        // 1. 先尝试官方接口获取直链
        val officialInfoResult = delegate.getSongPlaybackInfo(songId).first()
        val officialInfo = officialInfoResult.getOrNull()

        // 官方返回完整音频且非试听时，直接使用官方链接
        if (officialInfo != null && !officialInfo.isFreeTrial && officialInfo.url.isNotBlank()) {
            emit(Result.success(officialInfo))
            return@flow
        }

        // 官方失败或仅为试听，检查是否启用换源
        if (!fallbackEnabled) {
            if (officialInfo != null && officialInfo.url.isNotBlank()) {
                AppLogger.d(TAG, "换源未启用，返回官方试听链接 songId=$songId")
                emit(Result.success(officialInfo))
            } else {
                AppLogger.d(TAG, "换源未启用，返回官方失败结果 songId=$songId")
                emit(Result.failure(officialInfoResult.exceptionOrNull() ?: AppError.BizError(-1, "无法获取播放链接")))
            }
            return@flow
        }

        // 2. 需换源且总开关开启
        AppLogger.i(TAG, "开始换源流程: songId=$songId (官方为试听: ${officialInfo?.isFreeTrial})")

        val communityPriority = sourcePreferences.communityPriority.first()
        var matchedUrl: String? = null
        var matchedSource: String? = null

        // 各来源失败原因的收集器：最终换源失败时随错误信息回传给 UI，避免只能靠抓日志定位
        val diagnostics = mutableListOf<String>()

        // 辅助函数：解析社区扩展源插件
        suspend fun tryResolveCommunity(): Pair<String, String>? {
            if (providers.isEmpty()) return null
            val track = delegate.getSongDetail(songId).first().getOrNull() ?: return null
            val quality = if (networkStateProvider.isWifiConnected()) {
                settingsPreferences.wifiQuality.first()
            } else {
                settingsPreferences.mobileQuality.first()
            }
            for (p in providers) {
                try {
                    val r = p.resolveUrl(
                        songId = songId.toString(),
                        songName = track.name,
                        artists = track.ar.joinToString("/") { it.name },
                        albumName = track.al.name,
                        durationMs = track.dt,
                        quality = quality,
                        diagnostics = diagnostics
                    )
                    if (r != null && r.url.isNotBlank() && !r.isTrial && !isTrialPlayUrl(r.url)) {
                        return Pair(r.url, "社区源 (${p.platform.displayName})")
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "${p.platform.displayName} 解析失败: ${e.message}")
                }
            }
            return null
        }

        // 辅助函数：解析远程 UNM 兜底服务
        suspend fun tryResolveRemoteUnm(): Pair<String, String>? {
            val remoteFallbackEnabled = sourcePreferences.unmRemoteFallbackEnabled.first()
            val serverUrl = sourcePreferences.unmServerUrl.first().trim()
            if (!remoteFallbackEnabled || serverUrl.isBlank()) {
                diagnostics.add("远程UNM 未启用")
                return null
            }
            AppLogger.d(TAG, "尝试远程 UNM 服务: $serverUrl songId=$songId")
            val remoteResult = unmApiClient.matchSong(serverUrl, songId, null)
            if (remoteResult != null && remoteResult.url.isNotBlank() && !isTrialPlayUrl(remoteResult.url)) {
                AppLogger.i(TAG, "远程 UNM 服务解析成功: songId=$songId 来源=${remoteResult.source}")
                return Pair(remoteResult.url, "远程UNM (${remoteResult.source ?: "auto"})")
            }
            diagnostics.add("远程UNM 未命中")
            return null
        }

        if (communityPriority) {
            // 社区源优先：先尝试社区扩展插件，未命中再走远程 UNM
            val communityRes = tryResolveCommunity()
            if (communityRes != null) {
                matchedUrl = communityRes.first
                matchedSource = communityRes.second
            } else {
                val unmRes = tryResolveRemoteUnm()
                if (unmRes != null) {
                    matchedUrl = unmRes.first
                    matchedSource = unmRes.second
                }
            }
        } else {
            // 远程 UNM 优先：先尝试远程 UNM，未命中再走社区扩展插件
            val unmRes = tryResolveRemoteUnm()
            if (unmRes != null) {
                matchedUrl = unmRes.first
                matchedSource = unmRes.second
            } else {
                val communityRes = tryResolveCommunity()
                if (communityRes != null) {
                    matchedUrl = communityRes.first
                    matchedSource = communityRes.second
                }
            }
        }

        // 3. 结果判定
        if (matchedUrl != null && matchedUrl.isNotBlank()) {
            AppLogger.i(TAG, "换源成功: songId=$songId 最终来源=$matchedSource")
            emit(Result.success(SongPlaybackInfo(url = matchedUrl, isFreeTrial = false)))
        } else {
            AppLogger.w(TAG, "所有换源均未命中: songId=$songId 详情=${diagnostics.joinToString("; ")}")
            if (officialInfo != null && officialInfo.url.isNotBlank()) {
                AppLogger.i(TAG, "兜底返回官方试听链接: songId=$songId")
                emit(Result.success(officialInfo.copy(trialFallback = officialInfo.isFreeTrial)))
            } else {
                val detail = if (diagnostics.isEmpty()) "" else "：${diagnostics.distinct().joinToString(" · ")}"
                emit(Result.failure(officialInfoResult.exceptionOrNull() ?: AppError.BizError(-1, "换源未能获取到可用播放链接$detail")))
            }
        }
    }.catch { e ->
        AppLogger.e(TAG, "换源流程异常 songId=$songId", e)
        emit(Result.failure(e))
    }

    override fun getSongUrl(songId: Long): Flow<Result<String>> =
        getSongPlaybackInfo(songId).map { result -> result.map { it.url } }

    // 委托其余方法
    override fun getLyrics(songId: Long) = delegate.getLyrics(songId)
    override fun getRawLyrics(songId: Long) = delegate.getRawLyrics(songId)
    override fun getSongDetail(songId: Long) = delegate.getSongDetail(songId)
    override fun getSimilarSongs(songId: Long) = delegate.getSimilarSongs(songId)
    override fun getIntelligenceSongs(songId: Long, playlistId: Long) = delegate.getIntelligenceSongs(songId, playlistId)
    override fun reportStartPlay(songId: Long, source: PlaySource?) = delegate.reportStartPlay(songId, source)
    override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?) = delegate.reportPlayEnd(songId, playedSeconds, source)
    override val playlistRecorded: SharedFlow<Long> get() = delegate.playlistRecorded
}
