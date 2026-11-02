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
import androidx.media3.common.Timeline
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
// 缓冲期间 setMediaItems 的 future 保持未完成，SimpleBasePlayer 用占位状态对外只报一次切歌。
// 音量平衡的曲目增益由 extras 给出初值，播放中经 setVolume 更新并平滑过渡，淡化时作为淡入目标
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
    private var pendingGain = 1f

    private var outgoing: ExoPlayer? = null
    private var outgoingStartGain = 1f
    private var fadeStartElapsedMs = 0L
    private var fadeDurationMs = 0L

    // 当前曲目增益，以及向它平滑过渡的起点
    private var trackGain = 1f
    private var gainRampFrom = 1f
    private var gainRampStartElapsedMs = 0L

    private val active: ExoPlayer
        get() = if (player === secondary) secondary else primary

    // 逐字歌词读取当前接管播放的 ExoPlayer，切换两台播放器时自动跟随。
    // 与其余 Player API 一样，只能从 applicationLooper 所在线程调用。
    internal fun currentPositionSample(): PlaybackPositionSample? = active.run {
        currentMediaItem?.let { PlaybackPositionSample(it.mediaId, currentPosition) }
    }

    private val fadeStep = object : Runnable {
        override fun run() {
            val out = outgoing ?: return
            val progress = ((SystemClock.elapsedRealtime() - fadeStartElapsedMs).toFloat() / fadeDurationMs).coerceIn(0f, 1f)
            out.volume = CrossfadePolicy.fadeOutGain(progress, outgoingStartGain)
            active.volume = CrossfadePolicy.fadeInGain(progress) * trackGain
            if (progress >= 1f) {
                releaseOutgoing()
            } else {
                handler.postDelayed(this, FADE_STEP_MS)
            }
        }
    }

    private val gainStep = object : Runnable {
        override fun run() {
            // 淡化进行中由淡入曲线负责，结束后直接落在目标增益上
            if (outgoing != null) return
            val progress = ((SystemClock.elapsedRealtime() - gainRampStartElapsedMs).toFloat() / LoudnessPolicy.RAMP_MS).coerceIn(0f, 1f)
            active.volume = gainRampFrom + (trackGain - gainRampFrom) * progress
            if (progress < 1f) handler.postDelayed(this, FADE_STEP_MS)
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
            val activeIndex = active.currentMediaItemIndex
            val transformedTimeline = TransformedTimeline(base.timeline, activeIndex, transformer)
            val transformedMetadata = transformer(base.currentMetadata)
            builder.setPlaylist(transformedTimeline, base.currentTracks, transformedMetadata)
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
        val gain = LoudnessPolicy.amplitude(extras?.getDouble(LoudnessPolicy.EXTRA_TRACK_GAIN_DB, 0.0) ?: 0.0)
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
            val future = super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
            applyGainImmediately(gain)
            return future
        }
        // 新歌跳过开头静音，直接从有效开头起播
        return startCrossfade(mediaItems, startIndex, maxOf(startPositionMs, startOffsetMs), fadeMs, gain)
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

    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
        val target = volume.coerceIn(0f, 1f)
        // 新歌还在缓冲时增益属于它，接管后再生效
        if (pendingIncoming != null) {
            pendingGain = target
        } else if (target != trackGain) {
            trackGain = target
            gainRampFrom = active.volume
            gainRampStartElapsedMs = SystemClock.elapsedRealtime()
            handler.removeCallbacks(gainStep)
            handler.post(gainStep)
        }
        return Futures.immediateVoidFuture()
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
        fadeMs: Long,
        gain: Float
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
        pendingGain = gain
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
        handler.removeCallbacks(gainStep)
        trackGain = pendingGain
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
        handler.removeCallbacks(gainStep)
        trackGain = pendingGain
        incoming.volume = trackGain
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

    // 中止淡化：停掉淡出方，当前播放器恢复到曲目增益
    private fun releaseOutgoing() {
        val hadOutgoing = outgoing != null
        stopOutgoing()
        if (hadOutgoing) active.volume = trackGain
    }

    private fun applyGainImmediately(gain: Float) {
        handler.removeCallbacks(gainStep)
        trackGain = gain
        active.volume = gain
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

@OptIn(UnstableApi::class)
private class TransformedTimeline(
    private val timeline: Timeline,
    private val activeMediaItemIndex: Int,
    private val transformer: (MediaMetadata) -> MediaMetadata
) : Timeline() {

    override fun getWindowCount(): Int = timeline.windowCount

    override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
        return timeline.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
    }

    override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
        return timeline.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
    }

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int {
        return timeline.getLastWindowIndex(shuffleModeEnabled)
    }

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int {
        return timeline.getFirstWindowIndex(shuffleModeEnabled)
    }

    override fun getWindow(
        windowIndex: Int,
        window: Window,
        defaultPositionProjectionUs: Long
    ): Window {
        val w = timeline.getWindow(windowIndex, window, defaultPositionProjectionUs)
        if (windowIndex == activeMediaItemIndex || activeMediaItemIndex == -1) {
            val originalItem = w.mediaItem
            w.mediaItem = originalItem.buildUpon()
                .setMediaMetadata(transformer(originalItem.mediaMetadata))
                .build()
        }
        return w
    }

    override fun getPeriodCount(): Int = timeline.periodCount

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        return timeline.getPeriod(periodIndex, period, setIds)
    }

    override fun getIndexOfPeriod(uid: Any): Int {
        return timeline.getIndexOfPeriod(uid)
    }

    override fun getUidOfPeriod(periodIndex: Int): Any {
        return timeline.getUidOfPeriod(periodIndex)
    }
}

