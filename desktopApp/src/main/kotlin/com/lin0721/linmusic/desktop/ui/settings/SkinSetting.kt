package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.preferences.ThemeSettings
import com.lin0721.linmusic.core.ui.skin.BUILTIN_SKIN_ID
import com.lin0721.linmusic.core.ui.skin.PaletteToken
import com.lin0721.linmusic.core.ui.skin.SKIN_FILE_EXTENSION
import com.lin0721.linmusic.core.ui.skin.Skin
import com.lin0721.linmusic.core.ui.skin.SkinImportStep
import com.lin0721.linmusic.core.ui.skin.SkinMode
import com.lin0721.linmusic.core.ui.skin.SkinRepository
import com.lin0721.linmusic.core.ui.skin.StagedSkin
import com.lin0721.linmusic.core.ui.skin.rememberSkinSettingsController
import com.lin0721.linmusic.core.ui.skin.resolveSkinPalette
import com.lin0721.linmusic.core.ui.skin.skinExportFileName
import com.lin0721.linmusic.core.ui.skin.skinSwitchChangesTheme
import com.lin0721.linmusic.core.ui.theme.rememberThemeSwitcher
import com.lin0721.linmusic.desktop.platform.chooseFile
import com.lin0721.linmusic.desktop.platform.chooseSaveFile
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogLabel
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.SystemDarkTheme

private val SkinCardWidth = 136.dp
private val SkinThumbHeight = 76.dp
private val SkinCardShape = RoundedCornerShape(10.dp)
private val SkinThumbShape = RoundedCornerShape(6.dp)

