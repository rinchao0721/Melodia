package com.lin0721.linmusic.desktop.ui.lyricsview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.artist.TabBar
import com.lin0721.linmusic.desktop.ui.player.PlayerSlider
import com.lin0721.linmusic.desktop.ui.settings.SettingSwitch
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlin.math.roundToInt

private val PanelWidth = 340.dp
private val PanelMaxHeight = 560.dp

private val AlignmentOptions = listOf("left" to "左对齐", "center" to "居中", "right" to "右对齐")

// 副行可选项：没有对应数据的选项不出现，“仅原词”始终可选
fun secondaryOptions(hasTranslation: Boolean, hasRoma: Boolean): List<Pair<String, String>> = buildList {
    if (hasTranslation) add("translation" to "翻译")
    if (hasRoma) add("roma" to "罗马音")
    add("none" to "仅原词")
}

// 全屏歌词的显示设置：字号、间距、对齐、副行与动效开关，改动立即生效并写入偏好
@Composable
fun LyricsSettingsPanel(
    settings: LyricsViewSettings,
    actions: LyricsViewSettingsActions,
    hasTranslation: Boolean,
    hasRoma: Boolean,
    modifier: Modifier = Modifier
) {
    val options = remember(hasTranslation, hasRoma) { secondaryOptions(hasTranslation, hasRoma) }
    Column(
        modifier.width(PanelWidth).heightIn(max = PanelMaxHeight)
            .themedShadow(16.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(DesktopColors.PopupSurface)
            .floatingOutline(RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("全屏歌词设置", color = DesktopColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)

        SliderSetting("歌词字号", "sp", settings.fontSize, LyricsFontSizeRange, actions.setFontSize)
        SliderSetting("歌词行间距", "dp", settings.lineSpacing, LyricsLineSpacingRange, actions.setLineSpacing)
        SliderSetting(
            "歌词与翻译、音译间距",
            "dp",
            settings.secondarySpacing,
            LyricsSecondarySpacingRange,
            actions.setSecondarySpacing
        )

        ChoiceSetting("歌词对齐方式") {
            TabBar(AlignmentOptions.map { it.first }, settings.alignment, { key -> AlignmentOptions.first { it.first == key }.second }, actions.setAlignment, small = true)
        }
        ChoiceSetting("歌词副文本") {
            TabBar(options.map { it.first }, settings.secondaryMode, { key -> options.first { it.first == key }.second }, actions.setSecondaryMode, small = true)
        }

        SwitchSetting("启用网易云歌词源", "提供歌词、翻译与逐字歌词", settings.neteaseEnabled, actions.setNeteaseEnabled)
        SwitchSetting("启用QQ音乐歌词源", "提供更多逐字歌词", settings.qqEnabled, actions.setQqEnabled)
        SwitchSetting("启用 AMLL 歌词源", "支持对唱分边与背景和声，对下一次加载的歌词生效", settings.amllEnabled, actions.setAmllEnabled)
        SwitchSetting("逐字歌词流光动效", "开启柔和渐变推进边缘", settings.advancedEffect, actions.setAdvancedEffect)
        SwitchSetting("字词呼吸光晕动效", "演唱字词叠加呼吸高亮微光", settings.glowEffect, actions.setGlowEffect)
        SwitchSetting("自动隐藏顶栏", "鼠标无操作或移出窗口后隐藏，移动鼠标重新呼出", settings.autoHideControls, actions.setAutoHideControls)
    }
}

// 拖动期间显示本地值，整数变化才写入偏好，写入次数不超过取值个数
@Composable
private fun SliderSetting(label: String, unit: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<Int?>(null) }
    val shown = dragging ?: value
    val span = (range.last - range.first).coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = DesktopColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$shown $unit", color = DesktopColors.TextGray, fontSize = 12.sp)
        }
        PlayerSlider(
            value = (shown - range.first).toFloat() / span,
            onValueChange = { fraction ->
                val next = (range.first + fraction * span).roundToInt().coerceIn(range)
                if (next != dragging) {
                    dragging = next
                    onChange(next)
                }
            },
            onValueChangeFinished = { dragging = null },
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ChoiceSetting(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = DesktopColors.TextGray, fontSize = 13.sp)
        content()
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = DesktopColors.TextPrimary, fontSize = 13.sp)
            Text(subtitle, color = DesktopColors.TextGray, fontSize = 11.sp)
        }
        SettingSwitch(checked, onChange = onChange)
    }
}
