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
    private val listenerBinding = PlayerListenerBinding()

    // 服务销毁导致会话断开时回调
    var onDisconnected: (() -> Unit)? = null

    private val commandPlayer: Player? get() = localPlayer ?: controller

    val isConnected: Boolean get() = controller != null

    val currentPosition: Long get() = commandPlayer?.currentPosition ?: 0L

    // 控制器未连接时返回 null，供调用方回退到本地缓存进度
    val currentPositionOrNull: Long? get() = commandPlayer?.currentPosition

    val duration: Long get() = commandPlayer?.duration ?: 0L

    val playbackState: Int get() = commandPlayer?.playbackState ?: Player.STATE_IDLE

    val playWhenReady: Boolean get() = commandPlayer?.playWhenReady ?: false

    val currentTracks: Tracks get() = commandPlayer?.currentTracks ?: Tracks.EMPTY

    // 直连服务内本机播放器，避免 MediaSession 切到远程播放器后反控远端状态；
    // 服务可能先于 connect 创建（联动唤醒），监听统一经 rebindListener 挂载
    fun attachLocalPlayer(player: Player?) {
        localPlayer = player
        rebindListener()
    }

    private fun rebindListener() {
        listenerBinding.bindTo(localPlayer ?: controller)
    }

    private fun dropController(target: MediaController) {
        if (controller !== target) return
        controller = null
        rebindListener()
        target.release()
    }

    private fun handleControllerDisconnected(disconnected: MediaController) {
        if (controller !== disconnected) return
        AppLogger.w(TAG, "MediaController 已断开")
        dropController(disconnected)
        onDisconnected?.invoke()
    }

    // 建立连接，onReady 在控制器就绪且监听挂好后回调，用于同步初始状态
    suspend fun connect(listener: Player.Listener, onReady: (Player) -> Unit) {
        if (controller != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, MelodiaPlaybackService::class.java)
        )

        listenerBinding.setListener(listener)
        rebindListener()
        suspendCancellableCoroutine { continuation ->
            val factory = MediaController.Builder(context, sessionToken)
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        handleControllerDisconnected(controller)
                    }
                })
                .buildAsync()
            factory.addListener(
                {
                    var connected: MediaController? = null
                    try {
                        val mediaController = factory.get()
                        connected = mediaController
                        controller = mediaController
                        rebindListener()
                        AppLogger.i(TAG, "MediaController 连接成功")
                        onReady(localPlayer ?: mediaController)
                        continuation.resume(Unit)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "MediaController 连接失败", e)
                        connected?.let(::dropController)
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

    // 曲目增益 (dB)，仅本机播放器支持
    fun setTrackGainDb(gainDb: Double) {
        (localPlayer as? CrossfadePlayer)?.setTrackGainDb(gainDb)
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
        listenerBinding.setListener(null)
        // 先置空，release 触发的 onDisconnected 不再走被动断开流程
        val released = controller
        controller = null
        released?.release()
    }
}
