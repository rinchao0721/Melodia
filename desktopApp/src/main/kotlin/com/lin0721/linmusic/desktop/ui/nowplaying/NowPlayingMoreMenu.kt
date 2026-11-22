package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.DOWNLOAD_QUALITY_LEVELS
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.model.qualityRank
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.MAX_SLEEP_TIMER_MINUTES
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackController.Companion.CONTEXT_INTELLIGENCE
import com.lin0721.linmusic.core.player.PlaybackController.Companion.CONTEXT_PERSONAL_FM
import com.lin0721.linmusic.core.player.SimilarRoamingController
import com.lin0721.linmusic.core.player.formatSleepTimerRemaining
import com.lin0721.linmusic.core.share.ShareCardKind
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.components.songLink
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.components.SubMenuItem
import com.lin0721.linmusic.desktop.ui.library.LibrarySearchInput
import com.lin0721.linmusic.desktop.ui.listentogether.ListenTogetherDialog
import com.lin0721.linmusic.desktop.ui.listentogether.LocalListenTogetherViewModel
import com.lin0721.linmusic.desktop.ui.player.PersonalFmModeGrid
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.CollectSubmenuContent
import com.lin0721.linmusic.desktop.ui.track.rememberTrackActions
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.launch
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.AddToQueue
import com.lin0721.linmusic.core.ui.icons.Favorite
import com.lin0721.linmusic.core.ui.icons.TimerOff
import com.lin0721.linmusic.core.ui.icons.FavoriteFilled
import com.lin0721.linmusic.core.ui.icons.Timer
import com.lin0721.linmusic.core.ui.icons.Check
import com.lin0721.linmusic.core.ui.icons.Close
import com.lin0721.linmusic.core.ui.icons.Delete
import com.lin0721.linmusic.core.ui.icons.Download
import com.lin0721.linmusic.core.ui.icons.Link
import com.lin0721.linmusic.core.ui.icons.PlaylistAdd
import com.lin0721.linmusic.core.ui.icons.Share
import com.lin0721.linmusic.core.ui.icons.Album as AlbumIcon
import com.lin0721.linmusic.core.ui.icons.Group
import com.lin0721.linmusic.core.ui.icons.Moon
import com.lin0721.linmusic.core.ui.icons.Tune

private val SleepTimerOptions = listOf(15, 30, 60, 90)
private val QualityKeys = listOf("standard", "exhigh", "lossless", "hires", "jymaster")
private val MoreMenuWidth = 220.dp
private val CustomInputHeight = 36.dp

