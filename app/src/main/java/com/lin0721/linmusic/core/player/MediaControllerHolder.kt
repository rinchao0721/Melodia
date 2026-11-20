package com.lin0721.linmusic.core.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
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
    private var localPlayer: Player? = null
    private var registeredListener: Player.Listener? = null

    private val commandPlayer: Player? get() = localPlayer ?: controller

    val isConnected: Boolean get() = controller != null

    val currentPosition: Long get() = commandPlayer?.currentPosition ?: 0L

    // 控制器未连接时返回 null，供调用方回退到本地缓存进度
    val currentPositionOrNull: Long? get() = commandPlayer?.currentPosition

    val duration: Long get() = commandPlayer?.duration ?: 0L

    val playbackState: Int get() = commandPlayer?.playbackState ?: Player.STATE_IDLE

    val playWhenReady: Boolean get() = commandPlayer?.playWhenReady ?: false

    val currentTracks: Tracks get() = commandPlayer?.currentTracks ?: Tracks.EMPTY

    // 直连服务内本机播放器，避免 MediaSession 切到远程播放器后反控远端状态
    fun attachLocalPlayer(player: Player?) {
        val oldTarget = localPlayer ?: controller
        val listener = registeredListener
        if (oldTarget !== player && listener != null) {
            oldTarget?.removeListener(listener)
        }
        localPlayer = player
        val newTarget = localPlayer ?: controller
        if (listener != null && newTarget != null && oldTarget !== newTarget) {
            newTarget.addListener(listener)
        }
    }

    // 建立连接，onReady 在控制器就绪后、字段赋值前回调，用于同步初始状态
    suspend fun connect(listener: Player.Listener, onReady: (Player) -> Unit) {
        if (controller != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, MelodiaPlaybackService::class.java)
        )

        registeredListener = listener
        controller = suspendCancellableCoroutine { continuation ->
            val factory = MediaController.Builder(context, sessionToken).buildAsync()
            factory.addListener(
                {
                    try {
                        val mediaController = factory.get()
                        if (localPlayer == null) {
                            mediaController.addListener(listener)
                        }
                        AppLogger.i(TAG, "MediaController 连接成功")
                        onReady(localPlayer ?: mediaController)
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
        commandPlayer?.repeatMode = if (mode == PlayMode.SINGLE_LOOP)
            Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // 装载并起播单个曲目，支持从断点位置直接起播；playWhenReady=false 时只装载不起播
    fun playItem(mediaItem: MediaItem, mode: PlayMode, startPosition: Long, playWhenReady: Boolean = true) {
        commandPlayer?.apply {
            repeatMode = if (mode == PlayMode.SINGLE_LOOP)
                Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setMediaItem(mediaItem, startPosition.coerceAtLeast(0L))
            prepare()
            if (playWhenReady) play() else pause()
        }
    }

    fun play() {
        commandPlayer?.play()
    }

    // 音量平衡的曲目增益，服务侧平滑过渡
    fun setVolume(volume: Float) {
        commandPlayer?.volume = volume
    }

    fun pause() {
        commandPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        commandPlayer?.seekTo(positionMs)
    }

    fun pauseAndRewind() {
        commandPlayer?.pause()
        commandPlayer?.seekTo(0)
    }

    fun stopAndClear() {
        commandPlayer?.stop()
        commandPlayer?.clearMediaItems()
    }

    // deviceId 为 -1 表示恢复自动路由，交给 Service 端反查匹配的 AudioDeviceInfo
    fun setPreferredAudioDevice(deviceId: Int) {
        controller?.sendCustomCommand(
            SessionCommand("ACTION_SET_PREFERRED_AUDIO_DEVICE", Bundle()),
            Bundle().apply { putInt("device_id", deviceId) }
        )
    }

    // 主动断开控制器连接，确保关闭服务时无残留绑定阻碍服务销毁
    fun disconnect() {
        val listener = registeredListener
        if (listener != null) {
            localPlayer?.removeListener(listener)
            controller?.removeListener(listener)
        }
        registeredListener = null
        controller?.release()
        controller = null
    }
}
