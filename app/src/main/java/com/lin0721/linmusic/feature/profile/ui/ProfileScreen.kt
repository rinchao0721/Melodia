package com.lin0721.linmusic.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileScreen(
    uid: Long,
    viewModel: ProfileViewModel = koinViewModel(),
    onBack: () -> Unit,
    onNavigateToFollowList: (uid: Long, mode: FollowListMode) -> Unit,
    onPlaylistClick: (id: Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // uid 变化（比如从一个人的主页跳到另一个人的主页）时重新加载，避免复用上一个人的数据
    LaunchedEffect(uid) {
        viewModel.load(uid)
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when (val state = uiState) {
            ProfileUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    TopNavigationBar(
                        isSelf = true,
                        onBack = onBack,
                        onMoreClick = {}
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            is ProfileUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    TopNavigationBar(
                        isSelf = true,
                        onBack = onBack,
                        onMoreClick = {}
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorState(
                            message = state.message,
                            onRetry = { viewModel.retry() }
                        )
                    }
                }
            }

            is ProfileUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    TopNavigationBar(
                        isSelf = state.isSelf,
                        onBack = onBack,
                        onMoreClick = {}
                    )

                    ProfileHeaderSection(
                        userInfo = state.userInfo,
                        isSelf = state.isSelf,
                        selectedTab = state.selectedTab,
                        onTabSelected = { viewModel.selectTab(it) },
                        onFollowClick = { viewModel.toggleFollow() },
                        onFollowsClick = { onNavigateToFollowList(uid, FollowListMode.FOLLOWS) },
                        onFollowedsClick = { onNavigateToFollowList(uid, FollowListMode.FOLLOWEDS) }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (state.selectedTab) {
                            0 -> ProfilePlaylistTab(
                                playlists = state.playlists,
                                isLoading = state.playlistsLoadingMore,
                                hasMore = state.playlistsHasMore,
                                onLoadMore = { viewModel.loadMorePlaylists() },
                                onPlaylistClick = onPlaylistClick
                            )
                            1 -> ProfileEventTab(
                                events = state.events,
                                isLoading = state.eventsLoadingMore,
                                hasMore = state.eventsHasMore,
                                onLoadMore = { viewModel.loadMoreEvents() }
                            )
                            2 -> ProfileRecordTab(
                                items = state.rankItems,
                                isLoading = state.rankLoading,
                                subTab = state.rankSubTab,
                                onSubTabSelected = { viewModel.selectRankSubTab(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopNavigationBar(
    isSelf: Boolean,
    onBack: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.xs, vertical = MelodiaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MelodiaIconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (!isSelf) {
            MelodiaIconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = Color.White
                )
            }
        }
    }
}
