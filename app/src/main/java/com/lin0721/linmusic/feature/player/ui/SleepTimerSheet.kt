package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.player.formatSleepTimerRemaining
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.TimerWarningRed

// ────────────────────────────────────────────────────────────────────────────
// "定时关闭"睡眠定时器弹层（预设选项 + 自定义时间滚轮）
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    sleepTimerRemaining: Long,
    onSetTimer: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = timerSheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        var isCustomMode by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
        ) {
            if (!isCustomMode) {
                Text(
                    text = if (sleepTimerRemaining > 0L) {
                        "定时关闭 (${formatSleepTimerRemaining(sleepTimerRemaining)})"
                    } else {
                        "定时关闭"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                )

                val options = listOf(
                    "关闭" to 0,
                    "10 分钟" to 10,
                    "15 分钟" to 15,
                    "30 分钟" to 30,
                    "45 分钟" to 45,
                    "1 小时" to 60
                )
                options.forEach { (label, minutes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSetTimer(minutes)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        val isSelected = if (minutes == 0) {
                            sleepTimerRemaining <= 0L
                        } else {
                            sleepTimerRemaining > (minutes - 1) * 60 * 1000L && sleepTimerRemaining <= minutes * 60 * 1000L
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // 自定义时间选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isCustomMode = true
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "自定义时间...",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // 自定义倒计时设置界面
                var customHours by remember { mutableIntStateOf(0) }
                var customMinutes by remember { mutableIntStateOf(30) }

                // 返回键与标题
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MelodiaSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MelodiaIconButton(
                        onClick = { isCustomMode = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
                    Text(
                        text = "自定义定时关闭",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // 小时和分钟大数字滚轮设置盘
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MelodiaSpacing.md),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 小时滚轮
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeWheelPicker(
                            value = customHours,
                            range = 0..23,
                            onValueChange = { customHours = it }
                        )
                        Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
                        Text(
                            text = "时",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = MelodiaSpacing.xs)
                        )
                        Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                    }

                    Text(
                        text = ":",
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // 分钟滚轮
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                        TimeWheelPicker(
                            value = customMinutes,
                            range = 0..59,
                            onValueChange = { customMinutes = it }
                        )
                        Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
                        Text(
                            text = "分",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = MelodiaSpacing.xs)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 快捷加减胶囊按钮
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                ) {
                    // 分钟调整
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                    ) {
                        val quickMinOptions = listOf(
                            "-15分" to -15,
                            "+15分" to 15,
                            "+30分" to 30
                        )
                        quickMinOptions.forEach { (label, delta) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(PillRadius))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        val totalMin = customHours * 60 + customMinutes + delta
                                        if (totalMin >= 0) {
                                            customHours = (totalMin / 60) % 24
                                            customMinutes = totalMin % 60
                                        } else {
                                            customHours = 0
                                            customMinutes = 0
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 小时与重置调整
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                    ) {
                        val quickHourOptions = listOf(
                            "-1时" to -60,
                            "+1时" to 60,
                            "重置" to 0
                        )
                        quickHourOptions.forEach { (label, delta) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(PillRadius))
                                    .background(
                                        if (label == "重置") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
                                    )
                                    .clickable {
                                        if (label == "重置") {
                                            customHours = 0
                                            customMinutes = 0
                                        } else {
                                            val totalMin = customHours * 60 + customMinutes + delta
                                            if (totalMin >= 0) {
                                                customHours = (totalMin / 60) % 24
                                                customMinutes = totalMin % 60
                                            } else {
                                                customHours = 0
                                                customMinutes = 0
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (label == "重置") MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 开启定时关闭确定按钮
                val totalTargetMinutes = customHours * 60 + customMinutes
                MelodiaButton(
                    onClick = {
                        if (totalTargetMinutes > 0) {
                            onSetTimer(totalTargetMinutes)
                        }
                    },
                    enabled = totalTargetMinutes > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.White.copy(alpha = 0.04f)
                    ),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(
                            brush = if (totalTargetMinutes > 0) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        TimerWarningRed
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.04f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        text = if (totalTargetMinutes > 0) "开启定时关闭 (${customHours}时${customMinutes}分)" else "请选择时间",
                        color = if (totalTargetMinutes > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeWheelPicker(
    value: Int,
    range: Iterable<Int>,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val list = remember(range) { range.toList() }
    val itemHeight = 44.dp
    val density = LocalDensity.current
    val itemHeightPx = remember(density) { with(density) { itemHeight.toPx() } }

    val initialIndex = remember(list, value) {
        val idx = list.indexOf(value)
        if (idx != -1) idx else 0
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    val currentSelection = remember {
        derivedStateOf {
            val index = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            val selected = if (offset > itemHeightPx / 2f) index + 1 else index
            selected.coerceIn(0, list.lastIndex)
        }
    }

    LaunchedEffect(currentSelection.value) {
        if (currentSelection.value in list.indices) {
            onValueChange(list[currentSelection.value])
        }
    }

    LaunchedEffect(value) {
        val targetIndex = list.indexOf(value)
        if (targetIndex != -1 && !state.isScrollInProgress && state.firstVisibleItemIndex != targetIndex) {
            state.animateScrollToItem(targetIndex)
        }
    }

    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val index = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            if (offset > 0) {
                val targetIndex = if (offset > itemHeightPx / 2f) index + 1 else index
                state.animateScrollToItem(targetIndex.coerceIn(0, list.lastIndex))
            }
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * 3)
            .width(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 1.dp,
                modifier = Modifier.padding(top = itemHeight)
            )
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = itemHeight)
            )
        }

        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(list) { index, item ->
                val isSelected = currentSelection.value == index
                val textColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                val fontSize = if (isSelected) 36.sp else 24.sp
                val fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format("%02d", item),
                        color = textColor,
                        fontSize = fontSize,
                        fontWeight = fontWeight
                    )
                }
            }
        }
    }
}
