package com.lin0721.linmusic.feature.profile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.TextGray
import org.koin.androidx.compose.koinViewModel

@Composable
fun FollowListScreen(
    uid: Long,
    mode: FollowListMode,
    viewModel: FollowListViewModel = koinViewModel(),
    onBack: () -> Unit,
    onUserClick: (uid: Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // uid/mode 变化时重新加载，避免复用上一个列表缓存的数据
    LaunchedEffect(uid, mode) {
        viewModel.load(uid, mode)
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    val title = if (mode == FollowListMode.FOLLOWS) "关注" else "粉丝"

    SecondaryScreenScaffold(
        title = title,
        onBack = onBack
    ) {
        when (val state = uiState) {
            FollowListUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            is FollowListUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.retry() }
                    )
                }
            }

            is FollowListUiState.Success -> {
                if (state.users.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Rounded.People,
                            title = if (mode == FollowListMode.FOLLOWS) "还没有关注任何人" else "还没有粉丝关注"
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    val shouldLoadMore by remember(state.hasMore, state.isLoadingMore, state.users.size) {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            state.users.isNotEmpty() && lastVisible >= state.users.size - 3 && state.hasMore && !state.isLoadingMore
                        }
                    }

                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) viewModel.loadMore()
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = MelodiaSpacing.sm,
                            bottom = LocalBottomOverlayInset.current + 16.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.users, key = { it.uid }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUserClick(user.uid) }
                                    .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = "${user.avatarUrl}?param=120y120",
                                    contentDescription = user.nickname,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.nickname,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (user.signature.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = user.signature,
                                            color = TextGray,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (state.isLoadingMore) {
                            item(key = "loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(MelodiaSpacing.md),
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
    }
}
