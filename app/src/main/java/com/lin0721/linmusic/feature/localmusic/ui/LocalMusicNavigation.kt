package com.lin0721.linmusic.feature.localmusic.ui

// 由 NavHost 统一构造
data class LocalMusicNavigation(
    val onBack: () -> Unit,
    val openSongs: () -> Unit,
    val openArtists: () -> Unit,
    val openAlbums: () -> Unit,
    val openFolders: () -> Unit,
    val openArtist: (name: String) -> Unit,
    val openAlbum: (key: String) -> Unit,
    val openFolder: (path: String) -> Unit,
    val openPlaylists: () -> Unit,
    val openPlaylist: (id: Long) -> Unit,
    val openSettings: () -> Unit,
    val openOnlineArtist: (id: Long) -> Unit,
    val openOnlineAlbum: (id: Long) -> Unit,
    val openTagEditor: (uri: String) -> Unit,
    val onLoginScreenVisibilityChanged: (Boolean) -> Unit,
    val openPrivateChat: (uid: Long) -> Unit = {}
)
