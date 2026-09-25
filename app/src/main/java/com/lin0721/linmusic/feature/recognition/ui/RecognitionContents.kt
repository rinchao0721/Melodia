package com.lin0721.linmusic.feature.recognition.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.PlayingEqualizerBars
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.FallbackBase
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.TimerWarningRed
import com.lin0721.linmusic.core.ui.theme.darken
import com.lin0721.linmusic.core.ui.theme.extractBaseColorFromUrl
import com.lin0721.linmusic.feature.player.ui.formatTime
import com.lin0721.linmusic.feature.recognition.domain.AttemptStatus
import com.lin0721.linmusic.feature.recognition.domain.MatchAttempt
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionNowPlaying
import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress
import com.lin0721.linmusic.feature.recognition.engine.RecognitionSession

private val WaveformHeight = 160.dp
private val PillShape = RoundedCornerShape(PillRadius)

@Composable
private fun Headline(title: String, subtitle: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)) {
        Text(text = title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(text = subtitle, color = TextGray, fontSize = 15.sp)
        }
    }
}

// ─── 聆听中 ───

@Composable
internal fun RecognitionListeningContent(
    progress: RecognitionProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recordedSeconds = (progress.recordedMs / 1000).toInt()
    val matching = progress.attempts.lastOrNull { it.status == AttemptStatus.MATCHING }
    val subtitle = when {
        matching != null -> "已录 $recordedSeconds 秒 · 正在匹配第 ${progress.attempts.indexOf(matching) + 1} 个片段"
        progress.recordedMs < RecognitionSession.WINDOW_SECONDS * 1000L -> "已录 $recordedSeconds 秒 · 满 3 秒开始匹配"
        else -> "已录 $recordedSeconds 秒"
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(28.dp))
        Headline(title = "正在聆听", subtitle = subtitle)
        Spacer(Modifier.height(72.dp))
        RecognitionWaveform(
            levels = progress.levels,
            highlightStartSecond = matching?.startSecond,
            modifier = Modifier.fillMaxWidth().height(WaveformHeight)
        )
        Spacer(Modifier.height(14.dp))
        RecognitionTimeAxis()
        if (progress.attempts.isNotEmpty()) {
            Spacer(Modifier.height(44.dp))
            AttemptChips(progress.attempts)
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = MelodiaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
        ) {
            MelodiaIconButton(
                onClick = onCancel,
                containerColor = SurfaceDark,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "取消识别", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Text(text = "取消", color = TextGray, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttemptChips(attempts: List<MatchAttempt>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "匹配进度", color = TextGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
        ) {
            attempts.forEach { AttemptChip(it) }
        }
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
    Surface(
        shape = PillShape,
        color = if (active) NeteaseRed.copy(alpha = 0.16f) else SurfaceDark,
        modifier = if (active) Modifier.border(1.dp, NeteaseRed.copy(alpha = 0.75f), PillShape) else Modifier
    ) {
        Row(
            modifier = Modifier.height(32.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (active) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(TimerWarningRed))
            } else {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = TextGray, modifier = Modifier.size(14.dp))
            }
            Text(text = label, color = if (active) Color.White else TextGray, fontSize = 13.sp)
        }
    }
}

// ─── 识别结果 ───

@Composable
internal fun RecognitionFoundContent(
    state: RecognitionUiState.Found,
    nowPlaying: RecognitionNowPlaying?,
    positionProvider: () -> Long,
    onOpen: (RecognitionCandidate) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val best = state.candidates.first()
    val others = state.candidates.drop(1)
    val hitSecond = state.windowStartSecond + RecognitionSession.WINDOW_SECONDS

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(MelodiaSpacing.md))
                RecognitionWaveform(
                    levels = state.progress.levels,
                    highlightStartSecond = state.windowStartSecond,
                    highlight = WaveformHighlight.Bars,
                    showPendingSlots = false,
                    barColor = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
                Spacer(Modifier.height(MelodiaSpacing.sm))
                Text(
                    text = "第 $hitSecond 秒命中 · 匹配片段 ${state.windowStartSecond}–$hitSecond 秒",
                    color = TextGray,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(20.dp))
                BestMatchCard(
                    candidate = best,
                    nowPlaying = nowPlaying?.takeIf { it.songId == best.songId },
                    positionProvider = positionProvider,
                    onOpen = { onOpen(best) }
                )
            }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(MelodiaSpacing.lg))
                Text(
                    text = "其他可能",
                    color = TextGray,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = MelodiaSpacing.xs)
                )
                others.forEach { candidate ->
                    val isCurrent = nowPlaying?.songId == candidate.songId
                    SongRow(
                        data = SongRowData(
                            id = candidate.songId,
                            title = candidate.title,
                            artist = "${candidate.artists} · 命中 ${formatTime(candidate.startTimeMs)}",
                            coverUrl = candidate.coverUrl
                        ),
                        isActive = isCurrent,
                        isPlaying = nowPlaying?.isPlaying == true,
                        onClick = { onOpen(candidate) },
                        showDownloadBadge = false
                    ) {
                        // 正在播放时由行内音柱与红色歌名表示，不再显示播放按钮
                        if (!isCurrent) MelodiaIconButton(
                            onClick = { onOpen(candidate) },
                            containerColor = SurfaceDark,
                            modifier = Modifier.padding(start = MelodiaSpacing.sm).size(44.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "从 ${formatTime(candidate.startTimeMs)} 播放 ${candidate.title}",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
        MelodiaButton(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.md)
                .height(48.dp)
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(MelodiaSpacing.sm))
            Text(text = "再识别一次", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BestMatchCard(
    candidate: RecognitionCandidate,
    nowPlaying: RecognitionNowPlaying?,
    positionProvider: () -> Long,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    var baseColor by remember(candidate.coverUrl) { mutableStateOf(FallbackBase) }
    LaunchedEffect(candidate.coverUrl) {
        baseColor = extractBaseColorFromUrl(context, candidate.coverUrl)
    }
    val animatedBase by animateColorAsState(baseColor, tween(400), label = "recognition_card_color")
    val positionText = formatTime(candidate.startTimeMs)

    val cardShape = RoundedCornerShape(InfoCardRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(style = MelodiaPress.Card, shape = cardShape, role = Role.Button, onClick = onOpen)
            .clip(cardShape)
            .background(Brush.linearGradient(listOf(animatedBase, animatedBase.darken(0.4f))))
            .padding(MelodiaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp))) {
                AsyncImage(
                    model = "${candidate.coverUrl}?param=300y300",
                    contentDescription = "${candidate.title} 封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (nowPlaying != null) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingEqualizerBars(color = Color.White, animate = nowPlaying.isPlaying)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.xs)) {
                Text(text = "最可能是", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                Text(
                    text = candidate.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = candidate.artists,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (candidate.durationMs > 0) {
            // 正在播放时跟随实际进度，否则停在命中位置
            val displayMs = if (nowPlaying != null) positionProvider() else candidate.startTimeMs
            val fraction = (displayMs.toFloat() / candidate.durationMs).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)) {
                HitPositionBar(fraction)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = if (nowPlaying != null) formatTime(displayMs) else "命中位置 $positionText",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp
                    )
                    Text(text = formatTime(candidate.durationMs), color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                }
            }
        }
        MelodiaButton(
            onClick = onOpen,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BackgroundDark),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (nowPlaying != null) {
                PlayingEqualizerBars(color = BackgroundDark, animate = nowPlaying.isPlaying)
                Spacer(Modifier.width(MelodiaSpacing.sm))
                Text(
                    text = if (nowPlaying.isPlaying) "正在播放" else "已暂停",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(MelodiaSpacing.sm))
                Text(text = "从 $positionText 开始播放", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HitPositionBar(fraction: Float) {
    Box(modifier = Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.25f))
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .offset(x = (maxWidth - 12.dp) * fraction)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

// ─── 未识别到 / 失败 / 已停止 ───

@Composable
internal fun RecognitionFailedContent(
    reason: RecognitionFailedReason,
    progress: RecognitionProgress,
    onRetry: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, subtitle) = failureCopy(reason, progress)
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(28.dp))
        Headline(title = title, subtitle = subtitle)
        if (progress.levels.isNotEmpty()) {
            Spacer(Modifier.height(72.dp))
            RecognitionWaveform(
                levels = progress.levels,
                barColor = Color.White.copy(alpha = 0.28f),
                modifier = Modifier.fillMaxWidth().height(WaveformHeight)
            )
            Spacer(Modifier.height(14.dp))
            RecognitionTimeAxis()
        }
        if (progress.attempts.isNotEmpty()) {
            Spacer(Modifier.height(44.dp))
            AttemptChips(progress.attempts)
        }
        if (reason == RecognitionFailedReason.NOT_FOUND || reason == RecognitionFailedReason.SILENT) {
            Spacer(Modifier.height(MelodiaSpacing.md))
            Text(text = "可以试试：靠近音源、调大音量，或等到副歌再识别", color = TextGray, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = MelodiaSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MelodiaButton(
                onClick = onOpenHistory,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = Color.White),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text(text = "查看历史", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            MelodiaButton(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = NeteaseRed, contentColor = Color.White),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(MelodiaSpacing.sm))
                Text(text = "重试", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun failureCopy(reason: RecognitionFailedReason, progress: RecognitionProgress): Pair<String, String?> =
    when (reason) {
        RecognitionFailedReason.NOT_FOUND ->
            "未识别到" to "录满 ${RecognitionSession.MAX_SECONDS} 秒，${progress.attempts.size} 个片段都没有匹配结果"
        RecognitionFailedReason.SILENT -> "未识别到" to "没有采集到声音，请靠近音源或检查麦克风"
        RecognitionFailedReason.NETWORK -> "识别失败" to "网络异常，请检查网络后重试"
        RecognitionFailedReason.ENGINE_UNAVAILABLE -> "识别失败" to "识别组件加载失败，请确认系统 WebView 可用"
        RecognitionFailedReason.RECORDER_UNAVAILABLE -> "识别失败" to "麦克风不可用，可能正被其他应用占用"
        RecognitionFailedReason.STOPPED -> "识别已停止" to "已开始播放所选歌曲"
        RecognitionFailedReason.UNKNOWN -> "识别失败" to "出现未知错误，请重试"
    }

// ─── 麦克风权限 ───

@Composable
internal fun RecognitionPermissionContent(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(28.dp))
        Headline(
            title = "需要麦克风权限",
            subtitle = if (permanentlyDenied) {
                "麦克风权限已被拒绝，请在系统设置中开启后再识别"
            } else {
                "听歌识曲需要用麦克风录一小段声音，录音只用于本次识别"
            }
        )
        Spacer(Modifier.weight(1f))
        MelodiaButton(
            onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = NeteaseRed, contentColor = Color.White),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = MelodiaSpacing.lg).height(52.dp)
        ) {
            Text(
                text = if (permanentlyDenied) "去设置" else "授权麦克风",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
