package com.lin0721.linmusic.core.player

import android.media.AudioDeviceInfo
import android.os.Handler
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "CrossfadePlayer"
private const val FADE_STEP_MS = 16L

// 会话播放器：内部两台 ExoPlayer 轮换，对外始终是同一个 Player。
// 带 crossfadeMs 的切歌先在空闲播放器里静音缓冲，就绪后才接管并等功率交叉淡化；
// 缓冲期间 setMediaItems 的 future 保持未完成，SimpleBasePlayer 用占位状态对外只报一次切歌
@OptIn(UnstableApi::class)
class CrossfadePlayer(
    private val primary: ExoPlayer,
    private val secondary: ExoPlayer,
    private val audioAttributes: AudioAttributes
) : ForwardingSimpleBasePlayer(primary) {

    private val handler = Handler(primary.applicationLooper)

    private var handleAudioFocus = true
    private var metadataTransformer: ((MediaMetadata) -> MediaMetadata)? = null

    private var pendingIncoming: ExoPlayer? = null
    private var pendingFuture: SettableFuture<Unit>? = null
    private var pendingFadeMs = 0L

    private var outgoing: ExoPlayer? = null
    private var outgoingStartGain = 1f
    private var fadeStartElapsedMs = 0L
    private var fadeDurationMs = 0L

    private val active: ExoPlayer
        get() = if (player === secondary) secondary else primary

    private val fadeStep = object : Runnable {
        override fun run() {
            val out = outgoing ?: return
            val progress = ((SystemClock.elapsedRealtime() - fadeStartElapsedMs).toFloat() / fadeDurationMs).coerceIn(0f, 1f)
            out.volume = CrossfadePolicy.fadeOutGain(progress, outgoingStartGain)
            active.volume = CrossfadePolicy.fadeInGain(progress)
            if (progress >= 1f) {
                releaseOutgoing()
            } else {
                handler.postDelayed(this, FADE_STEP_MS)
            }
        }
    }

    init {
        primary.setAudioAttributes(audioAttributes, handleAudioFocus)
        secondary.setAudioAttributes(audioAttributes, false)
        val internalListener = InternalListener()
        primary.addListener(internalListener)
        secondary.addListener(internalListener)
    }

    fun setHandleAudioFocus(handle: Boolean) {
        handleAudioFocus = handle
        active.setAudioAttributes(audioAttributes, handle)
    }

    fun setPreferredAudioDevice(device: AudioDeviceInfo?) {
        primary.setPreferredAudioDevice(device)
        secondary.setPreferredAudioDevice(device)
    }

    fun setMetadataTransformer(transformer: ((MediaMetadata) -> MediaMetadata)?) {
        metadataTransformer = transformer
        invalidateState()
    }

    // 外部歌词等元数据来源变化时刷新对外状态
    fun notifyMetadataChanged() {
        invalidateState()
    }

    override fun getState(): State {
        val base = super.getState()
        // 通知栏上/下一首按钮依赖这两个命令，底层单曲播放器本身不会提供
        val commands = base.availableCommands.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
        // 淡化期间音量每帧变化，对外固定为 1，避免向所有控制器高频广播
        val builder = base.buildUpon().setAvailableCommands(commands).setVolume(1f)
        val transformer = metadataTransformer
        if (transformer != null && !base.timeline.isEmpty) {
            builder.setPlaylist(base.timeline, base.currentTracks, transformer(base.currentMetadata))
        }
        return builder.build()
    }

    // 父类在切歌等待期间把占位状态标成 BUFFERING；此时旧歌仍在出声，沿用 READY，
    // 否则 PlayerManager 会判定为未播放，连续切歌时不再下发淡化
    override fun getPlaceholderState(suggestedPlaceholderState: State): State {
        if (pendingIncoming == null) return suggestedPlaceholderState
        return suggestedPlaceholderState.buildUpon()
            .setPlaybackState(Player.STATE_READY)
            .build()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        val extras = mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaMetadata?.extras
        val fadeMs = extras?.getLong(CrossfadePolicy.EXTRA_CROSSFADE_MS, 0L) ?: 0L
        val startOffsetMs = extras?.getLong(CrossfadePolicy.EXTRA_CROSSFADE_START_MS, 0L) ?: 0L
        // 上一轮还在缓冲就被新请求顶替：缓冲中的播放器直接复用，旧 future 推迟到新操作入队后再完成，
        // 否则两次操作之间 SimpleBasePlayer 会读到旧播放器状态，多报一次切回旧歌
        pendingFuture?.let { stale -> handler.post { stale.set(Unit) } }
        pendingFuture = null
        val abandoned = pendingIncoming
        pendingIncoming = null
        if (fadeMs <= 0L || !active.isPlaying) {
            abandoned?.run {
                stop()
                clearMediaItems()
            }
            releaseOutgoing()
            return super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
        }
        // 新歌跳过开头静音，直接从有效开头起播
        return startCrossfade(mediaItems, startIndex, maxOf(startPositionMs, startOffsetMs), fadeMs)
    }

    // 上/下一首已由会话回调 onPlayerCommandRequest 转给 PlayerManager；
    // 底层单曲播放器的上一首会退化成回到开头，这里直接吞掉
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        return when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> Futures.immediateVoidFuture()
            else -> super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (!playWhenReady) {
            completePendingImmediately()
            releaseOutgoing()
        }
        return super.handleSetPlayWhenReady(playWhenReady)
    }

    override fun handleStop(): ListenableFuture<*> {
        completePendingImmediately()
        releaseOutgoing()
        return super.handleStop()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        other(active).repeatMode = repeatMode
        return super.handleSetRepeatMode(repeatMode)
    }

    override fun handleRelease(): ListenableFuture<*> {
        handler.removeCallbacksAndMessages(null)
        pendingFuture?.set(Unit)
        pendingFuture = null
        pendingIncoming = null
        outgoing = null
        primary.release()
        secondary.release()
        return Futures.immediateVoidFuture()
    }

    private fun startCrossfade(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
        fadeMs: Long
    ): ListenableFuture<*> {
        // 上一轮淡出还没结束时先停掉它；正在淡入的当前播放器保持现有音量，成为新一轮的淡出方
        stopOutgoing()
        val incoming = other(active)
        incoming.stop()
        incoming.volume = 0f
        incoming.repeatMode = active.repeatMode
        incoming.setMediaItems(mediaItems, startIndex, startPositionMs)
        incoming.playWhenReady = true
        incoming.prepare()

        val future = SettableFuture.create<Unit>()
        pendingIncoming = incoming
        pendingFuture = future
        pendingFadeMs = fadeMs
        return future
    }

    private fun onIncomingReady(incoming: ExoPlayer) {
        // 同一台播放器可能已被新请求复用并重新缓冲，以执行时的状态为准
        if (pendingIncoming !== incoming || incoming.playbackState != Player.STATE_READY) return
        val previous = active
        takeOver(incoming)
        outgoing = previous
        outgoingStartGain = previous.volume
        fadeStartElapsedMs = SystemClock.elapsedRealtime()
        fadeDurationMs = pendingFadeMs.coerceAtLeast(1L)
        handler.post(fadeStep)
        finishPending()
    }

    // 焦点必须先从旧播放器释放再交给新播放器，否则两台互抢焦点会被系统暂停
    private fun takeOver(incoming: ExoPlayer) {
        val previous = active
        previous.setAudioAttributes(audioAttributes, false)
        incoming.setAudioAttributes(audioAttributes, handleAudioFocus)
        setPlayer(incoming)
    }

    private fun completePendingImmediately(playWhenReady: Boolean = true) {
        val incoming = pendingIncoming ?: return
        val previous = active
        takeOver(incoming)
        incoming.volume = 1f
        if (!playWhenReady) incoming.playWhenReady = false
        previous.stop()
        previous.clearMediaItems()
        previous.volume = 1f
        finishPending()
    }

    private fun finishPending() {
        pendingIncoming = null
        val future = pendingFuture
        pendingFuture = null
        future?.set(Unit)
    }

    // 中止淡化：停掉淡出方，当前播放器恢复满音量
    private fun releaseOutgoing() {
        val hadOutgoing = outgoing != null
        stopOutgoing()
        if (hadOutgoing) active.volume = 1f
    }

    private fun stopOutgoing() {
        handler.removeCallbacks(fadeStep)
        val out = outgoing ?: return
        outgoing = null
        out.stop()
        out.clearMediaItems()
        out.volume = 1f
    }

    private fun other(player: ExoPlayer): ExoPlayer = if (player === primary) secondary else primary

    private inner class InternalListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val exo = player as? ExoPlayer ?: return
            if (exo === pendingIncoming && exo.playbackState == Player.STATE_READY) {
                // 在 ExoPlayer 回调内切换转发目标会重入其监听分发，推迟到下一帧
                handler.post { onIncomingReady(exo) }
            }
            // 新歌迟迟未就绪而旧歌已放完或出错：立即接管，让对外状态如实显示新歌在缓冲，避免一直停在占位的 READY
            if (exo === active && pendingIncoming != null &&
                (exo.playbackState == Player.STATE_ENDED || exo.playerError != null)
            ) {
                handler.post { completePendingImmediately() }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // 新歌缓冲失败时直接接管，让错误经会话状态传给 PlayerManager 走原有的跳过逻辑
            val incoming = pendingIncoming ?: return
            if (incoming.playerError == null) return
            AppLogger.w(TAG, "交叉淡化目标加载失败，改为直接切换", error)
            completePendingImmediately()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // 音频焦点丢失等系统暂停只作用于当前播放器：淡出方同步停掉，缓冲中的新歌接管后保持暂停
            if (playWhenReady || active.playWhenReady) return
            completePendingImmediately(playWhenReady = false)
            releaseOutgoing()
        }
    }
}
