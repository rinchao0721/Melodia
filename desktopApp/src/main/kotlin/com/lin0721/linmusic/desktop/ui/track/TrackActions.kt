package com.lin0721.linmusic.desktop.ui.track

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.desktop.ui.components.MenuScope
import com.lin0721.linmusic.desktop.ui.library.CreatePlaylistDialog
import com.lin0721.linmusic.desktop.ui.library.EMPTY_NAME_MESSAGE
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator

// 歌曲行的红心与更多菜单操作，由各页面按自己的 ViewModel 组装
@Immutable
class TrackActions(
    val likedSongIds: Set<Long>,
    val onQuickLike: (Track) -> Unit,
    val onToggleLike: (Track) -> Unit,
    val onPlayNext: (Track) -> Unit,
    // 收藏到歌单的二级菜单及 ✓ 按钮弹窗
    val collectState: PlaylistCollectState,
    val onPrepareCollect: (Track) -> Unit,
    val onToggleCollect: (Track, PlaylistCollectItem) -> Unit,
    val onSaveCollect: (Track, List<PlaylistCollectItem>) -> Unit,
    val onCreateAndAdd: (Track, String) -> Unit,
    val onCreateCollect: (Track) -> Unit,
    val onDownload: (Track) -> Unit,
    // 仅自建歌单传入，菜单才出现“从歌单中删除”
    val onRemove: ((Track) -> Unit)? = null,
    // 仅每日推荐传入，菜单才出现“减少推荐”
    val onReduceRecommend: ((Track) -> Unit)? = null,
    // 页面专属的菜单项，追加在菜单末尾
    val extraMenuItems: (@Composable MenuScope.(Track, dismiss: () -> Unit) -> Unit)? = null
)

@Composable
fun rememberTrackActions(
    likedSongIds: Set<Long>,
    collectState: PlaylistCollectState,
    onQuickLike: (songId: Long) -> Unit,
    onToggleLike: (songId: Long, like: Boolean) -> Unit,
    onPlayNext: (Track) -> Unit,
    onPrepareCollect: (songId: Long) -> Unit,
    onSaveCollect: (songId: Long, items: List<PlaylistCollectItem>) -> Unit,
    onCreateAndAdd: (name: String, songId: Long) -> Unit,
    onRemove: ((Track) -> Unit)? = null,
    onReduceRecommend: ((Track) -> Unit)? = null,
    extraMenuItems: (@Composable MenuScope.(Track, dismiss: () -> Unit) -> Unit)? = null
): TrackActions {
    val navigator = LocalDesktopNavigator.current
    var creatingFor by remember { mutableStateOf<Track?>(null) }

    creatingFor?.let { track ->
        CreatePlaylistDialog(
            onDismiss = { creatingFor = null },
            onCreate = { name, _ ->
                onCreateAndAdd(name, track.id)
                creatingFor = null
            },
            onEmptyName = { navigator.showMessage(EMPTY_NAME_MESSAGE) },
            showPrivacy = false
        )
    }

    val requireLogin: (() -> Unit) -> Unit = { action ->
        if (navigator.isLoggedIn) action() else navigator.showMessage("请先登录账号")
    }
    return TrackActions(
        likedSongIds = likedSongIds,
        onQuickLike = { track -> requireLogin { onQuickLike(track.id) } },
        onToggleLike = { track -> requireLogin { onToggleLike(track.id, track.id !in likedSongIds) } },
        onPlayNext = onPlayNext,
        collectState = collectState,
        onPrepareCollect = { track -> onPrepareCollect(track.id) },
        onToggleCollect = { track, item -> onSaveCollect(track.id, listOf(item)) },
        onSaveCollect = { track, items -> onSaveCollect(track.id, items) },
        onCreateAndAdd = { track, name -> onCreateAndAdd(name, track.id) },
        onCreateCollect = { track -> requireLogin { creatingFor = track } },
        onDownload = navigator.downloadTrack,
        onRemove = onRemove,
        onReduceRecommend = onReduceRecommend,
        extraMenuItems = extraMenuItems
    )
}
