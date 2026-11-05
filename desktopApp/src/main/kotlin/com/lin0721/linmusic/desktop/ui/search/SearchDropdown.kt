package com.lin0721.linmusic.desktop.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import kotlin.math.roundToInt

private val PanelMaxHeight = 460.dp
private val PanelGap = 6.dp
private const val SELECTED_ALPHA = 0.1f
private const val HOVER_ALPHA = 0.06f

internal sealed interface DropdownEntry {
    data class Recent(val keyword: String) : DropdownEntry
    data class Literal(val query: String) : DropdownEntry
    data class SongHit(val track: Track) : DropdownEntry
    data class ArtistHit(val id: Long, val name: String, val avatarUrl: String) : DropdownEntry
    data class AlbumHit(val id: Long, val name: String, val artistName: String) : DropdownEntry
    data class Keyword(val text: String) : DropdownEntry
}

// 搜索框下拉浮层的显隐与键盘选中状态，标题栏与浮层各持同一份
@Stable
class SearchDropdownState {
    var focused by mutableStateOf(false)
    // 搜索框与浮层面板在窗口中的范围，用来判断点击是否落在外部
    var anchor by mutableStateOf(Rect.Zero)
    internal var panelBounds by mutableStateOf(Rect.Zero)
    var selected by mutableIntStateOf(NO_SELECTION)
    private var entryCount by mutableIntStateOf(0)
    internal var visible by mutableStateOf(false)
    private var activate: ((Int) -> Unit)? = null
    private var releaseFocus: (() -> Unit)? = null

    internal fun bind(count: Int, onActivate: (Int) -> Unit, onReleaseFocus: () -> Unit) {
        entryCount = count
        activate = onActivate
        releaseFocus = onReleaseFocus
        if (selected >= count) selected = NO_SELECTION
    }

    fun move(delta: Int): Boolean {
        if (!visible || entryCount == 0) return false
        selected = when {
            selected == NO_SELECTION -> if (delta > 0) 0 else entryCount - 1
            else -> (selected + delta).mod(entryCount)
        }
        return true
    }

    // 回车时有选中项就触发它，返回 false 表示交给默认的提交搜索
    fun activateSelected(): Boolean {
        val index = selected
        if (!visible || index == NO_SELECTION || index >= entryCount) return false
        activate?.invoke(index)
        return true
    }

    // 鼠标是否在浮层上；按下浮层条目时输入框会先失焦，此时浮层必须保留到点击完成
    internal var pointerInPanel by mutableStateOf(false)

    // 输入框焦点变化。鼠标在浮层上时的失焦视为点击浮层，不收起
    fun onFocusChange(hasFocus: Boolean) {
        if (!hasFocus && pointerInPanel) return
        focused = hasFocus
        if (!hasFocus) selected = NO_SELECTION
    }

    fun close() {
        pointerInPanel = false
        focused = false
        selected = NO_SELECTION
    }

    fun onWindowPress(position: Offset) {
        if (!focused) return
        if (anchor.contains(position) || (visible && panelBounds.contains(position))) return
        close()
        releaseFocus?.invoke()
    }

    companion object {
        const val NO_SELECTION = -1
    }
}

internal sealed interface DropdownRow {
    data class Header(val text: String) : DropdownRow
    data class Item(val entry: DropdownEntry, val index: Int) : DropdownRow
    data object ClearHistory : DropdownRow
}

// 空输入列最近搜索；有输入时依次是“搜索xxx”、歌曲/歌手/专辑命中卡片、联想词
internal fun buildRows(query: String, history: List<String>, hits: List<Track>, suggestions: List<SearchSuggestion>): List<DropdownRow> {
    val rows = mutableListOf<DropdownRow>()
    var index = 0
    fun item(entry: DropdownEntry) = rows.add(DropdownRow.Item(entry, index++))
    if (query.isBlank()) {
        if (history.isEmpty()) return rows
        rows.add(DropdownRow.Header("最近的搜索记录"))
        history.forEach { item(DropdownEntry.Recent(it)) }
        rows.add(DropdownRow.ClearHistory)
        return rows
    }
    item(DropdownEntry.Literal(query.trim()))
    hits.forEach { item(DropdownEntry.SongHit(it)) }
    suggestions.filterIsInstance<SearchSuggestion.ArtistMatch>()
        .forEach { item(DropdownEntry.ArtistHit(it.id, it.text, it.avatarUrl)) }
    suggestions.filterIsInstance<SearchSuggestion.AlbumMatch>()
        .forEach { item(DropdownEntry.AlbumHit(it.id, it.text, it.artistName)) }
    suggestions.filterIsInstance<SearchSuggestion.Keyword>().forEach { item(DropdownEntry.Keyword(it.text)) }
    return rows
}

