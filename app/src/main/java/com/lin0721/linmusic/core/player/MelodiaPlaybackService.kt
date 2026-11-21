package com.lin0721.linmusic.core.player

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.lin0721.linmusic.MainActivity
import com.lin0721.linmusic.R
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.lanlink.ConnectedPeerInfo
import com.lin0721.linmusic.core.lanlink.LanLinkAndroidClient
import com.lin0721.linmusic.core.lanlink.PlaybackTarget
import com.lin0721.linmusic.core.lanlink.RoutingPlaybackController
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.external.EXTRA_LYRIC_INFO
import com.lin0721.linmusic.core.player.external.ExternalLyricCoordinator
import com.lin0721.linmusic.core.player.external.FlymeStatusBarLyric
import com.lin0721.linmusic.core.player.usb.UsbAudioDeviceManager
import com.lin0721.linmusic.core.player.usb.UsbAudioOutputProvider
import com.lin0721.linmusic.core.preferences.NotificationButton
import com.lin0721.linmusic.core.preferences.NotificationButtonLayout
import com.lin0721.linmusic.core.preferences.NotificationButtonSetting
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject

private const val TAG = "MelodiaPlaybackService"
private const val NOTIFICATION_ID = 1001
private const val CONNECTED_NOTIFICATION_ID = 1002
private const val PLAYBACK_CHANNEL_ID = "melodia_playback_channel"
private const val PLAYBACK_CHANNEL_NAME = "正在播放"
private const val CONNECTED_CHANNEL_ID = "melodia_lanlink_connected"
private const val CONNECTED_CHANNEL_NAME = "局域网联动保持连接"
private const val ACTION_TOGGLE_LIKE = "ACTION_TOGGLE_LIKE"
private const val ACTION_TRASH_FM = "ACTION_TRASH_FM"
private const val ACTION_TOGGLE_PLAY_MODE = "ACTION_TOGGLE_PLAY_MODE"
private const val ACTION_TOGGLE_DESKTOP_LRC = "ACTION_TOGGLE_DESKTOP_LRC"
private const val ACTION_OPEN_SLEEP_TIMER = "ACTION_OPEN_SLEEP_TIMER"
private const val ACTION_CLOSE_APP = "ACTION_CLOSE_APP"
private const val ACTION_SEEK_BACK_15 = "ACTION_SEEK_BACK_15"
private const val ACTION_SEEK_FORWARD_15 = "ACTION_SEEK_FORWARD_15"
private const val REQUEST_CODE_SLEEP_TIMER = 1003
private const val SEEK_STEP_SECONDS = 15L
private const val ACTION_PREPARE_FOREGROUND = "com.lin0721.linmusic.action.PREPARE_FOREGROUND"
private const val ACTION_STOP_PENDING_FOREGROUND = "com.lin0721.linmusic.action.STOP_PENDING_FOREGROUND"
private const val ACTION_ENSURE_LANLINK_KEEPALIVE = "com.lin0721.linmusic.action.ENSURE_LANLINK_KEEPALIVE"
private const val EXTRA_PENDING_TITLE = "extra_pending_title"
private const val EXTRA_PENDING_ARTIST = "extra_pending_artist"

class MelodiaPlaybackService : MediaSessionService() {

    companion object {
        // 拉取在线播放链接前趁应用仍具前台资格先行提升前台服务，避免切后台后网络受限或 FGS 被拒
        fun startForPendingPlay(context: Context, title: String, artist: String) {
            val intent = Intent(context, MelodiaPlaybackService::class.java).apply {
                action = ACTION_PREPARE_FOREGROUND
                putExtra(EXTRA_PENDING_TITLE, title)
                putExtra(EXTRA_PENDING_ARTIST, artist)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    AppLogger.w(TAG, "系统限制后台启动播放前台服务", e)
                } else {
                    AppLogger.w(TAG, "预启动播放前台服务失败", e)
                }
            }
        }

        // 待播拉链被取消或彻底失败且底层未在播放时，移除预启动的前台状态
        fun stopPendingForeground(context: Context) {
            val intent = Intent(context, MelodiaPlaybackService::class.java).apply {
                action = ACTION_STOP_PENDING_FOREGROUND
            }
            runCatching { context.startService(intent) }
        }

