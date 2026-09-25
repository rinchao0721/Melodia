package com.lin0721.linmusic.feature.recognition.data

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.mapToAppError
import com.lin0721.linmusic.feature.recognition.data.dto.AudioMatchResponse
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

private const val TAG = "RecognitionRepository"

// 边录边识别每秒一个窗口，单次匹配不能沿用全局 30 秒读超时
private const val MATCH_TIMEOUT_MS = 8_000L

private const val MAX_CANDIDATES = 3

class RecognitionRepositoryImpl(
    private val api: RecognitionApi
) : RecognitionRepository {

    override suspend fun match(fingerprint: String, durationSeconds: Int): List<RecognitionCandidate> {
        require(fingerprint.isNotBlank()) { "指纹为空" }
        require(durationSeconds > 0) { "时长必须为正: $durationSeconds" }

        val response = try {
            withTimeoutOrNull(MATCH_TIMEOUT_MS) {
                api.matchAudio(sessionId = newSessionId(), durationSeconds = durationSeconds, fingerprint = fingerprint)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "识别请求失败: ${mapToAppError(e)}", e)
            throw RecognitionException(RecognitionFailure.NETWORK, "识别请求失败", e)
        } ?: throw RecognitionException(RecognitionFailure.NETWORK, "识别请求超时")

        if (response.code != 200) {
            AppLogger.e(TAG, "识别业务错误码 code=${response.code} msg=${response.message}")
            throw RecognitionException(RecognitionFailure.NETWORK, "识别服务返回 ${response.code}")
        }
        return response.toCandidates()
    }

    // 参考实现写死同一个 sessionId，这里每次随机，避免服务端按会话聚合
    private fun newSessionId(): String =
        Random.nextBytes(8).joinToString("") { "%02x".format(it) }
}

internal fun AudioMatchResponse.toCandidates(): List<RecognitionCandidate> =
    data?.result.orEmpty()
        .mapNotNull { result ->
            val song = result.song ?: return@mapNotNull null
            if (song.id <= 0L) return@mapNotNull null
            RecognitionCandidate(
                songId = song.id,
                title = song.name,
                artists = song.artists.joinToString(", ") { it.name },
                album = song.album.name,
                // 该接口下发的封面是 http 地址
                coverUrl = song.album.picUrl.replaceFirst("http://", "https://"),
                startTimeMs = result.startTime.coerceAtLeast(0L),
                durationMs = song.duration.coerceAtLeast(0L)
            )
        }
        .distinctBy { it.songId }
        .take(MAX_CANDIDATES)
