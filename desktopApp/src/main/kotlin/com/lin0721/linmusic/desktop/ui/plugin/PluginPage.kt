package com.lin0721.linmusic.desktop.ui.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.plugin.ActivePluginPage
import com.lin0721.linmusic.core.plugin.PlayerLayoutNode
import com.lin0721.linmusic.core.plugin.PluginConfirmations
import com.lin0721.linmusic.core.plugin.PluginImageSource
import com.lin0721.linmusic.core.plugin.PluginOption
import com.lin0721.linmusic.core.plugin.PluginPageHost
import com.lin0721.linmusic.core.plugin.PluginPagePlacement
import com.lin0721.linmusic.core.plugin.PluginPageUi
import com.lin0721.linmusic.core.plugin.PluginPageView
import com.lin0721.linmusic.core.plugin.PluginRepository
import com.lin0721.linmusic.core.plugin.PluginTrack
import com.lin0721.linmusic.core.plugin.layoutShape
import com.lin0721.linmusic.core.plugin.snapToStep
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.player.PlayerSlider
import com.lin0721.linmusic.desktop.ui.settings.SettingSwitch
import com.lin0721.linmusic.desktop.ui.settings.SettingsActionButton
import com.lin0721.linmusic.desktop.ui.settings.SettingsDropdown
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.TrackRow
import kotlin.math.roundToInt

private const val DEFAULT_IMAGE_SIZE = 120f

// 插件页面：路由里只存 id，插件被停用或删除后提示不可用
@Composable
internal fun PluginPageScreen(
    pluginId: String,
    pageId: String,
    repository: PluginRepository,
    host: PluginPageHost,
    modifier: Modifier = Modifier
) {
    val pages by repository.pages.collectAsState()
    val active = pages.firstOrNull { it.manifest.id == pluginId && it.page.id == pageId }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        when {
            active == null -> Text("插件已停用或已删除", color = DesktopColors.TextGray, fontSize = 13.sp)
            active.root == null -> Text("页面描述无效：${active.error.orEmpty()}", color = DesktopColors.Error, fontSize = 13.sp)
            else -> {
                Text(active.page.title, color = DesktopColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(active.manifest.name, color = DesktopColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
                PluginPageView(active, host, DesktopPluginPageUi, Modifier.fillMaxWidth())
            }
        }
    }
}

internal object DesktopPluginPageUi : PluginPageUi {

    @Composable
    override fun Image(source: PluginImageSource, node: PlayerLayoutNode, modifier: Modifier) {
        val size = (node.maxSize ?: DEFAULT_IMAGE_SIZE) * node.size.scale
        AsyncImage(
            model = when (source) {
                is PluginImageSource.Asset -> source.file
                is PluginImageSource.Remote -> source.url
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size.dp).clip(node.layoutShape())
        )
    }

    @Composable
    override fun Tracks(tracks: List<PluginTrack>, onPlay: (Int) -> Unit, modifier: Modifier) {
        Column(modifier) {
            tracks.forEachIndexed { index, track ->
                val row = remember(track) {
                    Track(
                        id = track.songId,
                        name = track.title,
                        ar = listOf(Artist(name = track.artist)),
                        al = Album(picUrl = track.cover)
                    )
                }
                TrackRow(index = index, track = row, isCurrent = false, onPlay = { onPlay(index) })
            }
        }
    }

    @Composable
    override fun Button(text: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier) {
        Box(modifier) { SettingsActionButton(text, onClick = onClick, enabled = !busy) }
    }

    @Composable
    override fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = DesktopColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            SettingSwitch(checked, onChange = onChange)
        }
    }

    @Composable
    override fun Input(label: String, value: String, onChange: (String) -> Unit, onCommit: () -> Unit, modifier: Modifier) {
        DialogTextField(value, onChange, modifier, placeholder = label, onEnter = onCommit)
    }

    @Composable
    override fun Loading(modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = DesktopColors.Accent)
        }
    }

    @Composable
    override fun Select(label: String, options: List<PluginOption>, selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = DesktopColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            SettingsDropdown(
                options = options,
                current = options.firstOrNull { it.value == selected } ?: options.first(),
                label = { it.label },
                onSelect = { onSelect(it.value) }
            )
        }
    }

    @Composable
    override fun Slider(label: String, value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit, onCommit: () -> Unit, modifier: Modifier) {
        Column(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = DesktopColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(formatNumber(value), color = DesktopColors.TextGray, fontSize = 12.sp)
            }
            PlayerSlider(
                value = (value - min) / (max - min),
                onValueChange = { onChange(snapToStep(min + it * (max - min), min, max, step)) },
                modifier = Modifier.fillMaxWidth(),
                onValueChangeFinished = onCommit
            )
        }
    }

    @Composable
    override fun ProgressBar(fraction: Float, modifier: Modifier) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = modifier.fillMaxWidth(),
            color = DesktopColors.Accent,
            trackColor = DesktopColors.Surface
        )
    }

    @Composable
    override fun Badge(text: String, modifier: Modifier) {
        Text(
            text,
            color = DesktopColors.TextGray,
            fontSize = 11.sp,
            modifier = modifier.clip(RoundedCornerShape(4.dp)).background(DesktopColors.Surface).padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }

    @Composable
    override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                FilterChip(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = DesktopColors.Surface,
                        labelColor = DesktopColors.TextGray,
                        selectedContainerColor = DesktopColors.TextPrimary,
                        selectedLabelColor = DesktopColors.InverseText
                    ),
                    border = null
                )
            }
        }
    }

    private fun formatNumber(value: Float): String =
        if (value == value.toLong().toFloat()) value.toLong().toString() else ((value * 100).roundToInt() / 100f).toString()
}

// 插件请求用户确认时显示，关闭即拒绝
@Composable
internal fun PluginConfirmDialog(confirmations: PluginConfirmations) {
    val request by confirmations.pending.collectAsState()
    val pending = request ?: return
    DesktopDialog(
        title = pending.title,
        onDismiss = { confirmations.respond(false) },
        actions = {
            DialogButton("拒绝", onClick = { confirmations.respond(false) })
            DialogButton("允许", onClick = { confirmations.respond(true) }, primary = true)
        }
    ) {
        Text(pending.message, color = DesktopColors.TextGray, fontSize = 13.sp)
    }
}

// 音乐库侧栏底部的插件页面入口
@Composable
internal fun PluginPageEntries(repository: PluginRepository, onOpen: (ActivePluginPage) -> Unit) {
    val pages by repository.pages.collectAsState()
    val sidebarPages = pages.filter { it.page.placement == PluginPagePlacement.SIDEBAR }
    if (sidebarPages.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text("插件", color = DesktopColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        sidebarPages.forEach { active ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onOpen(active) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Extension, null, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
                Text(
                    active.page.title,
                    color = DesktopColors.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
