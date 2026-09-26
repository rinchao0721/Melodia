package com.lin0721.linmusic.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion

private val LeadingSize = 40.dp

@Composable
internal fun SearchTypingPanel(
    query: String,
    suggestions: List<SearchSuggestion>,
    history: List<String>,
    onSubmitKeyword: (String) -> Unit,
    onFillQuery: (String) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md)

    if (query.isBlank()) {
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(top = 96.dp), contentAlignment = Alignment.TopCenter) {
                EmptyState(icon = Icons.Rounded.Search, title = "搜索歌曲、歌手、专辑或歌单")
            }
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = MelodiaSpacing.md, end = MelodiaSpacing.xs, top = MelodiaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最近搜索",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    MelodiaTextButton(onClick = onClearHistory) {
                        Text("清空", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
            items(history, key = { "history_$it" }) { keyword ->
                PanelRow(
                    onClick = { onSubmitKeyword(keyword) },
                    leading = { LeadingIcon(Icons.Rounded.History) },
                    title = AnnotatedString(keyword),
                    trailing = {
                        MelodiaIconButton(onClick = { onRemoveHistory(keyword) }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "删除搜索记录 $keyword",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        }
        return
    }

    val highlight = MaterialTheme.colorScheme.primary
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "submit_query") {
            PanelRow(
                onClick = { onSubmitKeyword(query) },
                leading = { LeadingIcon(Icons.Rounded.Search, tint = MaterialTheme.colorScheme.onSurface) },
                title = AnnotatedString("搜索“$query”"),
                titleWeight = FontWeight.Bold
            )
        }
        items(suggestions, key = { it.stableKey }) { suggestion ->
            val title = remember(suggestion.text, query, highlight) {
                highlightMatch(suggestion.text, query, SpanStyle(color = highlight))
            }
            val fillButton: @Composable () -> Unit = {
                MelodiaIconButton(onClick = { onFillQuery(suggestion.text) }) {
                    Icon(
                        Icons.Rounded.NorthWest,
                        contentDescription = "填入 ${suggestion.text}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            when (suggestion) {
                is SearchSuggestion.ArtistMatch -> PanelRow(
                    onClick = { onArtistClick(suggestion.id) },
                    leading = { SuggestionCover(suggestion.avatarUrl, Icons.Rounded.Person, circle = true) },
                    title = title,
                    subtitle = "歌手",
                    trailing = fillButton
                )
                is SearchSuggestion.AlbumMatch -> PanelRow(
                    onClick = { onAlbumClick(suggestion.id) },
                    leading = { SuggestionCover(null, Icons.Rounded.Album, circle = false) },
                    title = title,
                    subtitle = listOf("专辑", suggestion.artistName).filter { it.isNotBlank() }.joinToString(" · "),
                    trailing = fillButton
                )
                is SearchSuggestion.Keyword -> PanelRow(
                    onClick = { onSubmitKeyword(suggestion.text) },
                    leading = { LeadingIcon(Icons.Rounded.Search) },
                    title = title,
                    trailing = fillButton
                )
            }
        }
    }
}

private val SearchSuggestion.stableKey: String
    get() = when (this) {
        is SearchSuggestion.ArtistMatch -> "artist_$id"
        is SearchSuggestion.AlbumMatch -> "album_$id"
        is SearchSuggestion.Keyword -> "keyword_$text"
    }

@Composable
private fun PanelRow(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: AnnotatedString,
    subtitle: String? = null,
    titleWeight: FontWeight = FontWeight.Normal,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(start = MelodiaSpacing.md, end = MelodiaSpacing.xs, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = titleWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Spacer(modifier = Modifier.width(MelodiaSpacing.md - MelodiaSpacing.xs))
        }
    }
}

@Composable
private fun LeadingIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(modifier = Modifier.size(LeadingSize), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// 联想接口的专辑不带封面地址，统一用图标占位
@Composable
private fun SuggestionCover(url: String?, fallbackIcon: ImageVector, circle: Boolean) {
    val shape = if (circle) CircleShape else RoundedCornerShape(RadiusCompact)
    val placeholder = @Composable {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(fallbackIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
    Box(modifier = Modifier.size(LeadingSize).clip(shape)) {
        if (url.isNullOrBlank()) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = "$url?param=120y120",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal fun highlightMatch(text: String, query: String, style: SpanStyle): AnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var start = 0
        while (start < text.length) {
            val index = text.indexOf(needle, startIndex = start, ignoreCase = true)
            if (index < 0) break
            append(text.substring(start, index))
            withStyle(style) { append(text.substring(index, index + needle.length)) }
            start = index + needle.length
        }
        if (start < text.length) append(text.substring(start))
    }
}
