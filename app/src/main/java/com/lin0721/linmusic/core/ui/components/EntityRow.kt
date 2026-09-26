package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass

enum class EntityCoverShape { Rounded, Circle }

// 非歌曲类实体（专辑/歌手/歌单等）的轻量 UI 数据模型，视觉比例对齐 SongRow
data class EntityRowData(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val coverUrl: String?,
    val coverShape: EntityCoverShape = EntityCoverShape.Rounded
)

// 通用实体行：专辑/歌手/歌单等非歌曲结果共用，视觉比例照抄 SongRow 保持列表族群观感统一
@Composable
fun EntityRow(
    data: EntityRowData,
    onClick: () -> Unit
) {
    val shape: Shape = when (data.coverShape) {
        EntityCoverShape.Rounded -> RoundedCornerShape(RadiusCompact)
        EntityCoverShape.Circle -> CircleShape
    }
    // 平板 Expanded 断点下行高/封面/字号统一加码，结构不变，视觉比例继续照抄 SongRow
    val isExpanded = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded
    val coverSize = if (isExpanded) 56.dp else 48.dp
    val verticalPadding = if (isExpanded) 14.dp else 10.dp
    val titleFontSize = if (isExpanded) 17.sp else 15.sp
    val subtitleFontSize = if (isExpanded) 14.sp else 13.sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MelodiaSpacing.md, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val imageRequest = remember(data.coverUrl) {
            if (!data.coverUrl.isNullOrBlank()) {
                ImageRequest.Builder(context)
                    .data("${data.coverUrl}?param=100y100")
                    .crossfade(true)
                    .build()
            } else {
                null
            }
        }
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = data.title,
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() },
            modifier = Modifier.size(coverSize).clip(shape)
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = data.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = titleFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!data.subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = data.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = subtitleFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
