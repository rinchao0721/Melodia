package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

// 由列表滚动位置推导的视觉指标：顶栏标题显隐、封面缩放、背景层位移
@Stable
class FullPlayerScrollMetrics(private val listState: LazyListState) {

    // 封面滚出视野后没有实测尺寸，沿用最后一次记录的高度做估算
    private var coverHeight by mutableStateOf(1000f)

    val showTitleInBar by derivedStateOf { listState.firstVisibleItemIndex > 0 }

    val backgroundTranslationY by derivedStateOf {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val coverItem = visibleItems.firstOrNull { it.key == "cover" }
        if (coverItem != null) {
            coverHeight = coverItem.size.toFloat()
            coverItem.offset.toFloat()
        } else {
            val firstIndex = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            val estimatedSubsequentScroll = (firstIndex - 1) * 250f + firstOffset
            -(coverHeight + estimatedSubsequentScroll)
        }
    }
}

@Composable
fun rememberFullPlayerScrollMetrics(listState: LazyListState): FullPlayerScrollMetrics =
    remember(listState) { FullPlayerScrollMetrics(listState) }

const val FullPlayerFillGapKeyPrefix = "fill_gap_"

// 信息卡片全部隐藏时，把首屏剩余高度均分给各留白项，让播放区恰好撑满一屏，其后的项留在屏外滑动可见；
// 只统计 contentKeys 的实测高度，留白项自身变化不会反过来影响计算
@Composable
fun rememberFullPlayerFillGap(
    listState: LazyListState,
    enabled: Boolean,
    gapCount: Int,
    contentKeys: List<String>,
    bottomReserve: Dp
): Dp {
    val density = LocalDensity.current
    val bottomReservePx = with(density) { bottomReserve.roundToPx() }
    // 滚动中项可能移出视野失去实测尺寸，沿用最后一次记录的高度，避免留白随滚动跳变
    val cachedSizes = remember(listState) { HashMap<String, Int>() }
    val gapPx by remember(listState, enabled, gapCount, contentKeys, bottomReservePx) {
        derivedStateOf {
            if (!enabled || gapCount <= 0) return@derivedStateOf 0
            val info = listState.layoutInfo
            info.visibleItemsInfo.forEach { item ->
                val key = item.key as? String ?: return@forEach
                if (key in contentKeys) cachedSizes[key] = item.size
            }
            // 尚有项未测量过说明首屏已放不下，无需补白
            if (contentKeys.any { it !in cachedSizes }) return@derivedStateOf 0
            val available = info.viewportSize.height - info.beforeContentPadding - bottomReservePx
            val remaining = available - contentKeys.sumOf { cachedSizes.getValue(it) }
            (remaining / gapCount).coerceAtLeast(0)
        }
    }
    return with(density) { gapPx.toDp() }
}
