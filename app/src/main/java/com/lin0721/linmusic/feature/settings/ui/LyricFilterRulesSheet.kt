package com.lin0721.linmusic.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricFilterRules
import com.lin0721.linmusic.core.player.domain.isValidLyricFilterRegex
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LyricFilterRulesSheet"
private const val EXPORT_FILE_NAME = "melodia-lyric-filter.json"

private enum class RuleTab(val label: String) { Keywords("关键词"), Regexes("正则表达式") }

// 编辑歌词过滤规则：本地草稿，保存时才写入设置
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LyricFilterRulesSheet(
    rules: LyricFilterRules,
    onSave: (LyricFilterRules) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                }?.let(LyricFilterRules::decodeOrNull)
            } catch (e: Exception) {
                AppLogger.w(TAG, "读取规则文件失败: ${e.message}")
                null
            }
            if (imported == null) {
                ToastManager.showToast("规则文件解析失败")
                return@launch
            }
            val valid = imported.withoutInvalidRegexes()
            keywords = valid.keywords
            regexes = valid.regexes
            val skipped = imported.regexes.size - valid.regexes.size
            ToastManager.showToast(if (skipped > 0) "已导入，跳过 $skipped 条无效正则，保存后生效" else "已导入，保存后生效")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = LyricFilterRules.encode(LyricFilterRules(keywords, regexes))
        scope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) } != null
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "导出规则失败: ${e.message}")
                false
            }
            ToastManager.showToast(if (ok) "已导出" else "导出失败")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "歌词过滤规则",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "只处理歌词开头和结尾的信息行。关键词需紧跟冒号、括号等符号才算命中，如“作词：”；正则命中整行即删除。点按规则可删除",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.padding(top = MelodiaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleTab.entries.forEach { entry ->
                    val count = if (entry == RuleTab.Keywords) keywords.size else regexes.size
                    FilterChip(
                        selected = tab == entry,
                        onClick = {
                            tab = entry
                            input = ""
                            error = null
                        },
                        label = { Text("${entry.label} $count") }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(if (tab == RuleTab.Keywords) "添加或筛选关键词" else "添加或筛选正则") },
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() })
                )
                Spacer(Modifier.width(8.dp))
                MelodiaButton(onClick = ::add, enabled = input.isNotBlank()) { Text("添加") }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(top = MelodiaSpacing.sm)
                    .verticalScroll(rememberScrollState())
            ) {
                if (shown.isEmpty()) {
                    Text(
                        text = if (current.isEmpty()) "暂无规则" else "没有匹配的规则",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        shown.forEach { value ->
                            InputChip(
                                selected = false,
                                onClick = { remove(value) },
                                label = { Text(value) },
                                trailingIcon = { Icon(Icons.Filled.Close, "删除", modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MelodiaTextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                    Text("导入")
                }
                MelodiaTextButton(onClick = { exportLauncher.launch(EXPORT_FILE_NAME) }) { Text("导出") }
                MelodiaTextButton(onClick = {
                    keywords = LyricFilterRules.DEFAULT.keywords
                    regexes = LyricFilterRules.DEFAULT.regexes
                    input = ""
                    error = null
                }) { Text("恢复默认") }
                Spacer(Modifier.weight(1f))
                MelodiaTextButton(
                    onClick = {
                        when (tab) {
                            RuleTab.Keywords -> keywords = emptyList()
                            RuleTab.Regexes -> regexes = emptyList()
                        }
                    },
                    enabled = current.isNotEmpty()
                ) { Text("清空", color = MaterialTheme.colorScheme.error) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
            ) {
                MelodiaTextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                MelodiaButton(
                    onClick = {
                        onSave(LyricFilterRules(keywords, regexes))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }
}
