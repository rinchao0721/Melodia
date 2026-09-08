package com.lin0721.linmusic.feature.library.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.gestures.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.lin0721.linmusic.core.ui.components.ProfileSidebar
import kotlinx.coroutines.launch
import com.lin0721.linmusic.core.auth.UserProfile

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onPlaylistClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenSidebar: () -> Unit = {},
    onLoginScreenVisibilityChanged: (Boolean) -> Unit = {}
) {
    val viewModel: LibraryViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedPlaylistOwnerFilter by viewModel.selectedPlaylistOwnerFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showLoginSheet by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }

    // 监听网页登录界面可见性
    LaunchedEffect(showWebViewLogin) {
        onLoginScreenVisibilityChanged(showWebViewLogin)
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { message ->
            com.lin0721.linmusic.core.ui.components.ToastManager.showToast(message)
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val onAvatarClick: () -> Unit = {
        if (userProfile != null) {
            onOpenSidebar()
        } else {
            showLoginSheet = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // 1. 顶部栏 (支持搜索展开)
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "top_bar_search"
            ) { active ->
                if (active) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = MelodiaSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MelodiaIconButton(onClick = {
                            isSearchActive = false
                            viewModel.updateSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(PillRadius))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = MelodiaSpacing.md),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text("搜索您的收藏内容...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            MelodiaIconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = MelodiaSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 用户头像
                        val avatarUrl = userProfile?.avatarUrl
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = "$avatarUrl?param=100y100",
                                contentDescription = "用户头像",
                                modifier = Modifier
                                    .size(36.dp)
                                    .pressable(MelodiaPress.Icon) { onAvatarClick() }
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .pressable(MelodiaPress.Icon) { onAvatarClick() }
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "音乐库",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        MelodiaIconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        MelodiaIconButton(onClick = {
                            if (userProfile != null) {
                                showCreateDialog = true
                            } else {
                                com.lin0721.linmusic.core.ui.components.ToastManager.showToast("请先登录以创建歌单！")
                                showLoginSheet = true
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "创建歌单", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // 用户登录状态条件渲染
            if (userProfile == null) {
                NotLoggedInView(
                    onLoginClick = { showLoginSheet = true }
                )
            } else {
                val successState = uiState as? LibraryUiState.Success
                // 2. 分类过滤器横向滚动列表
                LibraryFilterPillsRow(
                    selectedFilter = selectedFilter,
                    playlistCount = successState?.playlistCount ?: 0,
                    albumCount = successState?.albumCount ?: 0,
                    artistCount = successState?.artistCount ?: 0,
                    onSelect = { viewModel.toggleFilter(it) },
                    onClear = { viewModel.clearFilter() },
                    selectedPlaylistOwnerFilter = selectedPlaylistOwnerFilter,
                    onSelectPlaylistOwnerFilter = { viewModel.togglePlaylistOwnerFilter(it) }
                )

                // 3. 排序与视图展示状态栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.pressable(MelodiaPress.Pill) { showSortMenu = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (sortOrder) {
                                LibrarySortOrder.RECENTLY_PLAYED -> "最近播放"
                                LibrarySortOrder.CREATE_TIME -> "创建时间"
                                LibrarySortOrder.NAME -> "字母排序"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )


                    }

                    MelodiaIconButton(
                        onClick = { viewModel.updateGridView(!isGridView) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Rounded.List else Icons.Rounded.GridView,
                            contentDescription = "切换视图",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 4. 混合聚合列表
                when (val state = uiState) {
                    is LibraryUiState.Loading -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is LibraryUiState.Error -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(MelodiaSpacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(MelodiaSpacing.md))
                                MelodiaButton(
                                    onClick = { viewModel.loadLibraryData() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("重试", color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                    is LibraryUiState.Success -> {
                        if (state.filteredItems.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(MelodiaSpacing.xl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "未找到相关收藏项" else "列表为空，快去添加吧！",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (isGridView) {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp, top = MelodiaSpacing.xs, start = MelodiaSpacing.md, end = MelodiaSpacing.md)
                            ) {
                                val rows = state.filteredItems.chunked(3)
                                items(rows, key = { row -> row.joinToString(separator = "_") { it.id } }) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        row.forEach { item ->
                                            LibraryGridItem(
                                                item = item,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    if (item.type == LibraryItemType.PLAYLIST) {
                                                        onPlaylistClick(item.id.toLong())
                                                    } else if (item.type == LibraryItemType.ARTIST) {
                                                        onArtistClick(item.id.toLong())
                                                    } else {
                                                        onAlbumClick(item.id.toLong())
                                                    }
                                                }
                                            )
                                        }
                                        repeat(3 - row.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp, top = MelodiaSpacing.xs)
                            ) {
                                items(state.filteredItems, key = { "${it.type}_${it.id}" }) { item ->
                                    LibraryItemRow(
                                        item = item,
                                        onClick = {
                                            if (item.type == LibraryItemType.PLAYLIST) {
                                                onPlaylistClick(item.id.toLong())
                                            } else if (item.type == LibraryItemType.ARTIST) {
                                                onArtistClick(item.id.toLong())
                                            } else {
                                                onAlbumClick(item.id.toLong())
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.togglePin(item.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

        // 创建歌单对话框
        if (showCreateDialog) {
            LibraryCreatePlaylistSheet(
                onDismiss = { showCreateDialog = false },
                onCreate = { name ->
                    viewModel.createPlaylist(name)
                    showCreateDialog = false
                }
            )
        }

        // 登录管理底部面板与 WebView
        if (showLoginSheet) {
            LoginBottomSheet(
                onDismiss = { showLoginSheet = false },
                onWebLogin = {
                    showLoginSheet = false
                    showWebViewLogin = true
                },
                onLoginSuccess = { cookies ->
                    showLoginSheet = false
                    viewModel.handleLoginSuccess(cookies)
                }
            )
        }

        AnimatedVisibility(
            visible = showWebViewLogin,
            enter = slideInVertically(tween(ScreenSlideDurationMs)) { it } + fadeIn(tween(ScreenSlideDurationMs)),
            exit = slideOutVertically(tween(ScreenSlideDurationMs)) { it } + fadeOut(tween(ScreenSlideDurationMs))
        ) {
            WebViewLoginScreen(
                onClose = { showWebViewLogin = false },
                onLoginSuccess = { cookies ->
                    showWebViewLogin = false
                    viewModel.handleLoginSuccess(cookies)
                }
            )
        }

        if (showSortMenu) {
            LibrarySortMenuSheet(
                currentSortOrder = sortOrder,
                onSelect = { order ->
                    viewModel.updateSortOrder(order)
                    showSortMenu = false
                },
                onDismiss = { showSortMenu = false }
            )
        }
    }
}
}

