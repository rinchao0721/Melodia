package com.lin0721.linmusic.feature.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.feature.podcast.domain.PodcastCategory
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.formatListenerCount
import com.lin0721.linmusic.feature.podcast.domain.formatProgramDuration
import com.lin0721.linmusic.feature.podcast.domain.formatSubCount

internal val PodcastEdgePadding = 20.dp

// 电台封面自带查询串的情况与歌单同理，追加 param 前需判断
internal fun String.withPodcastCoverParam(param: String): String =
    if (contains('?')) this else "$this?param=$param"

@Composable
fun PodcastSectionTitle(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PodcastEdgePadding, end = PodcastEdgePadding, top = 20.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        trailing?.let {
            Text(text = it, color = TextGray, fontSize = 11.5.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// 分类胶囊。「推荐」是虚拟项，对应不限分类
@Composable
fun PodcastCategoryChips(
    categories: List<PodcastCategory>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        contentPadding = PaddingValues(horizontal = PodcastEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item(key = "all") {
            CategoryChip(text = "推荐", selected = selectedId == null, onClick = { onSelect(null) })
        }
        itemsIndexed(categories, key = { _, item -> item.id }) { _, category ->
            CategoryChip(
                text = category.name,
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) }
            )
        }
    }
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .pressable(MelodiaPress.Pill) { onClick() }
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(Color.White)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            )
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFF121212) else Color(0xFFBDBDBD),
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// 节目行：封面 + 标题 + 电台·主播 + 时长收听数 + 播放按钮
@Composable
fun PodcastProgramRow(
    program: PodcastProgram,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = PodcastEdgePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = program.coverUrl.withPodcastCoverParam("200y200"),
            contentDescription = program.name,
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(RadiusCompact)),
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() }
        )

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = program.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val source = listOfNotNull(
                program.radioName.takeIf { it.isNotBlank() },
                program.djName.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (source.isNotBlank()) {
                Text(
                    text = source,
                    color = TextGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            val meta = listOfNotNull(
                formatProgramDuration(program.durationMs).takeIf { it.isNotBlank() },
                formatListenerCount(program.listenerCount).takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = TextGray.copy(alpha = 0.75f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// 电台货架。showRank 时在封面左上角标名次
@Composable
fun PodcastRadioRow(
    radios: List<PodcastRadio>,
    showRank: Boolean = false,
    onClick: (PodcastRadio) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = PodcastEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(radios, key = { index, item -> "${item.id}_$index" }) { index, radio ->
            Column(modifier = Modifier.width(120.dp).pressable(MelodiaPress.Card) { onClick(radio) }) {
                Box {
                    SubcomposeAsyncImage(
                        model = radio.picUrl.withPodcastCoverParam("300y300"),
                        contentDescription = radio.name,
                        modifier = Modifier.size(120.dp).clip(RoundedCornerShape(RadiusCompact)),
                        contentScale = ContentScale.Crop,
                        loading = { CoverPlaceholder() },
                        error = { CoverPlaceholder() }
                    )
                    if (showRank) {
                        Text(
                            text = "${index + 1}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = radio.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp)
                )
                val meta = listOfNotNull(
                    radio.programCount.takeIf { it > 0 }?.let { "$it 期" },
                    formatSubCount(radio.subCount).takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = TextGray,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
