package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpOffset
import com.lin0721.linmusic.desktop.data.cloud.CloudLibraryState
import com.lin0721.linmusic.desktop.ui.cloud.CloudMusicActions
import com.lin0721.linmusic.desktop.ui.cloud.uploadFilesFromPicker
import com.lin0721.linmusic.desktop.ui.cloud.uploadFolderFromPicker
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.local.LocalMusicActions
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType

// 桌面端独有的音乐库胶囊，选中时共享筛选为空
enum class LibraryExtraFilter { LOCAL, CLOUD }

internal const val CLOUD_FILTER_LABEL = "云盘"
internal const val CLOUD_ITEM_ID = "cloud:"
internal const val CLOUD_TITLE = "我的云盘"

// 音乐库侧栏里的本地歌单与云盘入口：胶囊选中态、打开与播放
@Stable
class LibraryExtrasEntry(
    val local: LocalMusicActions,
    val cloud: CloudMusicActions,
    val selected: LibraryExtraFilter?,
    val onToggle: (LibraryExtraFilter) -> Unit,
    val onOpenLocal: (id: Long, name: String) -> Unit,
    val onOpenCloud: () -> Unit
)

internal val LibraryItem.isCloud: Boolean get() = id == CLOUD_ITEM_ID

// 本地歌单与云盘条目由桌面端自己打开，其余交给共享的打开逻辑
internal fun LibraryExtrasEntry.open(item: LibraryItem, fallback: (LibraryItem) -> Unit) {
    val localId = item.localPlaylistId()
    when {
        item.isCloud -> onOpenCloud()
        localId != null -> onOpenLocal(localId, item.title)
        else -> fallback(item)
    }
}

internal fun cloudSummaryText(state: CloudLibraryState): String {
    val quota = state.quota ?: return if (state.failed) "加载失败" else "云盘"
    return "${quota.totalCount} 首 · ${formatFileSize(quota.usedBytes)} / ${formatFileSize(quota.maxBytes)}"
}

internal fun cloudLibraryItem(state: CloudLibraryState): LibraryItem = LibraryItem(
    id = CLOUD_ITEM_ID,
    title = CLOUD_TITLE,
    subtitle = cloudSummaryText(state),
    coverUrl = state.songs.firstNotNullOfOrNull { it.coverUrl }.orEmpty(),
    type = LibraryItemType.PLAYLIST,
    trackCount = state.quota?.totalCount ?: state.songs.size,
    isOwnedByMe = true
)

@Composable
internal fun CloudLibraryItemMenu(
    expanded: Boolean,
    offset: DpOffset,
    cloud: CloudMusicActions,
    state: CloudLibraryState,
    onDismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    DesktopMenu(expanded = expanded, onDismiss = onDismiss, offset = offset) {
        MenuItem("播放", icon = Icons.Rounded.PlayArrow, onClick = {
            onDismiss()
            if (state.songs.isEmpty()) navigator.showMessage("云盘里还没有歌曲") else cloud.play(state.songs, 0)
        })
        MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
            onDismiss()
            cloud.addToPlayNext(state.songs)
            navigator.showMessage(if (state.songs.isEmpty()) "云盘里还没有歌曲" else "已添加至下一首播放")
        })
        MenuDivider()
        MenuItem("上传歌曲", icon = Icons.Rounded.CloudUpload, onClick = {
            onDismiss()
            uploadFilesFromPicker(scope, cloud, navigator)
        })
        MenuItem("上传文件夹", icon = Icons.Rounded.DriveFolderUpload, onClick = {
            onDismiss()
            uploadFolderFromPicker(scope, cloud, navigator)
        })
        MenuItem("刷新", icon = Icons.Rounded.Refresh, onClick = {
            onDismiss()
            cloud.library.refresh()
        })
    }
}
