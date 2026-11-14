package com.lin0721.linmusic.desktop.ui.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.LyricsResolver
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.lyricLineKey
import com.lin0721.linmusic.core.share.LyricPick
import com.lin0721.linmusic.core.share.LyricShareSelection
import com.lin0721.linmusic.core.share.ShareCardBackground
import com.lin0721.linmusic.core.share.ShareCardKind
import com.lin0721.linmusic.core.share.ShareCardMessageCodec
import com.lin0721.linmusic.core.share.ShareCardRatio
import com.lin0721.linmusic.core.share.ShareCardStyle
import com.lin0721.linmusic.core.share.ui.ShareCard
import com.lin0721.linmusic.core.share.ui.ShareCardArt
import com.lin0721.linmusic.core.share.ui.ShareCardExportSurface
import com.lin0721.linmusic.core.share.ui.ShareCardTypography
import com.lin0721.linmusic.core.share.ui.ShareCardTypographyOptions
import com.lin0721.linmusic.core.share.ui.rememberShareCardCapture
import com.lin0721.linmusic.core.ui.theme.contentColorOn
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.platform.chooseSaveFile
import com.lin0721.linmusic.desktop.platform.revealInFileManager
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogLabel
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.components.songLink
import com.lin0721.linmusic.desktop.ui.components.uiEnter
import com.lin0721.linmusic.desktop.ui.components.uiExit
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.home.OutlineChip
import com.lin0721.linmusic.desktop.ui.message.ShareToFriendDialog
import com.lin0721.linmusic.desktop.ui.palette.FallbackBase
import com.lin0721.linmusic.desktop.ui.shell.DesktopAppState
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.shell.PageViewModelFactory
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.message.ui.ShareToFriendUiState
import com.lin0721.linmusic.feature.message.ui.ShareToFriendViewModel
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ShareCardDialog"
private val RowShape = RoundedCornerShape(10.dp)
private val PreviewShape = RoundedCornerShape(14.dp)
private val SelectedInk = Color(0xFF1A1A1A)
private const val COVER_SIZE = 1080

private enum class Export { SAVE, COPY }

private const val NOTICE_MS = 2_500L

private data class SentBanner(val id: Long, val user: MessageUser)

