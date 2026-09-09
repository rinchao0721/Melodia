package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
fun LyricsSettingsView(viewModel: SettingsViewModel) {
    val showDesktopLrc by viewModel.showDesktopLrc.collectAsStateWithLifecycle()
    val lyricTextSize by viewModel.lyricTextSize.collectAsStateWithLifecycle()
    val lyricTextColor by viewModel.lyricTextColor.collectAsStateWithLifecycle()

    var showSizeSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }

    val sizeLabel = "${lyricTextSize} sp"
    val colorLabel = when (lyricTextColor) {
        "#FFFFFF" -> "白色"
        "#E03E3E" -> "网易红"
        "#10B981" -> "绿色"
        else -> lyricTextColor
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
        ) {
            item {
                SettingsGroupCard("悬浮歌词") {
                    SettingsSwitchRow(
                        title = "启用桌面悬浮歌词",
                        subtitle = "返回桌面时以悬浮窗形态展示当前播放词句",
                        checked = showDesktopLrc,
                        onCheckedChange = { viewModel.updateShowDesktopLrc(it) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsRow(
                        title = "悬浮歌词字号",
                        subtitle = sizeLabel,
                        onClick = { showSizeSheet = true }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsRow(
                        title = "悬浮歌词颜色",
                        subtitle = colorLabel,
                        onClick = { showColorSheet = true }
                    )
                }
            }
        }

        // 字号选择弹层
        if (showSizeSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSizeSheet = false },
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
                        text = "选择悬浮歌词字号",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                    )

                    val options = listOf(12, 14, 16, 18, 20)
                    options.forEach { size ->
                        val isSelected = lyricTextSize == size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateLyricTextSize(size)
                                    showSizeSheet = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${size} sp",
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

        // 颜色选择弹层
        if (showColorSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showColorSheet = false },
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
                        text = "选择悬浮歌词颜色",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                    )

                    val options = listOf(
                        "#FFFFFF" to "白色",
                        "#E03E3E" to "网易红",
                        "#10B981" to "绿色"
                    )
                    options.forEach { (colorCode, label) ->
                        val isSelected = lyricTextColor == colorCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateLyricTextColor(colorCode)
                                    showColorSheet = false
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
    }
}
