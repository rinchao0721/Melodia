package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogArtist
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistCatalogSheet(
    artistName: String,
    state: ArtistCatalogUiState,
    selection: ArtistCatalogSelection?,
    onEnabledChanged: (Boolean) -> Unit,
    onFollowDefault: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onBind: (ArtistCatalogArtist) -> Unit,
    onChangeBinding: () -> Unit,
    onRetry: () -> Unit,
    onRestoreAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("主要录音室专辑", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (state.enabledOverride == null) "$artistName · 跟随全局（${if (state.defaultEnabled) "开启" else "关闭"}）"
                        else "$artistName · 此歌手单独设置", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.enabled, onCheckedChange = onEnabledChanged, enabled = !state.saving)
            }
            if (state.enabledOverride != null) {
                TextButton(onClick = onFollowDefault, enabled = !state.saving) {
                    Text("恢复跟随全局（${if (state.defaultEnabled) "开启" else "关闭"}）")
                }
            }
            Text("使用 MusicBrainz 音乐资料库的专辑分类，合并同一专辑的重制、地区等发行版本。单曲、EP、现场、精选和混音专辑不在此列表。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!state.enabled) {
                Text("目前使用网易云原专辑列表和已有曲数设置。开启此开关仅为该歌手单独设置；全局开关在设置 → 扩展 → 歌手专辑中管理。",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                ArtistCatalogStatus(state, selection)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRetry, enabled = !state.loading && !state.saving) { Text("重试资料库") }
                    if (state.artistMbid != null) TextButton(onClick = onChangeBinding, enabled = !state.saving) { Text("更换资料库歌手") }
                    TextButton(onClick = onRestoreAll, enabled = !state.saving) { Text("恢复全部专辑") }
                }
                if (state.choosingArtist || state.artistMbid == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = state.query, onValueChange = onQueryChanged,
                            modifier = Modifier.weight(1f), label = { Text("资料库歌手名称") }, singleLine = true,
                            enabled = !state.saving,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch() }))
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onSearch, enabled = state.query.isNotBlank() && !state.loading && !state.saving) { Text("搜索") }
                    }
                    if (state.searched && !state.loading && state.candidates.isEmpty() && state.error == null) {
                        Text("未找到资料库歌手，可尝试其他名称或别名。", style = MaterialTheme.typography.bodySmall)
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(state.candidates, key = { it.id }) { candidate ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = !state.saving) { onBind(candidate) }
                                .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(candidate.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    candidate.aliases.takeIf { it.isNotEmpty() }?.let {
                                        Text("别名：${it.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(listOfNotNull(candidate.country, candidate.type,
                                        candidate.disambiguation.takeIf(String::isNotBlank)).joinToString(" · ").ifBlank { "未提供更多识别信息" },
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("绑定", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider()
                        }
                    }
                } else Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}

@Composable
fun ArtistCatalogStatus(state: ArtistCatalogUiState, selection: ArtistCatalogSelection?) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        state.artistName?.takeIf(String::isNotBlank)?.let {
            Text("资料库歌手：$it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (selection != null) {
            Text("当前已加载网易云专辑：匹配 ${selection.matchedCount} · 未识别 ${selection.unmatchedCount} · 合并 ${selection.duplicateCount} · 排除 ${selection.excludedCount}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("未识别的专辑不在筛选列表，可恢复全部查看。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!state.loading) {
            Text(if (state.artistMbid == null) "尚未绑定资料库歌手，暂显示网易云专辑。"
                else "尚无可用的资料库目录，暂显示网易云专辑。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.loading || state.saving) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(if (state.saving) "正在保存歌手设置…" else if (state.groups == null) "正在读取音乐资料库，暂显示网易云专辑…"
                else "正在更新音乐资料库…", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(if (state.groups == null) "现有专辑列表保留，可稍后重试。" else "继续使用已保存的资料库目录。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
