package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

// 首屏必须完整露出的最后一个条目，key 与 fullPlayerPlaybackSection 里的快捷操作行一致
private const val FIT_ANCHOR_KEY = "actions"
private val CoverMinEdge = 160.dp

// 沿用上次校正结果起步，避免手机播放页每次打开时封面再缩一下
private var lastSettledInsetPx = 0f

// 布局停止变化这么久才校正一次：单行歌词切换、条目入场都带尺寸动画，动画中途的偏移不可信
private const val SETTLE_DELAY_MS = 250L

// 收缩时在操作行下方额外留出的余量，吸收单行歌词等条目的高度起伏
private val FitReserve = 12.dp

// 余量超过 FitReserve + FitHysteresis 才放大回去，两个方向之间留回差，避免在临界点来回伸缩
private val FitHysteresis = 24.dp

// 锚点条目还没被排进视口时，每轮在越界量之外多收的量，让它尽快进入可见区
private val AnchorProbeStep = 48.dp

// 按快捷操作行底部相对视口底边（再让出 bottomReservePx）的越界量反推封面额外内缩（内缩 1px，封面高度少 2px）。
// 只在列表停在顶部、且布局已稳定时校正，滚动中和动画中保持当前值
@Composable
fun rememberCoverFitInsetPx(
    listState: LazyListState,
    enabled: Boolean,
    bottomReservePx: Float = 0f
): Float {
    var insetPx by remember { mutableFloatStateOf(lastSettledInsetPx) }
    if (!enabled) return 0f

    val density = LocalDensity.current
    LaunchedEffect(listState, density, bottomReservePx) {
        val minEdgePx = with(density) { CoverMinEdge.toPx() }
        val defaultPaddingPx = with(density) { MelodiaSpacing.lg.toPx() }
        val reservePx = with(density) { FitReserve.toPx() }
        val hysteresisPx = with(density) { FitHysteresis.toPx() }
        val probeStepPx = with(density) { AnchorProbeStep.toPx() }
        snapshotFlow { listState.layoutInfo }.collectLatest {
            delay(SETTLE_DELAY_MS)
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) return@collectLatest
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return@collectLatest

            val anchor = items.firstOrNull { it.key == FIT_ANCHOR_KEY }
            val fitEndPx = info.viewportEndOffset - bottomReservePx.coerceAtLeast(0f)
            // 正值为需要多收的封面高度，负值为可以放回的高度
            val heightDeltaPx = if (anchor != null) {
                val overflowPx = anchor.offset + anchor.size - fitEndPx
                when {
                    overflowPx > 0f -> overflowPx + reservePx
                    -overflowPx > reservePx + hysteresisPx -> overflowPx + reservePx
                    else -> 0f
                }
            } else {
                val last = items.last()
                (last.offset + last.size - fitEndPx).coerceAtLeast(0f) + probeStepPx
            }
            if (heightDeltaPx == 0f) return@collectLatest

            val maxInsetPx = ((info.viewportSize.width - defaultPaddingPx * 2 - minEdgePx) / 2f).coerceAtLeast(0f)
            val next = (insetPx + heightDeltaPx / 2f).coerceIn(0f, maxInsetPx)
            if (next != insetPx) {
                insetPx = next
                lastSettledInsetPx = next
            }
        }
    }
    return insetPx
}
