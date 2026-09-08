package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.extractBackdropPaletteFromUrl
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 封面区：播放来源标题栏 + 方形封面，加载成功后回传取色结果
@Composable
fun FullPlayerCoverArt(
    coverUrl: String,
    title: String,
    playContext: String?,
    onClose: () -> Unit,
    onPaletteExtracted: (PlayerBackdropPalette) -> Unit,
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 取色跟封面显示解码完全脱钩，单独发一次固定尺寸的请求
    LaunchedEffect(coverUrl) {
        if (coverUrl.isNotEmpty()) {
            onPaletteExtracted(extractBackdropPaletteFromUrl(context, coverUrl))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(top = MelodiaSpacing.md, bottom = MelodiaSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MelodiaIconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .offset(x = (-4).dp)
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                val (sourceText, detailText) = when (playContext) {
                    null -> "NOW PLAYING" to null
                    "搜索" -> "播放自" to "搜索"
                    "每日推荐" -> "播放自" to "每日推荐"
                    "历史日推" -> "播放自" to "历史日推"
                    "intelligence" -> "播放自" to "心动模式"
                    else -> "播放自歌单" to playContext
                }
                Text(
                    text = sourceText,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (detailText != null) {
                    Text(
                        text = "“$detailText”",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            MelodiaIconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .size(32.dp)
                    .offset(x = 4.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverUrl.ifEmpty { null })
                .allowHardware(false)
                .crossfade(true)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(RadiusCompact), clip = false)
                .clip(RoundedCornerShape(RadiusCompact))
        )
    }
}
