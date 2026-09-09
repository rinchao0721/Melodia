package com.lin0721.linmusic.feature.profile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material3.CircularProgressIndicator
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
import coil.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.feature.profile.domain.ProfileEventInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatEventTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

// 「动态」Tab 的内容项，作为外层 LazyColumn 的普通 item 加入，理由同 profilePlaylistItems
fun LazyListScope.profileEventItems(
    events: List<ProfileEventInfo>,
    isLoading: Boolean
) {
    if (events.isEmpty()) {
        item(key = "event_empty") {
            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            } else {
                EmptyState(icon = Icons.Rounded.DynamicFeed, title = "暂无动态")
            }
        }
        return
    }

    items(events, key = { it.id }) { event ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = MelodiaSpacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = "${event.authorAvatarUrl}?param=120y120",
                contentDescription = event.authorNickname,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.authorNickname,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val timeText = formatEventTime(event.showTime)
                if (timeText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }

                if (event.rawJson.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = event.rawJson,
                        color = TextGray,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (isLoading) {
        item(key = "event_loading_more") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(MelodiaSpacing.md),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}
