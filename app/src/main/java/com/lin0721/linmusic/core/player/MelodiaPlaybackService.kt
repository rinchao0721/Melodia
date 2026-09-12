package com.lin0721.linmusic.core.player

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.annotation.OptIn
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
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.CommandButton
import com.lin0721.linmusic.MainActivity
import com.lin0721.linmusic.R
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

private const val TAG = "MelodiaPlaybackService"

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

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "Service onCreate instanceId=${System.identityHashCode(this)}")

        // 系统媒体通知的图标
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )

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
        }
            
        player = forwardingPlayer

        // 点击通知时跳转回应用；REORDER_TO_FRONT 避免每次新建 Activity 实例导致重新加载
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(CustomSessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        // 监听歌曲切换以更新控制栏上的红心图标状态
        localExoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                mediaItem?.mediaId?.toLongOrNull()?.let { songId ->
                    checkAndFetchLikedStatus(songId)
                }
            }
        })

        // 监听播放模式改变以实时同步控制栏的按钮状态
        serviceScope.launch {
            playerManager.playMode.collect {
                updateCustomLayout()
            }
        }
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
                            updateCustomLayout()
                        }.onFailure {
                            AppLogger.w(TAG, "获取已喜欢歌曲列表失败，红心状态可能不同步", it)
                        }
                    }
                } else {
                    updateCustomLayout()
                }
            } else {
                updateCustomLayout()
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
            updateCustomLayout()

            songLikeRepository.likeSong(songId, targetLiked).collect { result ->
                result.onFailure {
                    // 回滚
                    if (targetLiked) {
                        likedSongIdsCache.remove(songId)
                    } else {
                        likedSongIdsCache.add(songId)
                    }
                    updateCustomLayout()
                }
            }
        }
    }

    private fun updateCustomLayoutForController(session: MediaSession, controller: MediaSession.ControllerInfo) {
        val songId = exoPlayer?.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        val isLiked = songId != -1L && songId in likedSongIdsCache

        val likeIconRes = if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        val likeDisplayName = if (isLiked) "取消喜欢" else "喜欢"

        val likeButton = CommandButton.Builder()
            .setDisplayName(likeDisplayName)
            .setIconResId(likeIconRes)
            .setSessionCommand(SessionCommand("ACTION_TOGGLE_LIKE", Bundle()))
            .build()

        val mode = playerManager.playMode.value
        val modeIconRes = when (mode) {
            PlayMode.SHUFFLE -> R.drawable.ic_shuffle
            PlayMode.SINGLE_LOOP -> R.drawable.ic_repeat_one
            PlayMode.LIST_LOOP -> R.drawable.ic_repeat
        }
        val modeDisplayName = when (mode) {
            PlayMode.SHUFFLE -> "随机播放"
            PlayMode.SINGLE_LOOP -> "单曲循环"
            PlayMode.LIST_LOOP -> "列表循环"
        }

        val modeButton = CommandButton.Builder()
            .setDisplayName(modeDisplayName)
            .setIconResId(modeIconRes)
            .setSessionCommand(SessionCommand("ACTION_TOGGLE_PLAY_MODE", Bundle()))
            .build()

        val customLayout = com.google.common.collect.ImmutableList.of(likeButton, modeButton)
        session.setCustomLayout(controller, customLayout)
    }

    private fun updateCustomLayout() {
        val session = mediaSession ?: return
        val songId = exoPlayer?.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        val isLiked = songId != -1L && songId in likedSongIdsCache

        val likeIconRes = if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        val likeDisplayName = if (isLiked) "取消喜欢" else "喜欢"

        val likeButton = CommandButton.Builder()
            .setDisplayName(likeDisplayName)
            .setIconResId(likeIconRes)
            .setSessionCommand(SessionCommand("ACTION_TOGGLE_LIKE", Bundle()))
            .build()

        val mode = playerManager.playMode.value
        val modeIconRes = when (mode) {
            PlayMode.SHUFFLE -> R.drawable.ic_shuffle
            PlayMode.SINGLE_LOOP -> R.drawable.ic_repeat_one
            PlayMode.LIST_LOOP -> R.drawable.ic_repeat
        }
        val modeDisplayName = when (mode) {
            PlayMode.SHUFFLE -> "随机播放"
            PlayMode.SINGLE_LOOP -> "单曲循环"
            PlayMode.LIST_LOOP -> "列表循环"
        }

        val modeButton = CommandButton.Builder()
            .setDisplayName(modeDisplayName)
            .setIconResId(modeIconRes)
            .setSessionCommand(SessionCommand("ACTION_TOGGLE_PLAY_MODE", Bundle()))
            .build()

        val customLayout = com.google.common.collect.ImmutableList.of(likeButton, modeButton)
        session.setCustomLayout(customLayout)
    }

    private inner class CustomSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()

            availableSessionCommands.add(SessionCommand("ACTION_TOGGLE_LIKE", Bundle()))
            availableSessionCommands.add(SessionCommand("ACTION_TOGGLE_PLAY_MODE", Bundle()))
            availableSessionCommands.add(SessionCommand("ACTION_SET_PREFERRED_AUDIO_DEVICE", Bundle()))

            return MediaSession.ConnectionResult.accept(
                availableSessionCommands.build(),
                connectionResult.availablePlayerCommands
            )
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onPostConnect(session, controller)
            updateCustomLayoutForController(session, controller)
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
                    updateCustomLayout()
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
}


