package com.lin0721.linmusic.desktop.ui.track

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportState
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Favorite
import com.lin0721.linmusic.core.ui.icons.Queue
import com.lin0721.linmusic.core.ui.icons.Close
import com.lin0721.linmusic.core.ui.icons.Delete
import com.lin0721.linmusic.core.ui.icons.Download
import com.lin0721.linmusic.core.ui.icons.PlaylistAdd

// 多选态工具条：歌单页与歌手页共用，吸顶显示在列表表头上方
@Composable
fun TrackSelectionBar(
    count: Int,
    allSelected: Boolean,
    importState: PlaylistImportState,
    canRemove: Boolean,
    onToggleAll: () -> Unit,
    onPlayNext: () -> Unit,
    onPrepareImport: () -> Unit,
    onImportTo: (UserPlaylist) -> Unit,
    onCreateImport: () -> Unit,
    onLike: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val hasSelection = count > 0
    var importMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp)).background(DesktopColors.CardSurface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("已选 $count 首", color = DesktopColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = onToggleAll) {
            Text(if (allSelected) "取消全选" else "全选", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
        Box(Modifier.weight(1f))
        SelectionActionButton(MelodiaIcons.Queue, "下一首播放", hasSelection, onClick = onPlayNext)
        Box {
            SelectionActionButton(MelodiaIcons.PlaylistAdd, "添加到歌单", hasSelection) {
                if (navigator.isLoggedIn) {
                    onPrepareImport()
                    importMenuOpen = true
                } else {
                    navigator.showMessage("请先登录账号")
                }
            }
            DesktopMenu(expanded = importMenuOpen, onDismiss = { importMenuOpen = false }) {
                ImportSubmenuContent(importState, onImportTo = {
                    importMenuOpen = false
                    onImportTo(it)
                }, onCreate = {
                    importMenuOpen = false
                    onCreateImport()
                })
            }
        }
        SelectionActionButton(MelodiaIcons.Favorite, "喜欢", hasSelection) {
            if (navigator.isLoggedIn) onLike() else navigator.showMessage("请先登录账号")
        }
        SelectionActionButton(MelodiaIcons.Download, "下载", hasSelection, onClick = onDownload)
        if (canRemove) {
            SelectionActionButton(MelodiaIcons.Delete, "删除", hasSelection, onClick = onRemove, tint = DangerColor)
        }
        DesktopTooltip("退出多选") {
            TextButton(onClick = onExit) {
                Icon(MelodiaIcons.Close, "退出多选", tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SelectionActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = DesktopColors.TextPrimary,
    onClick: () -> Unit
) {
    val color = if (enabled) tint else DesktopColors.TextGray.copy(alpha = 0.4f)
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, color = color, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
