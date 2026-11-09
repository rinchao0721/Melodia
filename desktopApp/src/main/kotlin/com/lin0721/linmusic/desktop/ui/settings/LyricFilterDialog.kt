package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricFilterRules
import com.lin0721.linmusic.core.player.domain.isValidLyricFilterRegex
import com.lin0721.linmusic.desktop.platform.chooseFile
import com.lin0721.linmusic.desktop.platform.chooseSaveFile
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LyricFilterDialog"
private const val EXPORT_FILE_NAME = "melodia-lyric-filter.json"

private enum class RuleTab(val label: String) { Keywords("关键词"), Regexes("正则表达式") }

// 编辑歌词过滤规则：本地草稿，保存时才写入设置
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LyricFilterDialog(
    rules: LyricFilterRules,
    onSave: (LyricFilterRules) -> Unit,
    onDismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    var keywords by remember { mutableStateOf(rules.keywords) }
    var regexes by remember { mutableStateOf(rules.regexes) }
    var tab by remember { mutableStateOf(RuleTab.Keywords) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val current = if (tab == RuleTab.Keywords) keywords else regexes
    val query = input.trim()
    val shown = if (query.isEmpty()) current else current.filter { it.contains(query, ignoreCase = true) }

    fun add() {
        val value = input.trim()
        if (value.isEmpty()) return
        when (tab) {
            RuleTab.Keywords -> if (value !in keywords) keywords = keywords + value
            RuleTab.Regexes -> {
                if (!isValidLyricFilterRegex(value)) {
                    error = "正则表达式格式不正确"
                    return
                }
                if (value !in regexes) regexes = regexes + value
            }
        }
        input = ""
        error = null
    }

    fun remove(value: String) {
        when (tab) {
            RuleTab.Keywords -> keywords = keywords - value
            RuleTab.Regexes -> regexes = regexes - value
        }
    }

    fun importRules() {
        scope.launch {
            val file = chooseFile("导入歌词过滤规则", "*.json") ?: return@launch
            val imported = try {
                withContext(Dispatchers.IO) { LyricFilterRules.decodeOrNull(file.readText()) }
            } catch (e: Exception) {
                AppLogger.w(TAG, "读取规则文件失败: ${e.message}")
                null
            }
            if (imported == null) {
                navigator.showMessage("规则文件解析失败")
                return@launch
            }
            val valid = imported.withoutInvalidRegexes()
            keywords = valid.keywords
            regexes = valid.regexes
            val skipped = imported.regexes.size - valid.regexes.size
            navigator.showMessage(if (skipped > 0) "已导入，跳过 $skipped 条无效正则，保存后生效" else "已导入，保存后生效")
        }
    }

    fun exportRules() {
        scope.launch {
            val file = chooseSaveFile("导出歌词过滤规则", EXPORT_FILE_NAME, "json") ?: return@launch
            val ok = try {
                withContext(Dispatchers.IO) { file.writeText(LyricFilterRules.encode(LyricFilterRules(keywords, regexes))) }
                true
            } catch (e: Exception) {
                AppLogger.w(TAG, "导出规则失败: ${e.message}")
                false
            }
            navigator.showMessage(if (ok) "已导出到 ${file.name}" else "导出失败")
        }
    }

    DesktopDialog(
        title = "歌词过滤规则",
        onDismiss = onDismiss,
        width = 560.dp,
        actions = {
            DialogButton("导入", onClick = ::importRules)
            DialogButton("导出", onClick = ::exportRules)
            DialogButton("恢复默认", onClick = {
                keywords = LyricFilterRules.DEFAULT.keywords
                regexes = LyricFilterRules.DEFAULT.regexes
                input = ""
                error = null
            })
            Spacer(Modifier.weight(1f))
            DialogButton("取消", onClick = onDismiss)
            DialogButton("保存", primary = true, onClick = {
                onSave(LyricFilterRules(keywords, regexes))
                onDismiss()
            })
        }
    ) {
        Text(
            "只处理歌词开头和结尾的信息行。关键词需紧跟冒号、括号等符号才算命中，如“作词：”；正则命中整行即删除",
            color = DesktopColors.TextGray,
            fontSize = 12.sp
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleTab.entries.forEach { entry ->
                val count = if (entry == RuleTab.Keywords) keywords.size else regexes.size
                FilterChip(
                    selected = tab == entry,
                    onClick = {
                        tab = entry
                        input = ""
                        error = null
                    },
                    label = { Text("${entry.label} $count", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = DesktopColors.Surface,
                        labelColor = DesktopColors.TextGray,
                        selectedContainerColor = DesktopColors.TextPrimary,
                        selectedLabelColor = DesktopColors.WindowBackground
                    ),
                    border = null
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DialogTextField(
                value = input,
                onValueChange = {
                    input = it
                    error = null
                },
                modifier = Modifier.weight(1f),
                placeholder = if (tab == RuleTab.Keywords) "输入关键词，回车添加，也可用来筛选" else "输入正则，回车添加，也可用来筛选",
                onEnter = ::add
            )
            Spacer(Modifier.size(8.dp))
            DialogButton("添加", onClick = ::add, enabled = input.isNotBlank())
        }
        error?.let { Text(it, color = DesktopColors.Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 10.dp).verticalScroll(rememberScrollState())) {
            if (shown.isEmpty()) {
                Text(
                    if (current.isEmpty()) "暂无规则" else "没有匹配的规则",
                    color = DesktopColors.TextGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.forEach { value ->
                        InputChip(
                            selected = false,
                            onClick = { remove(value) },
                            label = { Text(value, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(Icons.Rounded.Close, "删除", tint = DesktopColors.TextGray, modifier = Modifier.size(14.dp))
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = DesktopColors.Surface,
                                labelColor = DesktopColors.TextPrimary
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp).pointerHoverIcon(PointerIcon.Hand)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DialogButton("清空当前列表", danger = true, enabled = current.isNotEmpty(), onClick = {
                when (tab) {
                    RuleTab.Keywords -> keywords = emptyList()
                    RuleTab.Regexes -> regexes = emptyList()
                }
            })
        }
    }
}
