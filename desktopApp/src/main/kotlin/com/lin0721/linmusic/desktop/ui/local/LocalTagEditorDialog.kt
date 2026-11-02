package com.lin0721.linmusic.desktop.ui.local

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.data.CoverImage
import com.lin0721.linmusic.desktop.data.chooseImageFile
import com.lin0721.linmusic.desktop.data.local.CoverChange
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.desktop.data.local.LocalTagEdit
import com.lin0721.linmusic.desktop.data.local.LocalTrack
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogLabel
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

private const val TEXT_MAX_LENGTH = 200
private val YEAR_PATTERN = Regex("""\d{0,4}""")
private val TRACK_PATTERN = Regex("""(\d{1,3}(/\d{1,3})?)?""")

private sealed interface TagLoad {
    data object Loading : TagLoad
    data object Unsupported : TagLoad
    class Ready(val tags: LocalTagEdit) : TagLoad
}

// 编辑结果直接写回音频文件
@Composable
internal fun LocalTagEditorDialog(
    track: LocalTrack,
    repository: LocalLibraryRepository,
    onDismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    var load by remember(track.path) { mutableStateOf<TagLoad>(TagLoad.Loading) }
    LaunchedEffect(track.path) {
        val tags = withContext(Dispatchers.IO) { repository.readEditableTags(track.path) }
        load = if (tags == null) TagLoad.Unsupported else TagLoad.Ready(tags)
    }
    var saving by remember { mutableStateOf(false) }

    when (val state = load) {
        TagLoad.Loading -> DesktopDialog(
            title = "编辑歌曲信息",
            onDismiss = onDismiss,
            width = 520.dp,
            actions = { DialogButton("取消", onDismiss) }
        ) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesktopColors.Accent)
            }
        }
        TagLoad.Unsupported -> DesktopDialog(
            title = "编辑歌曲信息",
            onDismiss = onDismiss,
            width = 400.dp,
            actions = { DialogButton("知道了", onDismiss, primary = true) }
        ) {
            Text("无法读取该文件的标签，暂不支持编辑这种格式。", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
        is TagLoad.Ready -> TagEditorForm(
            track = track,
            initial = state.tags,
            coverUri = repository.coverUri(track),
            saving = saving,
            onDismiss = onDismiss,
            onSave = { edit, cover ->
                saving = true
                scope.launch {
                    val result = repository.updateTags(track.path, edit, cover)
                    saving = false
                    if (result.isSuccess) {
                        navigator.showMessage("已保存歌曲信息")
                        onDismiss()
                    } else {
                        navigator.showMessage("保存失败，文件可能正在播放或为只读")
                    }
                }
            }
        )
    }
}

@Composable
private fun TagEditorForm(
    track: LocalTrack,
    initial: LocalTagEdit,
    coverUri: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (LocalTagEdit, CoverChange) -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial.title) }
    var artist by remember { mutableStateOf(initial.artist) }
    var album by remember { mutableStateOf(initial.album) }
    var year by remember { mutableStateOf(initial.year) }
    var trackNumber by remember { mutableStateOf(initial.trackNumber) }
    var lyrics by remember { mutableStateOf(initial.lyrics) }
    var cover by remember { mutableStateOf<CoverChange>(CoverChange.Keep) }
    var coverPreview by remember { mutableStateOf<ImageBitmap?>(null) }

    val edit = LocalTagEdit(title, artist, album, year, trackNumber, lyrics)
    val valid = title.isNotBlank() && year.matches(YEAR_PATTERN) && trackNumber.trim().matches(TRACK_PATTERN)
    val changed = edit != initial || cover != CoverChange.Keep
    val pickCover: () -> Unit = {
        scope.launch {
            val file = chooseImageFile("选择封面") ?: return@launch
            withContext(Dispatchers.IO) { CoverImage.prepareJpeg(file) }
                .onSuccess { bytes ->
                    cover = CoverChange.Replace(bytes)
                    coverPreview = withContext(Dispatchers.IO) { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }
                }
                .onFailure { navigator.showMessage(it.message ?: "无法处理该图片") }
        }
    }

    DesktopDialog(
        title = "编辑歌曲信息",
        onDismiss = { if (!saving) onDismiss() },
        width = 560.dp,
        actions = {
            DialogButton("取消", onDismiss, enabled = !saving)
            DialogButton("保存", { onSave(edit, cover) }, primary = true, enabled = valid && changed, loading = saving)
        }
    ) {
        Text(
            track.path,
            color = DesktopColors.TextGray,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(6.dp))) {
                    val preview = coverPreview
                    when {
                        preview != null -> Image(preview, "新封面", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        cover == CoverChange.Remove -> Cover(null, 120.dp, shape = RoundedCornerShape(6.dp))
                        else -> Cover(coverUri, 120.dp, shape = RoundedCornerShape(6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DialogButton("更换", pickCover, enabled = !saving)
                    DialogButton("移除", {
                        cover = CoverChange.Remove
                        coverPreview = null
                    }, enabled = !saving)
                }
            }
            Column(Modifier.weight(1f)) {
                DialogLabel("标题")
                DialogTextField(title, { title = it.take(TEXT_MAX_LENGTH) }, enabled = !saving)
                DialogLabel("歌手（多位用 / 分隔）", Modifier.padding(top = 8.dp))
                DialogTextField(artist, { artist = it.take(TEXT_MAX_LENGTH) }, enabled = !saving)
                DialogLabel("专辑", Modifier.padding(top = 8.dp))
                DialogTextField(album, { album = it.take(TEXT_MAX_LENGTH) }, enabled = !saving)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        DialogLabel("年份")
                        DialogTextField(year, { if (it.matches(YEAR_PATTERN)) year = it }, enabled = !saving, placeholder = "如 2024")
                    }
                    Column(Modifier.weight(1f)) {
                        DialogLabel("音轨号")
                        DialogTextField(trackNumber, { trackNumber = it.take(7) }, enabled = !saving, placeholder = "如 3 或 3/12")
                    }
                }
            }
        }
        DialogLabel("内嵌歌词（LRC 格式）", Modifier.padding(top = 12.dp))
        DialogTextField(
            value = lyrics,
            onValueChange = { lyrics = it },
            singleLine = false,
            minLines = 6,
            maxLines = 10,
            enabled = !saving
        )
    }
}
