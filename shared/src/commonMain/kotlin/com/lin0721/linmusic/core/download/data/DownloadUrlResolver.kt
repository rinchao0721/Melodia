package com.lin0721.linmusic.core.download.data

import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.source.isTrialPlayUrl
import kotlinx.coroutines.CancellationException

private val LOSSLESS_AND_ABOVE = setOf("lossless", "hires", "jyeffect", "sky", "jymaster")
private val COMPRESSED_ENCODE_TYPES = setOf("mp3", "aac", "m4a")
private val URL_ENCODE_TYPES = setOf("mp3", "flac", "m4a", "aac", "ogg", "wav")

// 换源直链没有音质信息，按最低档记录，避免被误判为已完成高音质下载
private const val FALLBACK_LEVEL = "standard"

// 下载用的链接与元数据；换源结果缺少 size / encodeType，由这里补齐
data class DownloadSource(
    val url: String,
    val encodeType: String,
    val size: Long,
    val level: String,
    val fromFallback: Boolean
)

sealed interface DownloadUrlOutcome {
    data class Ready(val source: DownloadSource) : DownloadUrlOutcome
    data class Rejected(val reason: String) : DownloadUrlOutcome
}

// 官方下载接口拿不到可用链接时，复用播放侧 AudioSourceRouter 的换源结果。
// 只决议「用哪个链接」，网络与异常重试仍交给两端下载器。
class DownloadUrlResolver(
    private val fallbackEnabled: suspend () -> Boolean,
    private val official: suspend (Long, String) -> SongDownloadUrlResponse,
    private val playback: suspend (Long) -> SongPlaybackInfo?
) {

    suspend fun resolve(songId: Long, level: String): DownloadUrlOutcome {
        val response = official(songId, level)
        val item = response.data
        val officialUrl = item?.url?.takeIf { it.isNotBlank() }

        // 官方成功且非试听：保留原有的音质静默降级判定
        if (response.isSuccess && item != null && officialUrl != null && !item.isTrialAudio) {
            val encodeType = (item.type ?: item.encodeType)?.lowercase() ?: "mp3"
            if (level in LOSSLESS_AND_ABOVE && encodeType in COMPRESSED_ENCODE_TYPES) {
                return DownloadUrlOutcome.Rejected(NEED_HIGHER_PLAN)
            }
            return DownloadUrlOutcome.Ready(
                DownloadSource(
                    url = officialUrl,
                    encodeType = encodeType,
                    size = item.size,
                    level = item.level ?: level,
                    fromFallback = false
                )
            )
        }

        // 只在用户打开换源总开关时回退，避免下载绕过播放侧的设置
        if (fallbackEnabled()) {
            val info = try {
                playback(songId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val url = info?.url?.takeIf { it.isNotBlank() && !isTrialPlayUrl(it) }
            if (info != null && !info.isFreeTrial && !info.trialFallback && url != null) {
                return DownloadUrlOutcome.Ready(
                    DownloadSource(
                        url = url,
                        encodeType = guessEncodeType(url),
                        // 换源链接不提供大小，下载器按响应头的 Content-Length 推断
                        size = 0,
                        level = FALLBACK_LEVEL,
                        fromFallback = true
                    )
                )
            }
        }

        val reason = if (response.isSuccess && item != null && item.isTrialAudio) TRIAL_ONLY else NO_URL
        return DownloadUrlOutcome.Rejected(reason)
    }

    // 换源直链没有 encodeType，只能从路径后缀推断；拿不准时按 mp3 保存
    private fun guessEncodeType(url: String): String {
        val file = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        return file.substringAfterLast('.', "").lowercase().takeIf { it in URL_ENCODE_TYPES } ?: "mp3"
    }

    private companion object {
        const val NO_URL = "获取下载链接失败"
        const val TRIAL_ONLY = "该音质仅支持试听，需要 VIP/购买后才能完整下载"
        const val NEED_HIGHER_PLAN = "需要更高会员等级才能下载该音质"
    }
}
