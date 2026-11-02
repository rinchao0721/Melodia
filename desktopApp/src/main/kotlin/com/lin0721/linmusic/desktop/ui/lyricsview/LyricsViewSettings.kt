package com.lin0721.linmusic.desktop.ui.lyricsview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricAlignment
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import kotlinx.coroutines.launch

val LyricsFontSizeRange = 24..72
val LyricsLineSpacingRange = 8..48
val LyricsSecondarySpacingRange = 0..24

// 全屏歌词的显示偏好；字号以外的项与移动端共用同一份偏好
data class LyricsViewSettings(
    val fontSize: Int = DesktopPreferences.DEFAULT_LYRICS_VIEW_FONT_SIZE,
    val lineSpacing: Int = 24,
    val secondarySpacing: Int = 6,
    val alignment: String = "left",
    val secondaryMode: String = "translation",
    val advancedEffect: Boolean = true,
    val glowEffect: Boolean = false,
    val neteaseEnabled: Boolean = true,
    val qqEnabled: Boolean = true,
    val amllEnabled: Boolean = true,
    val autoHideControls: Boolean = false
) {
    // 副行、背景和声等随主字号按比例缩放
    val secondaryFontSize: TextUnit get() = (fontSize * 0.55f).sp
    val backgroundFontSize: TextUnit get() = (fontSize * 0.7f).sp
    val backgroundSecondaryFontSize: TextUnit get() = (fontSize * 0.45f).sp
}

// 行在歌词列内的对齐方向
enum class RowAlignment(val textAlign: TextAlign, val horizontal: Alignment.Horizontal) {
    Start(TextAlign.Start, Alignment.Start),
    Center(TextAlign.Center, Alignment.CenterHorizontally),
    End(TextAlign.End, Alignment.End)
}

// 居中时所有声部居中；对唱的后声部取与用户设置相反的一侧，右对齐时主声部与后声部互换位置
fun effectiveAlignment(alignment: String, line: LyricAlignment): RowAlignment = when {
    alignment == "center" -> RowAlignment.Center
    alignment == "right" && line == LyricAlignment.END -> RowAlignment.Start
    line == LyricAlignment.END -> RowAlignment.End
    alignment == "right" -> RowAlignment.End
    else -> RowAlignment.Start
}

// 读写偏好：读取随偏好变化，写入在协程里完成
class LyricsViewSettingsActions internal constructor(
    val setFontSize: (Int) -> Unit,
    val setLineSpacing: (Int) -> Unit,
    val setSecondarySpacing: (Int) -> Unit,
    val setAlignment: (String) -> Unit,
    val setSecondaryMode: (String) -> Unit,
    val setAdvancedEffect: (Boolean) -> Unit,
    val setGlowEffect: (Boolean) -> Unit,
    val setNeteaseEnabled: (Boolean) -> Unit,
    val setQqEnabled: (Boolean) -> Unit,
    val setAmllEnabled: (Boolean) -> Unit,
    val setAutoHideControls: (Boolean) -> Unit
)

@Composable
fun rememberLyricsViewSettings(
    settingsPreferences: SettingsPreferences,
    desktopPreferences: DesktopPreferences
): Pair<LyricsViewSettings, LyricsViewSettingsActions> {
    val defaults = remember { LyricsViewSettings() }
    val fontSize by desktopPreferences.lyricsViewFontSize.collectAsState(initial = defaults.fontSize)
    val lineSpacing by settingsPreferences.fullScreenLyricLineSpacing.collectAsState(initial = defaults.lineSpacing)
    val secondarySpacing by settingsPreferences.fullScreenLyricSecondarySpacing.collectAsState(initial = defaults.secondarySpacing)
    val alignment by settingsPreferences.fullScreenLyricAlignment.collectAsState(initial = defaults.alignment)
    val secondaryMode by settingsPreferences.fullScreenLyricSecondaryMode.collectAsState(initial = defaults.secondaryMode)
    val advanced by settingsPreferences.fullScreenKaraokeAdvancedEffect.collectAsState(initial = defaults.advancedEffect)
    val glow by settingsPreferences.fullScreenKaraokeGlowEffect.collectAsState(initial = defaults.glowEffect)
    val netease by settingsPreferences.neteaseLyricsEnabled.collectAsState(initial = defaults.neteaseEnabled)
    val qq by settingsPreferences.qqLyricsEnabled.collectAsState(initial = defaults.qqEnabled)
    val amll by settingsPreferences.amllLyricsEnabled.collectAsState(initial = defaults.amllEnabled)
    val autoHide by settingsPreferences.fullScreenLyricAutoHideControls.collectAsState(initial = defaults.autoHideControls)
    val scope = rememberCoroutineScope()
    val actions = remember(settingsPreferences, desktopPreferences) {
        LyricsViewSettingsActions(
            setFontSize = { scope.launch { desktopPreferences.saveLyricsViewFontSize(it.coerceIn(LyricsFontSizeRange)) } },
            setLineSpacing = { scope.launch { settingsPreferences.saveFullScreenLyricLineSpacing(it.coerceIn(LyricsLineSpacingRange)) } },
            setSecondarySpacing = { scope.launch { settingsPreferences.saveFullScreenLyricSecondarySpacing(it.coerceIn(LyricsSecondarySpacingRange)) } },
            setAlignment = { scope.launch { settingsPreferences.saveFullScreenLyricAlignment(it) } },
            setSecondaryMode = { scope.launch { settingsPreferences.saveFullScreenLyricSecondaryMode(it) } },
            setAdvancedEffect = { scope.launch { settingsPreferences.saveFullScreenKaraokeAdvancedEffect(it) } },
            setGlowEffect = { scope.launch { settingsPreferences.saveFullScreenKaraokeGlowEffect(it) } },
            setNeteaseEnabled = { scope.launch { settingsPreferences.saveNeteaseLyricsEnabled(it) } },
            setQqEnabled = { scope.launch { settingsPreferences.saveQqLyricsEnabled(it) } },
            setAmllEnabled = { scope.launch { settingsPreferences.saveAmllLyricsEnabled(it) } },
            setAutoHideControls = { scope.launch { settingsPreferences.saveFullScreenLyricAutoHideControls(it) } }
        )
    }
    val settings = remember(fontSize, lineSpacing, secondarySpacing, alignment, secondaryMode, advanced, glow, netease, qq, amll, autoHide) {
        LyricsViewSettings(
            fontSize = fontSize.coerceIn(LyricsFontSizeRange),
            lineSpacing = lineSpacing.coerceIn(LyricsLineSpacingRange),
            secondarySpacing = secondarySpacing.coerceIn(LyricsSecondarySpacingRange),
            alignment = alignment,
            secondaryMode = secondaryMode,
            advancedEffect = advanced,
            glowEffect = glow,
            neteaseEnabled = netease,
            qqEnabled = qq,
            amllEnabled = amll,
            autoHideControls = autoHide
        )
    }
    return settings to actions
}
