package com.lin0721.linmusic.desktop.ui.track

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.trackTextInputFocus
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlin.math.max

private const val PLAYLIST_NAME_MAX_LENGTH = 40
private const val DIVIDER_ALPHA = 0.5f
private const val SHADOW_FADE_MS = 150
private const val SHADOW_ALPHA = 0.4f
private val ShadowHeight = 6.dp
private val PanelWidth = 320.dp
private val PanelShape = RoundedCornerShape(8.dp)
private val ListMaxHeight = 320.dp
private val PopupGap = 8.dp
private val WindowMargin = 12.dp

// 锚定在底栏加号按钮上方的“加入歌单”悬浮卡片
@Composable
fun CollectToPlaylistPopup(
    songId: Long,
    state: PlaylistCollectState,
    onSave: (List<PlaylistCollectItem>) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val provider = remember(density) {
        with(density) { CollectPopupPositionProvider(PopupGap.roundToPx(), WindowMargin.roundToPx()) }
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        CollectPanel(songId, state, onSave, onCreate, onDismiss, Modifier.themedShadow(16.dp, PanelShape))
    }
}

// 列表、菜单等无锚点处使用的居中弹窗，内容与底栏弹窗是同一块面板
@Composable
internal fun CollectToPlaylistDialog(
    songId: Long,
    state: PlaylistCollectState,
    onSave: (List<PlaylistCollectItem>) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        CollectPanel(songId, state, onSave, onCreate, onDismiss)
    }
}