// 分享卡片：顶部发给好友，左侧选句，中间预览，右侧样式；导出为另存 PNG 或复制到剪贴板
@Composable
fun ShareCardDialog(
    initialKind: ShareCardKind,
    targetSong: DesktopAppState.ShareSongInfo? = null,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    lyricsResolver: LyricsResolver,
    pageViewModels: PageViewModelFactory,
    onDismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    // 主窗口的提示条被对话框挡住，结果提示显示在对话框底部
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MS)
            notice = null
        }
    }
    var sentBanner by remember { mutableStateOf<SentBanner?>(null) }
    var sentBannerVisible by remember { mutableStateOf(false) }
    var nextBannerId by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val detail by playerViewModel.songDetailState.collectAsState()
    val primaryIndex by playerViewModel.primaryLyricIndex.collectAsState()
    val playbackDuration by controller.duration.collectAsState()
    val podcast by playerViewModel.podcastState.collectAsState()

    val isUsingNowPlaying = targetSong == null || (nowPlaying != null && nowPlaying?.songId == targetSong.songId)
    if (isUsingNowPlaying && nowPlaying == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var resolvedTrack by remember(targetSong?.songId) { mutableStateOf<Track?>(null) }
    var resolvedLyrics by remember(targetSong?.songId) { mutableStateOf<List<LyricLine>>(emptyList()) }
    LaunchedEffect(targetSong?.songId, isUsingNowPlaying) {
        val id = targetSong?.songId ?: return@LaunchedEffect
        if (isUsingNowPlaying || id <= 0L) return@LaunchedEffect
        launch {
            resolvedTrack = playerViewModel.resolveSongDetail(id)
        }
        launch {
            lyricsResolver.lyricsWithSourceFor(id).collect { result ->
                result.onSuccess { resolvedLyrics = it.lines }
            }
        }
    }

    val songId: Long? = if (isUsingNowPlaying) nowPlaying?.songId else targetSong?.songId
    val songTitle: String = if (isUsingNowPlaying) {
        nowPlaying?.title.orEmpty()
    } else {
        resolvedTrack?.name?.takeIf { it.isNotBlank() } ?: targetSong?.title.orEmpty()
    }
    val songArtist: String = if (isUsingNowPlaying) {
        nowPlaying?.artist.orEmpty()
    } else {
        resolvedTrack?.ar?.joinToString(" / ") { it.name }?.takeIf { it.isNotBlank() }
            ?: targetSong?.artist.orEmpty()
    }
    val coverUrl: String = if (isUsingNowPlaying) {
        nowPlaying?.artworkUri.orEmpty()
    } else {
        resolvedTrack?.al?.picUrl?.takeIf { it.isNotBlank() } ?: targetSong?.coverUrl.orEmpty()
    }
    val duration: Long = if (isUsingNowPlaying) {
        playbackDuration
    } else {
        resolvedTrack?.dt?.takeIf { it > 0L } ?: targetSong?.durationMs ?: 0L
    }
    // 详情属于当前曲目时才用它的歌词，切歌瞬间不会混进上一首
    val lyrics: List<LyricLine> = if (isUsingNowPlaying) {
        detail.lyrics.takeIf { detail.songDetail == null || detail.songDetail?.id == nowPlaying?.songId }.orEmpty()
    } else {
        resolvedLyrics
    }
    val anchorIndex = if (isUsingNowPlaying) primaryIndex else 0
    val hasLyrics = remember(lyrics) { lyrics.any { LyricShareSelection.isSelectable(it) } }

    var kind by remember { mutableStateOf(if (hasLyrics) initialKind else ShareCardKind.SONG) }
    var pick by remember(lyrics) { mutableStateOf(LyricShareSelection.initial(lyrics, anchorIndex)) }
    var styleByKind by remember { mutableStateOf(mapOf(ShareCardKind.SONG to ShareCardStyle.COVER, ShareCardKind.LYRIC to ShareCardStyle.CLASSIC)) }
    var background by remember { mutableStateOf<ShareCardBackground>(ShareCardBackground.Auto) }
    var ratio by remember { mutableStateOf(ShareCardRatio.PORTRAIT) }
    var typography by remember { mutableStateOf(ShareCardTypography()) }
    var fontOption by remember { mutableStateOf(ShareCardTypographyOptions.presetFonts.first()) }
    var exporting by remember { mutableStateOf<Export?>(null) }

    val art = rememberShareCardArt(coverUrl, COVER_SIZE)
    val style = styleByKind.getValue(kind)
    val content = remember(lyrics, pick, songTitle, songArtist, duration) {
        LyricShareSelection.content(songTitle, songArtist, lyrics, pick, duration)
    }
    val lyricReady = kind == ShareCardKind.SONG || !pick.isEmpty
    // 未匹配云端的本地歌曲只有负数占位 id，没有可复制的链接，也不能私信分享
    val cloudSongId = if (isUsingNowPlaying) {
        songId?.takeIf { it > 0 && !detail.isLocalOnly && !podcast.isPodcast }
    } else {
        songId?.takeIf { it > 0 }
    }
    val capture = rememberShareCardCapture()
    // 首帧就按会显示好友条预留高度，避免数据回来后整页跳动
    var friendBarVisible by remember { mutableStateOf(cloudSongId != null && navigator.isLoggedIn) }

    fun buildShareNote(): String {
        val shareContent = if (kind == ShareCardKind.SONG) {
            content.copy(lines = emptyList(), lineBefore = null, lineAfter = null)
        } else {
            content
        }
        return ShareCardMessageCodec.encode(style, background, ratio, typography, shareContent)
    }

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
                        val name = ShareCardImageIO.safeFileName("$songTitle - ${style.label}") + ".png"
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
            Column(Modifier.fillMaxWidth()) {
                if (cloudSongId != null && navigator.isLoggedIn) {
                    ShareFriendBarHost(
                        songId = cloudSongId,
                        songTitle = songTitle,
                        lyricReady = lyricReady,
                        factory = pageViewModels,
                        buildNote = ::buildShareNote,
                        onVisibleChange = { friendBarVisible = it },
                        onSent = { user ->
                            nextBannerId += 1
                            sentBanner = SentBanner(nextBannerId, user)
                            sentBannerVisible = true
                        },
                        onNotice = { msg ->
                            notice = msg
                            navigator.showMessage(msg)
                        }
                    )
                } else {
                    LaunchedEffect(Unit) { friendBarVisible = false }
                }
                if (friendBarVisible) {
                    Spacer(Modifier.height(12.dp))
                }
                Row(
                    Modifier.fillMaxWidth().height(if (friendBarVisible) 480.dp else 540.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (showPicker) {
                        LyricPicker(
                            lyrics = lyrics,
                            pick = pick,
                            anchor = anchorIndex,
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
            ShareSentCapsule(
                banner = sentBanner,
                visible = sentBannerVisible,
                onHide = { sentBannerVisible = false },
                onOpenChat = { user ->
                    onDismiss()
                    navigator.openMessages(user.uid, user.nickname, user.avatarUrl)
                }
            )
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

@Composable
private fun ShareFriendBarHost(
    songId: Long,
    songTitle: String,
    lyricReady: Boolean,
    factory: PageViewModelFactory,
    buildNote: () -> String,
    onVisibleChange: (Boolean) -> Unit,
    onSent: (MessageUser) -> Unit,
    onNotice: (String) -> Unit
) {
    // 对话框关闭时随宿主释放临时 ViewModelStore
    val store = remember { ViewModelStore() }
    val viewModel = remember(store, factory) {
        val vmFactory = viewModelFactory {
            addInitializer(ShareToFriendViewModel::class) {
                factory.create(ShareToFriendViewModel::class)
            }
        }
        ViewModelProvider.create(store, vmFactory)[ShareToFriendViewModel::class]
    }
    DisposableEffect(store) {
        onDispose {
            onVisibleChange(false)
            store.clear()
        }
    }

    LaunchedEffect(viewModel) { viewModel.loadIfNeeded() }
    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect(onNotice)
    }
    LaunchedEffect(viewModel) {
        viewModel.shareCompleted.collect(onSent)
    }

    val uiState by viewModel.uiState.collectAsState()
    val readyState = uiState as? ShareToFriendUiState.Ready
    val recentUsers = readyState?.recentUsers.orEmpty()
    // 加载中显示骨架占位；没有最近联系人时仍保留「更多」，可以从关注列表里选人
    val visible = uiState !is ShareToFriendUiState.Error
    LaunchedEffect(visible) { onVisibleChange(visible) }

    var showMoreFriends by remember { mutableStateOf(false) }

    if (visible) {
        ShareFriendBar(
            recentUsers = recentUsers,
            loading = readyState == null,
            sendingToUid = readyState?.sendingToUid,
            lyricReady = lyricReady,
            onShareToUser = { user ->
                if (!lyricReady) {
                    onNotice("在左侧选择要分享的歌词")
                } else {
                    viewModel.shareSong(user.uid, songId, songTitle, buildNote())
                }
            },
            onOpenMore = {
                if (!lyricReady) {
                    onNotice("在左侧选择要分享的歌词")
                } else {
                    showMoreFriends = true
                }
            }
        )
    }

    if (showMoreFriends) {
        ShareToFriendDialog(
            songId = songId,
            songName = songTitle,
            factory = factory,
            note = buildNote(),
            onMessage = onNotice,
            onSent = onSent,
            onDismiss = { showMoreFriends = false }
        )
    }
}

@Composable
private fun ShareFriendBar(
    recentUsers: List<MessageUser>,
    loading: Boolean,
    sendingToUid: Long?,
    lyricReady: Boolean,
    onShareToUser: (MessageUser) -> Unit,
    onOpenMore: () -> Unit
) {
    val idle = sendingToUid == null && !loading
    val moreHover = remember { MutableInteractionSource() }
    val moreHovered by moreHover.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(DesktopColors.Surface)
            .border(1.dp, DesktopColors.Divider, RowShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "发给好友",
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                items(FRIEND_PLACEHOLDER_COUNT) { FriendChipPlaceholder() }
            }
            items(recentUsers, key = { it.uid }) { user ->
                RecentFriendChip(
                    user = user,
                    sending = sendingToUid == user.uid,
                    enabled = idle,
                    dimmed = !lyricReady || (!idle && sendingToUid != user.uid),
                    onClick = { onShareToUser(user) }
                )
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .hoverable(moreHover)
                .background(if (moreHovered && idle) DesktopColors.Hover else DesktopColors.CardSurface)
                .border(
                    width = 1.dp,
                    color = if (moreHovered && idle) DesktopColors.Outline else DesktopColors.Divider,
                    shape = RoundedCornerShape(16.dp)
                )
                .then(
                    if (idle) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onOpenMore)
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer { alpha = if (idle && lyricReady) 1f else 0.55f }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Rounded.MoreHoriz,
                contentDescription = null,
                tint = DesktopColors.TextPrimary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "更多",
                color = DesktopColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private const val FRIEND_PLACEHOLDER_COUNT = 8

// 与 RecentFriendChip 同尺寸的骨架，数据回来后原位替换
@Composable
private fun FriendChipPlaceholder() {
    Column(
        modifier = Modifier.width(54.dp).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(DesktopColors.CoverPlaceholder))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(36.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(DesktopColors.CoverPlaceholder))
    }
}

@Composable
private fun RecentFriendChip(
    user: MessageUser,
    sending: Boolean,
    enabled: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit
) {
    val displayName = user.nickname.ifBlank { "用户 ${user.uid}" }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .width(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .hoverable(hoverSource)
            .background(if (hovered && enabled) DesktopColors.Hover else Color.Transparent)
            .then(
                if (enabled) {
                    Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .graphicsLayer { alpha = if (dimmed) 0.55f else 1f }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DesktopTooltip(displayName, side = TooltipSide.Bottom) {
            Box(Modifier.size(34.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Cover(user.avatarUrl, 34.dp, shape = CircleShape)
                if (sending) {
                    Box(
                        Modifier.fillMaxSize().background(DesktopColors.PopupSurface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = DesktopColors.Accent,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = displayName,
            color = DesktopColors.TextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun sentRecipientDisplayName(nickname: String): String = nickname.ifBlank { "对方" }

internal suspend fun awaitSentCapsuleTimeout(
    hovered: Flow<Boolean>,
    timeoutMs: Long = NOTICE_MS,
    nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    var remainingMs = timeoutMs
    var isHovered = hovered.first()
    while (remainingMs > 0L) {
        if (isHovered) {
            hovered.first { !it }
            isHovered = false
        } else {
            val startedMs = nowMs()
            val becameHovered = withTimeoutOrNull(remainingMs) {
                hovered.first { it }
            } != null
            if (!becameHovered) {
                remainingMs = 0L
            } else {
                val elapsedMs = (nowMs() - startedMs).coerceAtLeast(0L)
                remainingMs = (remainingMs - elapsedMs).coerceAtLeast(0L)
                isHovered = true
            }
        }
    }
}

@Composable
private fun BoxScope.ShareSentCapsule(
    banner: SentBanner?,
    visible: Boolean,
    onHide: () -> Unit,
    onOpenChat: (MessageUser) -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hoveredState = hoverSource.collectIsHoveredAsState()
    val goHover = remember { MutableInteractionSource() }
    val goHovered by goHover.collectIsHoveredAsState()

    LaunchedEffect(banner?.id, visible) {
        if (banner == null || !visible) return@LaunchedEffect
        awaitSentCapsuleTimeout(snapshotFlow { hoveredState.value })
        onHide()
    }

    AnimatedVisibility(
        visible = visible && banner != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = uiEnter(fadeIn(uiTween(180)) + slideInVertically(uiTween(200)) { it / 3 }),
        exit = uiExit(fadeOut(uiTween(220)))
    ) {
        val current = banner ?: return@AnimatedVisibility
        val message = buildAnnotatedString {
            append("已发送给 ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(sentRecipientDisplayName(current.user.nickname))
            }
        }

        Row(
            modifier = Modifier
                // 阴影绘制在边距内，避免被动画层边界裁掉
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .widthIn(max = 460.dp)
                .themedShadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(DesktopColors.PopupSurface)
                .border(1.dp, DesktopColors.Outline, CircleShape)
                .hoverable(hoverSource)
                // 吞掉胶囊空白处点击，避免误触背后的预览或选项
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(DesktopColors.Success),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contentColorOn(DesktopColors.Success),
                    modifier = Modifier.size(14.dp)
                )
            }
            Cover(current.user.avatarUrl, 22.dp, shape = CircleShape)
            Text(
                text = message,
                color = DesktopColors.TextPrimary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .hoverable(goHover)
                    .background(if (goHovered) DesktopColors.Hover else Color.Transparent)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onOpenChat(current.user) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "去聊天 ›",
                    color = DesktopColors.AccentText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

