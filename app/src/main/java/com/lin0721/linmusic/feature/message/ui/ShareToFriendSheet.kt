package com.lin0721.linmusic.feature.message.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.PlaceholderTextField
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.message.domain.MessageUser
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareToFriendSheet(
    songId: Long,
    songName: String,
    onDismiss: () -> Unit,
    note: String = "",
    handleToasts: Boolean = true,
    viewModel: ShareToFriendViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.updateQuery("")
        viewModel.loadIfNeeded()
    }

    if (handleToasts) {
        LaunchedEffect(viewModel) {
            viewModel.toastEvent.collect { ToastManager.showToast(it) }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.shareCompleted.collect { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "分享给好友",
                    color = MelodiaColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (songName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = songName,
                        color = MelodiaColors.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when (val current = uiState) {
                ShareToFriendUiState.Idle, ShareToFriendUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                is ShareToFriendUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorState(
                            message = current.message,
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }

                is ShareToFriendUiState.Ready -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    PlaceholderTextField(
                        value = current.query,
                        onValueChange = viewModel::updateQuery,
                        placeholder = "搜索好友",
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MelodiaColors.CardSurface,
                        borderColor = MelodiaColors.Divider,
                        textColor = MelodiaColors.TextPrimary,
                        placeholderColor = MelodiaColors.TextTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        color = MelodiaColors.Divider,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    val recentUsers = current.filteredRecentUsers
                    val followUsers = current.filteredFollowUsers

                    if (recentUsers.isEmpty() && followUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(
                                icon = Icons.Rounded.Person,
                                title = if (current.query.isNotBlank()) "没有找到匹配的好友" else "暂无可分享的好友"
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val shouldLoadMore by remember(
                            current.hasMoreFollows,
                            current.isLoadingMoreFollows,
                            recentUsers.size,
                            followUsers.size
                        ) {
                            derivedStateOf {
                                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                val total = listState.layoutInfo.totalItemsCount
                                total > 0 && lastVisible >= total - 3 && current.hasMoreFollows && !current.isLoadingMoreFollows
                            }
                        }

                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore) viewModel.loadMoreFollows()
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 420.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            if (recentUsers.isNotEmpty()) {
                                item(key = "section_recent") {
                                    SectionHeader(title = "最近联系人")
                                }
                                items(recentUsers, key = { "recent_${it.uid}" }) { user ->
                                    ShareFriendRow(
                                        user = user,
                                        isSending = current.sendingToUid == user.uid,
                                        enabled = current.sendingToUid == null,
                                        onClick = {
                                            viewModel.shareSong(
                                                userId = user.uid,
                                                songId = songId,
                                                songName = songName,
                                                note = note
                                            )
                                        }
                                    )
                                }
                            }

                            if (followUsers.isNotEmpty()) {
                                item(key = "section_follows") {
                                    SectionHeader(title = "关注列表")
                                }
                                items(followUsers, key = { "follow_${it.uid}" }) { user ->
                                    ShareFriendRow(
                                        user = user,
                                        isSending = current.sendingToUid == user.uid,
                                        enabled = current.sendingToUid == null,
                                        onClick = {
                                            viewModel.shareSong(
                                                userId = user.uid,
                                                songId = songId,
                                                songName = songName,
                                                note = note
                                            )
                                        }
                                    )
                                }
                            }

                            if (current.isLoadingMoreFollows) {
                                item(key = "loading_more_follows") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(MelodiaSpacing.md),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp
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
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MelodiaColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun ShareFriendRow(
    user: MessageUser,
    isSending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.pressable(MelodiaPress.Row, onClick = onClick) else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = user.avatarUrl.takeIf { it.isNotBlank() }?.let { "$it?param=120y120" },
            contentDescription = user.nickname,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MelodiaColors.Fill),
            contentScale = ContentScale.Crop
        )

        Text(
            text = user.nickname.takeIf { it.isNotBlank() } ?: "网易云用户",
            color = MelodiaColors.TextPrimary,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isSending) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "发送",
                color = if (enabled) MelodiaColors.AccentText else MelodiaColors.TextDisabled,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
