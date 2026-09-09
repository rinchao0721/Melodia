package com.lin0721.linmusic.feature.settings.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsView(viewModel: SettingsViewModel, context: Context) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentMaxSize by viewModel.audioCacheMaxSize.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    val currentSizeStr = when (currentMaxSize) {
        200 * 1024 * 1024L -> "200 MB"
        500 * 1024 * 1024L -> "500 MB"
        1024 * 1024 * 1024L -> "1 GB"
        2 * 1024 * 1024 * 1024L -> "2 GB"
        else -> "${currentMaxSize / (1024 * 1024)} MB"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
        ) {
            item {
                SettingsGroupCard("存储管理") {
                    // 清理缓存行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.clearApplicationCache(context) }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("清理应用缓存", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Text("清理图片缓存、播放器缓冲和临时数据文件", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // 缓存大小上限设置行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSheet = true }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("最大音频缓存上限", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Text("当前上限: $currentSizeStr", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Text(
                            text = "修改",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // 缓存上限选择弹层
        if (showSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.background,
                shape = BottomSheetShape,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = MelodiaSpacing.xs)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(DragHandleShape)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
                ) {
                    Text(
                        text = "最大音频缓存上限",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                    )

                    val options = listOf(
                        200 * 1024 * 1024L to "200 MB",
                        500 * 1024 * 1024L to "500 MB",
                        1024 * 1024 * 1024L to "1 GB",
                        2 * 1024 * 1024 * 1024L to "2 GB"
                    )
                    options.forEach { (size, label) ->
                        val isSelected = currentMaxSize == size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateAudioCacheMaxSize(context, size)
                                    showSheet = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // 加载指示器
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
