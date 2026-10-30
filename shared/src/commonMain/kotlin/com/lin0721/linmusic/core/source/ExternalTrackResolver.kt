package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "ExternalTrackResolver"
private const val FALLBACK_QUALITY = "lossless"

// 自带播放地址来源的外部歌曲，不走换源
interface ExternalTrackDirectResolver {
    fun owns(track: ExternalTrack): Boolean

    suspend fun resolve(track: ExternalTrack, quality: String): String?
}

// 外部平台歌曲没有官方链接，不看换源总开关，直接照搬网易云歌曲的换源流程：
// 社区源插件与远程 UNM 按「社区源优先」决定先后，音质跟随 Wi-Fi／流量设置，试听片段视为失败
class ExternalTrackResolver(
    private val sourceProviders: List<AudioSourceProvider>,
    private val sourcePreferences: SourcePreferences?,
    private val settingsPreferences: SettingsPreferences? = null,
    private val networkStateProvider: NetworkStateProvider? = null,
    private val unmApiClient: UnmApiClient? = null,
    // 远程 UNM 只认网易云 id，外部歌曲要先匹配网易云上的同一首
    private val neteaseLookup: NeteaseSongLookup? = null,
    private val directResolver: ExternalTrackDirectResolver? = null
) {

    suspend fun resolve(track: ExternalTrack): String? {
        if (directResolver?.owns(track) == true) return directResolver.resolve(track, preferredQuality())
        val communityFirst = sourcePreferences?.communityPriority?.first() ?: true
        return if (communityFirst) {
            resolveCommunity(track) ?: resolveUnm(track)
        } else {
            resolveUnm(track) ?: resolveCommunity(track)
        }
    }

    private suspend fun resolveCommunity(track: ExternalTrack): String? {
        val quality = preferredQuality()
        for (provider in sourceProviders) {
            val result = try {
                provider.resolveUrl(
                    songId = track.hash ?: track.id,
                    songName = track.name,
                    artists = track.artists,
                    albumName = track.albumName,
                    durationMs = track.durationMs,
                    quality = quality,
                    origin = track.platform
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "${provider.platform.displayName} 解析外部歌曲失败: ${e.message}")
                null
            } ?: continue
            if (result.url.isBlank()) continue
            if (result.isTrial || isTrialPlayUrl(result.url)) {
                AppLogger.i(TAG, "《${track.name}》解析到试听片段，继续尝试其他插件")
                continue
            }
            val actualQuality = result.quality.ifBlank { "未返回" }
            AppLogger.i(
                TAG,
                "《${track.name}》社区源解析成功 平台=${track.platform.key} 插件=${result.pluginId ?: provider.platform.key} " +
                    "请求音质=$quality 实际音质=$actualQuality 域名=${hostOf(result.url)}"
            )
            return result.url
        }
        return null
    }

    private suspend fun resolveUnm(track: ExternalTrack): String? {
        val prefs = sourcePreferences ?: return null
        val client = unmApiClient ?: return null
        if (!prefs.unmRemoteFallbackEnabled.first()) return null
        val serverUrl = prefs.unmServerUrl.first().trim()
        if (serverUrl.isBlank()) return null
        val neteaseId = try {
            neteaseLookup?.find(track)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "《${track.name}》匹配网易云歌曲失败: ${e.message}")
            null
        }
        if (neteaseId == null) {
            AppLogger.i(TAG, "《${track.name}》在网易云没有匹配到同一首，跳过远程 UNM")
            return null
        }
        // UNM 客户端已过滤试听链接
        val result = client.matchSong(serverUrl, neteaseId, null) ?: return null
        AppLogger.i(
            TAG,
            "《${track.name}》远程 UNM 解析成功 网易云id=$neteaseId 来源=${result.source ?: "auto"} 域名=${hostOf(result.url)}"
        )
        return result.url
    }

    private suspend fun preferredQuality(): String {
        val settings = settingsPreferences ?: return FALLBACK_QUALITY
        val onWifi = networkStateProvider?.isWifiConnected() ?: true
        return if (onWifi) settings.wifiQuality.first() else settings.mobileQuality.first()
    }

    // 只记域名不记完整链接，链接里带播放签名
    private fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrNull() ?: "未知"

    // 区分没配置与配置了但没解析出来，让用户知道该去哪里处理
    suspend fun explainFailure(track: ExternalTrack): String {
        if (directResolver?.owns(track) == true) return "《${track.name}》无法播放，提供它的插件已停用或没有返回地址"
        val prefs = sourcePreferences ?: return "无法获取播放直链"
        val communityReady = sourceProviders.isNotEmpty() && prefs.lxPluginEnabled.first() &&
            prefs.lxPlugins.first().any { it.isEnabled }
        val unmReady = prefs.unmRemoteFallbackEnabled.first() && prefs.unmServerUrl.first().isNotBlank()
        if (!communityReady && !unmReady) return "未配置可用的换源，请到 设置 › 音源与换源 中启用社区源插件或远程 UNM 服务"
        return "《${track.name}》换源失败，可能暂无该音源或插件已失效"
    }
}
