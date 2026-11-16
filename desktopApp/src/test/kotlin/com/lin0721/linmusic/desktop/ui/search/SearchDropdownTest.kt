package com.lin0721.linmusic.desktop.ui.search

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchDropdownTest {

    private val song = Track(id = 1, name = "七里香", ar = listOf(Artist(id = 9, name = "周杰伦")))

    private fun entriesOf(rows: List<DropdownRow>) = rows.filterIsInstance<DropdownRow.Item>().map { it.entry }

    @Test
    fun `空输入只列最近搜索并附清空按钮`() {
        val rows = buildRows("", listOf("周杰伦", "w我"), emptyList(), emptyList())
        assertTrue(rows.first() is DropdownRow.Header)
        assertEquals(
            listOf(DropdownEntry.Recent("周杰伦"), DropdownEntry.Recent("w我")),
            entriesOf(rows)
        )
        assertTrue(rows.last() is DropdownRow.ClearHistory)
    }

    @Test
    fun `空输入且没有历史时不出浮层`() {
        assertTrue(buildRows("  ", emptyList(), listOf(song), emptyList()).isEmpty())
    }

    @Test
    fun `有输入时依次是搜索项、歌曲、歌手、专辑、联想词`() {
        val suggestions = listOf(
            SearchSuggestion.Keyword("七里香 周杰伦"),
            SearchSuggestion.AlbumMatch(5, "七里香", "周杰伦"),
            SearchSuggestion.ArtistMatch(9, "周杰伦", "http://a")
        )
        val entries = entriesOf(buildRows(" 七里香 ", listOf("旧词"), listOf(song), suggestions))
        assertEquals(
            listOf(
                DropdownEntry.Literal("七里香"),
                DropdownEntry.SongHit(song),
                DropdownEntry.ArtistHit(9, "周杰伦", "http://a"),
                DropdownEntry.AlbumHit(5, "七里香", "周杰伦"),
                DropdownEntry.Keyword("七里香 周杰伦")
            ),
            entries
        )
    }

    @Test
    fun `条目序号按可选项连续编号`() {
        val rows = buildRows("a", emptyList(), listOf(song), listOf(SearchSuggestion.Keyword("ab")))
        assertEquals(listOf(0, 1, 2), rows.filterIsInstance<DropdownRow.Item>().map { it.index })
    }

    @Test
    fun `输入网易云链接时链接直达排在最前面`() {
        val link = "https://music.163.com/song?id=2023954033"
        val hit = com.lin0721.linmusic.feature.search.ui.SearchLinkHit(
            query = link,
            target = com.lin0721.linmusic.core.deeplink.SongLinkTarget.Song(2023954033L),
            previewState = com.lin0721.linmusic.core.deeplink.LinkPreviewState.Loading
        )
        val rows = buildRows(link, emptyList(), emptyList(), emptyList(), linkHit = hit)
        assertEquals(
            listOf(DropdownEntry.LinkHit(hit), DropdownEntry.Literal(link)),
            entriesOf(rows)
        )
        assertEquals(listOf(0, 1), rows.filterIsInstance<DropdownRow.Item>().map { it.index })
    }

    private fun shown(count: Int, onActivate: (Int) -> Unit = {}, onRelease: () -> Unit = {}) =
        SearchDropdownState().apply {
            focused = true
            visible = true
            bind(count, onActivate, onRelease)
        }

    @Test
    fun `上下键在条目间循环`() {
        val state = shown(3)
        assertTrue(state.move(1))
        assertEquals(0, state.selected)
        state.move(1)
        state.move(1)
        assertEquals(2, state.selected)
        state.move(1)
        assertEquals(0, state.selected)
        state.move(-1)
        assertEquals(2, state.selected)
    }

    @Test
    fun `未选中时按上键从最后一项开始`() {
        val state = shown(4)
        state.move(-1)
        assertEquals(3, state.selected)
    }

    @Test
    fun `浮层未显示或没有条目时不消费按键`() {
        assertFalse(SearchDropdownState().move(1))
        assertFalse(shown(0).move(1))
    }

    @Test
    fun `回车只在有选中项时触发条目`() {
        var activated = -1
        val state = shown(3, onActivate = { activated = it })
        assertFalse(state.activateSelected())
        state.move(1)
        state.move(1)
        assertTrue(state.activateSelected())
        assertEquals(1, activated)
    }

    @Test
    fun `条目减少后越界的选中项被清除`() {
        val state = shown(5)
        state.selected = 4
        state.bind(2, {}, {})
        assertEquals(SearchDropdownState.NO_SELECTION, state.selected)
    }

    @Test
    fun `点击落在搜索框或面板之外才收起`() {
        var released = 0
        val state = shown(2, onRelease = { released++ })
        state.anchor = Rect(100f, 10f, 500f, 50f)
        state.panelBounds = Rect(100f, 56f, 500f, 300f)

        state.onWindowPress(Offset(200f, 30f))
        state.onWindowPress(Offset(200f, 100f))
        assertEquals(0, released)

        state.onWindowPress(Offset(700f, 400f))
        assertEquals(1, released)
    }

    @Test
    fun `未聚焦时窗口点击不处理`() {
        var released = 0
        val state = shown(2, onRelease = { released++ })
        state.focused = false
        state.onWindowPress(Offset(700f, 400f))
        assertEquals(0, released)
    }

    @Test
    fun `鼠标在浮层上时输入框失焦不收起`() {
        val state = shown(2)
        state.pointerInPanel = true
        state.onFocusChange(false)
        assertTrue(state.focused)
    }

    @Test
    fun `鼠标不在浮层上时失焦会收起并清除选中`() {
        val state = shown(2)
        state.selected = 1
        state.onFocusChange(false)
        assertFalse(state.focused)
        assertEquals(SearchDropdownState.NO_SELECTION, state.selected)
    }

    @Test
    fun `关闭会重置鼠标在浮层上的状态`() {
        val state = shown(2)
        state.pointerInPanel = true
        state.close()
        assertFalse(state.focused)
        assertFalse(state.pointerInPanel)
    }

    @Test
    fun `失焦后点击外部也能收起`() {
        val state = shown(2)
        state.anchor = Rect(100f, 10f, 500f, 50f)
        state.panelBounds = Rect(100f, 56f, 500f, 300f)
        state.pointerInPanel = true
        state.onFocusChange(false)
        state.pointerInPanel = false
        state.onWindowPress(Offset(700f, 400f))
        assertFalse(state.focused)
    }
}
