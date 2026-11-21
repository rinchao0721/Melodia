package com.lin0721.linmusic.core.player

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.lin0721.linmusic.core.lanlink.RemoteVolumeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

// Android 系统媒体音量桥接：供局域网被控端读写本机媒体音量并监听按键变化
class AndroidVolumeBridge(context: Context) : RemoteVolumeBridge {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _volume = MutableStateFlow(readVolumePercent())
    override val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _lastAudibleVolume = MutableStateFlow(_volume.value.takeIf { it > 0 } ?: 100)
    override val lastAudibleVolume: StateFlow<Int> = _lastAudibleVolume.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshFromSystem()
        }
    }

    init {
        runCatching {
            appContext.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer
            )
        }
    }

    override fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val min = minStreamVolume()
        val max = maxStreamVolume()
        val span = (max - min).coerceAtLeast(1)
        val targetIndex = (min + (clamped / 100f) * span).roundToInt().coerceIn(min, max)
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
        }
        _volume.value = clamped
        if (clamped > 0) {
            _lastAudibleVolume.value = clamped
        }
    }

    private fun refreshFromSystem() {
        val current = readVolumePercent()
        _volume.value = current
        if (current > 0) {
            _lastAudibleVolume.value = current
        }
    }

    private fun readVolumePercent(): Int {
        val min = minStreamVolume()
        val max = maxStreamVolume()
        val span = (max - min).coerceAtLeast(1)
        val current = runCatching {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }.getOrDefault(max)
        return (((current - min).toFloat() / span) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun minStreamVolume(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        } else {
            0
        }

    private fun maxStreamVolume(): Int =
        runCatching { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(15)
            .coerceAtLeast(1)
}
