package com.lin0721.linmusic.core.player.external

import android.content.Context
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

class LyriconAdapter(private val context: Context) {
    companion object {
        private const val TAG = "LyriconAdapter"
        // 无行时长且无下一行时的兜底时长
        private const val FALLBACK_LINE_DURATION_MS = 4000L
    }

    private var provider: LyriconProvider? = null
    private var isEnabled = false
    private var lastPlaying: Boolean? = null

    fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return
        isEnabled = enabled
        try {
            if (enabled) {
                val target = provider ?: createProvider()?.also { provider = it } ?: return
                target.register()
            } else {
                provider?.player?.setSong(null)
                provider?.unregister()
                lastPlaying = null
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

    fun updatePlaybackState(isPlaying: Boolean, positionMs: Long) {
        if (!isEnabled) return
        val player = provider?.player ?: return
        try {
            // 位置写共享内存开销很小，播放状态仅在变化时跨进程发送
            player.setPosition(positionMs.coerceAtLeast(0L))
            if (lastPlaying != isPlaying) {
                lastPlaying = isPlaying
                player.setPlaybackState(isPlaying)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 更新播放状态失败", e)
        }
    }

    fun release() {
        isEnabled = false
        lastPlaying = null
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
