package com.lin0721.linmusic

import androidx.compose.ui.graphics.TransformOrigin
import com.lin0721.linmusic.core.ui.back.BackDismissState
import com.lin0721.linmusic.core.ui.back.BackTransitionGestureState
import com.lin0721.linmusic.core.ui.back.BackTransitionPhase
import com.lin0721.linmusic.core.ui.back.GESTURE_STAGE_END_FRACTION
import com.lin0721.linmusic.core.ui.back.computeBackPageTransform
import com.lin0721.linmusic.core.ui.back.gestureToTransitionFraction
import com.lin0721.linmusic.core.ui.back.computeBackDismissTransform
import com.lin0721.linmusic.core.ui.back.decelerateBackProgress
import com.lin0721.linmusic.core.ui.theme.UiMotion
import com.lin0721.linmusic.feature.playlist.ui.SEARCH_BAR_BACK_RETREAT_FRACTION
import com.lin0721.linmusic.feature.playlist.ui.computeSearchBarBackRevealPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBackGestureStateTest {

    @Test
    fun `共用减速曲线钳位并呈现前快后慢特性`() {
        assertEquals(0f, decelerateBackProgress(-0.5f), 1e-5f)
        assertEquals(0f, decelerateBackProgress(0f), 1e-5f)
        assertEquals(0.75f, decelerateBackProgress(0.5f), 1e-5f)
        assertEquals(1f, decelerateBackProgress(1f), 1e-5f)
        assertEquals(1f, decelerateBackProgress(1.5f), 1e-5f)
    }

    @Test
    fun `播放页返回跟手下移不超过屏幕高度的百分之八`() {
        assertEquals(0f, computePlayerSheetBackOffset(0.5f, 0f), 1e-5f)
        assertEquals(0f, computePlayerSheetBackOffset(0f, 2000f), 1e-5f)
        assertEquals(120f, computePlayerSheetBackOffset(0.5f, 2000f), 1e-3f)
        assertEquals(160f, computePlayerSheetBackOffset(1f, 2000f), 1e-3f)
        assertEquals(160f, computePlayerSheetBackOffset(1.5f, 2000f), 1e-3f)
    }

    @Test
    fun `平板面板返回跟手值从 1 减速回落到 0_85`() {
        assertEquals(1f, computePanelBackValue(-0.1f), 1e-5f)
        assertEquals(1f, computePanelBackValue(0f), 1e-5f)
        assertEquals(0.8875f, computePanelBackValue(0.5f), 1e-5f)
        assertEquals(PANEL_BACK_MIN_VALUE, computePanelBackValue(1f), 1e-5f)
        assertEquals(PANEL_BACK_MIN_VALUE, computePanelBackValue(2f), 1e-5f)
    }

    @Test
    fun `侧边栏返回跟手偏移相对宽度最多回缩百分之十二`() {
        assertEquals(0f, computeSidebarBackOffset(0.5f, 0f), 1e-5f)
        assertEquals(1000f, computeSidebarBackOffset(0f, 1000f), 1e-5f)
        assertEquals(910f, computeSidebarBackOffset(0.5f, 1000f), 1e-3f)
        assertEquals(880f, computeSidebarBackOffset(1f, 1000f), 1e-3f)
        assertEquals(880f, computeSidebarBackOffset(1.5f, 1000f), 1e-3f)
    }

    @Test
    fun `弹层跟手收缩在零分时恒等且满进度按指定原点缩放到 0_92`() {
        val identity = computeBackDismissTransform(0f, TransformOrigin(0.5f, 1f))
        assertEquals(1f, identity.scale, 1e-5f)
        assertEquals(0f, identity.translationXFraction, 1e-5f)
        assertEquals(0f, identity.translationYFraction, 1e-5f)

        val bottomFull = computeBackDismissTransform(1f, TransformOrigin(0.5f, 1f))
        assertEquals(0.92f, bottomFull.scale, 1e-5f)
        assertEquals(0f, bottomFull.translationXFraction, 1e-5f)
        assertEquals(0.03f, bottomFull.translationYFraction, 1e-5f)

        val leftMid = computeBackDismissTransform(0.5f, TransformOrigin(0f, 0.5f))
        assertEquals(0.96f, leftMid.scale, 1e-5f)
        assertEquals(-0.015f, leftMid.translationXFraction, 1e-5f)
        assertEquals(0f, leftMid.translationYFraction, 1e-5f)
    }

    @Test
    fun `弹层收缩状态提交时保留进度以衔接退场动画并在复位时归零`() {
        val state = BackDismissState(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            motion = UiMotion()
        )
        assertEquals(0f, state.fraction, 1e-5f)

        state.onProgress(0.5f)
        assertEquals(0.75f, state.fraction, 1e-5f)

        state.onCommit()
        assertEquals(0.75f, state.fraction, 1e-5f)

        state.reset()
        assertEquals(0f, state.fraction, 1e-5f)
    }

    @Test
    fun `搜索栏展开态返回跟手按减速曲线最多回缩百分之三十`() {
        assertEquals(0f, computeSearchBarBackRevealPx(0f, 0.5f), 1e-5f)
        assertEquals(200f, computeSearchBarBackRevealPx(200f, -0.2f), 1e-5f)
        assertEquals(200f, computeSearchBarBackRevealPx(200f, 0f), 1e-5f)
        // progress = 0.5 -> decelerate = 0.75 -> 200 * (1 - 0.3 * 0.75) = 155
        assertEquals(155f, computeSearchBarBackRevealPx(200f, 0.5f), 1e-3f)
        val fullExpected = 200f * (1f - SEARCH_BAR_BACK_RETREAT_FRACTION)
        assertEquals(fullExpected, computeSearchBarBackRevealPx(200f, 1f), 1e-3f)
        assertEquals(fullExpected, computeSearchBarBackRevealPx(200f, 1.5f), 1e-3f)
    }

    @Test
    fun `手势进度经过减速曲线后映射到过渡前 0_4 区间`() {
        assertEquals(0f, gestureToTransitionFraction(-0.2f), 1e-5f)
        assertEquals(0f, gestureToTransitionFraction(0f), 1e-5f)
        // 减速曲线在 0.5 处应大于线性中点 0.2
        val mid = gestureToTransitionFraction(0.5f)
        assertTrue(mid > 0.2f && mid < GESTURE_STAGE_END_FRACTION)
        assertEquals(0.3f, mid, 1e-5f)
        assertEquals(GESTURE_STAGE_END_FRACTION, gestureToTransitionFraction(1f), 1e-5f)
        assertEquals(GESTURE_STAGE_END_FRACTION, gestureToTransitionFraction(1.5f), 1e-5f)
    }

    @Test
    fun `手势阶段与提交阶段的页面变换参数连续衔接`() {
        val start = computeBackPageTransform(0f, fromLeftEdge = true)
        assertEquals(1f, start.scale, 1e-5f)
        assertEquals(0f, start.cornerDp, 1e-5f)
        assertEquals(0f, start.translationXFraction, 1e-5f)
        assertEquals(1f, start.alpha, 1e-5f)

        val handoff = computeBackPageTransform(GESTURE_STAGE_END_FRACTION, fromLeftEdge = true)
        assertEquals(0.9f, handoff.scale, 1e-5f)
        assertEquals(28f, handoff.cornerDp, 1e-5f)
        assertEquals(0.05f, handoff.translationXFraction, 1e-5f)
        assertEquals(1f, handoff.alpha, 1e-5f)

        val end = computeBackPageTransform(1f, fromLeftEdge = true)
        assertEquals(0.85f, end.scale, 1e-5f)
        assertEquals(28f, end.cornerDp, 1e-5f)
        assertEquals(0.05f, end.translationXFraction, 1e-5f)
        assertEquals(0f, end.alpha, 1e-5f)
    }

    @Test
    fun `从右侧边缘返回时水平平移朝左即负方向`() {
        val rightEdge = computeBackPageTransform(GESTURE_STAGE_END_FRACTION, fromLeftEdge = false)
        assertEquals(-0.05f, rightEdge.translationXFraction, 1e-5f)
    }

    @Test
    fun `可空目标状态机在目标为 null 时也能区分提交与取消`() {
        val state = BackTransitionGestureState<String?>()
        assertFalse(state.hasSegment)
        assertFalse(state.isPredictiveSegment("LYRICS", null))

        state.begin(origin = "LYRICS", target = null, fromLeftEdge = true, progress = 0.4f)
        assertTrue(state.hasSegment)
        assertTrue(state.isPredictiveSegment("LYRICS", null))
        assertFalse(state.isPredictiveSegment("PLAYBACK", null))

        state.commit()
        state.clearIfCommit("PLAYBACK")
        assertEquals(BackTransitionPhase.Committing, state.phase)
        state.clearIfCommit(null)
        assertEquals(BackTransitionPhase.Idle, state.phase)
        assertFalse(state.hasSegment)

        state.begin(origin = "LYRICS", target = null, fromLeftEdge = false, progress = 0.3f)
        state.cancel()
        assertTrue(state.isPredictiveSegment("LYRICS", null))
        assertTrue(state.isPredictiveSegment(null, "LYRICS"))
        state.clearIfCancel(null)
        assertEquals(BackTransitionPhase.Canceling, state.phase)
        state.clearIfCancel("LYRICS")
        assertEquals(BackTransitionPhase.Idle, state.phase)
        assertFalse(state.hasSegment)
    }

    @Test
    fun `手势状态机正确区分提交、取消、放弃与连续返回 zIndex`() {
        val state = PageBackGestureState()
        val a = NavEntry(1L, Screen.Artist(1L))
        val b = NavEntry(2L, Screen.Home)
        val c = NavEntry(3L, Screen.Library)

        assertEquals(BackTransitionPhase.Idle, state.phase)
        assertFalse(state.isPredictiveSegment(a, b))

        // 开始手势并更新进度
        state.begin(origin = a, target = b, fromLeftEdge = true, progress = 0.2f)
        assertEquals(BackTransitionPhase.Seeking, state.phase)
        assertEquals(-1f, state.targetZIndex(), 1e-5f)
        assertTrue(state.isPredictiveSegment(a, b))
        assertFalse(state.isPredictiveSegment(b, a))

        state.update(progress = 0.6f, fromLeftEdge = true)
        assertEquals(0.6f, state.progress, 1e-5f)

        // 取消回弹期间原线段与落定时翻转的线段都算预见式过渡
        state.cancel()
        assertEquals(BackTransitionPhase.Canceling, state.phase)
        assertTrue(state.isPredictiveSegment(a, b))
        assertTrue(state.isPredictiveSegment(b, a))
        assertFalse(state.isPredictiveSegment(a, c))

        // 条件清理不匹配时不误清，匹配时清回 Idle
        state.clearIfCommit(b)
        assertEquals(BackTransitionPhase.Canceling, state.phase)
        state.clearIfCancel(a)
        assertEquals(BackTransitionPhase.Idle, state.phase)
        assertNull(state.target)

        // 第二次手势 zIndex 递减且放弃后忽略后续进度
        state.begin(origin = a, target = b, fromLeftEdge = false, progress = 0.3f)
        assertEquals(-2f, state.targetZIndex(), 1e-5f)
        state.abandon()
        state.update(progress = 0.9f, fromLeftEdge = false)
        assertEquals(BackTransitionPhase.Idle, state.phase)
        assertEquals(0f, state.progress, 1e-5f)

        // 提交分支仅匹配对应目标
        state.begin(origin = a, target = b, fromLeftEdge = true, progress = 0.8f)
        state.commit()
        assertEquals(BackTransitionPhase.Committing, state.phase)
        assertTrue(state.isPredictiveSegment(a, b))
        assertFalse(state.isPredictiveSegment(a, c))
    }
}
