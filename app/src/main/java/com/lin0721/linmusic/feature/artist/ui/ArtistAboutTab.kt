package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 关于艺人 Tab: 艺人简介 + 相似艺人推荐
fun LazyListScope.artistAboutTab(
    artist: ArtistDetailInfo,
    similarArtists: List<ArtistInfo>,
    onShowBioDialog: () -> Unit,
    onArtistClick: (Long) -> Unit
) {
    item(key = "bio_content") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MelodiaColors.PageFill)
                .padding(horizontal = MelodiaSpacing.md, vertical = 20.dp)
        ) {
            Text(
                text = "艺人简介",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onShowBioDialog() }
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = artist.briefDesc.ifBlank { "暂无艺人简介信息" }.trim(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (artist.briefDesc.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "点击查看完整介绍",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    if (similarArtists.isNotEmpty()) {
        item(key = "similar_artists_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MelodiaColors.PageFill)
                    .padding(vertical = MelodiaSpacing.md)
            ) {
                Text(
                    text = "相似艺人推荐",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = MelodiaSpacing.md, end = MelodiaSpacing.md, bottom = 12.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MelodiaSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
                ) {
                    items(similarArtists, key = { it.id }) { artistInfo ->
                        SimilarArtistCard(artist = artistInfo, onClick = { onArtistClick(artistInfo.id) })
                    }
                }
            }
        }
    }
}
