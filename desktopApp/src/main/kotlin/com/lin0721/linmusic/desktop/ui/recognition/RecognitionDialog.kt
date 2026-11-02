package com.lin0721.linmusic.desktop.ui.recognition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.format.formatDuration
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.desktop.data.recognition.DesktopRecognition
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.MenuScope
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.playlist.PlaylistPill
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.shell.SongActionSources
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.TrackActions
import com.lin0721.linmusic.desktop.ui.track.TrackMenu
import com.lin0721.linmusic.desktop.ui.track.rememberTrackActions
import com.lin0721.linmusic.feature.recognition.domain.AttemptStatus
import com.lin0721.linmusic.feature.recognition.domain.MatchAttempt
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionHistoryEntry
import com.lin0721.linmusic.feature.recognition.domain.RecognitionMode
import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress
import com.lin0721.linmusic.feature.recognition.engine.RecognitionSession
import com.lin0721.linmusic.feature.recognition.ui.RecognitionFailedReason
import com.lin0721.linmusic.feature.recognition.ui.RecognitionUiState
import com.lin0721.linmusic.feature.recognition.ui.groupRecognitionHistory
import kotlinx.coroutines.launch
import java.time.ZoneId

private val DialogWidth = 780.dp
private val BodyHeight = 440.dp
private val HistoryWidth = 290.dp
private val CardShape = RoundedCornerShape(10.dp)
private val ChipShape = RoundedCornerShape(14.dp)
private val SegmentShape = RoundedCornerShape(15.dp)
private val SegmentWidth = 88.dp
private val SegmentHeight = 30.dp
private const val STAGGER_MS = 60
private const val FLASH_MS = 1_400

// 听歌识曲弹窗：左侧识别与结果，右侧识别历史；打开即开始，关闭即取消
@Composable
internal fun RecognitionDialog(
    recognition: DesktopRecognition,
    controller: PlaybackController,
    songActions: SongActionSources,
    onDismiss: () -> Unit
) {
    LaunchedEffect(recognition) { recognition.open() }
    DisposableEffect(recognition) { onDispose { recognition.close() } }

    val state by recognition.uiState.collectAsState()
    val mode by recognition.mode.collectAsState()
    val history by recognition.history.collectAsState()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val currentSongId = nowPlaying?.songId
    // 历史行右键时记下对应条目，菜单里的「从识别历史删除」据此定位；结果区右键时清空，避免同曲串到历史
    var menuEntry by remember { mutableStateOf<RecognitionHistoryEntry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val trackActions = rememberRecognitionTrackActions(controller, songActions) { track, dismiss ->
        val entry = menuEntry?.takeIf { it.songId == track.id } ?: return@rememberRecognitionTrackActions
        MenuDivider()
        MenuItem("从识别历史删除", icon = Icons.Rounded.Delete, danger = true, onClick = {
            dismiss()
            recognition.removeHistory(entry.id)
        })
    }

    DesktopDialog(
        title = "听歌识曲",
        onDismiss = onDismiss,
        width = DialogWidth,
        actions = { DialogButton("关闭", onDismiss) }
    ) {
        Row(Modifier.fillMaxWidth().height(BodyHeight)) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(end = 20.dp)) {
                ModeSwitch(
                    mode = mode,
                    loopbackSupported = recognition.loopbackSupported,
                    onSelect = { selected ->
                        if (selected != mode || state !is RecognitionUiState.Listening) recognition.start(selected)
                    }
                )
                Spacer(Modifier.height(16.dp))
                StageContent(
                    state = state,
                    mode = mode,
                    currentSongId = currentSongId,
                    actions = trackActions,
                    recognition = recognition,
                    onResultMenuOpen = { menuEntry = null },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(DesktopColors.SurfaceLight))
            HistoryColumn(
                history = history,
                currentSongId = currentSongId,
                actions = trackActions,
                onPlay = recognition::play,
                onMenuOpen = { menuEntry = it },
                onClear = { confirmClear = true },
                modifier = Modifier.width(HistoryWidth).fillMaxHeight().padding(start = 16.dp)
            )
        }
    }

    if (confirmClear) {
        DesktopDialog(
            title = "清空识别历史",
            onDismiss = { confirmClear = false },
            actions = {
                DialogButton("取消", { confirmClear = false })
                DialogButton("清空", {
                    confirmClear = false
                    recognition.clearHistory()
                }, danger = true)
            }
        ) {
            Text("确定清空全部 ${history.size} 条识别记录？", color = DesktopColors.TextGray, fontSize = 14.sp)
        }
    }
}

