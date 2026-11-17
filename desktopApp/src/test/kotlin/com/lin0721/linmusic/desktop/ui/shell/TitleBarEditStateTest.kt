package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.lin0721.linmusic.desktop.data.TitleBarButton
import com.lin0721.linmusic.desktop.data.TitleBarLayout
import com.lin0721.linmusic.desktop.data.TitleBarSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleBarEditStateTest {

    private val slotBounds = mapOf(
        TitleBarSlot.CENTER_START to Rect(100f, 8f, 148f, 56f),
        TitleBarSlot.CENTER_END to Rect(460f, 8f, 508f, 56f),
        TitleBarSlot.TRAILING to Rect(800f, 16f, 870f, 48f)
    )

    private val buttonBounds = mapOf(
        TitleBarButton.HOME to Rect(100f, 8f, 148f, 56f),
        TitleBarButton.RECOGNITION to Rect(460f, 8f, 508f, 56f),
        TitleBarButton.MESSAGES to Rect(800f, 16f, 832f, 48f),
        TitleBarButton.DOWNLOADS to Rect(838f, 16f, 870f, 48f)
    )

    @Test
    fun `横向命中测试按槽中点与按钮中心点计算目标槽和下标`() {
        val layout = TitleBarLayout.DEFAULT

        // 位于搜索框中线左侧 -> 中左槽（HOME 中心点为 124：左侧下标 0，右侧下标 1）
        assertEquals(
            TitleBarSlot.CENTER_START to 0,
            resolveTitleBarDropTarget(layout, TitleBarButton.DOWNLOADS, 110f, slotBounds, buttonBounds)
        )
        assertEquals(
            TitleBarSlot.CENTER_START to 1,
            resolveTitleBarDropTarget(layout, TitleBarButton.DOWNLOADS, 200f, slotBounds, buttonBounds)
        )

        // 位于搜索框中线右侧、中右与右侧槽中点左侧 -> 中右槽（超过 RECOGNITION 中心点 484 后插入下标 1）
        assertEquals(
            TitleBarSlot.CENTER_END to 0,
            resolveTitleBarDropTarget(layout, TitleBarButton.DOWNLOADS, 400f, slotBounds, buttonBounds)
        )
        assertEquals(
            TitleBarSlot.CENTER_END to 1,
            resolveTitleBarDropTarget(layout, TitleBarButton.DOWNLOADS, 500f, slotBounds, buttonBounds)
        )

        // 位于右侧槽，在 MESSAGES(816) 左侧 -> 下标 0
        assertEquals(
            TitleBarSlot.TRAILING to 0,
            resolveTitleBarDropTarget(layout, TitleBarButton.DOWNLOADS, 810f, slotBounds, buttonBounds)
        )
    }

    @Test
    fun `拖拽状态跨槽移动并在松手时提交`() {
        val editState = TitleBarEditState(TitleBarLayout.DEFAULT)
        editState.slotBounds.putAll(slotBounds)
        editState.buttonBounds.putAll(buttonBounds)

        assertEquals(TitleBarButton.DOWNLOADS, editState.buttonAt(Offset(850f, 30f)))
        assertNull(editState.buttonAt(Offset(300f, 30f)))

        assertTrue(editState.startDrag(TitleBarButton.DOWNLOADS))
        assertTrue(editState.isDragging)

        // 从 854 向左拖到 400（进入 CENTER_END 第 0 位）
        editState.dragBy(Offset(-454f, 0f))
        assertEquals(
            listOf(TitleBarButton.DOWNLOADS, TitleBarButton.RECOGNITION),
            editState.workingLayout.centerEnd
        )
        assertEquals(listOf(TitleBarButton.MESSAGES), editState.workingLayout.trailing)

        var committed: TitleBarLayout? = null
        editState.endDrag { committed = it }
        assertFalse(editState.isDragging)
        assertEquals(
            TitleBarLayout(
                centerStart = listOf(TitleBarButton.HOME),
                centerEnd = listOf(TitleBarButton.DOWNLOADS, TitleBarButton.RECOGNITION),
                trailing = listOf(TitleBarButton.MESSAGES)
            ),
            committed
        )
    }

    @Test
    fun `cancelDrag 恢复到已提交布局`() {
        val editState = TitleBarEditState(TitleBarLayout.DEFAULT)
        editState.slotBounds.putAll(slotBounds)
        editState.buttonBounds.putAll(buttonBounds)

        editState.startDrag(TitleBarButton.HOME)
        editState.dragBy(Offset(700f, 0f))
        assertEquals(emptyList<TitleBarButton>(), editState.workingLayout.centerStart)

        editState.cancelDrag()
        assertFalse(editState.isDragging)
        assertEquals(TitleBarLayout.DEFAULT, editState.workingLayout)
    }
}
