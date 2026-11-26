package com.lin0721.linmusic.desktop.ui.local

import androidx.compose.runtime.Composable
import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.icons.CloudSync
import com.lin0721.linmusic.core.ui.icons.Copy
import com.lin0721.linmusic.core.ui.icons.Delete
import com.lin0721.linmusic.core.ui.icons.Edit
import com.lin0721.linmusic.core.ui.icons.Favorite
import com.lin0721.linmusic.core.ui.icons.FavoriteFilled
import com.lin0721.linmusic.core.ui.icons.FolderOpen
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Play
import com.lin0721.linmusic.core.ui.icons.PlaylistAdd
import com.lin0721.linmusic.core.ui.icons.Queue
import com.lin0721.linmusic.core.ui.icons.Upload
import com.lin0721.linmusic.desktop.data.local.LocalTrack
import com.lin0721.linmusic.desktop.platform.revealInFileManager
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.MenuScope
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.components.SubMenuItem
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.components.songInfoText
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.feature.playlist.presentation.PlaylistSortOrder
import com.lin0721.linmusic.feature.playlist.presentation.filterTracks
import com.lin0721.linmusic.feature.playlist.presentation.sortTracks
import java.io.File

// 本地歌曲列表行模型：id 恒为路径占位 id，避免多首歌曲匹配到同一网易歌曲时 key 冲突
internal class LocalTrackRowModel(val display: Track, val track: LocalTrack)

internal fun LocalTrack.toDisplayTrack(coverUri: String?): Track = Track(
    id = placeholderId,
    name = title,
    ar = listOf(Artist(name = artist)),
    al = Album(name = album, picUrl = coverUri.orEmpty()),
    dt = durationMs
)

internal fun filterAndSortLocalRows(
    rows: List<LocalTrackRowModel>,
    query: String,
    order: PlaylistSortOrder
): List<LocalTrackRowModel> {
    val byId = rows.associateBy { it.display.id }
    return sortTracks(filterTracks(rows.map { it.display }, query), order, emptyMap())
        .mapNotNull { byId[it.id] }
}

@Composable
internal fun MenuScope.LocalTrackMenuItems(
    track: LocalTrack,
    isLiked: Boolean,
    dismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleLike: (() -> Unit)?,
    onEditTags: () -> Unit,
    onUpload: (() -> Unit)? = null,
    onMatch: (() -> Unit)? = null,
    playlists: List<LocalPlaylist> = emptyList(),
    onAddToPlaylist: ((Long) -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val navigator = LocalDesktopNavigator.current
    MenuItem("播放", icon = MelodiaIcons.Play, onClick = {
        dismiss()
        onPlay()
    })
    MenuItem("下一首播放", icon = MelodiaIcons.Queue, onClick = {
        dismiss()
        onPlayNext()
    })
    onToggleLike?.let { toggle ->
        MenuItem(
            if (isLiked) "取消喜欢" else "喜欢",
            icon = if (isLiked) MelodiaIcons.FavoriteFilled else MelodiaIcons.Favorite,
            onClick = {
                dismiss()
                toggle()
            }
        )
    }
    if (onAddToPlaylist != null) {
        val targetPlaylists = playlists.filterNot { it.isSystem }
        SubMenuItem("加入本地歌单", icon = MelodiaIcons.PlaylistAdd) {
            if (targetPlaylists.isEmpty()) {
                SimpleMenuItem("暂无本地歌单", enabled = false, onClick = { dismiss() })
            } else {
                targetPlaylists.forEach { playlist ->
                    SimpleMenuItem(playlist.name, onClick = {
                        dismiss()
                        onAddToPlaylist(playlist.id)
                    })
                }
            }
        }
    }
    MenuDivider()
    MenuItem("编辑歌曲信息", icon = MelodiaIcons.Edit, onClick = {
        dismiss()
        onEditTags()
    })
    onMatch?.let { match ->
        MenuItem(if (track.songId != null) "更改网易云匹配" else "匹配网易云歌曲", icon = MelodiaIcons.CloudSync, onClick = {
            dismiss()
            match()
        })
    }
    onUpload?.let { upload ->
        MenuItem("上传到云盘", icon = MelodiaIcons.Upload, onClick = {
            dismiss()
            upload()
        })
    }
    MenuItem("在文件夹中显示", icon = MelodiaIcons.FolderOpen, onClick = {
        dismiss()
        if (!revealInFileManager(File(track.path))) navigator.showMessage("文件不存在或无法打开")
    })
    MenuItem("复制歌曲信息", icon = MelodiaIcons.Copy, onClick = {
        dismiss()
        val ok = copyTextToClipboard(songInfoText(track.title, track.artist))
        navigator.showMessage(if (ok) "已复制歌曲信息" else "复制失败")
    })
    onRemove?.let { remove ->
        MenuDivider()
        MenuItem("从歌单中移除", icon = MelodiaIcons.Delete, danger = true, onClick = {
            dismiss()
            remove()
        })
    }
}
