package com.lin0721.linmusic.core.player.external

import android.content.Context
import android.content.Intent
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricLine

class LyriconAdapter(private val context: Context) {
    companion object {
        private const val TAG = "LyriconAdapter"
        private const val ACTION_REGISTER = "io.github.proify.lyricon.lyric.bridge.REGISTER_PROVIDER"
        private const val ACTION_UPDATE_SONG = "io.github.proify.lyricon.ACTION_UPDATE_SONG"
        private const val ACTION_PLAYBACK_STATE = "io.github.proify.lyricon.ACTION_PLAYBACK_STATE"
    }

    private var isEnabled = false

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled) {
            register()
        }
    }

    private fun register() {
        try {
            val intent = Intent(ACTION_REGISTER).apply {
                putExtra("package", context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 注册广播发送失败", e)
        }
    }

    fun updateSong(
        songId: String,
        title: String,
        artist: String,
        durationMs: Long,
        lines: List<LyricLine>
    ) {
        if (!isEnabled) return
        try {
            val intent = Intent(ACTION_UPDATE_SONG).apply {
                putExtra("songId", songId)
                putExtra("title", title)
                putExtra("artist", artist)
                putExtra("duration", durationMs)
                putExtra("lyric", LyricInfoBuilder.buildPlainLrc(lines))
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 更新曲目失败", e)
        }
    }

    fun updatePlaybackState(isPlaying: Boolean, positionMs: Long) {
        if (!isEnabled) return
        try {
            val intent = Intent(ACTION_PLAYBACK_STATE).apply {
                putExtra("isPlaying", isPlaying)
                putExtra("position", positionMs)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 更新播放状态失败", e)
        }
    }

    fun release() {
        isEnabled = false
    }
}
