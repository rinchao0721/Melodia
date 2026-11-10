package com.lin0721.linmusic.core.songquality

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.data.PlaybackApi
import com.lin0721.linmusic.core.player.data.SongDetailRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SongQualityCache"

// 合并同一屏行登记的等待时间
private const val BATCH_DELAY_MS = 300L

// 对齐服务端 song/detail 单次上限
private const val MAX_BATCH_SIZE = 1000

// 歌曲最高音质缓存：列表行拿不到接口自带音质时登记 id，攒一批用 song/detail 补查；开关关闭时不发请求
class SongQualityCache(
    enabledFlow: Flow<Boolean>,
    private val fetcher: suspend (List<Long>) -> Map<Long, String>,
    private val scope: CoroutineScope,
    private val batchDelayMs: Long = BATCH_DELAY_MS
) {
    val enabled: StateFlow<Boolean> = enabledFlow.stateIn(scope, SharingStarted.Eagerly, false)

    private val _levels = MutableStateFlow<Map<Long, String>>(emptyMap())
    val levels: StateFlow<Map<Long, String>> = _levels.asStateFlow()

    private val requests = Channel<Long>(Channel.UNLIMITED)

    init {
        scope.launch { consume() }
    }

    fun request(songId: Long) {
        if (songId <= 0 || !enabled.value || songId in _levels.value) return
        requests.trySend(songId)
    }

    private suspend fun consume() {
        // 查过的 id（含没查到的），只在本协程里读写
        val queried = HashSet<Long>()
        while (true) {
            val first = requests.receive()
            if (first in queried) continue
            delay(batchDelayMs)
            val batch = LinkedHashSet<Long>()
            batch += first
            while (true) {
                val next = requests.tryReceive().getOrNull() ?: break
                if (next !in queried) batch += next
            }
            for (chunk in batch.chunked(MAX_BATCH_SIZE)) {
                if (!enabled.value) break
                queried += chunk
                val result = try {
                    fetcher(chunk)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 失败的允许之后重新登记再查
                    queried -= chunk.toSet()
                    AppLogger.w(TAG, "补查最高音质失败：${chunk.size} 首", e)
                    continue
                }
                if (result.isNotEmpty()) _levels.update { it + result }
            }
        }
    }
}

// song/detail 取最高音质：优先顶层 privileges，缺失时回退 songs 内嵌的 privilege
suspend fun fetchSongMaxQuality(api: PlaybackApi, songIds: List<Long>): Map<Long, String> {
    val c = songIds.joinToString(prefix = "[", postfix = "]") { """{"id":$it}""" }
    val response = api.getSongDetail(SongDetailRequest(c = c))
    if (!response.isSuccess) error("song/detail code=${response.code}")
    val result = HashMap<Long, String>()
    response.songs.forEach { track ->
        val level = track.privilege?.maxBrLevel
        if (track.id > 0 && !level.isNullOrBlank()) result[track.id] = level
    }
    response.privileges.forEach { privilege ->
        val level = privilege.maxBrLevel
        if (privilege.id > 0 && !level.isNullOrBlank()) result[privilege.id] = level
    }
    return result
}