        // 联动已连接且服务尚未运行时拉起前台服务保活
        fun startForLanLinkKeepalive(context: Context) {
            val intent = Intent(context, MelodiaPlaybackService::class.java).apply {
                action = ACTION_ENSURE_LANLINK_KEEPALIVE
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    AppLogger.w(TAG, "系统限制后台启动联动前台服务", e)
                } else {
                    AppLogger.w(TAG, "启动联动前台服务失败", e)
                }
            }
        }

        // 前台期间提前唤醒服务以订阅连接状态，无活跃连接时不强制进入前台
        fun wakeForLanLink(context: Context) {
            val intent = Intent(context, MelodiaPlaybackService::class.java)
            runCatching { context.startService(intent) }
        }
    }

    private val playerManager: PlayerManager by inject()
    private val routingPlaybackController: RoutingPlaybackController by inject()
    private val lanLinkClient: LanLinkAndroidClient by inject()
    private val lanLinkMediaRouteCoordinator: LanLinkMediaRouteCoordinator by inject()
    private val settingsPreferences: SettingsPreferences by inject()
    private val userPreferences: UserPreferences by inject()
    private val songLikeRepository: SongLikeRepository by inject()
    private val externalLyricCoordinator: ExternalLyricCoordinator by inject()
    private val externalInterruptionResumeController: ExternalInterruptionResumeController by inject()
    private val sleepTimerRequests: SleepTimerRequests by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isLikedListLoaded = false
    private var isShowLockscreenEnabled = true
    private var notificationButtons: List<NotificationButtonSetting> = NotificationButtonLayout.DEFAULT
    private var isShowDesktopLrc = false
    // 悬浮歌词开启且已锁定时，悬浮歌词开关按钮临时显示为解锁
    private var isFloatingLyricLocked = false
    private var isPersonalFm = false
    private var isPodcast = false
    private var currentPlayMode = PlayMode.LIST_LOOP
    private var isPendingForegroundActive = false
    private var isLanLinkKeepaliveEnabled = true
    private var isKeepaliveForegroundActive = false
    private var currentKeepalivePeerName: String? = null

    private var player: Player? = null
    private var crossfadePlayer: CrossfadePlayer? = null
    private var remoteSessionPlayer: RemoteSessionPlayer? = null
    private var usbAudio: UsbAudioDeviceManager? = null
    private var mediaSession: MediaSession? = null
    private var sessionActivityPendingIntent: PendingIntent? = null
    private var currentCoverBitmap: Bitmap? = null
    private var loadedCoverUrl: String? = null
    private var notificationCallback: MediaNotification.Provider.Callback? = null

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(PLAYBACK_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    PLAYBACK_CHANNEL_ID,
                    PLAYBACK_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "控制正在播放的音乐"
                    setShowBadge(false)
                }
                manager?.createNotificationChannel(channel)
            }
            if (manager?.getNotificationChannel(CONNECTED_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CONNECTED_CHANNEL_ID,
                    CONNECTED_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "保持局域网联动后台连接"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                manager?.createNotificationChannel(channel)
            }
        }
    }

    private fun loadCoverBitmap(coverUri: Uri?) {
        val rawUri = coverUri?.toString()
        if (coverUri == null || rawUri.isNullOrBlank()) {
            loadedCoverUrl = null
            currentCoverBitmap = null
            return
        }
        if (rawUri == loadedCoverUrl && currentCoverBitmap != null) return
        loadedCoverUrl = rawUri
        serviceScope.launch(Dispatchers.IO) {
            try {
                val dataModel: Any = if ((rawUri.startsWith("http://") || rawUri.startsWith("https://")) && !rawUri.contains('?')) {
                    "$rawUri?param=300y300"
                } else {
                    coverUri
                }
                val request = ImageRequest.Builder(this@MelodiaPlaybackService)
                    .data(dataModel)
                    .size(256, 256)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .allowHardware(false)
                    .build()
                val result = SingletonImageLoader.get(this@MelodiaPlaybackService).execute(request)
                val bitmap = result.image?.toBitmap()
                if (bitmap != null && loadedCoverUrl == rawUri) {
                    currentCoverBitmap = bitmap
                    withContext(Dispatchers.Main) {
                        updateMediaSessionButtons()
                        triggerNotificationUpdate()
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "加载通知栏封面失败", e)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "Service onCreate instanceId=${System.identityHashCode(this)}")

        ensureNotificationChannel()
        setMediaNotificationProvider(MelodiaNotificationProvider())

        serviceScope.launch {
            songLikeRepository.likedSongIds.collect {
                updateMediaSessionButtons()
                triggerNotificationUpdate()
            }
        }

        serviceScope.launch {
            settingsPreferences.showLockscreen.collect { enabled ->
                isShowLockscreenEnabled = enabled
            }
        }

        serviceScope.launch {
            settingsPreferences.notificationButtons
                .distinctUntilChanged()
                .collect { layout ->
                    notificationButtons = layout
                    updateMediaSessionButtons()
                    triggerNotificationUpdate()
                }
        }

        serviceScope.launch {
            combine(settingsPreferences.showDesktopLrc, settingsPreferences.floatingLyricLocked) { shown, locked ->
                shown to (shown && locked)
            }
                .distinctUntilChanged()
                .collect { (shown, locked) ->
                    isShowDesktopLrc = shown
                    isFloatingLyricLocked = locked
                    updateMediaSessionButtons()
                    triggerNotificationUpdate()
                }
        }

        serviceScope.launch {
            routingPlaybackController.playMode.collect { mode ->
                currentPlayMode = mode
                updateMediaSessionButtons()
                triggerNotificationUpdate()
            }
        }

        // 允许跨协议重定向（如 HTTPS 到 HTTP）
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // 每次创建数据源时读取最新配置；边听边存开启时歌曲会落到下载目录，不再重复写播放缓存
        val dynamicDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            val streamSaveEnabled = runBlocking {
                settingsPreferences.streamCacheEnabled.first()
            }
            if (streamSaveEnabled) {
                defaultDataSourceFactory.createDataSource()
            } else {
                val maxSize = runBlocking {
                    settingsPreferences.audioCacheMaxSize.first()
                }
                val cache = AudioCacheManager.getCache(this@MelodiaPlaybackService, maxSize)
                val cachedSource = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()
                NetworkCacheRoutingDataSource(cachedSource, defaultDataSourceFactory.createDataSource())
            }
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dynamicDataSourceFactory)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        // 耳机拔出 / 蓝牙耳机断开（如收回耳机盒）时系统发出 AUDIO_BECOMING_NOISY，需自动暂停，避免外放；
        // 两台都开启：交叉淡化期间淡出方和缓冲中的新歌也要一起停
        val usbAudio = UsbAudioDeviceManager(this).also { manager ->
            manager.start()
            // 与系统拔耳机自动暂停一致，避免拔出 DAC 后改从扬声器外放
            manager.onDeviceDetached = { crossfadePlayer?.pause() }
            this.usbAudio = manager
        }
        val primaryPlayer = ExoPlayer.Builder(this, MelodiaRenderersFactory(this, usbAudio))
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(buildMelodiaLoadControl())
            .setHandleAudioBecomingNoisy(true)
            .build()
        val secondaryPlayer = ExoPlayer.Builder(this, MelodiaRenderersFactory(this, usbAudio))
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(buildMelodiaLoadControl())
            .setHandleAudioBecomingNoisy(true)
            .build()
        val sessionPlayer = CrossfadePlayer(
            primary = primaryPlayer,
            secondary = secondaryPlayer,
            audioAttributes = audioAttributes,
            exclusiveVolume = usbAudio
        )
        sessionPlayer.setMetadataTransformer { base ->
            externalLyricCoordinator.applyToMediaMetadata(base.buildUpon(), base)
        }
        crossfadePlayer = sessionPlayer
        playerManager.attachLocalPlayer(sessionPlayer)
        playerManager.setPlaybackPositionSource(sessionPlayer::currentPositionSample)

        val remotePlayer = RemoteSessionPlayer(
            routingPlaybackController = routingPlaybackController,
            scope = serviceScope,
            routingControllerIdFlow = lanLinkMediaRouteCoordinator.routingControllerId
        )
        remoteSessionPlayer = remotePlayer

        serviceScope.launch {
            settingsPreferences.playWithOtherApps.collect { playWithOtherApps ->
                sessionPlayer.setHandleAudioFocus(!playWithOtherApps)
            }
        }
        serviceScope.launch {
            settingsPreferences.exclusiveOutput.collect { usbAudio.setEnabled(it) }
        }
        // 设置页已保证两者互斥，这里再兜底一次，避免淡化时两台播放器争用卸载通道
        serviceScope.launch {
            combine(settingsPreferences.audioOffload, settingsPreferences.crossfadeEnabled) { offload, crossfade ->
                offload && !crossfade
            }.distinctUntilChanged().collect { enabled ->
                primaryPlayer.setAudioOffloadEnabled(enabled)
                secondaryPlayer.setAudioOffloadEnabled(enabled)
            }
        }

        val initialKeepalive = runBlocking { settingsPreferences.lanLinkBackgroundKeepalive.first() }
        isLanLinkKeepaliveEnabled = initialKeepalive
        val initialTarget = routingPlaybackController.activeTarget.value
        val initialPlayer: Player = if (shouldUseRemoteSessionPlayer(initialKeepalive, initialTarget)) {
            remotePlayer
        } else {
            sessionPlayer
        }
        player = initialPlayer

        // 点击通知时跳转回应用；REORDER_TO_FRONT 避免每次新建 Activity 实例导致重新加载
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        sessionActivityPendingIntent = pendingIntent

        mediaSession = MediaSession.Builder(this, initialPlayer)
            .setCallback(CustomSessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        externalLyricCoordinator.onMetadataChanged = {
            sessionPlayer.notifyMetadataChanged()
        }
        externalLyricCoordinator.onTickerChanged = { triggerNotificationUpdate() }

        serviceScope.launch {
            routingPlaybackController.playContext.collect { ctx ->
                isPersonalFm = ctx == PlaybackController.CONTEXT_PERSONAL_FM
                isPodcast = ctx == PlaybackController.CONTEXT_PODCAST
                updateMediaSessionButtons()
                triggerNotificationUpdate()
            }
        }

        // 只切换 MediaSession 的底层播放器，不重建 MediaSession；关闭后台保持连接时立即切回本机播放器
        serviceScope.launch {
            combine(
                routingPlaybackController.activeTarget,
                settingsPreferences.lanLinkBackgroundKeepalive.distinctUntilChanged()
            ) { target, keepalive ->
                target to keepalive
            }.collect { (target, keepalive) ->
                isLanLinkKeepaliveEnabled = keepalive
                val nextPlayer: Player = if (shouldUseRemoteSessionPlayer(keepalive, target)) {
                    sessionPlayer.pause()
                    isPendingForegroundActive = false
                    remotePlayer
                } else {
                    sessionPlayer
                }
                if (mediaSession?.player !== nextPlayer) {
                    player = nextPlayer
                    mediaSession?.player = nextPlayer
                    onSessionMediaItemChanged(nextPlayer.currentMediaItem)
                }
                updateMediaSessionButtons()
                triggerNotificationUpdate()
                syncKeepaliveForegroundState()
            }
        }

        serviceScope.launch {
            combine(lanLinkClient.enabled, lanLinkClient.connectedDevice) { enabled, peer ->
                if (enabled) peer else null
            }.distinctUntilChanged().collect {
                syncKeepaliveForegroundState()
            }
        }

        externalInterruptionResumeController.start(
            scope = serviceScope,
            onResumeRequested = {
                playerManager.resume()
                sessionPlayer.play()
            }
        )

        // 监听本机播放器事件：更新红心状态、封面、中断恢复，并在停止播放时刷新保活前台状态
        sessionPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                val mime = (error as? ExoPlaybackException)?.rendererFormat?.sampleMimeType
                    ?: extractAudioMimeForError(sessionPlayer.currentTracks)
                playerManager.recordRendererErrorMime(sessionPlayer.currentMediaItem?.mediaId, mime)
                isPendingForegroundActive = false
                syncKeepaliveForegroundState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) isPendingForegroundActive = false
                if (player === sessionPlayer) {
                    externalInterruptionResumeController.onIsPlayingChanged(isPlaying)
                }
                syncKeepaliveForegroundState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (player === sessionPlayer) {
                    externalInterruptionResumeController.onTrackTransition(mediaItem?.mediaId?.toLongOrNull())
                    onSessionMediaItemChanged(mediaItem)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    isPendingForegroundActive = false
                }
                syncKeepaliveForegroundState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)
                if (player === sessionPlayer) {
                    if (!playWhenReady) {
                        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                            externalInterruptionResumeController.onAudioFocusLoss(
                                playbackState = sessionPlayer.playbackState,
                                currentSongId = sessionPlayer.currentMediaItem?.mediaId?.toLongOrNull()
                            )
                        } else {
                            externalInterruptionResumeController.onExplicitUserPause()
                        }
                    } else {
                        externalInterruptionResumeController.onPlaybackStarted()
                    }
                }
                syncKeepaliveForegroundState()
            }
        })

        // 监听远程代理播放器状态变化：同步封面、红心状态和连接保活通知切换
        remotePlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                syncKeepaliveForegroundState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                syncKeepaliveForegroundState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)
                syncKeepaliveForegroundState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (player === remotePlayer) {
                    onSessionMediaItemChanged(mediaItem)
                }
            }
        })
    }

    private fun onSessionMediaItemChanged(mediaItem: MediaItem?) {
        loadCoverBitmap(mediaItem?.mediaMetadata?.artworkUri)
        val songId = mediaItem?.mediaId?.toLongOrNull()
        if (songId != null) {
            checkAndFetchLikedStatus(songId)
        } else {
            updateMediaSessionButtons()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onUpdateNotificationAsync(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ): ListenableFuture<Void> {
        val future = super.onUpdateNotificationAsync(session, startInForegroundRequired)
        future.addListener(
            { syncKeepaliveForegroundState() },
            ContextCompat.getMainExecutor(this)
        )
        return future
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE_FOREGROUND -> {
                isPendingForegroundActive = true
                val title = intent.getStringExtra(EXTRA_PENDING_TITLE)
                val artist = intent.getStringExtra(EXTRA_PENDING_ARTIST)
                promoteToForegroundForPendingPlay(title, artist)
            }
            ACTION_STOP_PENDING_FOREGROUND -> {
                isPendingForegroundActive = false
                syncKeepaliveForegroundState()
            }
            ACTION_ENSURE_LANLINK_KEEPALIVE -> {
                val peer = if (isLanLinkKeepaliveEnabled && lanLinkClient.enabled.value) {
                    lanLinkClient.connectedDevice.value
                } else {
                    null
                }
                if (peer != null && !isMediaPlaybackForegroundActive()) {
                    startConnectedDeviceForeground(peer)
                } else if (!isMediaPlaybackForegroundActive()) {
                    // 通过 startForegroundService 启动但连接刚断开或保活已关闭时，先兜底进入前台再立即同步退出避免系统超时异常
                    startConnectedDeviceForeground(ConnectedPeerInfo("", getString(R.string.app_name)))
                    syncKeepaliveForegroundState()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val keepAlive = shouldKeepServiceOnTaskRemoved(
            mediaPlaybackForegroundActive = isMediaPlaybackForegroundActive(),
            keepaliveEnabled = isLanLinkKeepaliveEnabled,
            lanLinkEnabled = lanLinkClient.enabled.value,
            hasConnectedPeer = lanLinkClient.connectedDevice.value != null,
            hasPairedDevices = lanLinkClient.pairedDevices.value.isNotEmpty()
        )
        if (keepAlive) return
        super.onTaskRemoved(rootIntent)
    }

    // 系统「继续播放」卡片只取元数据，不需要播放地址
    @OptIn(UnstableApi::class)
    private fun lastTrackForResumptionCard(): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        serviceScope.launch {
            val last = try {
                playerManager.loadLastTrack()
            } catch (e: CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "读取上次播放歌曲失败", e)
                null
            }
            if (last == null) {
                future.setException(IllegalStateException("没有可恢复的歌曲"))
            } else {
                future.set(MediaSession.MediaItemsWithStartPosition(listOf(last.toRestoredMediaItem()), 0, last.lastPositionMs))
            }
        }
        return future
    }

    private fun promoteToForegroundForPendingPlay(pendingTitle: String?, pendingArtist: String?) {
        ensureNotificationChannel()
        val title = pendingTitle?.takeIf { it.isNotBlank() }
            ?: player?.currentMediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val artist = pendingArtist?.takeIf { it.isNotBlank() }
            ?: player?.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
        val notification = buildPendingForegroundNotification(title, artist)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
            if (isKeepaliveForegroundActive) {
                isKeepaliveForegroundActive = false
                currentKeepalivePeerName = null
                getSystemService(NotificationManager::class.java)?.cancel(CONNECTED_NOTIFICATION_ID)
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                AppLogger.w(TAG, "系统限制后台进入待播前台服务", e)
            } else {
                AppLogger.w(TAG, "进入待播前台服务失败", e)
            }
        }
    }

    private fun isMediaPlaybackForegroundActive(): Boolean {
        if (isPendingForegroundActive) return true
        val currentPlayer = mediaSession?.player ?: player ?: return false
        return !currentPlayer.currentTimeline.isEmpty &&
            (currentPlayer.isPlaying || currentPlayer.playWhenReady)
    }

    private fun hasMediaNotificationContent(): Boolean {
        if (isPendingForegroundActive) return true
        val currentPlayer = mediaSession?.player ?: player ?: return false
        return !currentPlayer.currentTimeline.isEmpty &&
            currentPlayer.playbackState != Player.STATE_IDLE
    }

    private fun syncKeepaliveForegroundState() {
        val manager = getSystemService(NotificationManager::class.java)
        val mediaActive = isMediaPlaybackForegroundActive()
        val mediaNotificationVisible = hasMediaNotificationContent()
        val connectedPeer = if (isLanLinkKeepaliveEnabled && lanLinkClient.enabled.value) {
            lanLinkClient.connectedDevice.value
        } else {
            null
        }

        if (mediaActive) {
            if (isKeepaliveForegroundActive) {
                isKeepaliveForegroundActive = false
                currentKeepalivePeerName = null
                manager?.cancel(CONNECTED_NOTIFICATION_ID)
            }
            return
        }

        if (connectedPeer != null) {
            startConnectedDeviceForeground(connectedPeer)
            return
        }

        val wasKeepalive = isKeepaliveForegroundActive
        isKeepaliveForegroundActive = false
        currentKeepalivePeerName = null
        manager?.cancel(CONNECTED_NOTIFICATION_ID)
        if (!mediaNotificationVisible || wasKeepalive) {
            val flag = if (mediaNotificationVisible) {
                ServiceCompat.STOP_FOREGROUND_DETACH
            } else {
                ServiceCompat.STOP_FOREGROUND_REMOVE
            }
            runCatching { ServiceCompat.stopForeground(this, flag) }
            if (!mediaNotificationVisible) {
                manager?.cancel(NOTIFICATION_ID)
            }
        }
    }

    private fun startConnectedDeviceForeground(peer: ConnectedPeerInfo) {
        if (isKeepaliveForegroundActive && currentKeepalivePeerName == peer.deviceName) return
        ensureNotificationChannel()
        val notification = buildConnectedKeepaliveNotification(peer.deviceName)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, CONNECTED_NOTIFICATION_ID, notification, fgsType)
            isKeepaliveForegroundActive = true
            currentKeepalivePeerName = peer.deviceName
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                AppLogger.w(TAG, "系统限制后台启动 connectedDevice 前台服务", e)
            } else {
                AppLogger.w(TAG, "进入 connectedDevice 前台服务失败", e)
            }
        }
    }

    private fun buildConnectedKeepaliveNotification(peerName: String): Notification {
        val displayName = peerName.ifBlank { "桌面端" }
        return NotificationCompat.Builder(this, CONNECTED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("已连接 $displayName")
            .setContentIntent(sessionActivityPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun buildPendingForegroundNotification(title: String, artist: String): Notification {
        val builder = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(sessionActivityPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
        currentCoverBitmap?.let { builder.setLargeIcon(it) }
        mediaSession?.let { session ->
            val mediaStyle = MediaNotificationCompat.MediaStyle()
                .setMediaSession(
                    android.support.v4.media.session.MediaSessionCompat.Token.fromToken(
                        session.platformToken
                    )
                )
            builder.setStyle(mediaStyle)
        }
        return builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (!isShowLockscreenEnabled) {
            val allowedPackages = listOf(packageName, "com.android.bluetooth")
            if (controllerInfo.packageName !in allowedPackages) {
                return null
            }
        }
        return mediaSession
    }

    override fun onDestroy() {
        playerManager.attachLocalPlayer(null)
        playerManager.setPlaybackPositionSource(null)
        AppLogger.i(TAG, "Service onDestroy instanceId=${System.identityHashCode(this)}")
        externalInterruptionResumeController.release()
        externalLyricCoordinator.onMetadataChanged = null
        externalLyricCoordinator.onTickerChanged = null
        externalLyricCoordinator.release()
        playerManager.release()
        playerManager.saveState()
        serviceScope.cancel()
        notificationCallback = null
        currentCoverBitmap = null
        loadedCoverUrl = null
        isKeepaliveForegroundActive = false
        currentKeepalivePeerName = null
        getSystemService(NotificationManager::class.java)?.cancel(CONNECTED_NOTIFICATION_ID)
        mediaSession?.run {
            release()
            mediaSession = null
        }
        remoteSessionPlayer?.release()
        remoteSessionPlayer = null
        crossfadePlayer?.release()
        crossfadePlayer = null
        player = null
        usbAudio?.stop()
        usbAudio = null
        super.onDestroy()
    }

    private fun checkAndFetchLikedStatus(songId: Long) {
        serviceScope.launch {
            val profile = userPreferences.userProfile.first()
            if (profile != null && !isLikedListLoaded) {
                songLikeRepository.getLikedSongIds(profile.uid).collect { result ->
                    result.onSuccess {
                        isLikedListLoaded = true
                    }.onFailure {
                        AppLogger.w(TAG, "获取已喜欢歌曲列表失败，红心状态可能不同步", it)
                    }
                }
            } else {
                updateMediaSessionButtons()
            }
        }
    }

    private fun toggleLike(songId: Long) {
        serviceScope.launch {
            val isLiked = songId in songLikeRepository.likedSongIds.value
            val targetLiked = !isLiked
            songLikeRepository.likeSong(songId, targetLiked).collect { }
        }
    }

    private fun currentVisibleButtons(): List<NotificationButton> =
        NotificationButtonLayout.resolveVisibleForContext(
            layout = notificationButtons,
            isPersonalFm = isPersonalFm,
            isPodcast = isPodcast
        )

    private fun playModePresentation(): Pair<Int, String> = when (currentPlayMode) {
        PlayMode.LIST_LOOP -> R.drawable.ic_repeat to "列表循环"
        PlayMode.SINGLE_LOOP -> R.drawable.ic_repeat_one to "单曲循环"
        PlayMode.SHUFFLE -> R.drawable.ic_shuffle to "随机播放"
    }

    private fun desktopLyricPresentation(): Pair<Int, String> = when {
        isFloatingLyricLocked -> R.drawable.ic_lock_open to "解锁歌词"
        isShowDesktopLrc -> R.drawable.ic_desktop_lyric to "关闭悬浮歌词"
        else -> R.drawable.ic_desktop_lyric to "开启悬浮歌词"
    }

    @OptIn(UnstableApi::class)
    private fun buildCommandButton(
        button: NotificationButton,
        slotIndex: Int
    ): CommandButton {
        val builder = when (button) {
            NotificationButton.LIKE -> {
                val songId = (mediaSession?.player ?: player)?.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
                val isLiked = songId != -1L && songId in songLikeRepository.likedSongIds.value
                val icon = if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
                val displayName = if (isLiked) "取消喜欢" else "喜欢"
                CommandButton.Builder(icon)
                    .setSessionCommand(SessionCommand(ACTION_TOGGLE_LIKE, Bundle()))
                    .setDisplayName(displayName)
            }
            NotificationButton.DISLIKE -> CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_thumb_down)
                .setSessionCommand(SessionCommand(ACTION_TRASH_FM, Bundle()))
                .setDisplayName("不喜欢")
            NotificationButton.PLAY_MODE -> {
                val (iconRes, title) = playModePresentation()
                CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setCustomIconResId(iconRes)
                    .setSessionCommand(SessionCommand(ACTION_TOGGLE_PLAY_MODE, Bundle()))
                    .setDisplayName(title)
            }
            NotificationButton.DESKTOP_LRC -> {
                val (iconRes, title) = desktopLyricPresentation()
                CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setCustomIconResId(iconRes)
                    .setSessionCommand(SessionCommand(ACTION_TOGGLE_DESKTOP_LRC, Bundle()))
                    .setDisplayName(title)
            }
            NotificationButton.SLEEP_TIMER -> CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_sleep_timer)
                .setSessionCommand(SessionCommand(ACTION_OPEN_SLEEP_TIMER, Bundle()))
                .setDisplayName("定时关闭")
            NotificationButton.CLOSE_APP -> CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_close)
                .setSessionCommand(SessionCommand(ACTION_CLOSE_APP, Bundle()))
                .setDisplayName("关闭")
            NotificationButton.SEEK_BACK -> CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_replay_15)
                .setSessionCommand(SessionCommand(ACTION_SEEK_BACK_15, Bundle()))
                .setDisplayName("快退 15 秒")
            NotificationButton.SEEK_FORWARD -> CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_forward_15)
                .setSessionCommand(SessionCommand(ACTION_SEEK_FORWARD_15, Bundle()))
                .setDisplayName("快进 15 秒")
        }

        // 绝不占用 SLOT_BACK / SLOT_FORWARD：前 2 个可见自定义项分配次级槽位，其余进入溢出槽位
        when (slotIndex) {
            0 -> builder.setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            1 -> builder.setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            else -> builder.setSlots(CommandButton.SLOT_OVERFLOW)
        }
        return builder.build()
    }

    @OptIn(UnstableApi::class)
    private fun buildVisibleCommandButtons(): List<CommandButton> =
        currentVisibleButtons().mapIndexed { index, button ->
            buildCommandButton(button, index)
        }

    @OptIn(UnstableApi::class)
    private fun buildCustomLayout(): List<CommandButton> = buildVisibleCommandButtons()

    // 不能在这里声明 SLOT_BACK / SLOT_FORWARD 的上/下一首按钮：Media3 一旦发现这两个槽位被占用，
    // 会把标准的 ACTION_SKIP_TO_PREVIOUS / NEXT 从旧版 PlaybackState 里剔除，改以自定义动作下发。
    // 一加(ColorOS/OxygenOS)的折叠态媒体卡片只认标准动作，上/下一首会显示成不可点，展开后才恢复。
    // 省略后上/下一首由播放器可用命令自动映射为标准动作，所有系统界面都能识别。
    @OptIn(UnstableApi::class)
    private fun buildMediaButtonPreferences(): List<CommandButton> = buildVisibleCommandButtons()

    private fun buildAvailableSessionCommands(): SessionCommands {
        return SessionCommands.Builder()
            .add(SessionCommand(ACTION_TOGGLE_LIKE, Bundle()))
            .add(SessionCommand(ACTION_TRASH_FM, Bundle()))
            .add(SessionCommand(ACTION_TOGGLE_PLAY_MODE, Bundle()))
            .add(SessionCommand(ACTION_TOGGLE_DESKTOP_LRC, Bundle()))
            .add(SessionCommand(ACTION_OPEN_SLEEP_TIMER, Bundle()))
            .add(SessionCommand(ACTION_CLOSE_APP, Bundle()))
            .add(SessionCommand(ACTION_SEEK_BACK_15, Bundle()))
            .add(SessionCommand(ACTION_SEEK_FORWARD_15, Bundle()))
            .add(SessionCommand("ACTION_SET_PREFERRED_AUDIO_DEVICE", Bundle()))
            .build()
    }

    private fun createSleepTimerActivityIntent(): Intent =
        Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_SLEEP_TIMER_FROM_NOTIFICATION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    private fun createSleepTimerActivityPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_CODE_SLEEP_TIMER,
            createSleepTimerActivityIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun toggleDesktopLyricFromNotification() {
        if (isFloatingLyricLocked) {
            serviceScope.launch { settingsPreferences.saveFloatingLyricLocked(false) }
            return
        }
        if (isShowDesktopLrc) {
            serviceScope.launch { settingsPreferences.saveShowDesktopLrc(false) }
            runCatching { stopService(Intent(this, FloatingLyricService::class.java)) }
            return
        }
        if (Settings.canDrawOverlays(this)) {
            serviceScope.launch { settingsPreferences.saveShowDesktopLrc(true) }
            runCatching { startService(Intent(this, FloatingLyricService::class.java)) }
        } else {
            Toast.makeText(this, "请开启悬浮窗权限以显示桌面歌词", Toast.LENGTH_LONG).show()
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun seekBySeconds(deltaSeconds: Long) {
        val currentPlayer = mediaSession?.player ?: player
        val currentPos = currentPlayer?.currentPosition?.takeIf { it >= 0L }
            ?: routingPlaybackController.currentPositionNow()
        val totalDuration = currentPlayer?.duration?.takeIf { it > 0L }
            ?: routingPlaybackController.duration.value
        val rawTarget = currentPos + deltaSeconds * 1000L
        val target = if (totalDuration > 0L) rawTarget.coerceIn(0L, totalDuration) else rawTarget.coerceAtLeast(0L)
        routingPlaybackController.seekTo(target)
        if (currentPlayer === crossfadePlayer && !playerManager.isControllerConnected) {
            currentPlayer?.seekTo(target)
        }
    }

    private fun closeAppFromNotification() {
        serviceScope.launch {
            runCatching {
                withTimeoutOrNull(800L) {
                    lanLinkClient.shutdownWithBye("user_close_app")
                }
            }
            playerManager.pause()
            crossfadePlayer?.pause()
            playerManager.saveStateNowAndDisconnect()
            crossfadePlayer?.stop()
            isKeepaliveForegroundActive = false
            currentKeepalivePeerName = null
            runCatching { stopService(Intent(this@MelodiaPlaybackService, FloatingLyricService::class.java)) }
            runCatching { ServiceCompat.stopForeground(this@MelodiaPlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.cancel(CONNECTED_NOTIFICATION_ID)
            manager?.cancel(NOTIFICATION_ID)
            getSystemService(ActivityManager::class.java)?.appTasks?.forEach { task ->
                runCatching { task.finishAndRemoveTask() }
            }
            stopSelf()
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildAvailablePlayerCommands(): Player.Commands {
        val base = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        return if (isPersonalFm) {
            base.buildUpon()
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        } else {
            base
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateMediaSessionButtons() {
        val session = mediaSession ?: return
        val customLayout = buildCustomLayout()
        val mediaButtonPreferences = buildMediaButtonPreferences()
        val sessionCommands = buildAvailableSessionCommands()
        val playerCommands = buildAvailablePlayerCommands()

        session.setCustomLayout(customLayout)
        session.setMediaButtonPreferences(mediaButtonPreferences)
        for (controller in session.connectedControllers) {
            session.setAvailableCommands(
                controller,
                sessionCommands,
                playerCommands
            )
        }
    }

    @Suppress("DEPRECATION")
    private inner class CustomSessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(buildAvailableSessionCommands())
                .setAvailablePlayerCommands(buildAvailablePlayerCommands())
                .setCustomLayout(buildCustomLayout())
                .setMediaButtonPreferences(buildMediaButtonPreferences())
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onPostConnect(session, controller)
            updateMediaSessionButtons()
        }

        // 播放交给 PlayerManager 走平时的拉链流程，返回取消让 Media3 不再自行装载媒体项
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (!isForPlayback) return lastTrackForResumptionCard()
            promoteToForegroundForPendingPlay(null, null)
            serviceScope.launch {
                val resumed = try {
                    playerManager.resumeLastSession()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "恢复上次播放失败", e)
                    false
                }
                if (!resumed) stopPendingForeground(this@MelodiaPlaybackService)
            }
            return Futures.immediateCancelledFuture()
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            val isLocalPlayer = session.player === crossfadePlayer
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    if (isLocalPlayer) {
                        externalInterruptionResumeController.onTrackTransition(null)
                    }
                    routingPlaybackController.playNext()
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    if (isPersonalFm) {
                        return SessionResult.RESULT_ERROR_NOT_SUPPORTED
                    }
                    if (isLocalPlayer) {
                        externalInterruptionResumeController.onTrackTransition(null)
                    }
                    routingPlaybackController.playPrevious()
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_PLAY_PAUSE -> {
                    if (isLocalPlayer && externalInterruptionResumeController.isAwaitingResumeActive() && session.player.playWhenReady == false) {
                        externalInterruptionResumeController.onExplicitUserPause()
                    }
                }
                Player.COMMAND_STOP -> {
                    if (isLocalPlayer) {
                        externalInterruptionResumeController.onExplicitUserPause()
                    }
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_LIKE -> {
                    val songId = (mediaSession?.player ?: player)?.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
                    if (songId != -1L) {
                        toggleLike(songId)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_TRASH_FM -> {
                    if (isPersonalFm) {
                        routingPlaybackController.trashCurrentFmSong()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_TOGGLE_PLAY_MODE -> {
                    routingPlaybackController.rotatePlayMode()
                    updateMediaSessionButtons()
                    triggerNotificationUpdate()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_TOGGLE_DESKTOP_LRC -> {
                    toggleDesktopLyricFromNotification()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_OPEN_SLEEP_TIMER -> {
                    sleepTimerRequests.requestOpenTimer()
                    runCatching { startActivity(createSleepTimerActivityIntent()) }
                        .onFailure { AppLogger.w(TAG, "从媒体控制卡片打开定时关闭失败", it) }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_CLOSE_APP -> {
                    closeAppFromNotification()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SEEK_BACK_15 -> {
                    seekBySeconds(-SEEK_STEP_SECONDS)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SEEK_FORWARD_15 -> {
                    seekBySeconds(SEEK_STEP_SECONDS)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "ACTION_SET_PREFERRED_AUDIO_DEVICE" -> {
                    val deviceId = args.getInt("device_id", -1)
                    val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                    val targetDevice = if (deviceId == -1) {
                        null
                    } else {
                        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                            .firstOrNull { it.id == deviceId }
                    }
                    crossfadePlayer?.setPreferredAudioDevice(targetDevice)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    @OptIn(UnstableApi::class)
    private inner class MelodiaNotificationProvider : MediaNotification.Provider {
        override fun createNotification(
            session: MediaSession,
            customLayout: com.google.common.collect.ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification {
            notificationCallback = onNotificationChangedCallback
            ensureNotificationChannel()

            val currentItem = session.player.currentMediaItem
            val metadata = currentItem?.mediaMetadata
            val title = metadata?.title?.toString()?.ifBlank { null } ?: getString(R.string.app_name)
            val artist = metadata?.artist?.toString() ?: ""
            val subtitle = metadata?.subtitle?.toString()?.takeIf { it.isNotBlank() }

            val isPlaying = session.player.isPlaying
            val playPauseIconRes = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
            val playPauseTitle = if (isPlaying) "暂停" else "播放"

            val playPauseAction = actionFactory.createMediaAction(
                session,
                IconCompat.createWithResource(this@MelodiaPlaybackService, playPauseIconRes),
                playPauseTitle,
                Player.COMMAND_PLAY_PAUSE
            )
            val nextAction = actionFactory.createMediaAction(
                session,
                IconCompat.createWithResource(this@MelodiaPlaybackService, R.drawable.ic_media_next),
                "下一首",
                Player.COMMAND_SEEK_TO_NEXT
            )

            val builder = NotificationCompat.Builder(this@MelodiaPlaybackService, PLAYBACK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(sessionActivityPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .setShowWhen(false)
            if (subtitle != null) {
                builder.setSubText(subtitle)
            }
            if (!isPersonalFm) {
                val prevAction = actionFactory.createMediaAction(
                    session,
                    IconCompat.createWithResource(this@MelodiaPlaybackService, R.drawable.ic_media_previous),
                    "上一首",
                    Player.COMMAND_SEEK_TO_PREVIOUS
                )
                builder.addAction(prevAction)
            }
            builder
                .addAction(playPauseAction)
                .addAction(nextAction)

            val songId = currentItem?.mediaId?.toLongOrNull() ?: -1L
            val isLiked = songId != -1L && songId in songLikeRepository.likedSongIds.value
            for (button in currentVisibleButtons()) {
                val action = when (button) {
                    NotificationButton.LIKE -> actionFactory.createCustomAction(
                        session,
                        IconCompat.createWithResource(
                            this@MelodiaPlaybackService,
                            if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                        ),
                        if (isLiked) "取消喜欢" else "喜欢",
                        ACTION_TOGGLE_LIKE,
                        Bundle.EMPTY
                    )
                    NotificationButton.DISLIKE -> actionFactory.createCustomAction(
                        session,
                        IconCompat.createWithResource(this@MelodiaPlaybackService, R.drawable.ic_thumb_down),
                        "不喜欢",
                        ACTION_TRASH_FM,
                        Bundle.EMPTY
                    )
                    NotificationButton.PLAY_MODE -> {
                        val (iconRes, modeTitle) = playModePresentation()
                        actionFactory.createCustomAction(
                            session,
                            IconCompat.createWithResource(this@MelodiaPlaybackService, iconRes),
                            modeTitle,
                            ACTION_TOGGLE_PLAY_MODE,
                            Bundle.EMPTY
                        )
                    }
                    NotificationButton.DESKTOP_LRC -> {
                        val (iconRes, lyricTitle) = desktopLyricPresentation()
                        actionFactory.createCustomAction(
                            session,
                            IconCompat.createWithResource(this@MelodiaPlaybackService, iconRes),
                            lyricTitle,
                            ACTION_TOGGLE_DESKTOP_LRC,
                            Bundle.EMPTY
                        )
                    }
                    // 定时关闭直接用 Activity PendingIntent，避开 Android 12+ 通知跳板限制
                    NotificationButton.SLEEP_TIMER -> NotificationCompat.Action.Builder(
                        R.drawable.ic_sleep_timer,
                        "定时关闭",
                        createSleepTimerActivityPendingIntent()
                    ).build()
                    NotificationButton.CLOSE_APP -> actionFactory.createCustomAction(
                        session,
                        IconCompat.createWithResource(this@MelodiaPlaybackService, R.drawable.ic_close),
                        "关闭",
                        ACTION_CLOSE_APP,
                        Bundle.EMPTY
                    )
                    NotificationButton.SEEK_BACK -> actionFactory.createCustomAction(
                        session,
                        IconCompat.createWithResource(this@MelodiaPlaybackService, R.drawable.ic_replay_15),
                        "快退 15 秒",
                        ACTION_SEEK_BACK_15,
                        Bundle.EMPTY
                    )
                    NotificationButton.SEEK_FORWARD -> actionFactory.createCustomAction(
                        session,
                        IconCompat.createWithResource(this@MelodiaPlaybackService, R.drawable.ic_forward_15),
                        "快进 15 秒",
                        ACTION_SEEK_FORWARD_15,
                        Bundle.EMPTY
                    )
                }
                builder.addAction(action)
            }

            val lyricInfo = metadata?.extras?.getString(EXTRA_LYRIC_INFO)
            if (!lyricInfo.isNullOrBlank()) {
                builder.addExtras(Bundle().apply { putString(EXTRA_LYRIC_INFO, lyricInfo) })
            }

            currentCoverBitmap?.let { bitmap ->
                builder.setLargeIcon(bitmap)
            }

            val compactIndices = if (isPersonalFm) intArrayOf(0, 1) else intArrayOf(0, 1, 2)
            val mediaStyle = MediaNotificationCompat.MediaStyle()
                .setShowActionsInCompactView(*compactIndices)
                .setMediaSession(
                    android.support.v4.media.session.MediaSessionCompat.Token.fromToken(
                        session.platformToken
                    )
                )
            builder.setStyle(mediaStyle)

            val tickerText = externalLyricCoordinator.flymeTickerText
            if (tickerText != null) builder.setTicker(tickerText)
            val notification = builder.build()
            if (tickerText != null) FlymeStatusBarLyric.applyTo(notification, R.drawable.ic_notification)
            return MediaNotification(NOTIFICATION_ID, notification)
        }

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: Bundle
        ): Boolean = false

        override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
            MediaNotification.Provider.NotificationChannelInfo(PLAYBACK_CHANNEL_ID, PLAYBACK_CHANNEL_NAME)
    }
}

// 输出层先交给 USB 独占判断，不能直出时走系统 AudioTrack；开启浮点输出让高精度源以浮点进入输出层，
// 是否向解码器请求浮点由 UsbAudioOutputProvider 的格式支持声明决定
@OptIn(UnstableApi::class)
private class MelodiaRenderersFactory(
    context: Context,
    private val usbAudio: UsbAudioDeviceManager
) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
    }

    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(true)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioOutputProvider(UsbAudioOutputProvider(AudioTrackAudioOutputProvider.Builder(context).build(), usbAudio))
            .build()
}

// 音频卸载交给 DSP 解码；格式或设备不支持时 ExoPlayer 自动回退软件解码，播放中切换会重配音频输出
@OptIn(UnstableApi::class)
private fun ExoPlayer.setAudioOffloadEnabled(enabled: Boolean) {
    val mode = if (enabled) {
        AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
    } else {
        AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
    }
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setAudioOffloadPreferences(AudioOffloadPreferences.Builder().setAudioOffloadMode(mode).build())
        .build()
}

// 本地默认 1s 才续载，卸载休眠时播放线程常错过这个窗口导致周期性欠载卡顿；下限拉到与上限一致，任何一次唤醒都会补满
@OptIn(UnstableApi::class)
private fun buildMelodiaLoadControl(): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMsForLocalPlayback(
            DefaultLoadControl.DEFAULT_MAX_BUFFER_FOR_LOCAL_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_FOR_LOCAL_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_FOR_LOCAL_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_FOR_LOCAL_PLAYBACK_MS
        )
        .build()



