package com.lin0721.linmusic.feature.settings.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.preferences.ThemeMode
import com.lin0721.linmusic.core.preferences.ThemeSettings
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.ColorPickerState
import com.lin0721.linmusic.core.ui.theme.MIN_ACCENT_CONTRAST
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.ThemeAccents
import com.lin0721.linmusic.core.ui.theme.contentColorOn
import com.lin0721.linmusic.core.ui.theme.contrastRatio
import com.lin0721.linmusic.core.ui.theme.parseRgbHex
import com.lin0721.linmusic.core.ui.theme.rememberThemeSwitcher
import com.lin0721.linmusic.core.ui.theme.themedShadow

private val ModeThumbHeight = 72.dp
private val SwatchSize = 36.dp
private val CardShape = RoundedCornerShape(12.dp)

private val ModeLabels = mapOf(
    ThemeMode.DARK to "深色",
    ThemeMode.LIGHT to "浅色",
    ThemeMode.SYSTEM to "跟随系统"
)

// 主题设置：模式缩略图卡片 + 当前模式的主色色块；跟随系统时深浅两套主色分两行
@Composable
fun ThemeSettingSection(
    settings: ThemeSettings,
    onModeChange: (ThemeMode) -> Unit,
    onAccentChange: (isDark: Boolean, accentId: String) -> Unit,
    onCustomColorChange: (isDark: Boolean, hex: String) -> Unit
) {
    var pickerFor by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val darkDynamic = rememberDynamicAccent(isDark = true)
    val lightDynamic = rememberDynamicAccent(isDark = false)
    val darkAccent = ThemeAccents.resolve(settings.darkAccentId, settings.darkCustomHex, isDark = true, dynamicColor = darkDynamic)
    val lightAccent = ThemeAccents.resolve(settings.lightAccentId, settings.lightCustomHex, isDark = false, dynamicColor = lightDynamic)
    val switcher = rememberThemeSwitcher()
    val currentIsDark = MelodiaColors.IsDark
    val systemDark = isSystemInDarkTheme()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("主题（实验性）", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        Text("切换深浅模式与主色，立即生效", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                ModeCard(
                    mode = mode,
                    darkAccent = darkAccent,
                    lightAccent = lightAccent,
                    selected = settings.mode == mode,
                    onClick = { center ->
                        val changes = mode.isDark(systemDark) != currentIsDark
                        switcher.switch(center, changesTheme = changes) { onModeChange(mode) }
                    },
                    modifier = Modifier.weight(1f)
                )
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
                dynamicColor = if (isDark) darkDynamic else lightDynamic,
                onSelect = { onAccentChange(isDark, it) },
                onEditCustom = { pickerFor = isDark }
            )
        }
    }

    pickerFor?.let { isDark ->
        val initial = settings.customHex(isDark)?.let(::parseRgbHex)
            ?: ThemeAccents.resolve(settings.accentId(isDark), null, isDark)
        CustomAccentSheet(
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

// 系统动态色仅 Android 12+ 可用，其余返回 null
@Composable
private fun rememberDynamicAccent(isDark: Boolean): Color? {
    val context = LocalContext.current
    return remember(context, isDark) {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> null
            isDark -> dynamicDarkColorScheme(context).primary
            else -> dynamicLightColorScheme(context).primary
        }
    }
}

@Composable
private fun ModeCard(
    mode: ThemeMode,
    darkAccent: Color,
    lightAccent: Color,
    selected: Boolean,
    onClick: (windowCenter: Offset) -> Unit,
    modifier: Modifier
) {
    var windowCenter by remember { mutableStateOf(Offset.Unspecified) }
    Surface(
        onClick = { onClick(windowCenter) },
        shape = CardShape,
        color = MelodiaColors.Surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MelodiaColors.Accent else MelodiaColors.Overlay.copy(alpha = 0.1f)
        ),
        modifier = modifier.onGloballyPositioned { windowCenter = it.boundsInWindow().center }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(ModeThumbHeight).clip(RoundedCornerShape(8.dp))
            ) {
                when (mode) {
                    ThemeMode.DARK -> ModeThumb(MelodiaPalettes.dark, darkAccent, Modifier.fillMaxSize())
                    ThemeMode.LIGHT -> ModeThumb(MelodiaPalettes.light, lightAccent, Modifier.fillMaxSize())
                    ThemeMode.SYSTEM -> Row(modifier = Modifier.fillMaxSize()) {
                        ModeThumb(MelodiaPalettes.light, lightAccent, Modifier.weight(1f).fillMaxHeight())
                        ModeThumb(MelodiaPalettes.dark, darkAccent, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ModeLabels.getValue(mode),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MelodiaColors.Accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// 缩略图：窗口底色上一块面板，两条文字线与一枚主色药丸
@Composable
internal fun ModeThumb(palette: MelodiaPalette, accent: Color, modifier: Modifier) {
    Box(modifier = modifier.background(palette.windowBackground).padding(5.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(palette.pane).padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.fillMaxWidth(0.7f).height(5.dp).clip(CircleShape).background(palette.textPrimary))
            Box(Modifier.fillMaxWidth(0.45f).height(4.dp).clip(CircleShape).background(palette.textSecondary))
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(20.dp).height(8.dp).clip(CircleShape).background(accent))
        }
    }
}

@Composable
private fun AccentRow(
    label: String,
    isDark: Boolean,
    selectedId: String,
    customHex: String?,
    dynamicColor: Color?,
    onSelect: (String) -> Unit,
    onEditCustom: () -> Unit
) {
    val presets = ThemeAccents.presets(isDark)
    val custom = customHex?.let(::parseRgbHex)
    val selectedLabel = when (selectedId) {
        ThemeAccents.CUSTOM_ID -> if (custom != null) "自定义" else presets.first().label
        ThemeAccents.DYNAMIC_ID -> if (dynamicColor != null) "系统动态色" else presets.first().label
        else -> presets.firstOrNull { it.id == selectedId }?.label ?: presets.first().label
    }
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(" · $selectedLabel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (dynamicColor != null) {
                DynamicSwatch(dynamicColor, selected = selectedId == ThemeAccents.DYNAMIC_ID) { onSelect(ThemeAccents.DYNAMIC_ID) }
            }
            presets.forEach { accent ->
                AccentSwatch(accent.color, accent.label, selected = selectedId == accent.id) { onSelect(accent.id) }
            }
            if (custom == null) {
                AddCustomSwatch(onEditCustom)
            } else {
                Box {
                    AccentSwatch(custom, "自定义", selected = selectedId == ThemeAccents.CUSTOM_ID) { onSelect(ThemeAccents.CUSTOM_ID) }
                    EditBadge(onEditCustom, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(color: Color, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        border = if (selected) BorderStroke(2.dp, MelodiaColors.TextPrimary) else BorderStroke(1.dp, MelodiaColors.Outline),
        modifier = Modifier.size(SwatchSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "$description，已选中", tint = contentColorOn(color), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DynamicSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        border = if (selected) {
            BorderStroke(2.dp, MelodiaColors.TextPrimary)
        } else {
            BorderStroke(2.dp, Brush.sweepGradient(listOf(Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4), Color(0xFFEA4335))))
        },
        modifier = Modifier.size(SwatchSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.AutoAwesome,
                contentDescription = if (selected) "系统动态色，已选中" else "系统动态色",
                tint = contentColorOn(color),
                modifier = Modifier.size(if (selected) 18.dp else 16.dp)
            )
        }
    }
}

@Composable
private fun AddCustomSwatch(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MelodiaColors.SurfaceLight,
        border = BorderStroke(1.dp, MelodiaColors.Overlay.copy(alpha = 0.2f)),
        modifier = Modifier.size(SwatchSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = "添加自定义主色", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EditBadge(onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MelodiaColors.SurfaceLight,
        shadowElevation = 2.dp,
        modifier = modifier.size(20.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Edit, contentDescription = "修改自定义主色", tint = MelodiaColors.TextPrimary, modifier = Modifier.size(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomAccentSheet(isDark: Boolean, initial: Color, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = remember { ColorPickerState(initial) }
    val background = MelodiaPalettes.of(isDark).pane
    val lowContrast = state.isHexValid && contrastRatio(state.color, background) < MIN_ACCENT_CONTRAST

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.lg)
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Text(
                text = if (isDark) "自定义深色主色" else "自定义浅色主色",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(background).padding(10.dp)
                ) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(if (state.isHexValid) state.color else Color.Transparent))
                }
                OutlinedTextField(
                    value = state.hexText,
                    onValueChange = state::updateHexText,
                    label = { Text("十六进制") },
                    placeholder = { Text("#RRGGBB") },
                    isError = !state.isHexValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f).padding(start = 16.dp)
                )
            }
            GradientSlider("色相", state.hsv.hue / 360f, state.hueTrack(), state.color) { state.updateHue(it * 360f) }
            GradientSlider("饱和度", state.hsv.saturation, state.saturationTrack(), state.color, state::updateSaturation)
            GradientSlider("明度", state.hsv.value, state.valueTrack(), state.color, state::updateValue)
            when {
                !state.isHexValid -> Text(
                    "请输入 #RRGGBB 格式的颜色",
                    color = MelodiaColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
                lowContrast -> Text(
                    "该颜色与背景对比度较低，按钮和图标可能不易看清",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MelodiaTextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消", color = MelodiaColors.TextPrimary)
                }
                MelodiaButton(
                    onClick = { onConfirm(state.hexText) },
                    enabled = state.isHexValid,
                    colors = ButtonDefaults.buttonColors(containerColor = MelodiaColors.Accent, contentColor = MelodiaColors.OnAccent),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("确定", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientSlider(label: String, fraction: Float, track: Brush, thumbColor: Color, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().height(32.dp),
            thumb = {
                Box(
                    Modifier.size(22.dp).themedShadow(2.dp, CircleShape).background(Color.White, CircleShape).padding(4.dp)
                        .background(thumbColor, CircleShape)
                )
            },
            track = {
                Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(track))
            }
        )
    }
}