// 按阶段切换内容；聆听中的进度刷新不触发切换动画
@Composable
private fun StageContent(
    state: RecognitionUiState,
    mode: RecognitionMode,
    currentSongId: Long?,
    actions: TrackActions,
    recognition: DesktopRecognition,
    onResultMenuOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalUiMotion.current
    AnimatedContent(
        targetState = state,
        contentKey = { current ->
            when (current) {
                is RecognitionUiState.Found -> "found:${current.candidates.first().songId}"
                else -> current::class.simpleName
            }
        },
        transitionSpec = {
            motion.transform(
                (fadeIn(tween(240, delayMillis = 80)) + scaleIn(tween(240, delayMillis = 80), initialScale = 0.96f)) togetherWith
                    fadeOut(tween(100))
            )
        },
        modifier = modifier,
        label = "recognitionStage"
    ) { current ->
        when (current) {
            is RecognitionUiState.Listening -> ListeningContent(current.progress, mode, onCancel = recognition::cancel)
            is RecognitionUiState.Found -> FoundContent(
                state = current,
                currentSongId = currentSongId,
                actions = actions,
                onPlay = recognition::play,
                onRetry = { recognition.start() },
                onMenuOpen = onResultMenuOpen
            )
            is RecognitionUiState.Failed -> FailedContent(current.reason, current.progress, mode, onRetry = { recognition.start() })
            is RecognitionUiState.PermissionRequired -> MessageContent(
                icon = Icons.Rounded.MicOff,
                title = "无法使用麦克风",
                subtitle = "请在系统设置的隐私设置中允许应用访问麦克风",
                buttonText = "重试",
                onClick = { recognition.start() }
            )
            RecognitionUiState.Idle, is RecognitionUiState.ChoosingMode -> MessageContent(
                icon = if (mode == RecognitionMode.PLAYBACK) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                title = "听歌识曲",
                subtitle = if (mode == RecognitionMode.PLAYBACK) "识别电脑正在播放的声音" else "让麦克风听一段正在播放的音乐",
                buttonText = "开始识别",
                onClick = { recognition.start() }
            )
        }
    }
}

@Composable
private fun rememberRecognitionTrackActions(
    controller: PlaybackController,
    songActions: SongActionSources,
    extraMenuItems: @Composable MenuScope.(Track, () -> Unit) -> Unit
): TrackActions {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    val likedSongIds by songActions.likes.likedSongIds.collectAsState()
    val collectState by songActions.collect.state.collectAsState()
    return rememberTrackActions(
        likedSongIds = likedSongIds,
        collectState = collectState,
        onToggleLike = { songId, like ->
            scope.launch {
                songActions.likes.likeSong(songId, like).collect { result ->
                    result.onFailure { navigator.showMessage("操作失败") }
                }
            }
        },
        onPlayNext = { track ->
            controller.addToPlayNext(listOf(QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)))
            navigator.showMessage("已添加至下一首播放")
        },
        onPrepareCollect = { songId ->
            scope.launch {
                songActions.collect.prepare(songId, songActions.likes.likedSongIds.value) { navigator.showMessage(it) }
            }
        },
        onSaveCollect = { songId, items ->
            scope.launch {
                songActions.collect.save(
                    songId = songId,
                    items = items,
                    likedSongIds = songActions.likes.likedSongIds.value,
                    onToast = { navigator.showMessage(it) },
                    onLikedChanged = { songActions.likes.syncLikedSongIds(it) }
                )
            }
        },
        onCreateAndAdd = { name, songId ->
            scope.launch {
                songActions.collect.createAndAdd(name, songId, songActions.likes.likedSongIds.value) { navigator.showMessage(it) }
            }
        },
        extraMenuItems = extraMenuItems
    )
}