// 与移动端播放页「更多」面板对齐
@Composable
internal fun NowPlayingMoreMenu(
    track: NowPlaying,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    val playContext by controller.playContext.collectAsState()
    val sleepRemaining by controller.sleepTimerRemaining.collectAsState()
    val podcast by playerViewModel.podcastState.collectAsState()
    val detailState by playerViewModel.songDetailState.collectAsState()
    val collectState by playerViewModel.collectState.collectAsState()
    val personalFmMode by playerViewModel.personalFmMode.collectAsState()

    // 红心与下一首播放用不到，只借用收藏二级菜单与新建歌单对话框
    val collectActions = rememberTrackActions(
        likedSongIds = emptySet(),
        collectState = collectState,
        onQuickLike = {},
        onToggleLike = { _, _ -> },
        onPlayNext = {},
        onPrepareCollect = playerViewModel::prepareCollectDialog,
        onSaveCollect = playerViewModel::savePlaylistCollection,
        onCreateAndAdd = playerViewModel::createPlaylistAndAddSong
    )

    val songId = track.songId
    val detail = detailState.songDetail?.takeIf { it.id == songId }
    val isIntelligence = playContext == CONTEXT_INTELLIGENCE
    val isRoaming = playContext == SimilarRoamingController.CONTEXT_ROAMING
    val isPersonalFm = playContext == CONTEXT_PERSONAL_FM
    val sleepActive = sleepRemaining > 0L
    // 本地歌曲未匹配云端时只有占位 id，依赖云端数据的项全部隐藏
    val cloudSongId = songId?.takeIf { it > 0L && !detailState.isLocalOnly }
    val cloud = !podcast.isPodcast && cloudSongId != null
    val modes = !podcast.isPodcast && (cloud || isIntelligence || isRoaming)
    val currentQuality = navigator.downloadLevel

    var showListenTogether by remember { mutableStateOf(false) }
    val listenTogether = LocalListenTogetherViewModel.current
    if (showListenTogether && listenTogether != null) {
        ListenTogetherDialog(
            listenTogether,
            onDismiss = { showListenTogether = false },
            onRoomReady = {
                showListenTogether = false
                navigator.openListenTogether()
            }
        )
    }

    DesktopMenu(expanded = expanded, onDismiss = onDismiss, width = MoreMenuWidth) {
        if (isPersonalFm) {
            SubMenuItem("FM 模式: ${personalFmMode.displayName}", icon = MelodiaIcons.Radio, width = 300.dp) {
                PersonalFmModeGrid(
                    currentMode = personalFmMode,
                    onSelectMode = { mode ->
                        onDismiss()
                        if (mode != personalFmMode) playerViewModel.switchPersonalFmMode(mode)
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            MenuItem("不喜欢", icon = MelodiaIcons.Delete, onClick = {
                onDismiss()
                playerViewModel.trashCurrentFmSong()
            })
            MenuItem("退出私人FM", icon = MelodiaIcons.Close, onClick = {
                onDismiss()
                playerViewModel.disablePersonalFm()
            })
            MenuDivider()
        }

        if (cloud && cloudSongId != null) {
            val albumName = detail?.al?.name?.takeIf { it.isNotBlank() } ?: detailState.songWiki?.album ?: "未知专辑"
            MenuItem("专辑: $albumName", icon = MelodiaIcons.AlbumIcon, onClick = {
                onDismiss()
                scope.launch { openCurrentSongAlbum(songId, playerViewModel, navigator) }
            })

            val artists = detail?.ar.orEmpty().filter { it.id > 0L }
            when {
                artists.size > 1 -> SubMenuItem("歌手", icon = MelodiaIcons.Person) {
                    artists.forEach { artist ->
                        SimpleMenuItem(artist.name, onClick = {
                            onDismiss()
                            navigator.openArtist(artist.id, artist.name)
                        })
                    }
                }
                artists.size == 1 -> MenuItem("歌手: ${artists[0].name}", icon = MelodiaIcons.Person, onClick = {
                    onDismiss()
                    navigator.openArtist(artists[0].id, artists[0].name)
                })
                else -> MenuItem("歌手: ${track.artist}", icon = MelodiaIcons.Person, enabled = false, onClick = {})
            }

            if (navigator.isLoggedIn) {
                val collectTrack = detail ?: Track(id = cloudSongId, name = track.title)
                SubMenuItem(
                    "收藏到歌单",
                    icon = MelodiaIcons.PlaylistAdd,
                    onOpen = { playerViewModel.prepareCollectDialog(cloudSongId) }
                ) {
                    CollectSubmenuContent(collectTrack, collectActions, onDismiss)
                }
            } else {
                MenuItem("收藏到歌单", icon = MelodiaIcons.PlaylistAdd, onClick = {
                    onDismiss()
                    navigator.showMessage("请先登录账号")
                })
            }
            MenuDivider()
        }

        // 外部平台歌曲（酷狗走云端收藏，其余平台存本机）
        if (!podcast.isPodcast && detailState.isExternal && songId != null) {
            if (navigator.hasCloudAccount) {
                SubMenuItem(
                    "收藏到歌单",
                    icon = MelodiaIcons.PlaylistAdd,
                    onOpen = { playerViewModel.prepareCollectDialog(songId) }
                ) {
                    CollectSubmenuContent(Track(id = songId, name = track.title), collectActions, onDismiss)
                }
            } else {
                MenuItem("收藏到歌单", icon = MelodiaIcons.PlaylistAdd, onClick = {
                    onDismiss()
                    navigator.showMessage("请先登录账号")
                })
            }
            MenuItem(
                if (detailState.isLiked) "取消喜欢" else "喜欢",
                icon = if (detailState.isLiked) MelodiaIcons.FavoriteFilled else MelodiaIcons.Favorite,
                onClick = {
                    onDismiss()
                    if (navigator.hasCloudAccount) playerViewModel.toggleLike() else navigator.showMessage("请先登录账号")
                }
            )
            MenuDivider()
        }

        if (modes) {
            if (isIntelligence || cloudSongId != null) {
                MenuItem(if (isIntelligence) "退出心动模式" else "心动模式", icon = MelodiaIcons.Heart, onClick = {
                    onDismiss()
                    if (isIntelligence) {
                        controller.disableIntelligence()
                    } else if (cloudSongId != null) {
                        playerViewModel.startIntelligenceMode(cloudSongId, track.title, track.artist, track.artworkUri.orEmpty())
                    }
                })
            }
            if (isRoaming || cloudSongId != null) {
                MenuItem(if (isRoaming) "退出相似歌曲漫游" else "相似歌曲漫游", icon = MelodiaIcons.Radio, onClick = {
                    onDismiss()
                    if (isRoaming) {
                        controller.disableRoaming()
                    } else if (cloudSongId != null) {
                        playerViewModel.startSimilarSongsRoaming(cloudSongId, track.title, track.artist, track.artworkUri.orEmpty())
                    }
                })
            }
            if (cloud && cloudSongId != null) {
                MenuItem("插播相似歌曲", icon = MelodiaIcons.AddToQueue, onClick = {
                    onDismiss()
                    playerViewModel.insertSimilarSongs(cloudSongId)
                })
            }
            MenuDivider()
        }

        if (cloud && cloudSongId != null) {
            MenuItem("分享", icon = MelodiaIcons.Share, onClick = {
                onDismiss()
                navigator.openShareCard(ShareCardKind.SONG)
            })
            MenuItem("复制歌曲链接", icon = MelodiaIcons.Link, onClick = {
                onDismiss()
                val ok = copyTextToClipboard(songLink(cloudSongId))
                navigator.showMessage(if (ok) "已复制链接" else "复制失败")
            })
            // 账号权限未知时不限制，超出可下载上限的档位置灰
            val maxDownloadRank = qualityRank(detail?.privilege?.dlLevel).takeIf { it >= 0 }
            SubMenuItem("下载", icon = MelodiaIcons.Download) {
                DOWNLOAD_QUALITY_LEVELS.forEach { level ->
                    SimpleMenuItem(
                        getQualityDisplayName(level),
                        enabled = maxDownloadRank == null || qualityRank(level) <= maxDownloadRank,
                        onClick = {
                            onDismiss()
                            scope.launch {
                                val target = playerViewModel.resolveSongDetail(cloudSongId) ?: Track(
                                    id = cloudSongId,
                                    name = track.title,
                                    ar = listOf(Artist(name = track.artist)),
                                    al = Album(picUrl = track.artworkUri.orEmpty())
                                )
                                navigator.downloadTrackAt(target, level)
                            }
                        }
                    )
                }
            }
            SubMenuItem("音质: ${getQualityDisplayName(currentQuality)}", icon = MelodiaIcons.Tune) {
                QualityKeys.forEach { key ->
                    val selected = key == currentQuality
                    SimpleMenuItem(
                        getQualityDisplayName(key),
                        onClick = {
                            onDismiss()
                            if (!selected) playerViewModel.updateQuality(key)
                        },
                        trailing = if (selected) {
                            { Icon(MelodiaIcons.Check, "当前音质", tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        if (cloud) {
            MenuItem("一起听", icon = MelodiaIcons.Group, onClick = {
                onDismiss()
                when {
                    !navigator.isLoggedIn -> navigator.showMessage("请先登录账号")
                    listenTogether?.state?.value?.active == true -> navigator.openListenTogether()
                    else -> showListenTogether = true
                }
            })
        }

        SubMenuItem(
            if (sleepActive) "睡眠定时 (${formatSleepTimerRemaining(sleepRemaining)})" else "睡眠定时",
            icon = MelodiaIcons.Moon
        ) {
            SleepTimerOptions.forEach { minutes ->
                SimpleMenuItem("$minutes 分钟", icon = MelodiaIcons.Timer, onClick = {
                    onDismiss()
                    playerViewModel.setSleepTimer(minutes)
                })
            }
            if (sleepActive) {
                SimpleMenuItem("取消定时", icon = MelodiaIcons.TimerOff, onClick = {
                    onDismiss()
                    playerViewModel.setSleepTimer(0)
                })
            }
            MenuDivider()
            CustomSleepTimerInput { minutes ->
                onDismiss()
                playerViewModel.setSleepTimer(minutes)
            }
        }
    }
}

// 自定义分钟数：只收数字，回车确认，超过上限按上限算
@Composable
private fun CustomSleepTimerInput(onConfirm: (Int) -> Unit) {
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().height(CustomInputHeight).clip(RoundedCornerShape(6.dp))
            .background(DesktopColors.SurfaceLight.copy(alpha = 0.6f))
    ) {
        LibrarySearchInput(
            query = input,
            startPadding = 10.dp,
            showClear = input.isNotEmpty(),
            onQueryChange = {
                input = it.filter(Char::isDigit).take(MAX_SLEEP_TIMER_MINUTES.toString().length)
                invalid = false
            },
            onClear = {
                input = ""
                invalid = false
            },
            placeholder = if (invalid) "请输入 1-$MAX_SLEEP_TIMER_MINUTES" else "自定义分钟数",
            modifier = Modifier.onPreviewKeyEvent { event ->
                val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                if (event.type == KeyEventType.KeyDown && isEnter) {
                    val minutes = input.toIntOrNull()?.takeIf { it > 0 }
                    if (minutes == null) invalid = true else onConfirm(minOf(minutes, MAX_SLEEP_TIMER_MINUTES))
                    true
                } else {
                    false
                }
            }
        )
    }
}