// 已加入的歌单置顶，其余在下；有勾选改动才出现保存按钮
@Composable
private fun CollectPanel(
    songId: Long,
    state: PlaylistCollectState,
    onSave: (List<PlaylistCollectItem>) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 用户勾选单独记录，后台回填包含状态时不覆盖用户操作
    val overrides = remember(songId) { mutableStateMapOf<Long, Boolean>() }
    var query by remember(songId) { mutableStateOf("") }
    var creating by remember(songId) { mutableStateOf(false) }
    var newName by remember(songId) { mutableStateOf("") }
    val ready = state.songId == songId && !state.isLoading
    val listState = rememberLazyListState()
    val topShadowAlpha by animateFloatAsState(if (listState.canScrollBackward) 1f else 0f, uiTween(SHADOW_FADE_MS), label = "listTopShadow")

    val keyword = query.trim()
    val searching = keyword.isNotEmpty()
    val visible = if (searching) {
        state.collectItems.filter { it.playlistName.contains(keyword, ignoreCase = true) }
    } else {
        state.collectItems
    }
    // 搜索时不分组；分组依据是进入面板时的包含状态，勾选过程中行不会跳段
    val joined = if (searching) emptyList() else visible.filter { it.isContains }
    val others = if (searching) visible else visible.filterNot { it.isContains }
    val changeCount = state.collectItems.count { item -> overrides[item.playlistId]?.let { it != item.isContains } == true }

    Column(
        modifier.width(PanelWidth).clip(PanelShape).background(DesktopColors.CardSurface).padding(12.dp)
    ) {
        Text(
            "加入歌单",
            color = DesktopColors.TextGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        CollectInput(
            value = query,
            onValueChange = { query = it },
            placeholder = "查找歌单",
            leading = { Icon(Icons.Rounded.Search, null, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp)) },
            trailing = if (query.isNotEmpty()) {
                {
                    Icon(
                        Icons.Rounded.Close,
                        "清除搜索",
                        tint = DesktopColors.TextGray,
                        modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand).clickable { query = "" }
                    )
                }
            } else {
                null
            }
        )
        Spacer(Modifier.height(8.dp))
        if (creating) {
            CreateRow(newName, { newName = it.take(PLAYLIST_NAME_MAX_LENGTH) }, onSubmit = {
                val trimmed = newName.trim()
                if (trimmed.isNotEmpty()) onCreate(trimmed)
            })
        } else {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).pointerHoverIcon(PointerIcon.Hand)
                    .clickable { creating = true }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(22.dp))
                Text(
                    "新建歌单",
                    color = DesktopColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), color = DesktopColors.SurfaceLight.copy(alpha = DIVIDER_ALPHA))

        when {
            !ready -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(28.dp))
            }
            state.collectItems.isEmpty() -> EmptyHint("还没有自建歌单")
            visible.isEmpty() -> EmptyHint("未找到匹配的歌单")
            else -> HoverScrollbarBox(listState, Modifier.topScrollShadow(DesktopColors.ShadowSpot) { topShadowAlpha }) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = ListMaxHeight),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                ) {
                    if (joined.isNotEmpty()) {
                        item(key = "header_joined") { SectionLabel("已加入 · ${joined.size}") }
                        items(joined, key = { it.playlistId }) { item ->
                            val checked = overrides[item.playlistId] ?: item.isContains
                            CollectRow(item, checked) { overrides[item.playlistId] = !checked }
                        }
                    }
                    if (others.isNotEmpty()) {
                        if (joined.isNotEmpty()) item(key = "header_others") { SectionLabel("其他歌单") }
                        items(others, key = { it.playlistId }) { item ->
                            val checked = overrides[item.playlistId] ?: item.isContains
                            CollectRow(item, checked) { overrides[item.playlistId] = !checked }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("取消", color = DesktopColors.TextGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (changeCount > 0) {
                Button(
                    onClick = {
                        onSave(state.collectItems.map { it.copy(isContains = overrides[it.playlistId] ?: it.isContains) })
                    },
                    enabled = ready,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(start = 4.dp).height(32.dp)
                ) {
                    Text("保存 $changeCount 项更改", color = DesktopColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 列表向下滚动后，在顶部分隔线下方叠一道渐隐阴影，提示上方还有被滚走的内容
private fun Modifier.topScrollShadow(shadow: Color, alpha: () -> Float): Modifier = drawWithContent {
    drawContent()
    val current = alpha()
    if (current > 0f) {
        val height = ShadowHeight.toPx()
        drawRect(
            Brush.verticalGradient(listOf(shadow.copy(alpha = SHADOW_ALPHA * current), shadow.copy(alpha = 0f)), endY = height),
            size = Size(size.width, height)
        )
    }
}

@Composable
private fun CreateRow(name: String, onNameChange: (String) -> Unit, onSubmit: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CollectInput(
            value = name,
            onValueChange = onNameChange,
            placeholder = "歌单名称",
            leading = { Icon(Icons.Rounded.Add, null, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.weight(1f),
            focusRequester = focusRequester,
            onSubmit = onSubmit
        )
        TextButton(onClick = onSubmit, enabled = name.isNotBlank(), modifier = Modifier.padding(start = 4.dp)) {
            Text(
                "创建",
                color = if (name.isNotBlank()) DesktopColors.TextPrimary else DesktopColors.TextGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// 面板内的输入框：浅底圆角，占位文字与输入共用一个基线
@Composable
private fun CollectInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onSubmit: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(6.dp))
            .background(DesktopColors.Surface).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, softWrap = false)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = DesktopColors.TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(DesktopColors.TextPrimary),
                modifier = Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .trackTextInputFocus()
                    .onPreviewKeyEvent { event ->
                        if (onSubmit != null && event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    }
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = DesktopColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
        Text(text, color = DesktopColors.TextGray, fontSize = 13.sp)
    }
}

@Composable
private fun CollectRow(item: PlaylistCollectItem, checked: Boolean, onToggle: () -> Unit) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (hovered) DesktopColors.Hover else Color.Transparent)
            .hoverable(hoverSource).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(item.coverUrl, 40.dp, shape = RoundedCornerShape(4.dp))
        Text(
            item.playlistName,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        Icon(
            if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            if (checked) "已加入" else "未加入",
            tint = if (checked) DesktopColors.Accent else DesktopColors.TextGray,
            modifier = Modifier.size(22.dp)
        )
    }
}

// 悬浮在锚点正上方，空间不足时改到下方，并限制在窗口边距内
private class CollectPopupPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(marginPx, max(marginPx, windowSize.width - popupContentSize.width - marginPx))
        val y = if (anchorBounds.top - popupContentSize.height - gapPx >= marginPx) {
            anchorBounds.top - popupContentSize.height - gapPx
        } else {
            anchorBounds.bottom + gapPx
        }.coerceIn(marginPx, max(marginPx, windowSize.height - popupContentSize.height - marginPx))
        return IntOffset(x, y)
    }
}
