package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.flow.first

private const val TAG = "LxAudioSourceProvider"

// LX 自定义插件音源 Provider 适配器（支持多插件按优先级轮询）
// 各平台的歌曲标识互不通用：只有与 origin 同平台的插件源能直接用 songId，其他平台要先搜索匹配出自己的标识
class LxAudioSourceProvider(
    private val runtime: LxPluginRuntime,
    private val sourcePreferences: SourcePreferences,
    private val searchProviders: List<PlatformSearchProvider> = emptyList()
) : AudioSourceProvider {

    override val platform: MusicPlatform = MusicPlatform.LX

    override suspend fun resolveUrl(
        songId: String,
        songName: String,
        artists: String,
        albumName: String?,
        durationMs: Long,
        quality: String,
        origin: MusicPlatform,
        diagnostics: MutableList<String>?
    ): SourceResult? {
        val enabled = sourcePreferences.lxPluginEnabled.first()
        if (!enabled) {
            diagnostics?.add("社区源未启用")
            return null
        }

        val plugins = sourcePreferences.lxPlugins.first().filter { it.isEnabled }
        if (plugins.isEmpty()) {
            diagnostics?.add("无已启用的社区源脚本")
            return null
        }

        // 多源脚本（依次试 kw/kg/tx）常被 12s 上限截断，宽松模式放宽到 30s
        val timeoutMs = if (sourcePreferences.lxPluginLooseTimeout.first()) {
            LxScriptSupport.LOOSE_RESOLVE_TIMEOUT_MS
        } else {
            LxScriptSupport.RESOLVE_TIMEOUT_MS
        }

        // 确保沙盒池与当前已启用的插件保持同步
        runtime.syncActivePlugins(plugins)

        val originKey = origin.lxKey
        // 同一首歌在各平台的匹配结果，多个插件之间复用，null 表示没匹配上
        val matched = mutableMapOf<String, String?>()

        // 按照用户调整的插件优先级顺序逐个尝试
        for (plugin in plugins) {
            val supported = runtime.getSupportedSources(plugin.id).ifEmpty { plugin.sources }
            for (source in candidateSources(originKey, supported)) {
                val sourceSongId = if (source == originKey && songId.isNotBlank()) {
                    songId
                } else {
                    matched.getOrPut(source) { matchSongId(source, songName, artists, albumName, durationMs) }
                }
                if (sourceSongId == null) {
                    diagnostics?.add("$source 未匹配到同一首歌")
                    continue
                }

                val startedAt = System.currentTimeMillis()
                try {
                    val url = runtime.resolveMusicUrl(
                        pluginId = plugin.id,
                        source = source,
                        songId = sourceSongId,
                        songName = songName,
                        singer = artists,
                        albumName = albumName ?: "",
                        durationMs = durationMs,
                        quality = quality,
                        timeoutMs = timeoutMs
                    )
                    val elapsed = System.currentTimeMillis() - startedAt
                    if (!url.isNullOrBlank() && isTrialPlayUrl(url)) {
                        AppLogger.w(TAG, "插件 [${plugin.name}] 通过 [$source] 返回的是试听链接，继续尝试下一个: $songName")
                        diagnostics?.add("$source 仅试听")
                    } else if (!url.isNullOrBlank()) {
                        AppLogger.i(TAG, "插件 [${plugin.name}] 成功通过 [$source] 解析直链: $songName")
                        return SourceResult(
                            url = url,
                            platform = platform,
                            quality = quality
                        )
                    } else {
                        // 超时与「脚本自己返回空」在运行时接口上没有区分，用耗时贴近上限来判定
                        val label = if (elapsed >= timeoutMs - TIMEOUT_JITTER_MS) {
                            "$source 超时(${timeoutMs / 1000}s)"
                        } else {
                            "$source 无直链"
                        }
                        diagnostics?.add(label)
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "插件 [${plugin.name}] 解析平台 [$source] 失败: ${e.message}")
                    diagnostics?.add("$source 解析异常")
                }
            }
        }
        return null
    }

    // 歌曲所属平台排最前；其余平台只收能搜索匹配的，拿不到对应标识的不尝试，避免把别家的 id 当成自己的
    private fun candidateSources(originKey: String, supported: List<String>): List<String> {
        val searchable = searchProviders.mapTo(hashSetOf()) { it.platform.lxKey }
        val others = PRIORITY_SOURCES.filter { it != originKey && it in searchable }
        return (listOf(originKey) + others).filter { it in supported }
    }

    // 在目标平台搜索并按歌名、歌手、时长匹配，没有把握时返回 null，宁可不播也不播错歌
    private suspend fun matchSongId(
        source: String,
        songName: String,
        artists: String,
        albumName: String?,
        durationMs: Long
    ): String? {
        val provider = searchProviders.firstOrNull { it.platform.lxKey == source } ?: return null
        val candidates = try {
            provider.search("$songName $artists".trim(), 0, MATCH_SEARCH_LIMIT)
        } catch (e: Exception) {
            AppLogger.w(TAG, "[$source] 搜索匹配失败: ${e.message}")
            return null
        }
        val best = SongMatcher.pickBestCandidate(
            candidates = candidates,
            targetName = songName,
            targetArtists = artists.split(ARTIST_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() },
            targetAlbum = albumName,
            targetDurationMs = durationMs
        )
        if (best == null) AppLogger.d(TAG, "[$source] 未匹配到同一首歌: $songName - $artists")
        return best?.let { it.hash ?: it.id }
    }

    override suspend fun search(keyword: String, offset: Int, limit: Int): List<ExternalTrack> {
        return emptyList()
    }

    private companion object {
        // 歌曲所属平台之外的尝试顺序；咪咕与网易云没有可用的搜索源，只在作为歌曲所属平台时参与
        val PRIORITY_SOURCES = listOf("kw", "kg", "tx")
        val ARTIST_SEPARATORS = Regex("[、&;，,/|·・]+")
        const val MATCH_SEARCH_LIMIT = 10
        // 运行时接口把「超时」和「返回空」都折成 null，用耗时是否贴住上限来区分二者
        const val TIMEOUT_JITTER_MS = 500L
    }
}
