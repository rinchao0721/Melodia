package com.lin0721.linmusic.desktop.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StylePortrait
import com.lin0721.linmusic.feature.music.domain.StylePreference
import com.lin0721.linmusic.feature.music.ui.MusicBrowseData
import com.lin0721.linmusic.feature.music.ui.MusicUiState

private val EdgePadding = 24.dp
private val TileGap = 16.dp
private val PreferenceTileMinWidth = 200.dp
private val PreferenceTileHeight = 120.dp
private val StyleTileMinWidth = 150.dp
private val StyleTileHeight = 76.dp
private val TiltedCoverSize = 84.dp

// 服务端没给配色的曲风统一退回中性灰
private val FallbackStyleColor = Color(0xFF4A4A4A)

// 「音乐」页：曲风浏览，点色块进入曲风详情；胶囊本身由 HomePage 固定在顶部
@Composable
fun MusicTab(
    uiState: MusicUiState,
    listState: LazyListState,
    onStyleClick: (id: Long, name: String) -> Unit,
    onRetry: () -> Unit
) {
    when (uiState) {
        MusicUiState.Loading -> HomeTabLoading()
        is MusicUiState.Error -> HomeTabError(uiState.message, onRetry)
        is MusicUiState.Success -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentWidth = maxWidth - EdgePadding * 2
            MusicBrowseList(
                data = uiState.data,
                listState = listState,
                preferenceColumns = columnCount(contentWidth, PreferenceTileMinWidth),
                styleColumns = columnCount(contentWidth, StyleTileMinWidth),
                onStyleClick = onStyleClick
            )
        }
    }
}

@Composable
private fun MusicBrowseList(
    data: MusicBrowseData,
    listState: LazyListState,
    preferenceColumns: Int,
    styleColumns: Int,
    onStyleClick: (id: Long, name: String) -> Unit
) {
    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(TileGap)
        ) {
            if (data.hasPreference) {
                item(key = "preference_title") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("你的偏好")
                        data.portrait?.let { PortraitSummary(it) }
                    }
                }
                items(data.preferences.chunked(preferenceColumns), key = { row -> "pref_${row.first().id}" }) { row ->
                    TileRow(row, preferenceColumns) { pref ->
                        PreferenceTile(pref, data.preferenceCovers[pref.id]) { onStyleClick(pref.id, pref.name) }
                    }
                }
                item(key = "styles_gap") { Spacer(Modifier.height(8.dp)) }
            }
            item(key = "styles_title") { SectionTitle("全部曲风") }
            items(data.styles.chunked(styleColumns), key = { row -> "style_${row.first().id}" }) { row ->
                TileRow(row, styleColumns) { style ->
                    StyleTile(style) { onStyleClick(style.id, style.name) }
                }
            }
        }
    }
}

// 末行不满时用空位补齐，保证各行色块等宽
@Composable
private fun <T> TileRow(row: List<T>, columns: Int, tile: @Composable (T) -> Unit) {
    Row(Modifier.padding(horizontal = EdgePadding).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        row.forEach { item -> Box(Modifier.weight(1f)) { tile(item) } }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

private fun columnCount(width: Dp, minTileWidth: Dp): Int =
    ((width + TileGap) / (minTileWidth + TileGap)).toInt().coerceAtLeast(2)

// 画像默认只露第一行，点开看全文
@Composable
private fun PortraitSummary(portrait: StylePortrait) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.padding(horizontal = EdgePadding).fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand).clickable { expanded = !expanded }
            .animateContentSize(uiSpring()).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (expanded) portrait.content else portrait.content.lineSequence().first(),
                color = DesktopColors.TextGray,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (expanded && portrait.dataTip.isNotBlank()) {
                Text(
                    portrait.dataTip,
                    color = DesktopColors.TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Icon(
            if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            if (expanded) "收起画像" else "展开画像",
            tint = DesktopColors.TextGray,
            modifier = Modifier.padding(start = 8.dp).size(20.dp)
        )
    }
}

// 偏好色块：右下角斜放曲风封面，封面未到时只显示色块
@Composable
private fun PreferenceTile(pref: StylePreference, coverUrl: String?, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(PreferenceTileHeight).clip(RoundedCornerShape(8.dp))
            .background(pref.colorHex.toStyleColor().liftedForTile()).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
    ) {
        if (coverUrl != null) {
            Cover(
                coverUrl,
                TiltedCoverSize,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 18.dp, y = 6.dp).rotate(25f),
                shape = RoundedCornerShape(4.dp)
            )
        }
        Column(Modifier.padding(16.dp).fillMaxWidth(0.7f)) {
            Text(
                pref.name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${pref.ratio}%",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun StyleTile(style: MusicStyle, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(StyleTileHeight).clip(RoundedCornerShape(8.dp))
            .background(style.colorHex.toStyleColor().liftedForTile()).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            style.name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (style.enName.isNotBlank()) {
            Text(
                style.enName,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 选中为白底、未选中为白色半透明底色的胶囊，曲风二级标签与播客分类共用
@Composable
internal fun OutlineChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 13.sp) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.1f),
            labelColor = DesktopColors.TextPrimary,
            selectedContainerColor = DesktopColors.TextPrimary,
            selectedLabelColor = DesktopColors.Pane
        ),
        border = null
    )
}

// 古典等曲风配色近黑（111111），直接铺在面板上看不出色块边界
private fun Color.liftedForTile(): Color = if (luminance() < 0.015f) lerp(this, Color.White, 0.12f) else this

// 六位 hex 转 Color，脏值退回中性灰
internal fun String?.toStyleColor(): Color {
    val hex = this?.trim()?.removePrefix("#")?.takeIf { it.length == 6 } ?: return FallbackStyleColor
    val rgb = hex.toIntOrNull(16) ?: return FallbackStyleColor
    return Color(0xFF000000.toInt() or rgb)
}
