package com.lin0721.linmusic.feature.player.share

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Velocity
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.lin0721.linmusic.MelodiaToastHost
import com.lin0721.linmusic.R
import com.lin0721.linmusic.rememberGlobalToastMessage
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.share.LyricShareSelection
import com.lin0721.linmusic.core.share.ShareCardBackground
import com.lin0721.linmusic.core.share.ShareCardKind
import com.lin0721.linmusic.core.share.ShareCardRatio
import com.lin0721.linmusic.core.share.ShareCardStyle
import com.lin0721.linmusic.core.share.ui.ShareCardArt
import com.lin0721.linmusic.core.share.ui.ShareCardExportSurface
import com.lin0721.linmusic.core.share.ui.ShareCardTypography
import com.lin0721.linmusic.core.share.ui.rememberShareCardCapture
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.extractShareColorsFromUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "LyricShareSheet"

private enum class ShareStep { SELECT, PREVIEW }

private enum class ExportAction { SAVE, SHARE }

// 歌词分享：先选句，再挑样式导出；没有可选歌词时直接进歌曲卡
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricShareSheet(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    title: String,
    artist: String,
    coverUrl: String,
    durationMs: Long,
    songLink: String? = null,
    initialKind: ShareCardKind = ShareCardKind.LYRIC,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasLyrics = remember(lyrics) { lyrics.any { LyricShareSelection.isSelectable(it) } }

    // 歌曲卡入口直接进预览，切到歌词卡时沿用默认选中的当前句
    val startWithLyric = hasLyrics && initialKind == ShareCardKind.LYRIC
    var step by remember { mutableStateOf(if (startWithLyric) ShareStep.SELECT else ShareStep.PREVIEW) }
    var pick by remember { mutableStateOf(LyricShareSelection.initial(lyrics, currentIndex)) }
    var kind by remember { mutableStateOf(if (startWithLyric) ShareCardKind.LYRIC else ShareCardKind.SONG) }
    val styleByKind = remember { mutableStateOf(mapOf(ShareCardKind.SONG to 0, ShareCardKind.LYRIC to 0)) }
    var background by remember { mutableStateOf<ShareCardBackground>(ShareCardBackground.Auto) }
    var ratio by remember { mutableStateOf(ShareCardRatio.PORTRAIT) }
    var typography by remember { mutableStateOf(ShareCardTypography()) }
    var exporting by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf(false) }

    var cover by remember(coverUrl) { mutableStateOf<Painter?>(null) }
    var palette by remember(coverUrl) { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(coverUrl) {
        if (coverUrl.isBlank()) return@LaunchedEffect
        cover = loadCoverPainter(context, coverUrl)
        palette = extractShareColorsFromUrl(context, coverUrl)
    }
    val iconBackground = painterResource(R.drawable.ic_launcher_background)
    val iconForeground = painterResource(R.drawable.ic_launcher_foreground)
    val logo = remember(iconBackground, iconForeground) { LayeredPainter(listOf(iconBackground, iconForeground)) }
    val art = remember(cover, palette, logo) { ShareCardArt(cover, logo, palette) }

    val styles = ShareCardStyle.of(kind)
    val style = styles[(styleByKind.value[kind] ?: 0).coerceIn(0, styles.lastIndex)]
    val content = remember(lyrics, pick, title, artist, durationMs) {
        LyricShareSelection.content(title, artist, lyrics, pick, durationMs)
    }
    val capture = rememberShareCardCapture()

    fun export(action: ExportAction) {
        if (exporting) return
        exporting = true
        scope.launch {
            try {
                val bitmap = capture.capture().asAndroidBitmap().let {
                    // 硬件位图不能直接编码
                    if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
                }
                val name = "${title}_${style.label}"
                when (action) {
                    ExportAction.SAVE -> ToastManager.showToast(
                        if (ShareCardImages.saveToGallery(context, bitmap, name)) "已保存到相册" else "保存失败"
                    )
                    ExportAction.SHARE -> {
                        val uri = ShareCardImages.writeShareFile(context, bitmap, name)
                        if (uri != null) ShareCardImages.launchShare(context, uri) else ToastManager.showToast("生成图片失败")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "导出分享卡片失败", e)
                ToastManager.showToast("生成图片失败")
            } finally {
                exporting = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) export(ExportAction.SAVE) else ToastManager.showToast("没有存储权限，无法保存")
    }

    LaunchedEffect(pendingSave) {
        if (!pendingSave) return@LaunchedEffect
        pendingSave = false
        if (ShareCardImages.needsLegacyPermission(context)) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            export(ExportAction.SAVE)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        val nestedScrollConnection = remember(sheetState) {
            object : NestedScrollConnection {
                // 列表先用完惯性，全展开时只拦截滚到底后剩余的向上速度，防止传给底栏引发回弹抽动
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    return if (available.y < 0 && sheetState.targetValue == SheetValue.Expanded) {
                        available
                    } else {
                        Velocity.Zero
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f).nestedScroll(nestedScrollConnection)) {
            ShareCardExportSurface(capture, content, style, background, ratio, art, typography)
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "shareStep"
            ) { target ->
                when (target) {
                    ShareStep.SELECT -> LyricSelectPage(
                        lyrics = lyrics,
                        pick = pick,
                        tint = palette.firstOrNull(),
                        onToggle = { index, translation ->
                            val next = if (translation) {
                                LyricShareSelection.toggleTranslation(lyrics, pick, index)
                            } else {
                                LyricShareSelection.toggleOriginal(lyrics, pick, index)
                            }
                            if (next == pick && index !in pick.lineIndices) {
                                ToastManager.showToast("最多选择 ${LyricShareSelection.MAX_LINES} 句")
                            }
                            pick = next
                        },
                        onClose = onDismiss,
                        onContinue = {
                            kind = ShareCardKind.LYRIC
                            step = ShareStep.PREVIEW
                        }
                    )
                    ShareStep.PREVIEW -> ShareCardPreviewPage(
                        content = content,
                        art = art,
                        kind = kind,
                        hasLyrics = hasLyrics && !pick.isEmpty,
                        styleIndex = styleByKind.value[kind] ?: 0,
                        background = background,
                        ratio = ratio,
                        typography = typography,
                        blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && cover != null,
                        exporting = exporting,
                        songLink = songLink,
                        onKindChange = { kind = it },
                        onStyleIndexChange = { styleByKind.value = styleByKind.value + (kind to it) },
                        onBackgroundChange = { background = it },
                        onRatioChange = { ratio = it },
                        onTypographyChange = { typography = it },
                        onEditLyrics = if (hasLyrics) ({ step = ShareStep.SELECT }) else null,
                        onSave = { pendingSave = true },
                        onShare = { export(ExportAction.SHARE) }
                    )
                }
            }
            // 弹窗是独立窗口，会盖住主界面的提示条，这里再挂一份
            MelodiaToastHost(rememberGlobalToastMessage())
        }
    }
}

private suspend fun loadCoverPainter(context: android.content.Context, url: String): Painter? = try {
    val request = ImageRequest.Builder(context)
        .data(url.substringBefore("?param="))
        .size(1080, 1080)
        .allowHardware(false)
        .build()
    context.imageLoader.execute(request).image?.toBitmap()?.let { BitmapPainter(it.asImageBitmap()) }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppLogger.w(TAG, "加载分享封面失败", e)
    null
}

// 自适应图标的背景层与前景层叠成一个 Painter，与关于页的图标画法一致
private class LayeredPainter(private val layers: List<Painter>) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        layers.forEach { with(it) { draw(size) } }
    }
}
