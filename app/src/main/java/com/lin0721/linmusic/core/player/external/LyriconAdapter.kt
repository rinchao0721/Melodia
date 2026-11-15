package com.lin0721.linmusic.core.player.external

import android.content.Context
import android.media.session.PlaybackState
import android.os.SystemClock
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricAlignment
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.WordInfo
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.service.addConnectionListener
import kotlin.math.abs

class LyriconAdapter(private val context: Context) {
    companion object {
        private const val TAG = "LyriconAdapter"
        // 无行时长且无下一行时的兜底时长
        private const val FALLBACK_LINE_DURATION_MS = 4000L
        // 位置偏差容忍值：小于此值不重发，避免频繁跨进程调用
        private const val POSITION_DRIFT_TOLERANCE_MS = 300L
    }

    private var provider: LyriconProvider? = null
    private var isEnabled = false
    private var lastPlaying: Boolean? = null
    private var lastPositionMs = 0L
    private var lastPositionAt = 0L

    fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return
        isEnabled = enabled
        try {
            if (enabled) {
                val target = provider ?: createProvider()?.also { provider = it } ?: return
                target.register()
            } else {
                provider?.player?.setSong(null)
                // 清空驱动状态，下次开启时重新建立时间基准
                provider?.player?.setPlaybackState(null as PlaybackState?)
                provider?.unregister()
                lastPlaying = null
                lastPositionAt = 0L
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 注册状态切换失败: enabled=$enabled", e)
        }
    }

    private fun createProvider(): LyriconProvider? = try {
        LyriconFactory.createProvider(context.applicationContext).apply {
            service.addConnectionListener {
                onConnected { AppLogger.i(TAG, "Lyricon 已连接") }
                onReconnected { AppLogger.i(TAG, "Lyricon 已重连") }
                onDisconnected { AppLogger.i(TAG, "Lyricon 连接断开") }
                onConnectTimeout { AppLogger.w(TAG, "Lyricon 连接超时，中心服务可能未运行") }
            }
        }
    } catch (e: Exception) {
        AppLogger.w(TAG, "Lyricon Provider 创建失败", e)
        null
    }

    fun updateSong(
        songId: String,
        title: String,
        artist: String,
        durationMs: Long,
        lines: List<LyricLine>,
        showTranslation: Boolean
    ) {
        if (!isEnabled) return
        val player = provider?.player ?: return
        try {
            player.setDisplayTranslation(showTranslation)
            player.setDisplayRoma(true)
            player.setSong(
                Song(
                    id = songId,
                    name = title,
                    artist = artist,
                    duration = durationMs.coerceAtLeast(0L),
                    lyrics = lines.toRichLines(durationMs)
                )
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 更新曲目失败", e)
        }
    }

    fun setDisplayTranslation(show: Boolean) {
        if (!isEnabled) return
        try {
            provider?.player?.setDisplayTranslation(show)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 更新翻译显示失败", e)
        }
    }

    /**
     * 同步播放状态与位置。
     *
     * 词幕中心服务支持 PlaybackState 驱动模式：拿到 (位置, 速度, 更新时间) 后
     * 会自己按 elapsedRealtime 外推，不再依赖提供端高频写共享内存。
     * 之前只调 setPosition，共享内存里永远是上一次 1s 轮询的值，
     * 状态栏歌词因此最多慢一整秒，这就是不同步的来源。
     *
     * @param force 切歌、跳转、连接建立后必须重发，否则会被漂移判断吞掉
     */
    fun syncPlaybackState(isPlaying: Boolean, positionMs: Long, force: Boolean = false) {
        if (!isEnabled) return
        val player = provider?.player ?: return
        val position = positionMs.coerceAtLeast(0L)
        val now = SystemClock.elapsedRealtime()
        try {
            val playingChanged = lastPlaying != isPlaying
            if (!force && !playingChanged) {
                // 播放中位置会自己走；暂停时位置不该变。两者都只按偏差决定是否重发
                val expected = if (isPlaying) lastPositionMs + (now - lastPositionAt) else lastPositionMs
                if (abs(expected - position) <= POSITION_DRIFT_TOLERANCE_MS) return
            }
            player.setPlaybackState(
                PlaybackState.Builder()
                    .setState(
                        if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                        position,
                        1.0f,
                        now
                    )
                    .setActions(
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO
                    )
                    .build()
            )
            lastPlaying = isPlaying
            lastPositionMs = position
            lastPositionAt = now
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 同步播放状态失败", e)
        }
    }

    /** 切歌或重新连接后清掉时间基准，保证下一次同步一定发出去。 */
    fun invalidatePositionBase() {
        lastPositionAt = 0L
        lastPlaying = null
    }

    fun release() {
        isEnabled = false
        lastPlaying = null
        lastPositionAt = 0L
        try {
            provider?.destroy()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 释放失败", e)
        }
        provider = null
    }

    private fun List<LyricLine>.toRichLines(songDurationMs: Long): List<RichLyricLine> =
        mapIndexedNotNull { index, line ->
            if (line.text.isBlank()) return@mapIndexedNotNull null
            val end = resolveLineEnd(line, getOrNull(index + 1)?.timeMs, songDurationMs)
            val background = line.backgroundLine?.takeIf { it.text.isNotBlank() }
            RichLyricLine(
                begin = line.timeMs,
                end = end,
                isAlignedRight = line.alignment == LyricAlignment.END,
                text = line.text,
                words = line.words.toLyricWords(line.timeMs),
                secondary = background?.text,
                secondaryWords = background?.let { it.words.toLyricWords(it.timeMs) },
                translation = line.translation?.takeIf { it.isNotBlank() },
                roma = line.roma?.takeIf { it.isNotBlank() }
            )
        }

    // SDK 会丢弃 end <= begin 的行，这里保证结束时间严格大于开始时间
    private fun resolveLineEnd(line: LyricLine, nextStartMs: Long?, songDurationMs: Long): Long {
        val candidate = when {
            line.durationMs > 0 -> line.timeMs + line.durationMs
            nextStartMs != null && nextStartMs > line.timeMs -> nextStartMs
            songDurationMs > line.timeMs -> songDurationMs
            else -> line.timeMs + FALLBACK_LINE_DURATION_MS
        }
        return if (candidate > line.timeMs) candidate else line.timeMs + FALLBACK_LINE_DURATION_MS
    }

    private fun List<WordInfo>.toLyricWords(lineStartMs: Long): List<LyricWord>? {
        if (isEmpty()) return null
        return map { word ->
            val begin = lineStartMs + word.startOffsetMs
            LyricWord(begin = begin, end = begin + word.durationMs.coerceAtLeast(0L), text = word.text)
        }
    }
}