// 窗口根层的覆盖层：不是子窗口，搜索框始终保有焦点，边输入边出联想
@Composable
fun SearchDropdown(
    state: SearchDropdownState,
    viewModel: SearchViewModel,
    onSearch: (String) -> Unit
) {
    val input by viewModel.inputState.collectAsState()
    val history by viewModel.history.collectAsState()
    val navigator = LocalDesktopNavigator.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    val rows = remember(input, history) {
        buildRows(input.query, history, input.currentSongHits, input.currentSuggestions)
    }
    val entries = remember(rows) { rows.filterIsInstance<DropdownRow.Item>().map { it.entry } }
    val visible = state.focused && rows.isNotEmpty()

    val activate: (DropdownEntry) -> Unit = { entry ->
        when (entry) {
            is DropdownEntry.Recent -> onSearch(entry.keyword)
            is DropdownEntry.Literal -> onSearch(entry.query)
            is DropdownEntry.Keyword -> onSearch(entry.text)
            is DropdownEntry.SongHit -> viewModel.playSongHit(entry.track)
            is DropdownEntry.ArtistHit -> navigator.openArtist(entry.id, entry.name)
            is DropdownEntry.AlbumHit -> navigator.openAlbum(entry.id, entry.name)
        }
        state.close()
        focusManager.clearFocus()
    }
    SideEffect {
        state.visible = visible
        if (!visible) state.pointerInPanel = false
        state.bind(entries.size, { index -> entries.getOrNull(index)?.let(activate) }, { focusManager.clearFocus() })
    }
    if (!visible) return

    val listState = rememberLazyListState()
    val motion = LocalUiMotion.current
    LaunchedEffect(state.selected) {
        val rowIndex = rows.indexOfFirst { it is DropdownRow.Item && it.index == state.selected }
        if (rowIndex >= 0) motion.scrollToItem(listState, rowIndex)
    }

    val anchor = state.anchor
    SearchDropdownPanel(
        rows = rows,
        selected = state.selected,
        listState = listState,
        onActivate = activate,
        onClearHistory = viewModel::clearHistory,
        onRemoveHistory = viewModel::removeHistory,
        onPointerInPanelChange = { state.pointerInPanel = it },
        modifier = Modifier
            .offset { IntOffset(anchor.left.roundToInt(), anchor.bottom.roundToInt() + with(density) { PanelGap.roundToPx() }) }
            .width(with(density) { anchor.width.toDp() })
            .onGloballyPositioned { state.panelBounds = it.boundsInRoot() }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SearchDropdownPanel(
    rows: List<DropdownRow>,
    selected: Int,
    listState: LazyListState,
    onActivate: (DropdownEntry) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onPointerInPanelChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .heightIn(max = PanelMaxHeight)
            // 面板里的可点条目不接收焦点，否则按下瞬间输入框失焦、浮层先消失，点击就丢了
            .focusProperties { canFocus = false }
            .onPointerEvent(PointerEventType.Enter) { onPointerInPanelChange(true) }
            .onPointerEvent(PointerEventType.Exit) { onPointerInPanelChange(false) }
            .themedShadow(12.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(DesktopColors.PopupSurface)
            .floatingOutline(RoundedCornerShape(10.dp))
    ) {
        LazyColumn(state = listState, contentPadding = PaddingValues(8.dp)) {
            itemsIndexed(rows) { _, row ->
                when (row) {
                    is DropdownRow.Header -> Text(
                        row.text,
                        color = DesktopColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                    )
                    DropdownRow.ClearHistory -> OutlinedButton(
                        onClick = onClearHistory,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, DesktopColors.TextGray),
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                    ) {
                        Text("清空最近的搜索记录", color = DesktopColors.TextPrimary, fontSize = 13.sp)
                    }
                    is DropdownRow.Item -> EntryRow(
                        entry = row.entry,
                        selected = selected == row.index,
                        onClick = { onActivate(row.entry) },
                        onRemove = (row.entry as? DropdownEntry.Recent)?.let { recent -> { onRemoveHistory(recent.keyword) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: DropdownEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val background = when {
        selected -> DesktopColors.Overlay.copy(alpha = SELECTED_ALPHA)
        hovered -> DesktopColors.Overlay.copy(alpha = HOVER_ALPHA)
        else -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(background)
            .hoverable(source).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (entry) {
            is DropdownEntry.Recent -> Line(Icons.Rounded.History, entry.keyword)
            is DropdownEntry.Literal -> Line(Icons.Rounded.Search, "搜索“${entry.query}”")
            is DropdownEntry.Keyword -> Line(Icons.Rounded.Search, entry.text)
            is DropdownEntry.SongHit -> Card(
                entry.track.al.picUrl, entry.track.name,
                "歌曲 · ${entry.track.ar.joinToString(" / ") { it.name }}", circle = false
            )
            is DropdownEntry.ArtistHit -> Card(entry.avatarUrl, entry.name, "歌手", circle = true)
            is DropdownEntry.AlbumHit -> Card("", entry.name, listOf("专辑", entry.artistName).filter { it.isNotBlank() }.joinToString(" · "), circle = false)
        }
        if (onRemove != null) {
            HoverReveal(revealed = hovered || selected) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, "删除", tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun RowScope.Line(icon: ImageVector, text: String) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
    }
    Text(
        text,
        color = DesktopColors.TextPrimary,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(start = 12.dp)
    )
}

@Composable
private fun RowScope.Card(coverUrl: String, title: String, subtitle: String, circle: Boolean) {
    Cover(coverUrl, 40.dp, shape = if (circle) CircleShape else RoundedCornerShape(4.dp))
    Column(Modifier.weight(1f).padding(start = 12.dp)) {
        Text(title, color = DesktopColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
