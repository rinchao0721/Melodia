package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.feature.player.domain.SongWikiCreatorRole
import com.lin0721.linmusic.feature.player.domain.SongWikiData
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@Composable
fun SongDetailCard(
    songWiki: SongWikiData?,
    songDetail: Track?,
    cardColor: Color
) {
    var showCreatorsSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        shape = RoundedCornerShape(InfoCardRadius),
        color = cardColor
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "歌曲详情",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (songWiki == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = NeteaseRed,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (songWiki.style.isNotEmpty()) {
                        SongDetailRow(label = "曲风", value = songWiki.style)
                    }

                    val albumName = songWiki.album.ifEmpty { songDetail?.al?.name.orEmpty() }
                    if (albumName.isNotEmpty()) {
                        SongDetailRow(label = "专辑", value = albumName)
                    }

                    if (songWiki.language.isNotEmpty()) {
                        SongDetailRow(label = "语种", value = songWiki.language)
                    }

                    val publishDate = songWiki.publishTime.ifEmpty {
                        val time = songDetail?.publishTime ?: 0L
                        if (time > 0L) {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(time))
                        } else ""
                    }
                    if (publishDate.isNotEmpty()) {
                        SongDetailRow(label = "发行时间", value = publishDate)
                    }

                    if (songWiki.bpm.isNotEmpty()) {
                        SongDetailRow(label = "BPM", value = songWiki.bpm)
                    }

                    if (songWiki.entertainment.isNotEmpty()) {
                        SongDetailRow(label = "影综", value = songWiki.entertainment)
                    }

                    if (songWiki.background.isNotEmpty()) {
                        SongDetailRow(
                            label = "歌曲背景",
                            value = songWiki.background,
                            maxLines = 15
                        )
                    }

                    if (songWiki.awards.isNotEmpty()) {
                        SongDetailRow(
                            label = "所获奖项",
                            value = songWiki.awards,
                            maxLines = 15
                        )
                    }

                    if (songWiki.creators.isNotEmpty()) {
                        SongDetailRow(
                            label = "制作",
                            value = songWiki.creators,
                            showChevron = songWiki.creatorRoles.isNotEmpty(),
                            maxLines = 15,
                            onClick = if (songWiki.creatorRoles.isNotEmpty()) {
                                { showCreatorsSheet = true }
                            } else null
                        )
                    }
                }
            }

            if (showCreatorsSheet) {
                SongCreatorsSheet(
                    creatorRoles = songWiki?.creatorRoles.orEmpty(),
                    onDismiss = { showCreatorsSheet = false }
                )
            }
        }
    }
}

// "制作"详情面板：按角色分组展示词曲编曲等制作人员
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongCreatorsSheet(
    creatorRoles: List<SongWikiCreatorRole>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark,
        shape = BottomSheetShape,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = MelodiaSpacing.xs)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(DragHandleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Text(
                text = "制作信息",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )
            creatorRoles.forEach { role ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MelodiaSpacing.xs),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = role.roleName,
                        color = TextGray.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        text = role.artistNames.joinToString(" / "),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun SongDetailRow(
    label: String,
    value: String,
    showChevron: Boolean = false,
    maxLines: Int = 3,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = MelodiaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextGray.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.width(70.dp)
        )

        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (showChevron) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = TextGray.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(20.dp)
                    .padding(start = MelodiaSpacing.xs)
            )
        }
    }
}

@Composable
fun SimilarArtistsCard(
    artists: List<ArtistInfo>,
    isLoading: Boolean,
    cardColor: Color,
    onArtistClick: (Long) -> Unit
) {
    if (artists.isEmpty() && !isLoading) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        shape = RoundedCornerShape(InfoCardRadius),
        color = cardColor
    ) {
        Column(modifier = Modifier.padding(vertical = 20.dp)) {
            Text(
                "探索类似艺人",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = NeteaseRed,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(artists, key = { it.id }) { artist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clickable { onArtistClick(artist.id) }
                        ) {
                            AsyncImage(
                                model = "${artist.avatarUrl}?param=150y150",
                                contentDescription = artist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = artist.name,
                                color = Color.White,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutArtistCard(
    artistDetail: ArtistDetailInfo?,
    fansCount: Long?,
    isFollowed: Boolean,
    onFollowClick: () -> Unit,
    cardColor: Color,
    onClick: () -> Unit
) {
    if (artistDetail == null) return

    val coverUrl = artistDetail.cover.ifEmpty { artistDetail.avatar }
    if (coverUrl.isBlank()) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(InfoCardRadius),
        color = cardColor
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                SubcomposeAsyncImage(
                    model = coverUrl,
                    contentDescription = artistDetail.name,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() },
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                )
                Text(
                    text = "关于艺人",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(MelodiaSpacing.md)
                )
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.md)) {
                        Text(
                            text = artistDetail.name,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (fansCount != null) {
                                "${formatFansCount(fansCount)}粉丝"
                            } else {
                                "--粉丝"
                            },
                            color = TextGray,
                            fontSize = 13.sp
                        )
                    }
                    MelodiaOutlinedButton(
                        onClick = onFollowClick,
                        shape = RoundedCornerShape(PillRadius),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                            containerColor = Color.Transparent
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            width = 1.dp
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = if (isFollowed) "已关注" else "关注",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (artistDetail.briefDesc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    var isExpanded by remember { mutableStateOf(false) }
                    val cleanDesc = artistDetail.briefDesc.trim()
                    val annotatedText = buildAnnotatedString {
                        val maxLen = 95
                        if (cleanDesc.length > maxLen && !isExpanded) {
                            val truncated = cleanDesc.take(maxLen)
                            append(truncated)
                            append("... ")
                            withStyle(
                                style = SpanStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("查看更多")
                            }
                        } else if (isExpanded) {
                            append(cleanDesc)
                            append(" ")
                            withStyle(
                                style = SpanStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("收起")
                            }
                        } else {
                            append(cleanDesc)
                        }
                    }

                    Text(
                        text = annotatedText,
                        color = TextGray,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (cleanDesc.length > 95) {
                                    isExpanded = !isExpanded
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistAlbumsCard(
    albums: List<ArtistAlbum>,
    artistName: String?,
    cardColor: Color,
    onAlbumClick: (Long) -> Unit = {}
) {
    if (albums.isEmpty()) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        shape = RoundedCornerShape(InfoCardRadius),
        color = cardColor
    ) {
        Column(modifier = Modifier.padding(vertical = 20.dp)) {
            Text(
                text = if (artistName != null) "${artistName}的更多专辑" else "更多专辑",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            ImperativeLazyRow(albums = albums, onAlbumClick = onAlbumClick)
        }
    }
}

@Composable
private fun ImperativeLazyRow(
    albums: List<ArtistAlbum>,
    onAlbumClick: (Long) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(RadiusCompact))
                    .clickable { onAlbumClick(album.id) }
            ) {
                SubcomposeAsyncImage(
                    model = "${album.picUrl}?param=250y250",
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(RadiusCompact))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = album.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatFansCount(count: Long): String {
    return if (count >= 10000) {
        val countDouble = count / 10000.0
        val formatted = String.format(java.util.Locale.US, "%.1f", countDouble)
        if (formatted.endsWith(".0")) {
            formatted.substring(0, formatted.length - 2) + "万"
        } else {
            formatted + "万"
        }
    } else {
        count.toString()
    }
}
