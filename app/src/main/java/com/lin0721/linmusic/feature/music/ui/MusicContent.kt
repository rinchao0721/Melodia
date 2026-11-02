package com.lin0721.linmusic.feature.music.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaGridColumns
import com.lin0721.linmusic.core.ui.theme.uiSpring
import com.lin0721.linmusic.feature.home.ui.ErrorContent
import com.lin0721.linmusic.feature.home.ui.LoadingIndicator
import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StylePortrait
import com.lin0721.linmusic.feature.music.domain.StylePreference

private val TileGap = 10.dp
// 偏好与全部曲风共用同一比例，随列宽等比缩放
private const val TileAspectRatio = 1.7f

// 「音乐」tab 曲风浏览：顶栏由 HomeScreen 统一渲染，点色块进入曲风详情
@Composable
fun MusicContent(
    uiState: MusicUiState,
    onStyleClick: (id: Long, name: String) -> Unit,
    onRetry: () -> Unit
) {
    val columns = rememberMelodiaGridColumns(compact = 2, expandedPortrait = 4, expandedLandscape = 5)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(TileGap)
    ) {
        when (uiState) {
            is MusicUiState.Loading -> item { LoadingIndicator() }

            is MusicUiState.Error -> item {
                ErrorContent(message = uiState.message, onRetry = onRetry)
            }

            is MusicUiState.Success -> {
                val data = uiState.data

                if (data.hasPreference) {
                    item(key = "preference_title") {
                        Column {
                            MusicSectionTitle("你的偏好")
                            data.portrait?.let { MusicPortraitSummary(it) }
                        }
                    }
                    items(data.preferences.chunked(columns), key = { row -> "pref_${row.first().id}" }) { row ->
                        TileRow(row, columns) { pref ->
                            PreferenceTile(pref, data.preferenceCovers[pref.id]) { onStyleClick(pref.id, pref.name) }
                        }
                    }
                }
                item(key = "styles_title") { MusicSectionTitle("全部曲风") }
                items(data.styles.chunked(columns), key = { row -> "style_${row.first().id}" }) { row ->
                    TileRow(row, columns) { style ->
                        StyleTile(style) { onStyleClick(style.id, style.name) }
                    }
                }
            }
        }
    }
}

// 末行不满时用空位补齐，保证各行色块等宽
@Composable
private fun <T> TileRow(row: List<T>, columns: Int, tile: @Composable (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MusicEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(TileGap)
    ) {
        row.forEach { item -> Box(modifier = Modifier.weight(1f)) { tile(item) } }
        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

// 画像默认只露第一行，点开看全文
@Composable
private fun MusicPortraitSummary(portrait: StylePortrait) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MusicEdgePadding)
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .animateContentSize(uiSpring())
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (expanded) portrait.content else portrait.content.lineSequence().first(),
                color = MelodiaColors.TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (expanded && portrait.dataTip.isNotBlank()) {
                Text(
                    text = portrait.dataTip,
                    color = MelodiaColors.TextTertiary,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (expanded) "收起画像" else "展开画像",
            tint = MelodiaColors.TextSecondary,
            modifier = Modifier.padding(start = 6.dp).size(18.dp)
        )
    }
}

// 偏好色块：右下角斜放曲风封面，封面未到时只显示色块
@Composable
private fun PreferenceTile(pref: StylePreference, coverUrl: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(TileAspectRatio)
            .pressable(MelodiaPress.Card) { onClick() }
            .clip(RoundedCornerShape(8.dp))
            .background(pref.colorHex.toStyleColor().liftedForTile())
    ) {
        coverUrl?.let {
            SubcomposeAsyncImage(
                model = it.withStyleCoverParam("200y200"),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 14.dp, y = 4.dp)
                    .rotate(25f)
                    .fillMaxHeight(0.66f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() }
            )
        }
        TileLabel(title = pref.name, subtitle = "${pref.ratio}%", modifier = Modifier.fillMaxWidth(0.7f))
    }
}

@Composable
private fun StyleTile(style: MusicStyle, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(TileAspectRatio)
            .pressable(MelodiaPress.Card) { onClick() }
            .clip(RoundedCornerShape(8.dp))
            .background(style.colorHex.toStyleColor().liftedForTile())
    ) {
        TileLabel(title = style.name, subtitle = style.enName)
    }
}

@Composable
private fun TileLabel(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(12.dp)) {
        Text(
            text = title,
            color = MelodiaColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = MelodiaColors.TextPrimary.copy(alpha = 0.7f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
