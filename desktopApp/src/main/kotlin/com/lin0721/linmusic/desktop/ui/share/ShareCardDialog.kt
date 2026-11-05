package com.lin0721.linmusic.desktop.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.lyricLineKey
import com.lin0721.linmusic.core.share.LyricPick
import com.lin0721.linmusic.core.share.LyricShareSelection
import com.lin0721.linmusic.core.share.ShareCardBackground
import com.lin0721.linmusic.core.share.ShareCardKind
import com.lin0721.linmusic.core.share.ShareCardRatio
import com.lin0721.linmusic.core.share.ShareCardStyle
import com.lin0721.linmusic.core.share.ui.ShareCard
import com.lin0721.linmusic.core.share.ui.ShareCardArt
import com.lin0721.linmusic.core.share.ui.ShareCardExportSurface
import com.lin0721.linmusic.core.share.ui.ShareCardTypography
import com.lin0721.linmusic.core.share.ui.ShareCardTypographyOptions
import com.lin0721.linmusic.core.share.ui.rememberShareCardCapture
import com.lin0721.linmusic.desktop.loadAppIcon
import com.lin0721.linmusic.desktop.platform.chooseSaveFile
import com.lin0721.linmusic.desktop.platform.revealInFileManager
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogLabel
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.components.songLink
import com.lin0721.linmusic.desktop.ui.home.OutlineChip
import com.lin0721.linmusic.desktop.ui.palette.extractShareColors
import com.lin0721.linmusic.desktop.ui.palette.shareColorsFrom
import com.lin0721.linmusic.desktop.ui.palette.FallbackBase
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ShareCardDialog"
private val RowShape = RoundedCornerShape(10.dp)
private val PreviewShape = RoundedCornerShape(14.dp)
private val SelectedInk = Color(0xFF1A1A1A)
private const val COVER_SIZE = 1080
private const val PALETTE_SIZE = 128

private enum class Export { SAVE, COPY }

private const val NOTICE_MS = 2_500L

