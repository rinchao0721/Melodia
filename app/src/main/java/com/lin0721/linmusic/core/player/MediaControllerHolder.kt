package com.lin0721.linmusic.core.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "MediaControllerHolder"

// 持有与 MelodiaPlaybackService 的 MediaController 连接，收敛所有播放器指令下发
class MediaControllerHolder(private val context: Context) {

    private var controller: MediaController? = null

    val isConnected: Boolean get() = controller != null

    val currentPosition: Long get() = controller?.currentPosition ?: 0L

    // 控制器未连接时返回 null，供调用方回退到本地缓存进度
    val currentPositionOrNull: Long? get() = controller?.currentPosition

    val duration: Long get() = controller?.duration ?: 0L

    val playbackState: Int get() = controller?.playbackState ?: Player.STATE_IDLE

    // 建立连接，onReady 在控制器就绪后、字段赋值前回调，用于同步初始状态
    suspend fun connect(listener: Player.Listener, onReady: (MediaController) -> Unit) {
        if (controller != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, MelodiaPlaybackService::class.java)
        )

        controller = suspendCancellableCoroutine { continuation ->
            val factory = MediaController.Builder(context, sessionToken).buildAsync()
            factory.addListener(
                {
                    try {
                        val mediaController = factory.get()
                        mediaController.addListener(listener)
                        AppLogger.i(TAG, "MediaController 连接成功")
                        onReady(mediaController)
                        continuation.resume(mediaController)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "MediaController 连接失败", e)
                        continuation.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )

            continuation.invokeOnCancellation {
                factory.cancel(true)
            }
        }
    }

    fun setRepeatMode(mode: PlayMode) {
        controller?.repeatMode = if (mode == PlayMode.SINGLE_LOOP)
            Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // 装载并起播单个曲目，支持从断点位置直接起播
    fun playItem(mediaItem: MediaItem, mode: PlayMode, startPosition: Long) {
        controller?.apply {
            repeatMode = if (mode == PlayMode.SINGLE_LOOP)
                Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setMediaItem(mediaItem, startPosition.coerceAtLeast(0L))
            prepare()
            play()
        }
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun pauseAndRewind() {
        controller?.pause()
        controller?.seekTo(0)
    }

    fun stopAndClear() {
        controller?.stop()
        controller?.clearMediaItems()
    }

    // deviceId 为 -1 表示恢复自动路由，交给 Service 端反查匹配的 AudioDeviceInfo
    fun setPreferredAudioDevice(deviceId: Int) {
        controller?.sendCustomCommand(
            SessionCommand("ACTION_SET_PREFERRED_AUDIO_DEVICE", Bundle()),
            Bundle().apply { putInt("device_id", deviceId) }
        )
    }
}
