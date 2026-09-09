package com.lin0721.linmusic.feature.settings.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.EntryHotGradient
import com.lin0721.linmusic.core.ui.theme.EntryRadarGradient
import com.lin0721.linmusic.core.ui.theme.EntryRoamingGradient
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize

// 5 个分类的图例色，复用首页功能入口卡片已有的策展色，不新造颜色
private val CacheAudioColor = NeteaseRed
private val CacheImageColor = EntryHotGradient.first()
private val CacheLogColor = EntryRadarGradient.first()
private val CacheUpdateColor = EntryRoamingGradient.first()
private val CacheOtherColor = TextGray

// 待确认的清理操作带二次确认
private data class PendingClear(val title: String, val message: String, val action: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsView(viewModel: SettingsViewModel, context: Context) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentMaxSize by viewModel.audioCacheMaxSize.collectAsStateWithLifecycle()
    val audioCacheSize by viewModel.audioCacheSize.collectAsStateWithLifecycle()
    val imageCacheSize by viewModel.imageCacheSize.collectAsStateWithLifecycle()
    val logCacheSize by viewModel.logCacheSize.collectAsStateWithLifecycle()
    val updatePackageSize by viewModel.updatePackageSize.collectAsStateWithLifecycle()
    val otherCacheSize by viewModel.otherCacheSize.collectAsStateWithLifecycle()
    val totalCacheSize by viewModel.totalCacheSize.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf<PendingClear?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadStorageStats(context)
    }

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
                    CacheUsageBar(
                        totalSize = totalCacheSize,
                        audioCacheSize = audioCacheSize,
                        imageCacheSize = imageCacheSize,
                        logCacheSize = logCacheSize,
                        updatePackageSize = updatePackageSize,
                        otherCacheSize = otherCacheSize
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // 一键清理全部
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pendingClear = PendingClear(
                                    title = "清理应用缓存",
                                    message = "确定要清空以下全部缓存吗？"
                                ) { viewModel.clearApplicationCache(context) }
                            }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("清理应用缓存", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Text("一键清空以下所有缓存", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    CacheCategoryRow(
                        title = "音频缓存",
                        subtitle = "播放器已缓冲的歌曲音频数据",
                        sizeBytes = audioCacheSize,
                        onClear = {
                            pendingClear = PendingClear(
                                title = "清理音频缓存",
                                message = "确定要清空音频缓存吗？"
                            ) { viewModel.clearAudioCacheOnly(context) }
                        }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    CacheCategoryRow(
                        title = "图片缓存",
                        subtitle = "封面、头像等图片的内存与磁盘缓存",
                        sizeBytes = imageCacheSize,
                        onClear = {
                            pendingClear = PendingClear(
                                title = "清理图片缓存",
                                message = "确定要清空图片缓存吗？"
                            ) { viewModel.clearImageCacheOnly(context) }
                        }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    CacheCategoryRow(
                        title = "日志文件",
                        subtitle = "诊断日志，清空后将无法再导出",
                        sizeBytes = logCacheSize,
                        onClear = {
                            pendingClear = PendingClear(
                                title = "清理日志文件",
                                message = "确定要清空日志文件吗？"
                            ) { viewModel.clearLogCacheOnly() }
                        }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    CacheCategoryRow(
                        title = "更新安装包",
                        subtitle = "App 更新时下载的旧安装包",
                        sizeBytes = updatePackageSize,
                        onClear = {
                            pendingClear = PendingClear(
                                title = "清理更新安装包",
                                message = "确定要清空 App 更新时下载的旧安装包吗？"
                            ) { viewModel.clearUpdatePackages(context) }
                        }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    CacheCategoryRow(
                        title = "其他临时文件",
                        subtitle = "其余无法归类的应用临时数据",
                        sizeBytes = otherCacheSize,
                        onClear = {
                            pendingClear = PendingClear(
                                title = "清理其他临时文件",
                                message = "确定要清空其他临时文件吗？"
                            ) { viewModel.clearOtherTempFiles(context) }
                        }
                    )

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

        // 清理二次确认
        pendingClear?.let { pending ->
            ClearConfirmDialog(
                title = pending.title,
                message = pending.message,
                onConfirm = {
                    pending.action()
                    pendingClear = null
                },
                onDismiss = { pendingClear = null }
            )
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

// 总占用分段进度条：按各分类占比拉伸色块，下方配图例，数据全为 0 时退化成一条灰底
@Composable
private fun CacheUsageBar(
    totalSize: Long,
    audioCacheSize: Long,
    imageCacheSize: Long,
    logCacheSize: Long,
    updatePackageSize: Long,
    otherCacheSize: Long
) {
    val categories = listOf(
        Triple("音频缓存", audioCacheSize, CacheAudioColor),
        Triple("图片缓存", imageCacheSize, CacheImageColor),
        Triple("日志文件", logCacheSize, CacheLogColor),
        Triple("更新安装包", updatePackageSize, CacheUpdateColor),
        Triple("其他临时文件", otherCacheSize, CacheOtherColor)
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "当前共占用 ${formatFileSize(totalSize)}",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

        if (totalSize > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
            ) {
                categories.forEach { (_, size, color) ->
                    if (size > 0) {
                        Box(
                            modifier = Modifier
                                .weight(size.toFloat())
                                .fillMaxHeight()
                                .background(color)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

        categories.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
            ) {
                rowItems.forEach { (label, size, color) ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$label ${formatFileSize(size)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// 分类占用行：展示大小并支持单独清理，清空后大小行会立即归零
@Composable
private fun CacheCategoryRow(
    title: String,
    subtitle: String,
    sizeBytes: Long,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = sizeBytes > 0) { onClear() }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.md)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Text(
            text = formatFileSize(sizeBytes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        if (sizeBytes > 0) {
            Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "清理$title",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// 清理操作二次确认
@Composable
private fun ClearConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
        confirmButton = {
            MelodiaTextButton(onClick = onConfirm) {
                Text("清空", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            MelodiaTextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurface)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    )
}
