package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.LyricsContent
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricParser
import com.lin0721.linmusic.core.player.domain.LyricTimeline
import com.lin0721.linmusic.core.player.domain.LyricsKind
import com.lin0721.linmusic.core.player.domain.lyricsKind
import com.lin0721.linmusic.core.player.domain.TtmlLyricParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeoutOrNull

// 已匹配网易的歌优先网易歌词（逐字与翻译更全），拿不到再读本地。
// 接入 AMLL 后：AMLL TTML 与网易并行拉取，AMLL 拿到逐字时序就直接采信（TTML 的逐字与背景和声
// 比网易 YRC 更完整）；否则等双方到齐后一起比较，有逐字的胜出，都没逐字时取有内容的一方。
enum class LyricsSource { AMLL, NETEASE, LOCAL }

data class ResolvedLyrics(
    val lines: List<LyricLine>,
    val source: LyricsSource,
    val kind: LyricsKind
)

class LyricsResolver(
    private val playbackRepository: PlaybackRepository,
    // 按来源 Uri 读取本地歌词原文，平台无本地音乐时返回 null
    private val readLocalLyrics: suspend (sourceUri: String) -> String?,
    private val localUriOf: (songId: Long) -> String?,
    // 按 songId 读取 AMLL TTML 原文；平台未接入 AMLL 时保持默认实现返回 null
    private val readAmllLyrics: suspend (songId: Long) -> String? = { null },
    private val isAmllEnabled: suspend () -> Boolean = { true },
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private companion object {
        // 真机上 TLS 加 XML 解析偶发超过 1.2s，首次择优要留出窗口，否则慢镜像会把结果拖成网易歌词。
        // 网易一侧刻意不加超时，保持上游"等到出结果为止"的行为。
        const val SELECTION_WINDOW_MS = 2500L
        const val AMLL_TIMEOUT_MS = 4000L
    }

    fun lyricsFor(songId: Long): Flow<Result<List<LyricLine>>> = lyricsWithSourceFor(songId)
        .map { result -> result.map { it.lines } }

    fun lyricsWithSourceFor(songId: Long): Flow<Result<ResolvedLyrics>> = flow {
        val localUri = localUriOf(songId)
        if (localUri == null) {
            if (songId > 0) emitAll(onlineLyricsFor(songId)) else emit(
                Result.success(ResolvedLyrics(emptyList(), LyricsSource.LOCAL, LyricsKind.UNAVAILABLE))
            )
            return@flow
        }
        if (songId > 0) {
            val online = onlineLyricsFor(songId).first()
            if (online.getOrNull()?.kind?.let { it != LyricsKind.UNAVAILABLE } == true) {
                emit(online)
                return@flow
            }
        }
        val lines = readLocalLyrics(localUri)?.let(LyricParser::parseLocal).orEmpty()
        val prepared = LyricTimeline.prepareLines(lines)
        emit(Result.success(ResolvedLyrics(prepared, LyricsSource.LOCAL, prepared.lyricsKind())))
    }.flowOn(processingDispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun onlineLyricsFor(songId: Long): Flow<Result<ResolvedLyrics>> = flow {
        if (!isAmllEnabled()) {
            emit(chooseWithSource(emptyList(), fetchNeteaseResult(songId)))
            return@flow
        }
        coroutineScope {
            val amll = async { fetchAmllLines(songId) }
            val netease = async { fetchNeteaseResult(songId) }
            try {
                // 窗口内优先等待 AMLL：若已带逐字时序直接定型，避免慢镜像在播放中途替换歌词
                val amllWindowLines = withTimeoutOrNull(SELECTION_WINDOW_MS) { amll.await() }
                if (amllWindowLines?.any { it.words.isNotEmpty() } == true) {
                    emit(Result.success(ResolvedLyrics(amllWindowLines, LyricsSource.AMLL, amllWindowLines.lyricsKind())))
                    return@coroutineScope
                }
                val neteaseResult = netease.await()
                // 网易已就绪，若此时 AMLL 已返回（包括窗口外刚好完成）则参与择优，否则视作未命中
                val amllLines = if (amll.isCompleted) amll.getCompleted() else emptyList()
                emit(chooseWithSource(amllLines, neteaseResult))
            } finally {
                amll.cancel()
                netease.cancel()
            }
        }
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

    private suspend fun fetchNeteaseResult(songId: Long): Result<LyricsContent> = try {
        playbackRepository.getLyrics(songId).first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    internal fun choose(amll: List<LyricLine>, netease: Result<LyricsContent>): Result<List<LyricLine>> {
        return chooseWithSource(amll, netease).map { it.lines }
    }

    internal fun chooseWithSource(amll: List<LyricLine>, netease: Result<LyricsContent>): Result<ResolvedLyrics> {
        val a = cleanLines(amll)
        val neteaseContent = netease.getOrNull()
        val n = cleanLines(neteaseContent?.lines.orEmpty())
        val (lines, source, kind) = when {
            a.any { it.words.isNotEmpty() } -> Triple(a, LyricsSource.AMLL, a.lyricsKind())
            n.any { it.words.isNotEmpty() } -> Triple(n, LyricsSource.NETEASE, neteaseContent!!.kind)
            a.isNotEmpty() -> Triple(a, LyricsSource.AMLL, a.lyricsKind())
            n.isNotEmpty() -> Triple(n, LyricsSource.NETEASE, neteaseContent!!.kind)
            // 两边都没内容：保留网易原结果，是失败就继续向上抛
            else -> return netease.map { ResolvedLyrics(it.lines, LyricsSource.NETEASE, it.kind) }
        }
        // 载入时统一做一次时间轴整理：补全行时长、把背景和声并入同组、裁掉标注误差级重叠，
        // 但保留有意为之的重叠行（对唱），这样上层才能识别出同时需要高亮的多行。
        return Result.success(ResolvedLyrics(LyricTimeline.prepareLines(lines), source, kind))
    }
}

private fun cleanLines(lines: List<LyricLine>): List<LyricLine> = lines
    .filter { it.timeMs >= 0 && it.text.isNotBlank() }
    .map(::cleanLine)
    .sortedBy { it.timeMs }

// 逐字时序不合法时整行退回普通行——宁可整行高亮，也不能渲染出错位的逐字；
// 背景和声行（TTML x-bg）走同一套校验。
private fun cleanLine(line: LyricLine): LyricLine {
    val wordsValid = line.words.isNotEmpty() &&
        line.words.any { it.durationMs > 0 } &&
        line.words.all {
            it.startOffsetMs >= 0 && it.durationMs >= 0 &&
                it.startOffsetMs <= line.durationMs && it.durationMs <= line.durationMs - it.startOffsetMs
        } &&
        line.words.zipWithNext().all { (a, b) -> a.startOffsetMs <= b.startOffsetMs } &&
        line.words.joinToString("") { it.text } == line.text
    return line.copy(
        words = if (wordsValid) line.words else emptyList(),
        backgroundLine = line.backgroundLine
            ?.takeIf { it.timeMs >= 0 && it.text.isNotBlank() }
            ?.let(::cleanLine)
    )
}
