package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.player.domain.SongMusicMemory
import com.lin0721.linmusic.feature.player.domain.SongWikiCreatorRole
import com.lin0721.linmusic.feature.player.domain.SongWikiData
import com.lin0721.linmusic.feature.player.presentation.DetailAction
import com.lin0721.linmusic.feature.player.presentation.DetailRow
import com.lin0721.linmusic.feature.player.presentation.songDetailRows

private val LabelWidth = 70.dp
private val LabelColor get() = DesktopColors.TextGray.copy(alpha = 0.6f)

// 歌曲详情：曲风、专辑、语种、发行时间、制作人员等；专辑可跳转，制作人员可展开按角色查看
@Composable
fun SongDetailCard(wiki: SongWikiData, track: Track?, modifier: Modifier = Modifier) {
    val navigator = LocalDesktopNavigator.current
    var showCreators by remember { mutableStateOf(false) }
    val rows = remember(wiki, track) { songDetailRows(wiki, track) }

    InfoCard("歌曲详情", modifier) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { row ->
                val onClick: (() -> Unit)? = when (val action = row.action) {
                    is DetailAction.OpenAlbum -> ({ navigator.openAlbum(action.id, action.name) })
                    DetailAction.ShowCreators -> ({ showCreators = true })
                    null -> null
                }
                DetailRowView(row, onClick)
            }
        }
    }
    if (showCreators) CreatorsDialog(wiki.creatorRoles, onDismiss = { showCreators = false })
}

@Composable
private fun DetailRowView(row: DetailRow, onClick: (() -> Unit)?) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .then(
                if (onClick != null) {
                    Modifier.hoverable(hoverSource).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .background(if (onClick != null && hovered) DesktopColors.PaneHover else Color.Transparent)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(row.label, color = LabelColor, fontSize = 14.sp, modifier = Modifier.width(LabelWidth))
        Column(Modifier.weight(1f)) {
            Text(
                row.value,
                color = DesktopColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = row.maxLines,
                overflow = TextOverflow.Ellipsis
            )
            row.supportingText?.let { Text(it, color = LabelColor, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (row.action == DetailAction.ShowCreators) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = DesktopColors.TextGray.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp).size(20.dp)
            )
        }
    }
}

// 制作信息：按角色分组展示词曲编曲等
@Composable
private fun CreatorsDialog(roles: List<SongWikiCreatorRole>, onDismiss: () -> Unit) {
    DesktopDialog(
        title = "制作信息",
        onDismiss = onDismiss,
        width = 360.dp,
        actions = { DialogButton("关闭", onDismiss, primary = true) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            roles.forEach { role ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(role.roleName, color = LabelColor, fontSize = 13.sp, modifier = Modifier.width(LabelWidth))
                    Text(
                        role.artistNames.joinToString(" / "),
                        color = DesktopColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// 回忆坐标：首次听到的时间与累计播放次数
@Composable
fun MusicMemoryCard(memory: SongMusicMemory, modifier: Modifier = Modifier) {
    val emphasis = remember { SpanStyle(color = Color.White, fontWeight = FontWeight.Medium) }
    val text = remember(memory) { buildMusicMemoryText(memory, emphasis) }
    InfoCard("回忆坐标", modifier) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
