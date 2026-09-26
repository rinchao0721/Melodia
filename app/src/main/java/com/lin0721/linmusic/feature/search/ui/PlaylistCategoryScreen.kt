package com.lin0721.linmusic.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.ui.components.DiscoverySectionSkeleton
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaGridColumns
import org.koin.androidx.compose.koinViewModel

// 精品歌单播放量简易格式化，与 feature/playlist 的同名函数逻辑一致但不跨域引用，保持模块边界干净
private fun formatPlayCount(count: Long): String = when {
    count >= 100000000 -> "${(count / 10000000) / 10.0}亿"
    count >= 10000 -> "${(count / 1000) / 10.0}万"
    else -> count.toString()
}

private val HEADER_HEIGHT = 220.dp
private val SIMPLE_HEADER_HEIGHT = 56.dp

@Composable
fun PlaylistCategoryScreen(
    category: String,
    viewModel: PlaylistCategoryViewModel = koinViewModel(),
    onBack: () -> Unit,
    onPlaylistClick: (id: Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(category) {
        viewModel.load(category)
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                PlaylistCategoryUiState.Loading -> {
                    SimpleHeader(category)
                    Column(modifier = Modifier.fillMaxSize().padding(top = MelodiaSpacing.md)) {
                        DiscoverySectionSkeleton()
                        Spacer(Modifier.height(MelodiaSpacing.lg))
                        DiscoverySectionSkeleton()
                    }
                }
                PlaylistCategoryUiState.Empty -> {
                    SimpleHeader(category)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(icon = Icons.Rounded.LibraryMusic, title = "该分类下暂时没有歌单")
                    }
                }
                is PlaylistCategoryUiState.Error -> {
                    SimpleHeader(category)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(message = state.message, onRetry = { viewModel.retry() })
                    }
                }
                is PlaylistCategoryUiState.Success -> {
                    val gridState = rememberLazyGridState()
                    val shouldLoadMore by remember(state.hasMore, state.isLoadingMore) {
                        derivedStateOf {
                            // 网格里第 0 项是头图，歌单项的 grid index 相应整体 +1
                            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= state.playlists.size - 3 && state.hasMore && !state.isLoadingMore
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) viewModel.loadMore()
                    }

                    val columns = rememberMelodiaGridColumns(compact = 2, expandedPortrait = 4, expandedLandscape = 6)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm),
                        contentPadding = PaddingValues(
                            start = MelodiaSpacing.md,
                            end = MelodiaSpacing.md,
                            bottom = LocalBottomOverlayInset.current + 16.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                            CategoryHeroHeader(
                                category = category,
                                coverUrl = state.playlists.first().coverImgUrl,
                                total = state.total,
                                modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                            )
                        }

                        items(state.playlists, key = { it.id }) { playlist ->
                            PlaylistCategoryCard(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
                        }

                        if (state.isLoadingMore) {
                            item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(MelodiaSpacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        MelodiaIconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(MelodiaSpacing.xs)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }
    }
}

// 无背景图数据时（加载中/空态/错误态）的简化顶部占位，保持返回箭头下方有足够留白且分类名始终可见
@Composable
private fun SimpleHeader(category: String) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Spacer(Modifier.height(SIMPLE_HEADER_HEIGHT))
        Text(
            text = category,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = MelodiaSpacing.md)
        )
    }
}

// 分类大图头图：取该分类下播放量最高（接口本身按此排序返回的第一条）的歌单封面做沉浸式背景
@Composable
private fun CategoryHeroHeader(
    category: String,
    coverUrl: String,
    total: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (coverUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = "${coverUrl}?param=500y500",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() },
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = MelodiaSpacing.md)
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Text(
                text = category,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "共 $total 个精品歌单",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun PlaylistCategoryCard(playlist: PlaylistDetail, onClick: () -> Unit) {
    Column(modifier = Modifier.pressable(MelodiaPress.Card, onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (playlist.coverImgUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = "${playlist.coverImgUrl}?param=300y300",
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(MelodiaSpacing.xs))
        Text(
            text = playlist.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (playlist.playCount > 0) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = formatPlayCount(playlist.playCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}