// 分享卡片：左侧选句，中间预览，右侧样式；导出为另存 PNG 或复制到剪贴板
@Composable
fun ShareCardDialog(
    initialKind: ShareCardKind,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    // 主窗口的提示条被对话框挡住，结果提示显示在对话框底部
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MS)
            notice = null
        }
    }
    val scope = rememberCoroutineScope()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val detail by playerViewModel.songDetailState.collectAsState()
    val primaryIndex by playerViewModel.primaryLyricIndex.collectAsState()
    val duration by controller.duration.collectAsState()
    val track = nowPlaying
    if (track == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    // 详情属于当前曲目时才用它的歌词，切歌瞬间不会混进上一首
    val lyrics: List<LyricLine> = detail.lyrics.takeIf { detail.songDetail == null || detail.songDetail?.id == track.songId }.orEmpty()
    val hasLyrics = remember(lyrics) { lyrics.any { LyricShareSelection.isSelectable(it) } }

    var kind by remember { mutableStateOf(if (hasLyrics) initialKind else ShareCardKind.SONG) }
    var pick by remember(lyrics) { mutableStateOf(LyricShareSelection.initial(lyrics, primaryIndex)) }
    var styleByKind by remember { mutableStateOf(mapOf(ShareCardKind.SONG to ShareCardStyle.COVER, ShareCardKind.LYRIC to ShareCardStyle.CLASSIC)) }
    var background by remember { mutableStateOf<ShareCardBackground>(ShareCardBackground.Auto) }
    var ratio by remember { mutableStateOf(ShareCardRatio.PORTRAIT) }
    var typography by remember { mutableStateOf(ShareCardTypography()) }
    var fontOption by remember { mutableStateOf(ShareCardTypographyOptions.presetFonts.first()) }
    var exporting by remember { mutableStateOf<Export?>(null) }

    val coverUrl = track.artworkUri.orEmpty()
    var coverImage by remember(coverUrl) { mutableStateOf<ImageBitmap?>(null) }
    var palette by remember(coverUrl) { mutableStateOf(shareColorsFrom(FallbackBase)) }
    LaunchedEffect(coverUrl) {
        if (coverUrl.isBlank()) return@LaunchedEffect
        coverImage = ShareCardImageIO.loadImage(coverUrl, COVER_SIZE)
        ShareCardImageIO.loadImage(coverUrl, PALETTE_SIZE)?.let { small ->
            val pixels = IntArray(small.width * small.height)
            small.readPixels(pixels)
            palette = runCatching { extractShareColors(pixels) }.getOrDefault(palette)
        }
    }
    val logo = remember { loadAppIcon() }
    val cover = remember(coverImage) { coverImage?.let { BitmapPainter(it) } }
    val art = remember(cover, palette, logo) { ShareCardArt(cover, logo, palette) }

    val style = styleByKind.getValue(kind)
    val content = remember(lyrics, pick, track, duration) {
        LyricShareSelection.content(track.title, track.artist, lyrics, pick, duration)
    }
    val lyricReady = kind == ShareCardKind.SONG || !pick.isEmpty
    val podcast by playerViewModel.podcastState.collectAsState()
    // 未匹配云端的本地歌曲只有负数占位 id，没有可复制的链接
    val cloudSongId = track.songId?.takeIf { it > 0 && !detail.isLocalOnly && !podcast.isPodcast }
    val capture = rememberShareCardCapture()

    fun export(action: Export) {
        if (exporting != null || !lyricReady) return
        exporting = action
        scope.launch {
            try {
                val image = capture.capture()
                when (action) {
                    Export.COPY -> notice = (
                        if (ShareCardImageIO.copyToClipboard(image)) "已复制图片" else "复制失败"
                    )
                    Export.SAVE -> {
                        val name = ShareCardImageIO.safeFileName("${track.title} - ${style.label}") + ".png"
                        val file = chooseSaveFile("保存分享图片", name, "png") ?: return@launch
                        if (ShareCardImageIO.savePng(image, file)) {
                            notice = "已保存到 ${file.name}"
                            revealInFileManager(file)
                        } else {
                            notice = "保存失败"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "导出分享卡片失败", e)
                notice = "生成图片失败"
            } finally {
                exporting = null
            }
        }
    }

    val showPicker = kind == ShareCardKind.LYRIC && hasLyrics
    DesktopDialog(
        title = "分享",
        onDismiss = onDismiss,
        width = if (showPicker) 940.dp else 680.dp,
        actions = {
            notice?.let {
                Text(it, color = DesktopColors.TextGray, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
            cloudSongId?.let { id ->
                DialogButton("复制链接", {
                    notice = if (copyTextToClipboard(songLink(id))) "已复制链接" else "复制失败"
                })
            }
            DialogButton("取消", onDismiss)
            DialogButton("另存为…", { export(Export.SAVE) }, enabled = lyricReady, loading = exporting == Export.SAVE)
            DialogButton("复制图片", { export(Export.COPY) }, primary = true, enabled = lyricReady, loading = exporting == Export.COPY)
        }
    ) {
        Box {
            ShareCardExportSurface(capture, content, style, background, ratio, art, typography)
            Row(Modifier.fillMaxWidth().height(540.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (showPicker) {
                    LyricPicker(
                        lyrics = lyrics,
                        pick = pick,
                        anchor = primaryIndex,
                        onToggle = { index, translation ->
                            val next = if (translation) {
                                LyricShareSelection.toggleTranslation(lyrics, pick, index)
                            } else {
                                LyricShareSelection.toggleOriginal(lyrics, pick, index)
                            }
                            if (next == pick && index !in pick.lineIndices) {
                                notice = "最多选择 ${LyricShareSelection.MAX_LINES} 句"
                            }
                            pick = next
                        },
                        modifier = Modifier.width(260.dp).fillMaxHeight()
                    )
                }
                Box(Modifier.width(300.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (lyricReady) {
                        ShareCard(
                            content = content,
                            style = style,
                            background = background,
                            ratio = ratio,
                            art = art,
                            modifier = Modifier.aspectRatio(ratio.aspect).clip(PreviewShape),
                            typography = typography
                        )
                    } else {
                        Text("在左侧选择要分享的歌词", color = DesktopColors.TextGray, fontSize = 13.sp)
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (hasLyrics) {
                        Section("类型") {
                            ShareCardKind.entries.forEach { option ->
                                OutlineChip(option.label, option == kind) { kind = option }
                            }
                        }
                    }
                    Section("样式") {
                        ShareCardStyle.of(kind).forEach { option ->
                            OutlineChip(option.label, option == style) { styleByKind = styleByKind + (kind to option) }
                        }
                    }
                    Section("背景") {
                        BackgroundSwatches(art, background) { background = it }
                    }
                    Section("比例") {
                        ShareCardRatio.entries.forEach { option ->
                            OutlineChip(option.label, option == ratio) { ratio = option }
                        }
                    }
                    if (kind == ShareCardKind.LYRIC) {
                        Section("歌词字体") {
                            ShareFontPicker(fontOption) { option ->
                                fontOption = option
                                typography = typography.copy(family = option.family)
                            }
                        }
                        Section("字号") {
                            ShareCardTypographyOptions.scales.forEach { scale ->
                                OutlineChip(ShareCardTypographyOptions.scaleLabel(scale), scale == typography.scale) {
                                    typography = typography.copy(scale = scale)
                                }
                            }
                        }
                        Section("字重") {
                            ShareCardTypographyOptions.weights.forEach { (label, weight) ->
                                OutlineChip(label, weight == typography.weight) { typography = typography.copy(weight = weight) }
                            }
                        }
                        Section("对齐") {
                            ShareCardTypographyOptions.aligns.forEach { (label, align) ->
                                OutlineChip(label, align == typography.align) { typography = typography.copy(align = align) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column {
        DialogLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun LyricPicker(
    lyrics: List<LyricLine>,
    pick: LyricPick,
    anchor: Int,
    onToggle: (index: Int, translation: Boolean) -> Unit,
    modifier: Modifier
) {
    val rows = remember(lyrics) { lyrics.withIndex().filter { LyricShareSelection.isSelectable(it.value) } }
    val listState = rememberLazyListState()
    LaunchedEffect(rows) {
        val target = pick.lineIndices.minOrNull() ?: anchor
        val position = rows.indexOfFirst { it.index >= target }
        if (position > 2) listState.scrollToItem(position - 2)
    }
    Column(modifier) {
        DialogLabel("选择歌词 · 已选 ${pick.lineCount}/${LyricShareSelection.MAX_LINES}，原文与翻译可分别点选")
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().clip(RowShape).background(DesktopColors.Overlay.copy(alpha = 0.04f)),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(rows, key = { lyricLineKey(it.index, it.value) }) { (index, line) ->
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    PickChunk(line.text.trim(), index in pick.originals, translation = false) { onToggle(index, false) }
                    line.translation?.takeIf { it.isNotBlank() }?.let {
                        PickChunk(it.trim(), index in pick.translations, translation = true) { onToggle(index, true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickChunk(text: String, selected: Boolean, translation: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = when {
            selected -> if (translation) SelectedInk.copy(alpha = 0.75f) else SelectedInk
            translation -> DesktopColors.TextGray
            else -> DesktopColors.TextPrimary
        },
        fontSize = if (translation) 12.sp else 14.sp,
        fontWeight = if (translation) FontWeight.Normal else FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
            .clip(RowShape)
            .background(if (selected) DesktopColors.TextPrimary else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (translation) 4.dp else 6.dp)
    )
}

@Composable
private fun BackgroundSwatches(
    art: ShareCardArt,
    current: ShareCardBackground,
    onChange: (ShareCardBackground) -> Unit
) {
    val base = art.palette.firstOrNull() ?: FallbackBase
    Swatch(current == ShareCardBackground.Auto, "自动", { onChange(ShareCardBackground.Auto) }) {
        Box(Modifier.fillMaxSize().background(lerp(base, Color.Black, 0.25f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
    art.palette.take(ShareCardBackground.PALETTE_SIZE).forEachIndexed { i, color ->
        val option = ShareCardBackground.Palette(i)
        Swatch(current == option, "封面取色 ${i + 1}", { onChange(option) }) {
            Box(Modifier.fillMaxSize().background(lerp(color, Color.Black, 0.15f)))
        }
    }
    ShareCardBackground.presets.forEach { option ->
        Swatch(current == option, option.label, { onChange(option) }) {
            Box(Modifier.fillMaxSize().background(Color(option.argb)))
        }
    }
    art.cover?.let { cover ->
        Swatch(current == ShareCardBackground.BlurCover, "模糊封面", { onChange(ShareCardBackground.BlurCover) }) {
            Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(3.dp))
        }
    }
}

@Composable
private fun Swatch(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    DesktopTooltip(label) {
        Box(
            Modifier.size(32.dp)
                .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.2f), CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
        ) {
            content()
        }
    }
}
