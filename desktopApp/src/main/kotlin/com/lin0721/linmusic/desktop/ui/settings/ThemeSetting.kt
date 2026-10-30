package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.preferences.ThemeMode
import com.lin0721.linmusic.core.preferences.ThemeSettings
import com.lin0721.linmusic.core.ui.theme.ColorPickerState
import com.lin0721.linmusic.core.ui.theme.MIN_ACCENT_CONTRAST
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes
import com.lin0721.linmusic.core.ui.theme.ThemeAccents
import com.lin0721.linmusic.core.ui.theme.contentColorOn
import com.lin0721.linmusic.core.ui.theme.contrastRatio
import com.lin0721.linmusic.core.ui.theme.parseRgbHex
import com.lin0721.linmusic.core.ui.theme.rememberThemeSwitcher
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogLabel
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.SystemDarkTheme
import com.lin0721.linmusic.desktop.ui.track.DangerColor

private val ModeCardWidth = 136.dp
private val ModeThumbHeight = 76.dp
private val SwatchSize = 30.dp
private val CardShape = RoundedCornerShape(10.dp)
private val ThumbShape = RoundedCornerShape(6.dp)

private val ModeLabels = mapOf(
    ThemeMode.DARK to "深色",
    ThemeMode.LIGHT to "浅色",
    ThemeMode.SYSTEM to "跟随系统"
)

// 主题设置：模式缩略图卡片 + 当前模式的主色色块；跟随系统时深浅两套主色分两行
@Composable
internal fun ThemeSetting(
    settings: ThemeSettings,
    onModeChange: (ThemeMode) -> Unit,
    onAccentChange: (isDark: Boolean, accentId: String) -> Unit,
    onCustomColorChange: (isDark: Boolean, hex: String) -> Unit
) {
    var pickerFor by remember { mutableStateOf<Boolean?>(null) }
    val darkAccent = ThemeAccents.resolve(settings.darkAccentId, settings.darkCustomHex, isDark = true)
    val lightAccent = ThemeAccents.resolve(settings.lightAccentId, settings.lightCustomHex, isDark = false)
    val switcher = rememberThemeSwitcher()
    val currentIsDark = DesktopColors.IsDark
    val systemDark by SystemDarkTheme.isDark.collectAsState()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("主题（实验性）", color = DesktopColors.TextPrimary, fontSize = 14.sp)
        Text("切换深浅模式与主色，立即生效", color = DesktopColors.TextGray, fontSize = 12.sp)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeMode.entries.forEach { mode ->
                ModeCard(mode, darkAccent, lightAccent, selected = settings.mode == mode, onClick = { center ->
                    val changes = mode.isDark(systemDark) != currentIsDark
                    switcher.switch(center, changesTheme = changes) { onModeChange(mode) }
                })
            }
        }
        val rows = when (settings.mode) {
            ThemeMode.DARK -> listOf(true to "主色")
            ThemeMode.LIGHT -> listOf(false to "主色")
            ThemeMode.SYSTEM -> listOf(true to "深色主色", false to "浅色主色")
        }
        rows.forEach { (isDark, label) ->
            AccentRow(
                label = label,
                isDark = isDark,
                selectedId = ThemeAccents.normalizeId(settings.accentId(isDark), isDark),
                customHex = settings.customHex(isDark),
                onSelect = { onAccentChange(isDark, it) },
                onEditCustom = { pickerFor = isDark }
            )
        }
    }
    pickerFor?.let { isDark ->
        val initial = settings.customHex(isDark)?.let(::parseRgbHex)
            ?: ThemeAccents.resolve(settings.accentId(isDark), null, isDark)
        CustomAccentDialog(
            isDark = isDark,
            initial = initial,
            onDismiss = { pickerFor = null },
            onConfirm = { hex ->
                pickerFor = null
                onCustomColorChange(isDark, hex)
            }
        )
    }
}

@Composable
private fun ModeCard(mode: ThemeMode, darkAccent: Color, lightAccent: Color, selected: Boolean, onClick: (windowCenter: Offset) -> Unit) {
    var windowCenter by remember { mutableStateOf(Offset.Unspecified) }
    Surface(
        onClick = { onClick(windowCenter) },
        shape = CardShape,
        color = DesktopColors.CardSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) DesktopColors.Accent else DesktopColors.Overlay.copy(alpha = 0.1f)),
        modifier = Modifier.width(ModeCardWidth).pointerHoverIcon(PointerIcon.Hand)
            .onGloballyPositioned { windowCenter = it.boundsInWindow().center }
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(Modifier.fillMaxWidth().height(ModeThumbHeight).clip(ThumbShape)) {
                when (mode) {
                    ThemeMode.DARK -> ModeThumb(MelodiaPalettes.dark, darkAccent, Modifier.fillMaxSize())
                    ThemeMode.LIGHT -> ModeThumb(MelodiaPalettes.light, lightAccent, Modifier.fillMaxSize())
                    ThemeMode.SYSTEM -> Row(Modifier.fillMaxSize()) {
                        ModeThumb(MelodiaPalettes.light, lightAccent, Modifier.weight(1f).fillMaxHeight())
                        ModeThumb(MelodiaPalettes.dark, darkAccent, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ModeLabels.getValue(mode), color = DesktopColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// 缩略图：窗口底色上一块面板，两条文字线与一枚主色药丸
@Composable
internal fun ModeThumb(palette: MelodiaPalette, accent: Color, modifier: Modifier) {
    Box(modifier.background(palette.windowBackground).padding(6.dp)) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(palette.pane).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.width(40.dp).height(5.dp).clip(CircleShape).background(palette.textPrimary))
            Box(Modifier.width(28.dp).height(4.dp).clip(CircleShape).background(palette.textSecondary))
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(22.dp).height(8.dp).clip(CircleShape).background(accent))
        }
    }
}

