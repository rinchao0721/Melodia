package com.lin0721.linmusic.desktop.ui.shell

import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.yearFromEpochMillis
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute

// 把应用状态与下载能力包装成页面使用的跳转与提示入口
internal fun desktopNavigator(
    state: DesktopAppState,
    deps: DesktopAppDependencies,
    isLoggedIn: Boolean,
    downloadLevel: String,
    defaultPlaylistPrivate: Boolean
): DesktopNavigator {
    val downloadAt: (Track, String) -> Unit = { track, level ->
        deps.downloader.enqueueSingle(
            DownloadTrackInfo(
                track.id, track.name, track.ar.joinToString("/") { it.name },
                track.al.name, track.al.picUrl.takeIf { it.isNotBlank() },
                yearFromEpochMillis(track.publishTime)
            ),
            level
        )
        state.showMessage("已加入下载队列")
    }
    return DesktopNavigator(
        isLoggedIn = isLoggedIn,
        openArtist = { id, name -> state.navigate(DesktopRoute.Artist(id, name)) },
        openPlaylist = { id, name -> state.navigate(DesktopRoute.Playlist(id, name)) },
        openAlbum = { id, name -> state.navigate(DesktopRoute.Playlist(id, name, isAlbum = true)) },
        openRadio = { id -> state.navigate(DesktopRoute.Radio(id)) },
        openProfile = { uid -> state.navigate(DesktopRoute.Profile(uid)) },
        openFollowList = { uid, mode -> state.navigate(DesktopRoute.FollowList(uid, mode)) },
        openPodcastSubscribed = { state.navigate(DesktopRoute.PodcastSubscribed) },
        openPodcastToplist = { state.navigate(DesktopRoute.PodcastToplist) },
        openPodcastCategory = { id, name -> state.navigate(DesktopRoute.PodcastCategory(id, name)) },
        openLogin = { state.showLogin = true },
        downloadLevel = downloadLevel,
        defaultPlaylistPrivate = defaultPlaylistPrivate,
        downloadTrack = { track -> downloadAt(track, downloadLevel) },
        downloadTrackAt = downloadAt,
        showMessage = state::showMessage,
        goBack = state::popBack,
        toggleCommentsPanel = state::toggleCommentsPanel,
        isCommentsPanelOpen = state::isCommentsPanelOpen,
        openShareCard = { kind -> state.shareCardKind = kind }
    )
}
