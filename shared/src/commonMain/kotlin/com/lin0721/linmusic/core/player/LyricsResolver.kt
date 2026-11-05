package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricParser
import com.lin0721.linmusic.core.player.domain.LyricTimeline
import com.lin0721.linmusic.core.player.domain.TtmlLyricParser
import com.lin0721.linmusic.core.source.ExternalTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// 启用的各源同时查询：首个有效结果立即发出，其后每 250ms 按最新快照择优，保留最后一批结果。
enum class LyricsSource { AMLL, NETEASE, LOCAL, QQ, PLUGIN }

data class ResolvedLyrics(val lines: List<LyricLine>, val source: LyricsSource)

class LyricsResolver(
    private val playbackRepository: PlaybackRepository,
    // 按来源 Uri 读取本地歌词原文，平台无本地音乐时返回 null
    private val readLocalLyrics: suspend (sourceUri: String) -> String?,
    private val localUriOf: (songId: Long) -> String?,
    // 按 songId 读取 AMLL TTML 原文；平台未接入 AMLL 时保持默认实现返回 null
    private val readAmllLyrics: suspend (songId: Long) -> String? = { null },
    private val isAmllEnabled: suspend () -> Boolean = { true },
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val readQqLyrics: suspend (songId: Long) -> List<LyricLine> = { emptyList() },
    private val isQqEnabled: suspend () -> Boolean = { true },
    private val isNeteaseEnabled: suspend () -> Boolean = { true },
    // 外部平台歌曲：从播放队列找回歌曲信息，QQ 按自身信息查，网易与 AMLL 先匹配网易云上的同一首
    private val externalOf: (songId: Long) -> ExternalTrack? = { null },
    private val matchNeteaseId: suspend (ExternalTrack) -> Long? = { null },
    private val readExternalQqLyrics: suspend (ExternalTrack) -> List<LyricLine> = { emptyList() },
    // 插件歌词源：没有可用插件时不查询；外部歌曲带上自身信息，网易歌曲由实现方自行补全
    private val hasPluginLyrics: () -> Boolean = { false },
    private val readPluginLyrics: suspend (songId: Long, external: ExternalTrack?) -> List<LyricLine> = { _, _ -> emptyList() }
) {

    private companion object {
        const val AMLL_TIMEOUT_MS = 6000L
        const val QQ_TIMEOUT_MS = 8000L
        const val PLUGIN_TIMEOUT_MS = 10000L
        const val UPDATE_INTERVAL_MS = 250L
    }

    fun lyricsFor(songId: Long): Flow<Result<List<LyricLine>>> = lyricsWithSourceFor(songId)
        .map { result -> result.map { it.lines } }

    fun lyricsWithSourceFor(songId: Long): Flow<Result<ResolvedLyrics>> = flow {
        // 外部歌曲的 localUri 可能是预先解析的直链，不能当本地文件读歌词
        val external = if (songId < 0) externalOf(songId) else null
        if (songId < 0) AppLogger.i("LyricsResolver", "负数 id 歌词 songId=$songId 外部歌曲=${external?.resolveKey ?: "否"}")
        if (external != null) {
            emitAll(externalLyricsFor(songId, external))
            return@flow
        }
        val localUri = localUriOf(songId)
        if (localUri == null) {
            if (songId > 0) emitAll(onlineLyricsFor(songId)) else emit(Result.success(ResolvedLyrics(emptyList(), LyricsSource.LOCAL)))
            return@flow
        }
        if (songId > 0) {
            // 本地歌词先行，在线歌词到达后升级；已关联网易的本地歌曲也持续收集，仍能升级为 AMLL。
            val local = localLyrics(localUri)
            var emitted = local.lines.isNotEmpty()
            if (emitted) emit(Result.success(local))
            onlineLyricsFor(songId).collect { online ->
                if (online.getOrNull()?.lines.orEmpty().isNotEmpty()) {
                    emit(online)
                    emitted = true
                }
            }
            if (!emitted) emit(Result.success(local))
            return@flow
        }
        emit(Result.success(localLyrics(localUri)))
    }.flowOn(processingDispatcher)

    private suspend fun localLyrics(uri: String): ResolvedLyrics {
        val lines = readLocalLyrics(uri)?.let(LyricParser::parseLocal).orEmpty()
        return ResolvedLyrics(LyricTimeline.prepareLines(lines), LyricsSource.LOCAL)
    }

    private sealed interface SourceUpdate {
        data class Netease(val result: Result<List<LyricLine>>) : SourceUpdate
        data class Amll(val lines: List<LyricLine>) : SourceUpdate
        data class Qq(val lines: List<LyricLine>) : SourceUpdate
        data class Plugin(val lines: List<LyricLine>) : SourceUpdate
        data class Cancelled(val source: LyricsSource, val exception: CancellationException) : SourceUpdate
        data object Tick : SourceUpdate
    }

    private fun CoroutineScope.query(
        source: LyricsSource,
        updates: SendChannel<SourceUpdate>,
        block: suspend () -> SourceUpdate
    ) = launch {
        try {
            updates.send(block())
        } catch (e: CancellationException) {
            // 子查询自行取消也要通知协调器，避免等待一条永远不会到达的结果。
            updates.trySend(SourceUpdate.Cancelled(source, e))
        }
    }

    private fun onlineLyricsFor(songId: Long): Flow<Result<ResolvedLyrics>> =
        onlineLyrics(songId, neteaseId = songId, matchNetease = null, readQq = { readQqLyrics(songId) }, external = null)

    private fun externalLyricsFor(songId: Long, track: ExternalTrack): Flow<Result<ResolvedLyrics>> =
        onlineLyrics(
            songId,
            neteaseId = null,
            matchNetease = {
                try {
                    matchNeteaseId(track).also {
                        AppLogger.i("LyricsResolver", "外部歌曲《${track.name}》匹配网易云 id=${it ?: "无"}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w("LyricsResolver", "外部歌曲匹配网易云失败 songId=$songId: ${e.message}")
                    null
                }
            },
            readQq = {
                readExternalQqLyrics(track).also {
                    AppLogger.i("LyricsResolver", "外部歌曲《${track.name}》QQ 歌词 ${it.size} 行 时长=${track.durationMs}")
                }
            },
            external = track
        )

    // 已知网易 id 时直接查，不多一次挂起；需要匹配时网易与 AMLL 共享同一次匹配，匹配不到按无歌词处理，不算失败
    private fun onlineLyrics(
        songId: Long,
        neteaseId: Long?,
        matchNetease: (suspend () -> Long?)?,
        readQq: suspend () -> List<LyricLine>,
        external: ExternalTrack?
    ): Flow<Result<ResolvedLyrics>> = flow {
        coroutineScope {
            val amllEnabled = isAmllEnabled()
            val qqEnabled = isQqEnabled()
            val neteaseEnabled = isNeteaseEnabled()
            val pluginEnabled = hasPluginLyrics()
            val state = LyricsSourceState(amllEnabled, qqEnabled, neteaseEnabled, pluginEnabled)
            // 所有来源都关闭时没有可等待的查询，直接交出空结果
            if (state.isFinished) {
                emit(state.candidate())
                return@coroutineScope
            }
            val updates = Channel<SourceUpdate>(Channel.UNLIMITED)
            val jobs = mutableListOf<Job>()
            val matched = matchNetease?.let { match -> async(start = CoroutineStart.LAZY) { match() }.also { jobs += it } }
            suspend fun resolvedNeteaseId(): Long? = matched?.await() ?: neteaseId
            if (neteaseEnabled) {
                jobs += query(LyricsSource.NETEASE, updates) {
                    val id = resolvedNeteaseId()
                    SourceUpdate.Netease(if (id == null) Result.success(emptyList()) else fetchNeteaseResult(id))
                }
            }
            val amllJob = if (amllEnabled) {
                query(LyricsSource.AMLL, updates) {
                    SourceUpdate.Amll(resolvedNeteaseId()?.let { fetchAmllLines(it) }.orEmpty())
                }.also { jobs += it }
            } else null
            if (qqEnabled) jobs += query(LyricsSource.QQ, updates) { SourceUpdate.Qq(fetchQqLines(readQq)) }
            if (pluginEnabled) jobs += query(LyricsSource.PLUGIN, updates) { SourceUpdate.Plugin(fetchPluginLines(songId, external)) }
            val ticker = launch {
                while (isActive) {
                    delay(UPDATE_INTERVAL_MS)
                    updates.send(SourceUpdate.Tick)
                }
            }
            var displayed: ResolvedLyrics? = null
            suspend fun publish() {
                val candidate = state.candidate()
                val resolved = candidate.getOrNull()?.takeIf { it.lines.isNotEmpty() }
                when {
                    resolved != null -> if (resolved != displayed) {
                        emit(Result.success(resolved))
                        displayed = resolved
                        AppLogger.d("LyricsResolver", "歌词更新 songId=$songId source=${resolved.source} lines=${resolved.lines.size}")
                    }
                    // 全部结束仍无可用歌词：交出网易原结果，失败要让上层看到
                    state.isFinished && displayed == null -> emit(candidate)
                }
                state.markPublished()
            }
            // 与 sample 类似按固定周期取最新值；查询结束后仍消费最后一次 Tick，避免丢失尾值。
            try {
                while (!state.isFinished || state.dirty) {
                    when (val update = updates.receive()) {
                        is SourceUpdate.Netease -> state.onNetease(update.result)
                        is SourceUpdate.Amll -> state.onAmll(update.lines)
                        is SourceUpdate.Qq -> state.onQq(update.lines)
                        is SourceUpdate.Plugin -> state.onPlugin(update.lines)
                        is SourceUpdate.Cancelled -> if (!state.isOwnAmllCancellation(update.source)) throw update.exception
                        SourceUpdate.Tick -> if (state.dirty) publish()
                    }
                    if (state.shouldCancelAmll()) {
                        state.cancelAmll()
                        amllJob?.cancel()
                    }
                    // 首个有效结果与最终空结果不等窗口，之后的更新才按周期合并
                    if (state.dirty && displayed == null) publish()
                }
            } finally {
                ticker.cancel()
                jobs.forEach { it.cancel() }
                updates.cancel()
            }
        }
    }

    private suspend fun fetchQqLines(readQq: suspend () -> List<LyricLine>): List<LyricLine> = try {
        withTimeoutOrNull(QQ_TIMEOUT_MS) { readQq() }.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun fetchPluginLines(songId: Long, external: ExternalTrack?): List<LyricLine> = try {
        withTimeoutOrNull(PLUGIN_TIMEOUT_MS) { readPluginLyrics(songId, external) }.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /** AMLL 任何失败都降级为空列表；网易的失败要原样带回，上层需要区分"没有歌词"与"请求失败"。 */
    private suspend fun fetchAmllLines(songId: Long): List<LyricLine> = try {
        withTimeoutOrNull(AMLL_TIMEOUT_MS) { readAmllLyrics(songId) }
            ?.let(TtmlLyricParser::parse)
            .orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun fetchNeteaseResult(songId: Long): Result<List<LyricLine>> = try {
        playbackRepository.getLyrics(songId).first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