@Composable
private fun AccentRow(
    label: String,
    isDark: Boolean,
    selectedId: String,
    customHex: String?,
    onSelect: (String) -> Unit,
    onEditCustom: () -> Unit
) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(label, color = DesktopColors.TextGray, fontSize = 12.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ThemeAccents.presets(isDark).forEach { accent ->
                DesktopTooltip(accent.label) {
                    AccentSwatch(accent.color, selected = selectedId == accent.id, onClick = { onSelect(accent.id) })
                }
            }
            val custom = customHex?.let(::parseRgbHex)
            DesktopTooltip(if (custom == null) "自定义" else "自定义 · 点角标修改") {
                if (custom == null) {
                    AddCustomSwatch(onEditCustom)
                } else {
                    Box {
                        AccentSwatch(custom, selected = selectedId == ThemeAccents.CUSTOM_ID, onClick = { onSelect(ThemeAccents.CUSTOM_ID) })
                        EditBadge(onEditCustom, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        border = if (selected) BorderStroke(2.dp, DesktopColors.TextPrimary) else BorderStroke(1.dp, DesktopColors.Outline),
        modifier = Modifier.size(SwatchSize).pointerHoverIcon(PointerIcon.Hand)
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "已选中", tint = contentColorOn(color), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AddCustomSwatch(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DesktopColors.Surface,
        border = BorderStroke(1.dp, DesktopColors.Overlay.copy(alpha = 0.2f)),
        modifier = Modifier.size(SwatchSize).pointerHoverIcon(PointerIcon.Hand)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, "添加自定义主色", tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EditBadge(onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DesktopColors.PopupSurface,
        shadowElevation = 2.dp,
        modifier = modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Edit, "修改自定义主色", tint = DesktopColors.TextPrimary, modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
private fun CustomAccentDialog(isDark: Boolean, initial: Color, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val state = remember { ColorPickerState(initial) }
    val background = MelodiaPalettes.of(isDark).pane
    val lowContrast = state.isHexValid && contrastRatio(state.color, background) < MIN_ACCENT_CONTRAST
    DesktopDialog(
        title = if (isDark) "自定义深色主色" else "自定义浅色主色",
        onDismiss = onDismiss,
        width = 380.dp,
        actions = {
            DialogButton("取消", onClick = onDismiss)
            DialogButton("确定", onClick = { onConfirm(state.hexText) }, primary = true, enabled = state.isHexValid)
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(background).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(if (state.isHexValid) state.color else Color.Transparent))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                DialogLabel("十六进制")
                DialogTextField(
                    value = state.hexText,
                    onValueChange = state::updateHexText,
                    placeholder = "#RRGGBB",
                    modifier = if (state.isHexValid) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth().border(1.dp, DangerColor, RoundedCornerShape(8.dp))
                    }
                )
            }
        }
        GradientSlider("色相", state.hsv.hue / 360f, state.hueTrack(), state.color) { state.updateHue(it * 360f) }
        GradientSlider("饱和度", state.hsv.saturation, state.saturationTrack(), state.color, state::updateSaturation)
        GradientSlider("明度", state.hsv.value, state.valueTrack(), state.color, state::updateValue)
        if (!state.isHexValid) {
            Text("请输入 #RRGGBB 格式的颜色", color = DangerColor, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        } else if (lowContrast) {
            Text("该颜色与背景对比度较低，按钮和图标可能不易看清", color = DesktopColors.TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientSlider(label: String, fraction: Float, track: Brush, thumbColor: Color, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        DialogLabel(label)
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().height(24.dp).pointerHoverIcon(PointerIcon.Hand),
            thumb = {
                Box(
                    Modifier.size(18.dp).themedShadow(2.dp, CircleShape).background(Color.White, CircleShape).padding(3.dp)
                        .background(thumbColor, CircleShape)
                )
            },
            track = {
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(track))
            }
        )
    }
}