// 皮肤设置：已安装皮肤卡片 + 导入导出；导入先预览再确认
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkinSetting(settings: ThemeSettings, repository: SkinRepository) {
    val controller = rememberSkinSettingsController(repository)
    val installed by controller.installed.collectAsState()
    val navigator = LocalDesktopNavigator.current
    val isDark = DesktopColors.IsDark
    var deleteTarget by remember { mutableStateOf<Skin?>(null) }
    val activeSkin = installed.firstOrNull { it.id == settings.skinId }
    val switcher = rememberThemeSwitcher()
    val systemDark by SystemDarkTheme.isDark.collectAsState()
    val switchTo: (Skin?, Offset) -> Unit = { target, center ->
        val changes = skinSwitchChangesTheme(settings, systemDark, activeSkin, target)
        switcher.switch(center, changesTheme = changes) { controller.apply(target?.id ?: BUILTIN_SKIN_ID) }
    }

    LaunchedEffect(repository) { controller.refresh() }
    LaunchedEffect(controller.exportMessage) {
        controller.exportMessage?.let {
            navigator.showMessage(it)
            controller.dismissExportMessage()
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("皮肤（实验性）", color = DesktopColors.TextPrimary, fontSize = 14.sp)
        Text("导入 .$SKIN_FILE_EXTENSION 皮肤包，更换配色、背景图与样式", color = DesktopColors.TextGray, fontSize = 12.sp)
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkinCard(
                name = "默认",
                subtitle = "内置主题",
                skin = null,
                settings = settings,
                isDark = isDark,
                selected = settings.skinId == BUILTIN_SKIN_ID || activeSkin == null,
                onClick = { center -> switchTo(null, center) },
                onDelete = null
            )
            installed.forEach { skin ->
                SkinCard(
                    name = skin.name,
                    subtitle = skin.author ?: "未署名",
                    skin = skin,
                    settings = settings,
                    isDark = isDark,
                    selected = skin.id == activeSkin?.id,
                    onClick = { center -> switchTo(skin, center) },
                    onDelete = { deleteTarget = skin }
                )
            }
        }
        activeSkin?.let { SkinLockHints(it, isDark) }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton(
                "导入皮肤",
                onClick = { controller.import { chooseFile("选择皮肤包", "*.$SKIN_FILE_EXTENSION")?.inputStream() } },
                loading = controller.step == SkinImportStep.Working
            )
            DialogButton(
                "导出当前主题",
                onClick = {
                    controller.export(settings, activeSkin) {
                        chooseSaveFile("导出当前主题", skinExportFileName(activeSkin), SKIN_FILE_EXTENSION)?.outputStream()
                    }
                }
            )
        }
    }

    when (val step = controller.step) {
        is SkinImportStep.Preview -> SkinPreviewDialog(
            staged = step.staged,
            conflict = step.conflict,
            settings = settings,
            onCancel = controller::cancelImport,
            onConfirm = controller::confirmImport
        )
        is SkinImportStep.Failed -> DesktopDialog(
            title = "无法导入皮肤",
            onDismiss = controller::dismissFailure,
            actions = { DialogButton("知道了", onClick = controller::dismissFailure, primary = true) }
        ) {
            Text(step.message, color = DesktopColors.TextGray, fontSize = 13.sp)
        }
        else -> Unit
    }

    deleteTarget?.let { skin ->
        DesktopDialog(
            title = "删除皮肤",
            onDismiss = { deleteTarget = null },
            actions = {
                DialogButton("取消", onClick = { deleteTarget = null })
                DialogButton("删除", onClick = {
                    deleteTarget = null
                    controller.delete(skin.id)
                }, danger = true)
            }
        ) {
            val hint = if (skin.id == activeSkin?.id) "，删除后恢复默认主题" else ""
            Text("确定删除「${skin.name}」吗$hint？", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SkinCard(
    name: String,
    subtitle: String,
    skin: Skin?,
    settings: ThemeSettings,
    isDark: Boolean,
    selected: Boolean,
    onClick: (windowCenter: Offset) -> Unit,
    onDelete: (() -> Unit)?
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var windowCenter by remember { mutableStateOf(Offset.Unspecified) }
    Box {
        Surface(
            onClick = { onClick(windowCenter) },
            shape = SkinCardShape,
            color = DesktopColors.CardSurface,
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) DesktopColors.Accent else DesktopColors.Outline),
            modifier = Modifier.width(SkinCardWidth).hoverable(hoverSource).pointerHoverIcon(PointerIcon.Hand)
                .onGloballyPositioned { windowCenter = it.boundsInWindow().center }
        ) {
            Column(Modifier.padding(8.dp)) {
                Box(Modifier.fillMaxWidth().height(SkinThumbHeight).clip(SkinThumbShape)) {
                    SkinThumb(skin, settings, skin?.effectiveDark(isDark) ?: isDark, Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = DesktopColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, color = DesktopColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (selected) Icon(Icons.Rounded.Check, null, tint = DesktopColors.AccentText, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (onDelete != null) {
            HoverReveal(hovered, Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                DesktopTooltip("删除") {
                    Surface(
                        onClick = onDelete,
                        shape = CircleShape,
                        color = DesktopColors.PopupSurface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(22.dp).hoverable(hoverSource).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "删除皮肤", tint = DesktopColors.TextPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinThumb(skin: Skin?, settings: ThemeSettings, isDark: Boolean, modifier: Modifier) {
    val palette = remember(skin, settings, isDark) {
        resolveSkinPalette(isDark, settings.accentId(isDark), settings.customHex(isDark), skin)
    }
    ModeThumb(palette, palette.accent, modifier)
}

@Composable
private fun SkinPreviewDialog(
    staged: StagedSkin,
    conflict: Boolean,
    settings: ThemeSettings,
    onCancel: () -> Unit,
    onConfirm: (replace: Boolean) -> Unit
) {
    val skin = staged.skin
    DesktopDialog(
        title = "导入皮肤",
        onDismiss = onCancel,
        width = 440.dp,
        actions = {
            DialogButton("取消", onClick = onCancel)
            if (conflict) {
                DialogButton("另存为新皮肤", onClick = { onConfirm(false) })
                DialogButton("覆盖并应用", onClick = { onConfirm(true) }, primary = true)
            } else {
                DialogButton("导入并应用", onClick = { onConfirm(false) }, primary = true)
            }
        }
    ) {
        Text(skin.name, color = DesktopColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        val meta = listOfNotNull(skin.author, skin.version?.let { "版本 $it" }).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, color = DesktopColors.TextGray, fontSize = 12.sp)
        Row(Modifier.padding(top = 12.dp).height(SkinThumbHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkinMode.entries.filter { it in skin.modes }.forEach { mode ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(SkinThumbShape)) {
                    SkinThumb(skin, settings, mode == SkinMode.DARK, Modifier.fillMaxSize())
                }
            }
        }
        val features = buildList {
            add(if (skin.modes.size == 2) "支持深色与浅色" else if (SkinMode.DARK in skin.modes) "仅深色，使用期间固定为深色" else "仅浅色，使用期间固定为浅色")
            if (skin.background != null) add("包含背景图")
            if (skin.layouts != null) add("包含播放器布局（后续版本生效）")
        }
        Text(features.joinToString("；"), color = DesktopColors.TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        if (staged.fixes.isNotEmpty()) {
            DialogLabel("已自动修正对比度", Modifier.padding(top = 12.dp))
            ReportLines(staged.fixes.map { it.description })
        }
        if (staged.warnings.isNotEmpty()) {
            DialogLabel("提示", Modifier.padding(top = 12.dp))
            ReportLines(staged.warnings)
        }
        if (conflict) {
            Text(
                "已安装同一皮肤，可覆盖原有版本或另存为新皮肤",
                color = DesktopColors.TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

private const val MAX_REPORT_LINES = 8

@Composable
private fun ReportLines(lines: List<String>) {
    lines.take(MAX_REPORT_LINES).forEach { Text(it, color = DesktopColors.TextGray, fontSize = 12.sp) }
    if (lines.size > MAX_REPORT_LINES) {
        Text("等共 ${lines.size} 项", color = DesktopColors.TextTertiary, fontSize = 12.sp)
    }
}

// 皮肤锁定模式或指定主色时，提示主题设置里哪些选项暂不生效
@Composable
private fun SkinLockHints(skin: Skin, isDark: Boolean) {
    val hints = buildList {
        if (skin.modes.size == 1) add("当前皮肤仅支持${if (SkinMode.DARK in skin.modes) "深色" else "浅色"}，深浅色设置暂不生效")
        if (PaletteToken.ACCENT in skin.colors(isDark)) add("当前皮肤指定了主色，选择自定义颜色可覆盖")
    }
    hints.forEach { Text(it, color = DesktopColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
}
