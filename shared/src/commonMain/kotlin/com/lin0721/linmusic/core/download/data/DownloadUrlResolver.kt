package com.lin0721.linmusic.core.download.data

import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.qualityRank
import com.lin0721.linmusic.core.plugin.PluginDownloadLink
import com.lin0721.linmusic.core.plugin.PluginTrackQuery
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
    private val plugins: suspend (query: PluginTrackQuery, level: String) -> PluginDownloadLink? = { _, _ -> null }
) {

    suspend fun resolve(songId: Long, level: String, query: PluginTrackQuery): DownloadUrlOutcome {
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
        AppLogger.w(TAG, "官方下载链接不可用 songId=$songId level=$level code=${response.code}: $reason")
        if (!fallbackEnabled()) return DownloadUrlOutcome.Rejected(reason)
        val link = try {
            plugins(query, level)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "插件下载源出错 songId=$songId", e)
            null
        }?.takeIf { it.url.toHttpUrlOrNull() != null } ?: return DownloadUrlOutcome.Rejected(reason)
        return DownloadUrlOutcome.Ready(
            ResolvedDownload(
                url = link.url,
                encodeType = fileType(link),
                size = 0,
                level = link.quality?.lowercase()?.takeIf { qualityRank(it) >= 0 } ?: UNKNOWN_PLUGIN_LEVEL,
                headers = sanitizeHeaders(link.headers),
                fromPlugin = true
            )
        )
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
    }
}
