package com.lin0721.linmusic.desktop.ui.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.app.DesktopPaths
import com.lin0721.linmusic.desktop.platform.chooseDirectory
import com.lin0721.linmusic.desktop.platform.chooseFiles
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.launch
import java.io.File

private const val NAME_MAX_LENGTH = 40

private const val AUDIO_FILE_FILTER = "*.mp3;*.flac;*.m4a;*.aac;*.ogg;*.opus;*.wav;*.aif;*.aiff;*.wma;*.ape;*.dsf;*.dff;*.wv"

internal val musicHome: File get() = File(System.getProperty("user.home"), "Music").takeIf { it.isDirectory } ?: DesktopPaths.defaultDownloadDir

internal suspend fun pickMusicFolder(): File? = chooseDirectory(musicHome, "选择音乐文件夹")

internal suspend fun pickAudioFiles(): List<File> = chooseFiles("选择歌曲文件", AUDIO_FILE_FILTER)

// 添加本地歌单：可只填名称建空歌单，也可选文件夹（名称留空时取文件夹名）
@Composable
internal fun AddLocalPlaylistDialog(onDismiss: () -> Unit, onCreate: (name: String, folder: File?) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<File?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val canCreate = name.isNotBlank() || folder != null
    val submit = { if (canCreate) onCreate(name.trim(), folder) }
    DesktopDialog(
        title = "添加本地歌单",
        onDismiss = onDismiss,
        width = 400.dp,
        actions = {
            DialogButton("取消", onDismiss)
            DialogButton("创建", submit, primary = true, enabled = canCreate)
        }
    ) {
        DialogTextField(
            value = name,
            onValueChange = { name = it.take(NAME_MAX_LENGTH) },
            placeholder = folder?.name?.let { "歌单名称（默认：$it）" } ?: "歌单名称",
            focusRequester = focusRequester,
            onEnter = submit
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("绑定文件夹（可选）", color = DesktopColors.TextPrimary, fontSize = 13.sp)
                Text(
                    folder?.path ?: "绑定后会自动收录文件夹内的歌曲",
                    color = DesktopColors.TextGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (folder != null) DialogButton("清除", { folder = null })
            DialogButton("选择", { scope.launch { pickMusicFolder()?.let { folder = it } } })
        }
    }
}

@Composable
internal fun RenameLocalPlaylistDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val submit = { if (name.isNotBlank()) onConfirm(name.trim()) }
    DesktopDialog(
        title = "重命名歌单",
        onDismiss = onDismiss,
        width = 360.dp,
        actions = {
            DialogButton("取消", onDismiss)
            DialogButton("保存", submit, primary = true, enabled = name.isNotBlank())
        }
    ) {
        DialogTextField(
            value = name,
            onValueChange = { name = it.take(NAME_MAX_LENGTH) },
            placeholder = "歌单名称",
            focusRequester = focusRequester,
            onEnter = submit
        )
    }
}

@Composable
internal fun RemoveLocalPlaylistDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    DesktopDialog(
        title = "移除本地歌单",
        onDismiss = onDismiss,
        width = 360.dp,
        actions = {
            DialogButton("取消", onDismiss)
            DialogButton("移除", onConfirm, danger = true)
        }
    ) {
        Text("确定要移除歌单「$name」吗？磁盘上的音乐文件不会被删除。", color = DesktopColors.TextGray, fontSize = 13.sp)
    }
}
