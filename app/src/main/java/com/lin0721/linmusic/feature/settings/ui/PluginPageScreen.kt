package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.plugin.ActivePluginPage
import com.lin0721.linmusic.core.plugin.PlayerLayoutNode
import com.lin0721.linmusic.core.plugin.PluginConfirmations
import com.lin0721.linmusic.core.plugin.PluginImageSource
import com.lin0721.linmusic.core.plugin.PluginManifest
import com.lin0721.linmusic.core.plugin.PluginSettingsView
import com.lin0721.linmusic.core.plugin.PluginOption
import com.lin0721.linmusic.core.plugin.PluginPageHost
import com.lin0721.linmusic.core.plugin.PluginPageUi
import com.lin0721.linmusic.core.plugin.PluginPageView
import com.lin0721.linmusic.core.plugin.PluginTrack
import com.lin0721.linmusic.core.plugin.layoutShape
import com.lin0721.linmusic.core.plugin.snapToStep
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.ui.components.MelodiaSwitch
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

private const val DEFAULT_IMAGE_SIZE = 120f

// 插件页面全屏打开，系统返回键关闭
@Composable
fun PluginPageDialog(active: ActivePluginPage, host: PluginPageHost, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(color = MelodiaColors.Background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MelodiaColors.TextPrimary)
                    }
                    Text(active.page.title, color = MelodiaColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MelodiaSpacing.lg, vertical = MelodiaSpacing.sm)
                ) {
                    if (active.root == null) {
                        Text("页面描述无效：${active.error.orEmpty()}", color = MelodiaColors.Error, fontSize = 14.sp)
                    } else {
                        PluginPageView(active, host, AndroidPluginPageUi, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// 插件请求用户确认时显示，关闭即拒绝
@Composable
fun PluginConfirmDialog(confirmations: PluginConfirmations) {
    val request by confirmations.pending.collectAsState()
    val pending = request ?: return
    AlertDialog(
        onDismissRequest = { confirmations.respond(false) },
        title = { Text(pending.title, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Text(pending.message, color = MelodiaColors.TextSecondary) },
        confirmButton = {
            MelodiaTextButton(onClick = { confirmations.respond(true) }) { Text("允许", color = MelodiaColors.AccentText) }
        },
        dismissButton = {
            MelodiaTextButton(onClick = { confirmations.respond(false) }) { Text("拒绝", color = MelodiaColors.TextPrimary) }
        },
        containerColor = MelodiaColors.Background
    )
}

// 插件设置全屏打开，字段由清单决定
@Composable
fun PluginSettingsDialog(manifest: PluginManifest, host: PluginPageHost, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(color = MelodiaColors.Background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MelodiaColors.TextPrimary)
                    }
                    Text("${manifest.name} 设置", color = MelodiaColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                PluginSettingsView(
                    manifest,
                    host,
                    AndroidPluginPageUi,
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MelodiaSpacing.lg, vertical = MelodiaSpacing.sm)
                )
            }
        }
    }
}

object AndroidPluginPageUi : PluginPageUi {

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
                SongRow(
                    data = SongRowData(id = track.songId, title = track.title, artist = track.artist, coverUrl = track.cover),
                    onClick = { onPlay(index) }
                )
            }
        }
    }

    @Composable
    override fun Button(text: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier) {
        MelodiaOutlinedButton(onClick = onClick, enabled = !busy, modifier = modifier) {
            Text(text, color = MelodiaColors.TextPrimary)
        }
    }

    @Composable
    override fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MelodiaColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            MelodiaSwitch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable
    override fun Input(label: String, value: String, onChange: (String) -> Unit, onCommit: () -> Unit, modifier: Modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier = modifier.fillMaxWidth()
        )
    }

    @Composable
    override fun Loading(modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MelodiaColors.Accent)
        }
    }

    @Composable
    override fun Select(label: String, options: List<PluginOption>, selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
        var picking by remember { mutableStateOf(false) }
        SettingsRow(
            title = label,
            subtitle = options.firstOrNull { it.value == selected }?.label ?: options.first().label,
            onClick = { picking = true }
        )
        if (picking) {
            OptionPickerSheet(
                title = label,
                options = options.map { it.value to it.label },
                selected = selected,
                onSelect = onSelect,
                onDismiss = { picking = false }
            )
        }
    }

    @Composable
    override fun Slider(label: String, value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit, onCommit: () -> Unit, modifier: Modifier) {
        Column(modifier) {
            Text(label, color = MelodiaColors.TextPrimary, fontSize = 15.sp)
            Slider(
                value = value,
                onValueChange = { onChange(snapToStep(it, min, max, step)) },
                onValueChangeFinished = onCommit,
                valueRange = min..max
            )
        }
    }

    @Composable
    override fun ProgressBar(fraction: Float, modifier: Modifier) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = modifier.fillMaxWidth(),
            color = MelodiaColors.Accent,
            trackColor = MelodiaColors.Surface
        )
    }

    @Composable
    override fun Badge(text: String, modifier: Modifier) {
        Text(
            text,
            color = MelodiaColors.TextSecondary,
            fontSize = 12.sp,
            modifier = modifier.clip(RoundedCornerShape(4.dp)).background(MelodiaColors.Surface).padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }

    @Composable
    override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                FilterChip(selected = index == selected, onClick = { onSelect(index) }, label = { Text(label) })
            }
        }
    }
}
