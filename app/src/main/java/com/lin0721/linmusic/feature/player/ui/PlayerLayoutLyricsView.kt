package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

// 用户的歌词显示设置，宽屏歌词栏与插件歌词视图共用
data class LyricsDisplaySettings(
    val fontSize: Int,
    val alignment: String,
    val secondaryMode: String,
    val lineSpacing: Int,
    val secondarySpacing: Int,
    val karaokeAdvanced: Boolean,
    val karaokeGlow: Boolean
)

@Composable
fun rememberLyricsDisplaySettings(): LyricsDisplaySettings {
    val preferences: SettingsPreferences = koinInject()
    val fontSize by preferences.fullScreenLyricTextSize.collectAsStateWithLifecycle(initialValue = 22)
    val alignment by preferences.fullScreenLyricAlignment.collectAsStateWithLifecycle(initialValue = "left")
    val secondaryMode by preferences.fullScreenLyricSecondaryMode.collectAsStateWithLifecycle(initialValue = "translation")
    val karaokeAdvanced by preferences.fullScreenKaraokeAdvancedEffect.collectAsStateWithLifecycle(initialValue = true)
    val karaokeGlow by preferences.fullScreenKaraokeGlowEffect.collectAsStateWithLifecycle(initialValue = false)
    val lineSpacing by preferences.fullScreenLyricLineSpacing.collectAsStateWithLifecycle(initialValue = 24)
    val secondarySpacing by preferences.fullScreenLyricSecondarySpacing.collectAsStateWithLifecycle(initialValue = 6)
    return LyricsDisplaySettings(
        fontSize = fontSize.coerceIn(20, 40),
        alignment = alignment,
        secondaryMode = secondaryMode,
        lineSpacing = lineSpacing,
        secondarySpacing = secondarySpacing,
        karaokeAdvanced = karaokeAdvanced,
        karaokeGlow = karaokeGlow
    )
}

// 插件布局的整页歌词：手动滚动后停留一会再回到当前行，点某行跳到那里
@Composable
fun EmbeddedLyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    activeIndices: Set<Int>,
    isLoading: Boolean,
    highlightColor: Color,
    currentPositionProvider: () -> Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = rememberLyricsDisplaySettings()
    val listState = rememberLazyListState()
    var viewportPx by remember { mutableFloatStateOf(0f) }
    var isUserScrolling by remember { mutableStateOf(false) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged, isPlaying) {
        if (isDragged) {
            isUserScrolling = true
        } else if (isUserScrolling && isPlaying) {
            delay(LYRICS_RESUME_DELAY_MS)
            isUserScrolling = false
        }
    }
    val isPureMusic = lyrics.size == 1 && lyrics[0].text == "纯音乐"
    if (!isLoading && (lyrics.isEmpty() || isPureMusic)) {
        Box(modifier.padding(horizontal = MelodiaSpacing.md), contentAlignment = Alignment.Center) {
            Text(
                text = if (isPureMusic) "纯音乐，请欣赏" else "暂无歌词",
                color = highlightColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }
    FullScreenLyricsList(
        lyrics = lyrics,
        currentIndex = currentIndex,
        activeIndices = activeIndices,
        isLoading = isLoading,
        isUserScrolling = isUserScrolling,
        highlightColor = highlightColor,
        currentPositionProvider = currentPositionProvider,
        lazyListState = listState,
        viewportHeightPx = viewportPx,
        onViewportHeightChange = { viewportPx = it },
        gestureModifier = Modifier,
        modifier = modifier.fillMaxSize(),
        fontSize = settings.fontSize,
        alignment = settings.alignment,
        secondaryMode = settings.secondaryMode,
        lineSpacing = settings.lineSpacing,
        secondarySpacing = settings.secondarySpacing,
        advancedKaraokeEffect = settings.karaokeAdvanced,
        karaokeGlowEffect = settings.karaokeGlow,
        isPlaying = isPlaying,
        onSeek = { timeMs ->
            isUserScrolling = false
            onSeek(timeMs)
        },
        onLyricClick = { line ->
            isUserScrolling = false
            onSeek(line.timeMs)
        }
    )
}
