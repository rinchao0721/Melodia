package com.lin0721.linmusic.desktop.ui.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.playlistexternal.toPlaylistTrack
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.MenuScope
import com.lin0721.linmusic.desktop.ui.components.SubMenuItem
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.components.songInfoText
import com.lin0721.linmusic.desktop.ui.shell.DesktopNavigator
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.track.CollectSubmenuContent
import com.lin0721.linmusic.desktop.ui.track.TrackActions
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.search.ui.SearchViewModel

private const val NETEASE_WEB = "https://music.163.com"

private fun DesktopNavigator.copyLink(url: String) {
    showMessage(if (copyTextToClipboard(url)) "已复制链接" else "复制失败")
}

private fun DesktopNavigator.requireLogin(action: () -> Unit) {
    if (isLoggedIn) action() else showMessage("请先登录账号")
}

// 歌单与专辑共用：整体播放、下一首、下载，歌单可收藏，专辑可跳到署名歌手
@Composable
internal fun MenuScope.CollectionMenuItems(
    id: Long,
    isAlbum: Boolean,
    albumArtists: List<Artist>,
    viewModel: SearchViewModel,
    dismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    MenuItem("播放", icon = Icons.Rounded.PlayArrow, onClick = {
        dismiss()
        viewModel.playCollection(id, isAlbum)
    })
    MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
        dismiss()
        viewModel.addCollectionToPlayNext(id, isAlbum)
    })
    if (!isAlbum) {
        MenuItem("收藏歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = {
            dismiss()
            navigator.requireLogin { viewModel.subscribePlaylist(id) }
        })
    }
    MenuItem("下载", icon = Icons.Rounded.Download, onClick = {
        dismiss()
        viewModel.downloadCollection(id, isAlbum, navigator.downloadLevel)
    })
    val artists = albumArtists.filter { it.id > 0 }
    if (isAlbum && artists.isNotEmpty()) {
        MenuDivider()
        if (artists.size > 1) {
            SubMenuItem("查看歌手", icon = Icons.Rounded.Person) {
                artists.forEach { artist ->
                    SimpleMenuItem(artist.name, onClick = {
                        dismiss()
                        navigator.openArtist(artist.id, artist.name)
                    })
                }
            }
        } else {
            MenuItem("查看歌手", icon = Icons.Rounded.Person, onClick = {
                dismiss()
                navigator.openArtist(artists[0].id, artists[0].name)
            })
        }
    }
    MenuItem(if (isAlbum) "复制专辑链接" else "复制歌单链接", icon = Icons.Rounded.Link, onClick = {
        dismiss()
        navigator.copyLink("$NETEASE_WEB/${if (isAlbum) "album" else "playlist"}?id=$id")
    })
}

// 关注状态在菜单展开时才查询，查到之前“关注”置灰
@Composable
internal fun MenuScope.ArtistMenuItems(artist: Artist, viewModel: SearchViewModel, dismiss: () -> Unit) {
    val navigator = LocalDesktopNavigator.current
    val followed by produceState<Boolean?>(null, artist.id, navigator.isLoggedIn) {
        value = if (navigator.isLoggedIn) viewModel.isArtistFollowed(artist.id) else false
    }
    MenuItem("打开歌手", icon = Icons.Rounded.Person, onClick = {
        dismiss()
        navigator.openArtist(artist.id, artist.name)
    })
    MenuItem(
        if (followed == true) "取消关注" else "关注",
        icon = if (followed == true) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
        enabled = followed != null,
        onClick = {
            dismiss()
            navigator.requireLogin { viewModel.setArtistFollowed(artist.id, followed != true) }
        }
    )
    MenuItem("复制歌手链接", icon = Icons.Rounded.Link, onClick = {
        dismiss()
        navigator.copyLink("$NETEASE_WEB/artist?id=${artist.id}")
    })
}

@Composable
internal fun MenuScope.RadioMenuItems(radioId: Long, viewModel: SearchViewModel, dismiss: () -> Unit) {
    val navigator = LocalDesktopNavigator.current
    val subscribed by produceState<Boolean?>(null, radioId, navigator.isLoggedIn) {
        value = if (navigator.isLoggedIn) viewModel.isRadioSubscribed(radioId) else false
    }
    MenuItem("打开电台", icon = Icons.Rounded.Mic, onClick = {
        dismiss()
        navigator.openRadio(radioId)
    })
    MenuItem(
        if (subscribed == true) "取消订阅" else "订阅",
        icon = if (subscribed == true) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
        enabled = subscribed != null,
        onClick = {
            dismiss()
            navigator.requireLogin { viewModel.setRadioSubscribed(radioId, subscribed != true) }
        }
    )
    MenuItem("复制电台链接", icon = Icons.Rounded.Link, onClick = {
        dismiss()
        navigator.copyLink("$NETEASE_WEB/djradio?id=$radioId")
    })
}

@Composable
internal fun MenuScope.ProgramMenuItems(
    program: PodcastProgram,
    viewModel: SearchViewModel,
    onPlay: () -> Unit,
    dismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    MenuItem("播放", icon = Icons.Rounded.PlayArrow, onClick = {
        dismiss()
        onPlay()
    })
    MenuItem("下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
        dismiss()
        viewModel.addProgramToPlayNext(program)
    })
    if (program.radioId > 0) {
        MenuItem("打开电台", icon = Icons.Rounded.Mic, onClick = {
            dismiss()
            navigator.openRadio(program.radioId)
        })
    }
    MenuItem("复制节目链接", icon = Icons.Rounded.Link, onClick = {
        dismiss()
        navigator.copyLink("$NETEASE_WEB/program?id=${program.id}")
    })
}

// 外部平台歌曲没有网易云 id，不提供喜欢、下载；收藏到歌单只存本机
@Composable
internal fun MenuScope.ExternalTrackMenuItems(
    track: ExternalTrack,
    viewModel: SearchViewModel,
    actions: TrackActions,
    onPlay: () -> Unit,
    dismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    MenuItem("播放", icon = Icons.Rounded.PlayArrow, onClick = {
        dismiss()
        onPlay()
    })
    MenuItem("下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
        dismiss()
        viewModel.addExternalToPlayNext(track)
    })
    val asTrack = track.toPlaylistTrack()
    if (navigator.isLoggedIn) {
        SubMenuItem("收藏到歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onOpen = { actions.onPrepareCollect(asTrack) }) {
            CollectSubmenuContent(asTrack, actions, dismiss)
        }
    } else {
        MenuItem("收藏到歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = {
            dismiss()
            navigator.showMessage("请先登录账号")
        })
    }
    MenuDivider()
    MenuItem("在网易云中搜索", icon = Icons.Rounded.Search, onClick = {
        dismiss()
        viewModel.searchOnNetease(track)
    })
    MenuItem("复制歌曲信息", icon = Icons.Rounded.ContentCopy, onClick = {
        dismiss()
        val ok = copyTextToClipboard(songInfoText(track.name, track.artists))
        navigator.showMessage(if (ok) "已复制歌曲信息" else "复制失败")
    })
}
