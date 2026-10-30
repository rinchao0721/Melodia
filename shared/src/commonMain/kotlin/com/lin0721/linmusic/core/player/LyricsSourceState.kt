package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricTimeline

// 多源择优的状态与判定，只含同步逻辑，协程收发由 LyricsResolver 负责；插件歌词优先级最低。
internal class LyricsSourceState(
    amllEnabled: Boolean,
    private val qqEnabled: Boolean,
    neteaseEnabled: Boolean = true,
    pluginEnabled: Boolean = false
) {
    private var netease: Result<List<LyricLine>>? = null
    private var amll = emptyList<LyricLine>()
    private var qq = emptyList<LyricLine>()
    private var plugin = emptyList<LyricLine>()
    private var neteasePending = neteaseEnabled
    private var amllPending = amllEnabled
    private var qqPending = qqEnabled
    private var pluginPending = pluginEnabled
    private var amllCancelled = false

    // 自上次发布后是否有新结果
    var dirty = false
        private set

    val isFinished: Boolean get() = !neteasePending && !amllPending && !qqPending && !pluginPending

    fun onNetease(result: Result<List<LyricLine>>) {
        netease = result
        neteasePending = false
        dirty = true
    }

    fun onAmll(lines: List<LyricLine>) {
        if (!amllPending) return
        amll = lines
        amllPending = false
        dirty = true
    }

    fun onQq(lines: List<LyricLine>) {
        if (!qqPending) return
        qq = lines
        qqPending = false
        dirty = true
    }

    fun onPlugin(lines: List<LyricLine>) {
        if (!pluginPending) return
        plugin = lines
        pluginPending = false
        dirty = true
    }

    // 网易成功但无歌词、且已启用的 QQ 也无歌词时，不再等待 AMLL；网易失败或关闭不触发，AMLL 与网易相互独立。
    fun shouldCancelAmll(): Boolean {
        if (!amllPending || !qqEnabled || qqPending) return false
        val result = netease ?: return false
        return result.isSuccess && cleanLines(result.getOrNull().orEmpty()).isEmpty() && cleanLines(qq).isEmpty()
    }

    fun cancelAmll() {
        amllPending = false
        amllCancelled = true
        dirty = true
    }

    // 协调器主动取消的 AMLL 查询会回报取消事件，这类事件应忽略
    fun isOwnAmllCancellation(source: LyricsSource): Boolean = source == LyricsSource.AMLL && amllCancelled

    fun candidate(): Result<ResolvedLyrics> = chooseLyrics(amll, netease ?: Result.success(emptyList()), qq, plugin)

    fun markPublished() {
        dirty = false
    }
}

internal fun chooseLyrics(
    amll: List<LyricLine>,
    netease: Result<List<LyricLine>>,
    qq: List<LyricLine> = emptyList(),
    plugin: List<LyricLine> = emptyList()
): Result<ResolvedLyrics> {
    val a = cleanLines(amll)
    val n = cleanLines(netease.getOrNull().orEmpty())
    val q = cleanLines(qq)
    val p = cleanLines(plugin)
    val (lines, source) = when {
        a.any { it.words.isNotEmpty() } -> a to LyricsSource.AMLL
        n.any { it.words.isNotEmpty() } -> n to LyricsSource.NETEASE
        q.any { it.words.isNotEmpty() } -> q to LyricsSource.QQ
        p.any { it.words.isNotEmpty() } -> p to LyricsSource.PLUGIN
        a.isNotEmpty() -> a to LyricsSource.AMLL
        n.isNotEmpty() -> n to LyricsSource.NETEASE
        q.isNotEmpty() -> q to LyricsSource.QQ
        p.isNotEmpty() -> p to LyricsSource.PLUGIN
        // 都没内容：保留网易原结果，是失败就继续向上抛
        else -> return netease.map { ResolvedLyrics(it, LyricsSource.NETEASE) }
    }
    // 载入时统一做一次时间轴整理：补全行时长、把背景和声并入同组、裁掉标注误差级重叠，
    // 但保留有意为之的重叠行（对唱），这样上层才能识别出同时需要高亮的多行。
    return Result.success(ResolvedLyrics(LyricTimeline.prepareLines(lines), source))
}

internal fun cleanLines(lines: List<LyricLine>): List<LyricLine> = lines
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
