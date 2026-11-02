package com.lin0721.linmusic.feature.settings.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.preferences.ThemeSettings
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.skin.BUILTIN_SKIN_ID
import com.lin0721.linmusic.core.ui.skin.PaletteToken
import com.lin0721.linmusic.core.ui.skin.Skin
import com.lin0721.linmusic.core.ui.skin.SkinImportStep
import com.lin0721.linmusic.core.ui.skin.SkinMode
import com.lin0721.linmusic.core.ui.skin.SkinRepository
import com.lin0721.linmusic.core.ui.skin.StagedSkin
import com.lin0721.linmusic.core.ui.skin.coverSummary
import com.lin0721.linmusic.core.ui.skin.rememberSkinSettingsController
import com.lin0721.linmusic.core.ui.skin.resolveSkinPalette
import com.lin0721.linmusic.core.ui.skin.skinExportFileName
import com.lin0721.linmusic.core.ui.skin.skinSwitchChangesTheme
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.rememberThemeSwitcher

private val SkinCardWidth = 112.dp
private val SkinThumbHeight = 72.dp
private val SkinCardShape = RoundedCornerShape(12.dp)
private val SkinThumbShape = RoundedCornerShape(8.dp)
private const val MAX_REPORT_LINES = 8

// 皮肤设置：默认、预设与已安装皮肤横向卡片 + 导入导出；导入先在底部弹窗预览再确认
@Composable
fun SkinSettingSection(settings: ThemeSettings, repository: SkinRepository) {
    val controller = rememberSkinSettingsController(repository)
    val installed by controller.installed.collectAsState()
    val presets by controller.presets.collectAsState()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<Skin?>(null) }
    val systemDark = isSystemInDarkTheme()
    // 皮肤按深浅分槽位保存，只列出并修改当前生效模式的那一组
    val isDark = settings.mode.isDark(systemDark)
    val visiblePresets = presets.filter { it.supports(isDark) }
    val visibleInstalled = installed.filter { it.supports(isDark) }
    val skinId = settings.skinId(isDark)
    val activeSkin = visiblePresets.firstOrNull { it.id == skinId } ?: visibleInstalled.firstOrNull { it.id == skinId }
    val switcher = rememberThemeSwitcher()
    val switchTo: (Skin?, Offset) -> Unit = { target, center ->
        val changes = skinSwitchChangesTheme(settings, systemDark, activeSkin, target)
        switcher.switch(center, changesTheme = changes) { controller.apply(target?.id ?: BUILTIN_SKIN_ID, isDark) }
    }

    // 皮肤包扩展名不在系统 MIME 表里，只能放开类型由解包校验
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.import { context.contentResolver.openInputStream(uri) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) controller.export(settings, activeSkin) { context.contentResolver.openOutputStream(uri) }
    }

    LaunchedEffect(repository) { controller.refresh() }
    LaunchedEffect(controller.exportMessage) {
        controller.exportMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            controller.dismissExportMessage()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("皮肤（实验性）", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        Text("导入皮肤包，更换配色、背景图与样式", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = BUILTIN_SKIN_ID) {
                SkinCard(
                    name = "默认",
                    subtitle = "内置主题",
                    skin = null,
                    settings = settings,
                    isDark = isDark,
                    selected = activeSkin == null,
                    onClick = { center -> switchTo(null, center) },
                    onDelete = null
                )
            }
            items(visiblePresets, key = { it.id }) { skin ->
                SkinCard(
                    name = skin.name,
                    subtitle = "预设",
                    skin = skin,
                    settings = settings,
                    isDark = isDark,
                    selected = skin.id == activeSkin?.id,
                    onClick = { center -> switchTo(skin, center) },
                    onDelete = null
                )
            }
            items(visibleInstalled, key = { it.id }) { skin ->
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MelodiaOutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                enabled = controller.step != SkinImportStep.Working,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (controller.step == SkinImportStep.Working) "正在读取…" else "导入皮肤", color = MelodiaColors.TextPrimary)
            }
            MelodiaOutlinedButton(
                onClick = { exportLauncher.launch(skinExportFileName(activeSkin)) },
                modifier = Modifier.weight(1f)
            ) {
                Text("导出当前主题", color = MelodiaColors.TextPrimary)
            }
        }
    }

    when (val step = controller.step) {
        is SkinImportStep.Preview -> SkinPreviewSheet(
            staged = step.staged,
            conflict = step.conflict,
            settings = settings,
            onCancel = controller::cancelImport,
            onConfirm = { replace -> controller.confirmImport(replace, isDark) }
        )
        is SkinImportStep.Failed -> AlertDialog(
            onDismissRequest = controller::dismissFailure,
            title = { Text("无法导入皮肤", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text(step.message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                MelodiaTextButton(onClick = controller::dismissFailure) { Text("知道了", color = MelodiaColors.AccentText) }
            },
            containerColor = MaterialTheme.colorScheme.background
        )
        else -> Unit
    }

    deleteTarget?.let { skin ->
        val hint = if (skin.id == activeSkin?.id) "，删除后恢复默认主题" else ""
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除皮肤", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text("确定删除「${skin.name}」吗$hint？", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                MelodiaTextButton(onClick = {
                    deleteTarget = null
                    controller.delete(skin.id)
                }) { Text("删除", color = MelodiaColors.Error) }
            },
            dismissButton = {
                MelodiaTextButton(onClick = { deleteTarget = null }) { Text("取消", color = MelodiaColors.TextPrimary) }
            },
            containerColor = MaterialTheme.colorScheme.background
        )
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
    var windowCenter by remember { mutableStateOf(Offset.Unspecified) }
    Box {
        Surface(
            onClick = { onClick(windowCenter) },
            shape = SkinCardShape,
            color = MelodiaColors.Surface,
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MelodiaColors.Accent else MelodiaColors.Outline),
            modifier = Modifier.width(SkinCardWidth).onGloballyPositioned { windowCenter = it.boundsInWindow().center }
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(SkinThumbHeight).clip(SkinThumbShape)) {
                    SkinThumb(skin, settings, isDark, Modifier.fillMaxSize())
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, color = MelodiaColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, color = MelodiaColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MelodiaColors.AccentText, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (onDelete != null) {
            Surface(
                onClick = onDelete,
                shape = CircleShape,
                color = MelodiaColors.PopupSurface,
                shadowElevation = 2.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = "删除皮肤", tint = MelodiaColors.TextPrimary, modifier = Modifier.size(14.dp))
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

// 皮肤锁定模式或指定主色时，提示主题设置里哪些选项暂不生效
@Composable
private fun SkinLockHints(skin: Skin, isDark: Boolean) {
    val hints = buildList {
        if (PaletteToken.ACCENT in skin.colors(isDark)) add("当前皮肤指定了主色，选择自定义颜色可覆盖")
    }
    hints.forEach { Text(it, color = MelodiaColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkinPreviewSheet(
    staged: StagedSkin,
    conflict: Boolean,
    settings: ThemeSettings,
    onCancel: () -> Unit,
    onConfirm: (replace: Boolean) -> Unit
) {
    val skin = staged.skin
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MelodiaSpacing.lg)
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Text(skin.name, color = MelodiaColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val meta = listOfNotNull(skin.author, skin.version?.let { "版本 $it" }).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = MelodiaColors.TextSecondary, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(SkinThumbHeight + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SkinMode.entries.filter { it in skin.modes }.forEach { mode ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(SkinThumbShape)) {
                        SkinThumb(skin, settings, mode == SkinMode.DARK, Modifier.fillMaxSize())
                    }
                }
            }
            val features = buildList {
                add(
                    when {
                        skin.modes.size == 2 -> "支持深色与浅色"
                        SkinMode.DARK in skin.modes -> "仅深色，只在深色模式下可选"
                        else -> "仅浅色，只在浅色模式下可选"
                    }
                )
                if (skin.background != null) add("包含背景图")
                if (skin.layouts != null) add("包含播放器布局（后续版本生效）")
            coverSummary(skin)?.let(::add)
            }
            Text(features.joinToString("；"), color = MelodiaColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            ReportSection("已自动修正对比度", staged.fixDescriptions)
            ReportSection("提示", staged.warnings)
            if (conflict) {
                Text(
                    "已安装同一皮肤，可覆盖原有版本或另存为新皮肤",
                    color = MelodiaColors.TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (conflict) {
                    MelodiaOutlinedButton(onClick = { onConfirm(false) }, modifier = Modifier.weight(1f)) {
                        Text("另存为新皮肤", color = MelodiaColors.TextPrimary)
                    }
                } else {
                    MelodiaTextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("取消", color = MelodiaColors.TextPrimary)
                    }
                }
                MelodiaButton(
                    onClick = { onConfirm(conflict) },
                    colors = ButtonDefaults.buttonColors(containerColor = MelodiaColors.Accent, contentColor = MelodiaColors.OnAccent),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (conflict) "覆盖并应用" else "导入并应用", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReportSection(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    Text(title, color = MelodiaColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    lines.take(MAX_REPORT_LINES).forEach { Text(it, color = MelodiaColors.TextSecondary, fontSize = 13.sp) }
    if (lines.size > MAX_REPORT_LINES) Text("等共 ${lines.size} 项", color = MelodiaColors.TextTertiary, fontSize = 12.sp)
}