// 识别结果只有歌手名与封面，菜单里「查看歌手 / 专辑」因无 id 自动隐藏
private fun trackOf(songId: Long, title: String, artists: String, coverUrl: String, album: String = "") =
    Track(id = songId, name = title, ar = listOf(Artist(name = artists)), al = Album(name = album, picUrl = coverUrl))

// 首次出现时按顺序上浮淡入
@Composable
private fun Modifier.riseIn(order: Int): Modifier {
    val motion = LocalUiMotion.current
    val progress = remember { Animatable(if (motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, motion.tween(durationMillis = 320, delayMillis = order * STAGGER_MS))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

// 选中底块在两段之间滑动
@Composable
private fun ModeSwitch(mode: RecognitionMode, loopbackSupported: Boolean, onSelect: (RecognitionMode) -> Unit) {
    val options = buildList {
        add(RecognitionMode.MICROPHONE to "麦克风")
        if (loopbackSupported) add(RecognitionMode.PLAYBACK to "系统声音")
    }
    val selectedIndex = options.indexOfFirst { it.first == mode }.coerceAtLeast(0)
    val indicatorOffset by animateDpAsState(
        SegmentWidth * selectedIndex,
        uiSpring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "modeIndicator"
    )
    Box(Modifier.clip(SegmentShape).background(DesktopColors.Surface).padding(3.dp)) {
        Box(
            Modifier.offset(x = indicatorOffset).width(SegmentWidth).height(SegmentHeight)
                .clip(SegmentShape).background(DesktopColors.TextPrimary)
        )
        Row {
            options.forEachIndexed { index, (option, label) ->
                val labelColor by animateColorAsState(
                    if (index == selectedIndex) DesktopColors.Pane else DesktopColors.TextPrimary,
                    uiTween(180),
                    label = "modeLabel"
                )
                Box(
                    Modifier.width(SegmentWidth).height(SegmentHeight).clip(SegmentShape)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = labelColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// 中心图标外扩三圈涟漪，表示正在收音
@Composable
private fun ListeningOrb(mode: RecognitionMode) {
    val phase by rememberInfiniteTransition(label = "listeningRipple").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "listeningRipplePhase"
    )
    val rippleColor = DesktopColors.TextPrimary
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inner = 36.dp.toPx()
            val outer = size.minDimension / 2
            repeat(3) { ring ->
                val progress = (phase + ring / 3f) % 1f
                drawCircle(
                    color = rippleColor.copy(alpha = (1f - progress) * 0.28f),
                    radius = inner + (outer - inner) * progress,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(DesktopColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (mode == RecognitionMode.PLAYBACK) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                null,
                tint = DesktopColors.TextPrimary,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun ListeningContent(progress: RecognitionProgress, mode: RecognitionMode, onCancel: () -> Unit) {
    val recordedSeconds = (progress.recordedMs / 1000).toInt()
    val matching = progress.attempts.lastOrNull { it.status == AttemptStatus.MATCHING }
    val progressText = when {
        matching != null -> "已录 $recordedSeconds 秒 · 正在匹配第 ${progress.attempts.indexOf(matching) + 1} 个片段"
        progress.recordedMs < RecognitionSession.WINDOW_SECONDS * 1000L -> "已录 $recordedSeconds 秒 · 满 3 秒开始匹配"
        else -> "已录 $recordedSeconds 秒"
    }
    val subtitle = if (mode == RecognitionMode.PLAYBACK) "识别电脑正在播放的声音 · $progressText" else progressText
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ListeningOrb(mode)
        Text("正在聆听", color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = DesktopColors.TextGray, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        RecognitionWaveform(
            levels = progress.levels,
            highlightStartSecond = matching?.startSecond,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        )
        Spacer(Modifier.height(6.dp))
        RecognitionTimeAxis()
        Spacer(Modifier.height(12.dp))
        AttemptChips(progress.attempts)
        Spacer(Modifier.weight(1f))
        PlaylistPill(Icons.Rounded.Close, "取消", onCancel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttemptChips(attempts: List<MatchAttempt>) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        attempts.forEach { attempt -> key(attempt.startSecond) { AttemptChip(attempt) } }
    }
}

@Composable
private fun AttemptChip(attempt: MatchAttempt) {
    val range = "${attempt.startSecond}–${attempt.startSecond + RecognitionSession.WINDOW_SECONDS} 秒"
    val active = attempt.status == AttemptStatus.MATCHING || attempt.status == AttemptStatus.HIT
    val label = when (attempt.status) {
        AttemptStatus.MATCHING -> "$range 匹配中"
        AttemptStatus.MISSED -> "$range 未命中"
        AttemptStatus.SILENT -> "$range 没有声音"
        AttemptStatus.HIT -> "$range 命中"
    }
    val background by animateColorAsState(
        if (active) DesktopColors.AccentContainer else DesktopColors.Surface,
        uiTween(220),
        label = "attemptBackground"
    )
    val border by animateColorAsState(
        if (active) DesktopColors.Accent.copy(alpha = 0.75f) else DesktopColors.Surface,
        uiTween(220),
        label = "attemptBorder"
    )
    Text(
        label,
        color = if (active) DesktopColors.TextPrimary else DesktopColors.TextGray,
        fontSize = 12.sp,
        modifier = Modifier.riseIn(0)
            .clip(ChipShape)
            .background(background)
            .border(1.dp, border, ChipShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun FoundContent(
    state: RecognitionUiState.Found,
    currentSongId: Long?,
    actions: TrackActions,
    onPlay: (RecognitionCandidate) -> Unit,
    onRetry: () -> Unit,
    onMenuOpen: () -> Unit
) {
    val best = state.candidates.first()
    val others = state.candidates.drop(1)
    val hitSecond = state.windowStartSecond + RecognitionSession.WINDOW_SECONDS
    val motion = LocalUiMotion.current
    val coverScale = remember { Animatable(if (motion.enabled) 0.82f else 1f) }
    LaunchedEffect(Unit) {
        coverScale.animateTo(1f, motion.spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        WithTrackMenu(trackOf(best.songId, best.title, best.artists, best.coverUrl, best.album), actions, onPlay = { onPlay(best) }, onMenuOpen = onMenuOpen) {
            Row(
                Modifier.fillMaxWidth().clip(CardShape).background(DesktopColors.CardSurface).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(
                    best.coverUrl,
                    128.dp,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = coverScale.value
                        scaleY = coverScale.value
                        alpha = ((coverScale.value - 0.82f) / 0.18f).coerceIn(0f, 1f)
                    }
                )
                Column(Modifier.weight(1f).padding(start = 18.dp)) {
                    Text(
                        best.title,
                        color = if (best.songId == currentSongId) DesktopColors.AccentText else DesktopColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.riseIn(0)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(best.artists, best.album).filter { it.isNotBlank() }.joinToString(" · "),
                        color = DesktopColors.TextGray,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.riseIn(1)
                    )
                    Text(
                        "第 $hitSecond 秒命中 · 原曲 ${formatDuration(best.startTimeMs)}",
                        color = DesktopColors.TextGray,
                        fontSize = 12.sp,
                        modifier = Modifier.riseIn(2)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.riseIn(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DialogButton("从 ${formatDuration(best.startTimeMs)} 播放", { onPlay(best) }, primary = true)
                        PlaylistPill(Icons.Rounded.Refresh, "再识别", onRetry)
                    }
                }
            }
        }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                "其他可能",
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.riseIn(4)
            )
            Spacer(Modifier.height(4.dp))
            others.forEachIndexed { index, candidate ->
                Box(Modifier.riseIn(5 + index)) {
                    SongLine(
                        track = trackOf(candidate.songId, candidate.title, candidate.artists, candidate.coverUrl, candidate.album),
                        subtitle = "${candidate.artists} · 命中 ${formatDuration(candidate.startTimeMs)}",
                        isCurrent = candidate.songId == currentSongId,
                        actions = actions,
                        onPlay = { onPlay(candidate) },
                        onMenuOpen = onMenuOpen
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedContent(reason: RecognitionFailedReason, progress: RecognitionProgress, mode: RecognitionMode, onRetry: () -> Unit) {
    val (title, subtitle) = failureCopy(reason, progress, mode)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        StageIcon(failureIcon(reason, mode), Modifier.riseIn(0))
        Spacer(Modifier.height(12.dp))
        Text(title, color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.riseIn(1))
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            color = DesktopColors.TextGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.riseIn(2)
        )
        if (progress.levels.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            RecognitionWaveform(
                levels = progress.levels,
                barColor = DesktopColors.TextPrimary.copy(alpha = 0.28f),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
            Spacer(Modifier.height(6.dp))
            RecognitionTimeAxis()
        }
        if (progress.attempts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            AttemptChips(progress.attempts)
        }
        if (reason == RecognitionFailedReason.NOT_FOUND || reason == RecognitionFailedReason.SILENT) {
            Spacer(Modifier.height(12.dp))
            Text("可以试试：调大音量、靠近音源，或等到副歌再识别", color = DesktopColors.TextGray, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        DialogButton(if (reason == RecognitionFailedReason.STOPPED) "再识别" else "重试", onRetry, primary = true)
    }
}

@Composable
private fun MessageContent(icon: ImageVector, title: String, subtitle: String, buttonText: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StageIcon(icon, Modifier.riseIn(0))
        Spacer(Modifier.height(14.dp))
        Text(title, color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.riseIn(1))
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = DesktopColors.TextGray, fontSize = 13.sp, modifier = Modifier.riseIn(2))
        Spacer(Modifier.height(20.dp))
        Box(Modifier.riseIn(3)) { DialogButton(buttonText, onClick, primary = true) }
    }
}

@Composable
private fun StageIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.size(64.dp).clip(CircleShape).background(DesktopColors.Surface), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(28.dp))
    }
}

private fun failureIcon(reason: RecognitionFailedReason, mode: RecognitionMode): ImageVector = when (reason) {
    RecognitionFailedReason.NOT_FOUND -> Icons.Rounded.MusicOff
    RecognitionFailedReason.SILENT -> if (mode == RecognitionMode.PLAYBACK) Icons.AutoMirrored.Rounded.VolumeOff else Icons.Rounded.MicOff
    RecognitionFailedReason.NETWORK -> Icons.Rounded.WifiOff
    RecognitionFailedReason.RECORDER_UNAVAILABLE -> Icons.Rounded.MicOff
    RecognitionFailedReason.STOPPED -> Icons.Rounded.StopCircle
    RecognitionFailedReason.ENGINE_UNAVAILABLE, RecognitionFailedReason.UNKNOWN -> Icons.Rounded.ErrorOutline
}

private fun failureCopy(reason: RecognitionFailedReason, progress: RecognitionProgress, mode: RecognitionMode): Pair<String, String> =
    when (reason) {
        RecognitionFailedReason.NOT_FOUND ->
            "未识别到" to "录满 ${RecognitionSession.MAX_SECONDS} 秒，${progress.attempts.size} 个片段都没有匹配结果"
        RecognitionFailedReason.SILENT -> "未识别到" to if (mode == RecognitionMode.PLAYBACK) {
            "没有录到声音，请确认电脑正在播放"
        } else {
            "没有采集到声音，请检查麦克风及系统的麦克风权限"
        }
        RecognitionFailedReason.NETWORK -> "识别失败" to "网络异常，请检查网络后重试"
        RecognitionFailedReason.ENGINE_UNAVAILABLE -> "识别失败" to "识别组件加载失败，详见日志"
        RecognitionFailedReason.RECORDER_UNAVAILABLE -> "识别失败" to if (mode == RecognitionMode.PLAYBACK) {
            "系统内录启动失败，请重试或改用麦克风"
        } else {
            "麦克风不可用，可能未连接或正被其他应用占用"
        }
        RecognitionFailedReason.STOPPED -> "识别已停止" to "已开始播放所选歌曲"
        RecognitionFailedReason.UNKNOWN -> "识别失败" to "出现未知错误，请重试"
    }

@Composable
private fun HistoryColumn(
    history: List<RecognitionHistoryEntry>,
    currentSongId: Long?,
    actions: TrackActions,
    onPlay: (RecognitionHistoryEntry) -> Unit,
    onMenuOpen: (RecognitionHistoryEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalUiMotion.current
    val sections = remember(history) { groupRecognitionHistory(history, System.currentTimeMillis(), ZoneId.systemDefault()) }
    // 打开弹窗之后识别到的条目入场时闪一下
    val openedAt = remember { System.currentTimeMillis() }
    val listState = rememberLazyListState()
    LaunchedEffect(history.firstOrNull()?.id) {
        if (history.isNotEmpty()) motion.scrollToItem(listState, 0)
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("识别历史", color = DesktopColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (history.isNotEmpty()) {
                Text(
                    "${history.size}",
                    color = DesktopColors.TextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("清空", color = DesktopColors.TextGray, fontSize = 12.sp) }
            }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("识别到的歌曲会出现在这里", color = DesktopColors.TextGray, fontSize = 13.sp)
            }
        } else {
            HoverScrollbarBox(listState) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    sections.forEach { section ->
                        item(key = "section:${section.label}") {
                            Text(
                                section.label,
                                color = DesktopColors.TextGray,
                                fontSize = 12.sp,
                                modifier = Modifier.then(if (motion.enabled) Modifier.animateItem() else Modifier)
                                    .padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                        items(section.rows, key = { it.entry.id }) { row ->
                            val entry = row.entry
                            Box(if (motion.enabled) Modifier.animateItem() else Modifier) {
                                SongLine(
                                    track = trackOf(entry.songId, entry.title, entry.artists, entry.coverUrl),
                                    subtitle = "${entry.artists} · ${row.timeLabel}",
                                    isCurrent = entry.songId == currentSongId,
                                    actions = actions,
                                    onPlay = { onPlay(entry) },
                                    onMenuOpen = { onMenuOpen(entry) },
                                    flash = entry.recognizedAt >= openedAt
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 结果「其他可能」与历史共用的紧凑行：单击播放，悬停露出播放键，右键歌曲菜单
@Composable
private fun SongLine(
    track: Track,
    subtitle: String,
    isCurrent: Boolean,
    actions: TrackActions,
    onPlay: () -> Unit,
    onMenuOpen: () -> Unit = {},
    flash: Boolean = false
) {
    val motion = LocalUiMotion.current
    val flashAlpha = remember { Animatable(if (flash && motion.enabled) 0.22f else 0f) }
    LaunchedEffect(Unit) {
        if (flashAlpha.value > 0f) flashAlpha.animateTo(0f, tween(FLASH_MS, delayMillis = 200))
    }
    WithTrackMenu(track, actions, onPlay, onMenuOpen) { menuOpen ->
        val hoverSource = remember { MutableInteractionSource() }
        val hovered by hoverSource.collectIsHoveredAsState()
        val background by animateColorAsState(
            if (hovered || menuOpen) DesktopColors.Surface else DesktopColors.PopupSurface,
            uiTween(150),
            label = "songLineBackground"
        )
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .background(DesktopColors.Accent.copy(alpha = flashAlpha.value))
                .hoverable(hoverSource)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onPlay)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(track.al.picUrl, 40.dp, shape = RoundedCornerShape(6.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    track.name,
                    color = if (isCurrent) DesktopColors.AccentText else DesktopColors.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = DesktopColors.TextGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HoverReveal(revealed = hovered || menuOpen) {
                DesktopTooltip("播放") {
                    IconButton(onClick = onPlay, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.PlayArrow, "播放", tint = DesktopColors.TextPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// 包一层右键歌曲菜单，菜单锚在点击位置
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WithTrackMenu(
    track: Track,
    actions: TrackActions,
    onPlay: () -> Unit,
    onMenuOpen: () -> Unit = {},
    content: @Composable (menuOpen: Boolean) -> Unit
) {
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(0) }
    var menuOffset by remember { mutableStateOf<DpOffset?>(null) }
    Box {
        Box(
            Modifier.onSizeChanged { heightPx = it.height }
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (!event.buttons.isSecondaryPressed) return@onPointerEvent
                    val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    onMenuOpen()
                    menuOffset = with(density) { DpOffset(position.x.toDp(), (position.y - heightPx).toDp()) }
                }
        ) {
            content(menuOffset != null)
        }
        TrackMenu(
            expanded = menuOffset != null,
            offset = menuOffset ?: DpOffset.Zero,
            track = track,
            actions = actions,
            onPlay = onPlay,
            onDismiss = { menuOffset = null }
        )
    }
}
