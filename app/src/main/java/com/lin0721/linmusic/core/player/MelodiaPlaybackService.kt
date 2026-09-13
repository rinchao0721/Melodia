package com.lin0721.linmusic.core.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import coil.Coil
import coil.request.ImageRequest
import com.lin0721.linmusic.MainActivity
import com.lin0721.linmusic.R
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private const val TAG = "MelodiaPlaybackService"
private const val NOTIFICATION_ID = 1001
private const val PLAYBACK_CHANNEL_ID = "melodia_playback_channel"

class MelodiaPlaybackService : MediaSessionService() {

    private val playerManager: PlayerManager by inject()
    private val settingsPreferences: SettingsPreferences by inject()
    private val userPreferences: UserPreferences by inject()
    private val songLikeRepository: SongLikeRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val likedSongIdsCache = mutableSetOf<Long>()
    private var isLikedListLoaded = false

    private var player: Player? = null
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var sessionActivityPendingIntent: PendingIntent? = null
    private var currentCoverBitmap: Bitmap? = null
    private var notificationCallback: MediaNotification.Provider.Callback? = null

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(PLAYBACK_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    PLAYBACK_CHANNEL_ID,
                    "正在播放",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "控制正在播放的音乐"
                    setShowBadge(false)
                }
                manager?.createNotificationChannel(channel)
            }
        }
    }

    private fun loadCoverBitmap(coverUri: android.net.Uri?) {
        if (coverUri == null) {
            currentCoverBitmap = null
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(this@MelodiaPlaybackService)
                    .data(coverUri)
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(this@MelodiaPlaybackService).execute(request)
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    currentCoverBitmap = drawable.bitmap
                    withContext(Dispatchers.Main) {
                        updateMediaSessionButtons()
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

        // 允许跨协议重定向（如 HTTPS 到 HTTP）
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // 动态代理数据源，每次请求新数据源时获取最新配置并创建对应的源
        val dynamicDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            val isCacheEnabled = runBlocking {
                settingsPreferences.streamCacheEnabled.first()
            }
            if (isCacheEnabled) {
                val maxSize = runBlocking {
                    settingsPreferences.audioCacheMaxSize.first()
                }
                val cache = AudioCacheManager.getCache(this@MelodiaPlaybackService, maxSize)
                CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()
            } else {
                defaultDataSourceFactory.createDataSource()
            }
        }

        val localExoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dynamicDataSourceFactory))
            .build()
        this.exoPlayer = localExoPlayer

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        serviceScope.launch {
            settingsPreferences.playWithOtherApps.collect { playWithOtherApps ->
                localExoPlayer.setAudioAttributes(audioAttributes, !playWithOtherApps)
            }
        }

        val forwardingPlayer = object : ForwardingPlayer(localExoPlayer) {
            override fun seekToNext() {
                playerManager.playNext()
            }

            override fun seekToNextMediaItem() {
                playerManager.playNext()
            }

            override fun seekToPrevious() {
                playerManager.playPrevious()
            }

            override fun seekToPreviousMediaItem() {
                playerManager.playPrevious()
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean = true

            override fun hasPreviousMediaItem(): Boolean = true
        }
            
        player = forwardingPlayer

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

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(CustomSessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        // 监听歌曲切换以更新控制栏上的红心图标及通知封面状态
        localExoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                loadCoverBitmap(mediaItem?.mediaMetadata?.artworkUri)
                mediaItem?.mediaId?.toLongOrNull()?.let { songId ->
                    checkAndFetchLikedStatus(songId)
                } ?: updateMediaSessionButtons()
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val showLock = runBlocking { settingsPreferences.showLockscreen.first() }
        if (!showLock) {
            val allowedPackages = listOf(packageName, "com.android.bluetooth")
            if (controllerInfo.packageName !in allowedPackages) {
                return null
            }
        }
        return mediaSession
    }

    override fun onDestroy() {
        AppLogger.i(TAG, "Service onDestroy instanceId=${System.identityHashCode(this)}")
        playerManager.release()
        playerManager.saveState()
        serviceScope.cancel()
        notificationCallback = null
        currentCoverBitmap = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        exoPlayer?.release()
        exoPlayer = null
        player = null
        super.onDestroy()
    }

    private fun checkAndFetchLikedStatus(songId: Long) {
        serviceScope.launch {
            val profile = userPreferences.userProfile.first()
            if (profile != null) {
                if (!isLikedListLoaded) {
                    songLikeRepository.getLikedSongIds(profile.uid).collect { result ->
                        result.onSuccess { ids ->
                            likedSongIdsCache.clear()
                            likedSongIdsCache.addAll(ids)
                            isLikedListLoaded = true
                            updateMediaSessionButtons()
                        }.onFailure {
                            AppLogger.w(TAG, "获取已喜欢歌曲列表失败，红心状态可能不同步", it)
                        }
                    }
                } else {
                    updateMediaSessionButtons()
                }
            } else {
                updateMediaSessionButtons()
            }
        }
    }

    private fun toggleLike(songId: Long) {
        serviceScope.launch {
            val profile = userPreferences.userProfile.first() ?: return@launch
            val isLiked = songId in likedSongIdsCache
            val targetLiked = !isLiked

            // 乐观更新
            if (targetLiked) {
                likedSongIdsCache.add(songId)
            } else {
                likedSongIdsCache.remove(songId)
            }
            updateMediaSessionButtons()

            songLikeRepository.likeSong(songId, targetLiked).collect { result ->
                result.onFailure {
                    // 回滚
                    if (targetLiked) {
                        likedSongIdsCache.remove(songId)
                    } else {
                        likedSongIdsCache.add(songId)
                    }
                    updateMediaSessionButtons()
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildLikeButton(): CommandButton {
        val songId = exoPlayer?.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        val isLiked = songId != -1L && songId in likedSongIdsCache
        val icon = if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        val displayName = if (isLiked) "取消喜欢" else "喜欢"

        return CommandButton.Builder(icon)
            .setSessionCommand(SessionCommand("ACTION_TOGGLE_LIKE", Bundle()))
            .setDisplayName(displayName)
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun buildCustomLayout(): List<CommandButton> {
        return listOf(buildLikeButton())
    }

    @OptIn(UnstableApi::class)
    private fun buildMediaButtonPreferences(): List<CommandButton> {
        return listOf(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setDisplayName("上一首")
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setDisplayName("下一首")
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            buildLikeButton()
        )
    }

    private fun buildAvailableSessionCommands(): SessionCommands {
        return SessionCommands.Builder()
            .add(SessionCommand("ACTION_TOGGLE_LIKE", Bundle()))
            .add(SessionCommand("ACTION_TOGGLE_PLAY_MODE", Bundle()))
            .add(SessionCommand("ACTION_SET_PREFERRED_AUDIO_DEVICE", Bundle()))
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun updateMediaSessionButtons() {
        val session = mediaSession ?: return
        val customLayout = buildCustomLayout()
        val mediaButtonPreferences = buildMediaButtonPreferences()
        val sessionCommands = buildAvailableSessionCommands()

        session.setCustomLayout(customLayout)
        session.setMediaButtonPreferences(mediaButtonPreferences)
        for (controller in session.connectedControllers) {
            session.setAvailableCommands(
                controller,
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
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
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .setCustomLayout(buildCustomLayout())
                .setMediaButtonPreferences(buildMediaButtonPreferences())
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onPostConnect(session, controller)
            updateMediaSessionButtons()
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    playerManager.playNext()
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    playerManager.playPrevious()
                    return SessionResult.RESULT_SUCCESS
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
        ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "ACTION_TOGGLE_LIKE" -> {
                    val songId = player?.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
                    if (songId != -1L) {
                        toggleLike(songId)
                    }
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    )
                }
                "ACTION_TOGGLE_PLAY_MODE" -> {
                    playerManager.rotatePlayMode()
                    updateMediaSessionButtons()
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    )
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
                    exoPlayer?.setPreferredAudioDevice(targetDevice)
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    )
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

            val isPlaying = session.player.isPlaying
            val playPauseIconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playPauseTitle = if (isPlaying) "暂停" else "播放"

            val prevAction = actionFactory.createMediaAction(
                session,
                IconCompat.createWithResource(this@MelodiaPlaybackService, android.R.drawable.ic_media_previous),
                "上一首",
                Player.COMMAND_SEEK_TO_PREVIOUS
            )
            val playPauseAction = actionFactory.createMediaAction(
                session,
                IconCompat.createWithResource(this@MelodiaPlaybackService, playPauseIconRes),
                playPauseTitle,
                Player.COMMAND_PLAY_PAUSE
            )
            val nextAction = actionFactory.createMediaAction(
                session,
                IconCompat.createWithResource(this@MelodiaPlaybackService, android.R.drawable.ic_media_next),
                "下一首",
                Player.COMMAND_SEEK_TO_NEXT
            )

            val songId = currentItem?.mediaId?.toLongOrNull() ?: -1L
            val isLiked = songId != -1L && songId in likedSongIdsCache
            val likeIconRes = if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            val likeTitle = if (isLiked) "取消喜欢" else "喜欢"
            val likeAction = actionFactory.createCustomAction(
                session,
                IconCompat.createWithResource(this@MelodiaPlaybackService, likeIconRes),
                likeTitle,
                "ACTION_TOGGLE_LIKE",
                Bundle.EMPTY
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
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .addAction(likeAction)

            currentCoverBitmap?.let { bitmap ->
                builder.setLargeIcon(bitmap)
            }

            val mediaStyle = MediaNotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(
                    android.support.v4.media.session.MediaSessionCompat.Token.fromToken(
                        session.platformToken
                    )
                )
            builder.setStyle(mediaStyle)

            return MediaNotification(NOTIFICATION_ID, builder.build())
        }

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: Bundle
        ): Boolean = false
    }
}



