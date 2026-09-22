# 平板适配 Phase 3：播放器常驻侧栏面板 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地平板适配设计文档第 6.3 节——Expanded 断点下，点击迷你播放组件不再拉起整屏播放器，而是在内容区右侧展开一个固定宽度的常驻侧栏面板，与内容区左右并排、同时可交互。面板内容**全量复用 `FullPlayerScreen`**（含评论区/歌曲百科/艺人简介/艺人专辑/相似艺人等深挖区块，已与用户确认），只是渲染宽度从整屏变成固定宽度列。手机分支（`playerSheet` 抽屉式全屏播放器）零改动。

**Architecture:** 关键发现：`FullPlayerScreen` 本身不依赖外部传入的 `offsetY`/`screenHeightPx`（那是 `MelodiaFullPlayerOverlay` 这层包装器才需要的东西，用来做拖拽拉起的位移动画），签名只有 `currentTrack/isPlaying/.../onClose/isPlayerOpen/onArtistClick/onAlbumClick/onNavigateToProfile/onDragClose`。这意味着面板可以**直接复用 `FullPlayerScreen`**，不需要另起一套内容或改它一行代码——只需要一个新的轻量包装器 `PlayerDockPanel.kt`，把它塞进一个固定宽度的 `Box` 里，`onClose`/`onDragClose` 改成"收起面板回迷你播放条"而不是"关闭播放器"。

真正的改动集中在 `MelodiaApp.kt` 的顶层布局：现在的"主页面内容层"要从单个 `Box` 变成 `Row(内容区 weight(1f), 面板 可选固定宽度)`，并新增 `isPanelExpanded` 状态（与手机端的 `playerSheet` 完全独立，互不干扰）。

**Tech Stack:** Jetpack Compose，不引入新依赖。`playerSheet`（`MelodiaPlayerSheetState`）和 `MelodiaFullPlayerOverlay` 原样保留、完全不改，只是在 Expanded 下不再被触发。

## 范围与已确认决策

1. **面板内容**：`FullPlayerScreen` 全部内容原样复用（含评论区等深挖区块），只窄化宽度——已与用户确认。
2. **面板宽度**：竖屏 340dp / 横屏 400dp（设计文档 6.3 节建议值），本阶段先按这两个数直接落地，真机看效果后再调，不是最终定案。
3. **可见性规则**——**已推翻，见下方"course correction"**：初版按设计文档 5.3 节做成"只在三个 Tab 根页面显示，二级页面暂时隐藏"，用户看过 Spotify 平板版参考图后要求改成任意页面都常驻展开，不再区分 Tab 根页面/二级页面。
4. **状态机独立**：新增一个纯布尔 `isPanelExpanded`，只在 Expanded 下由迷你播放条的点击/拖拽驱动；手机端继续用现有的 `playerSheet`（弹簧动画的拖拽抽屉状态机）。两套状态互不读写，靠"点击迷你播放条时按断点二选一分支"来避免冲突——不尝试合并成一套状态机，那样风险和改动面都更大。
5. **收起方式**：面板左上角收起箭头（`FullPlayerScreen` 内部 `onClose`）和内部下拉手势（`onDragClose`）都只是把 `isPanelExpanded` 置回 `false`，不触发 `navigation.resetPlayerNavigation()`/`navigateFromPlayer` 那套手机端专属的"从播放器进二级页、返回时重新拉起播放器"机制。
6. **返回键**——**已推翻，见下方"course correction"**：初版把面板收起纳入返回键优先级链，后来发现"面板扩展到所有页面"后这会导致"在二级页面按一次返回键只收面板、要按两次才能真正退出页面"的体验问题，改成返回键完全不处理面板，面板只能通过自身收起箭头/下拉手势关闭。

### Course correction（真机实测后的两次推翻，均已与用户确认）

**背景**：Phase 2（曲库列表-详情双栏）做完后，用户拿 Spotify 平板版截图对比，指出两点：① 曲库的双栏结构和"面板扩展到所有页面"应该二选一统一成后者（更贴合参考图里"任意详情页都是单栏内容+右侧常驻面板"的样子）；② 内容区和面板的接触边缘太生硬，应该做成两个圆角卡片+间隙（同样参照 Spotify）。这两点导致 Phase 2 的曲库双栏被整体撤销（`LibraryExpandedLayout.kt` 删除，`PlaylistScreen`/`PlaylistContent` 的 `showTopBar` 开关也一并撤销，曲库恢复成跟手机版一样的单栏内容），Phase 3 的面板可见性规则也相应改掉：

1. **面板可见性从"仅 Tab 根页面"改为"任意页面"**：`isPanelVisible` 不再检查 `currentScreen` 类型，只看 `windowSizeClass == Expanded && isPanelExpanded`。代码里 `isTabRootScreen` 这个 val 已删除。
2. **返回键不再处理面板**：真机验证发现"面板扩展到所有页面"后，如果返回键仍按原计划优先收起面板，会导致在任何二级页面按一次返回键都只收面板、不退出页面，需要按两次才能真正返回——用户确认这不符合预期（面板应该是常驻工具栏，不占用返回键），于是从 `isAnyOverlayOpen`/`BackHandler` 的 `when` 分支里整个移除了面板判断，只保留手机端 `playerSheet` 那一支。
3. **新增圆角卡片视觉**（不在原计划内，Task 2 的 Row 结构在此基础上又追加了一层）：内容区和面板各自 `clip(RoundedCornerShape(InfoCardRadius))` + `border(0.5.dp, Color.White.copy(alpha=0.08f), ...)`，两者之间用 `Arrangement.spacedBy(MelodiaSpacing.sm)` 留缝隙——复用 Phase 0 悬浮导航栏/迷你播放卡已经在用的同一套圆角+描边视觉语言，不是新发明的样式。这个圆角处理只在 `isPanelVisible` 为真时对内容区生效（面板不存在时内容区照旧铺满、不裁角），面板自身则始终应用（反正它只在 `AnimatedVisibility` 内渲染，不存在与否不需要额外判断）。

**已知遗留问题（真机实测发现，尚未处理）**：打开头像侧边栏（`ProfileSidebar`）时，整个"内容区+面板"Row 会跟随 `sidebar.offsetX` 一起向右平移（这个 `graphicsLayer` 平移是 Phase 0 之前就有的机制，本轮没有改），平板宽屏下会把面板挤到屏幕右边缘外面，只剩一条窄边可见，视觉上比较突兀。设计文档没有覆盖"侧边栏+平板面板"这个组合场景，需要用户决定怎么处理（面板跟着一起平移到屏幕外/侧边栏打开时面板临时隐藏/让面板不参与这层平移单独钉住），本轮先如实记录，不擅自改。

## Global Constraints

- 不改 `FullPlayerScreen.kt`/`MelodiaPlayerSheetState.kt`/`MelodiaFullPlayerOverlay` 任何一行——手机分支的回归基线就是这三者原样不动。
- `PlayerDockPanel.kt` 只是宽度约束 + 回调语义转换的薄包装，不重新实现播放器内容。
- 中文注释精简，不写占位符，每步都是完整代码。

---

### Task 1: 新增 PlayerDockPanel

**Files:**
- Create: `app/src/main/java/com/lin0721/linmusic/feature/player/ui/PlayerDockPanel.kt`

**Interfaces:**
- Consumes: `FullPlayerScreen`（原样复用，不改一行）

- [ ] **Step 1: 新建文件，完整实现**

```kotlin
package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem

// 平板常驻播放面板宽度：竖屏 340dp / 横屏 400dp（设计文档 6.3 节建议值，真机验证后可再调）
private val PanelWidthPortrait = 340.dp
private val PanelWidthLandscape = 400.dp

// Expanded 断点下的播放器展开态：不整屏覆盖，铺在一个固定宽度的常驻侧栏里，与内容区
// 左右并排（容器结构见 MelodiaApp.kt）。内容原样复用 FullPlayerScreen，onClose/onDragClose
// 改成收起面板回迷你播放条，不走手机端"关闭播放器+可能重新拉起"那套导航记账机制
@Composable
fun PlayerDockPanel(
    currentTrack: MediaItem?,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onDragClose: (Float, Float) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onNavigateToProfile: (Long) -> Unit
) {
    if (currentTrack == null) return

    val configuration = LocalConfiguration.current
    val panelWidth = if (configuration.screenWidthDp < configuration.screenHeightDp) {
        PanelWidthPortrait
    } else {
        PanelWidthLandscape
    }

    Box(
        modifier = Modifier
            .width(panelWidth)
            .fillMaxHeight()
    ) {
        FullPlayerScreen(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            onClose = onClose,
            isPlayerOpen = true,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onNavigateToProfile = onNavigateToProfile,
            onDragClose = onDragClose
        )
    }
}
```

- [ ] **Step 2: 编译确认无误**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 2: MelodiaApp.kt 顶层布局改为 Row(内容区, 可选面板) + 新增 isPanelExpanded 状态

**Files:**
- Modify: `app/src/main/java/com/lin0721/linmusic/MelodiaApp.kt`

- [ ] **Step 1: 新增 import**

在现有 import 块里补上：

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.feature.player.ui.PlayerDockPanel
```

- [x] **Step 2: 新增 `isPanelExpanded` 状态**

> **实际落地的最终版本**（已按 course correction 去掉 `isTabRootScreen` 判断——面板在任意页面都常驻展开）：

```kotlin
    // 悬浮播放卡片 + 导航栏的实际高度，下发给各页面用作列表底部留白
    var bottomOverlayHeight by remember { mutableStateOf(0.dp) }
    // 平板适配断点，顶层下发供 MelodiaBottomOverlay 及后续各阶段消费
    val windowSizeClass = rememberMelodiaWindowSizeClass()
    // Expanded 断点下播放器是否展开为常驻侧栏面板；与手机端 playerSheet 完全独立的状态机。
    // 面板在任意页面都保持展开态（不局限于 Tab 根页面），参照 Spotify 平板版
    var isPanelExpanded by remember { mutableStateOf(false) }
    val isPanelVisible = windowSizeClass == MelodiaWindowSizeClass.Expanded && isPanelExpanded
```

- [x] **Step 3: 返回键优先级链——最终决定不纳入面板收起**

> **实际落地的最终版本**：真机验证发现面板扩展到所有页面后，若返回键仍收面板，会导致二级页面要按两次返回键才能真正退出（第一次先收面板）。用户确认返回键应该完全不处理面板，面板只能通过自身的收起箭头/下拉手势关闭。所以这一步实际上**没有改动**返回键逻辑，仍是原样：

```kotlin
    // 系统返回键与侧滑返回拦截：按优先级关闭浮层或返回上一级。
    // 平板常驻播放面板不占用返回键——面板作为常驻工具栏跨页面持续展开（贴合 Spotify），
    // 只能通过自身的收起箭头/下拉手势关闭，返回键始终只处理内容导航
    val isAnyOverlayOpen = navigation.isNavigatingFromPlayer || playerSheet.isOpen || sidebar.isOpen || showCreateSheet || navigation.canNavigateBack

    BackHandler(enabled = isAnyOverlayOpen) {
        when {
            navigation.isNavigatingFromPlayer -> handleBack()
            playerSheet.isOpen -> {
                navigation.resetPlayerNavigation()
                playerSheet.animateTo(false, 0f)
            }
            sidebar.isOpen -> sidebar.close()
            showCreateSheet -> showCreateSheet = false
            navigation.canNavigateBack -> handleBack()
        }
    }
```

- [x] **Step 4: 主页面内容层改为 Row(内容区, 可选面板)，迷你播放条点击/拖拽按断点分支**

> **实际落地比下面的初版多一层**：course correction 追加了圆角卡片+间隙的视觉处理（`Row` 加 `Arrangement.spacedBy(MelodiaSpacing.sm)`，内容区在 `isPanelVisible` 时额外 `clip(RoundedCornerShape(InfoCardRadius))` + `border(...)`，面板固定套一层同样的 `clip`+`border`）。具体原因见文末"实施记录（与本文档初版计划的偏差）"第 3 点，最终代码以 `MelodiaApp.kt` 实际文件为准。

把：

```kotlin
        // 2. 主页面内容层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = sidebar.offsetX
                    clip = true
                    shape = RoundedCornerShape((sidebar.progress * 32).dp)
                    shadowElevation = (sidebar.progress * 30f)
                }
                .background(BackgroundDark)
        ) {
            CompositionLocalProvider(LocalBottomOverlayInset provides bottomOverlayHeight) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (playerSheet.isOpen) Modifier.haze(hazeState) else Modifier)
                ) {
                    MelodiaNavHost(
                        currentScreen = navigation.currentScreen,
                        homeViewModel = viewModel,
                        homeTab = navigation.homeTab,
                        showMusicNewWorks = navigation.showMusicNewWorks,
                        searchAutoFocus = navigation.searchAutoFocus,
                        onOpenSidebar = { sidebar.open() },
                        onLoginScreenVisibilityChanged = { isLoginScreenVisible = it },
                        onNavigateToPlaylist = { id, isAlbum -> navigation.openPlaylist(id, isAlbum) },
                        onNavigateToArtist = { id -> navigation.openArtist(id) },
                        onNavigateToRadio = { id -> navigation.openRadio(id) },
                        onNavigateToMv = { id, name -> navigation.openMvPlayer(id, name) },
                        onMvFullscreenChanged = { isMvFullscreen = it },
                        onNavigateToPlaylistCategory = { category -> navigation.openPlaylistCategory(category) },
                        onNavigateToProfile = { uid -> navigation.openProfile(uid) },
                        onNavigateToFollowList = { uid, mode -> navigation.openFollowList(uid, mode) },
                        onHomeTabSelected = { navigation.selectHomeTab(it) },
                        onShowMusicNewWorksChanged = { navigation.updateShowMusicNewWorks(it) },
                        onNavigateToSearch = { navigation.openSearch(autoFocus = true) },
                        onBack = { handleBack() }
                    )

                    // 创建菜单遮罩
                    if (showCreateSheet) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .pressable(MelodiaPress.None) { showCreateSheet = false }
                        )
                    }

                    // 放置在应用了平移 graphicsLayer 的主 Box 内部的底部
                    MelodiaBottomOverlay(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        currentScreen = navigation.currentScreen,
                        showCreateSheet = showCreateSheet,
                        isLoginScreenVisible = isLoginScreenVisible,
                        isMvFullscreen = isMvFullscreen,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        currentPositionProvider = currentPositionProvider,
                        duration = duration,
                        hazeState = hazeState,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onNext = { viewModel.playerManager.playNext() },
                        onMiniPlayerClick = { playerSheet.animateTo(true, 0f) },
                        onMiniPlayerDrag = { delta -> playerSheet.onDrag(delta) },
                        onMiniPlayerDragEnd = { velocity -> playerSheet.onDragEnd(velocity) },
                        previousQueueItem = previousQueueItem,
                        nextQueueItem = nextQueueItem,
                        onMiniPlayerPrevious = { viewModel.playerManager.skipToPrevious() },
                        onCreateDismiss = { showCreateSheet = false },
                        onNavigate = { navigation.openTab(it) },
                        onCreateClick = { showCreateSheet = !showCreateSheet },
                        showCreateEntry = showCreateEntry,
                        onOverlayHeightChanged = { bottomOverlayHeight = it }
                    )

                    // 侧边栏打开时的遮罩与点击收起事件
                    if (sidebar.progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f * sidebar.progress))
                                .pressable(
                                    style = MelodiaPress.None,
                                    enabled = sidebar.isOpen,
                                    onClick = { sidebar.close() }
                                )
                        )
                    }
                }
            }
        }
```

改为：

```kotlin
        // 2. 主页面内容层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = sidebar.offsetX
                    clip = true
                    shape = RoundedCornerShape((sidebar.progress * 32).dp)
                    shadowElevation = (sidebar.progress * 30f)
                }
                .background(BackgroundDark)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalBottomOverlayInset provides bottomOverlayHeight) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (playerSheet.isOpen) Modifier.haze(hazeState) else Modifier)
                    ) {
                        MelodiaNavHost(
                            currentScreen = navigation.currentScreen,
                            homeViewModel = viewModel,
                            homeTab = navigation.homeTab,
                            showMusicNewWorks = navigation.showMusicNewWorks,
                            searchAutoFocus = navigation.searchAutoFocus,
                            onOpenSidebar = { sidebar.open() },
                            onLoginScreenVisibilityChanged = { isLoginScreenVisible = it },
                            onNavigateToPlaylist = { id, isAlbum -> navigation.openPlaylist(id, isAlbum) },
                            onNavigateToArtist = { id -> navigation.openArtist(id) },
                            onNavigateToRadio = { id -> navigation.openRadio(id) },
                            onNavigateToMv = { id, name -> navigation.openMvPlayer(id, name) },
                            onMvFullscreenChanged = { isMvFullscreen = it },
                            onNavigateToPlaylistCategory = { category -> navigation.openPlaylistCategory(category) },
                            onNavigateToProfile = { uid -> navigation.openProfile(uid) },
                            onNavigateToFollowList = { uid, mode -> navigation.openFollowList(uid, mode) },
                            onHomeTabSelected = { navigation.selectHomeTab(it) },
                            onShowMusicNewWorksChanged = { navigation.updateShowMusicNewWorks(it) },
                            onNavigateToSearch = { navigation.openSearch(autoFocus = true) },
                            onBack = { handleBack() }
                        )

                        // 创建菜单遮罩
                        if (showCreateSheet) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .pressable(MelodiaPress.None) { showCreateSheet = false }
                            )
                        }

                        // 放置在应用了平移 graphicsLayer 的主 Box 内部的底部
                        MelodiaBottomOverlay(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            currentScreen = navigation.currentScreen,
                            showCreateSheet = showCreateSheet,
                            isLoginScreenVisible = isLoginScreenVisible,
                            isMvFullscreen = isMvFullscreen,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            currentPositionProvider = currentPositionProvider,
                            duration = duration,
                            hazeState = hazeState,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onNext = { viewModel.playerManager.playNext() },
                            onMiniPlayerClick = {
                                if (windowSizeClass == MelodiaWindowSizeClass.Expanded) {
                                    isPanelExpanded = true
                                } else {
                                    playerSheet.animateTo(true, 0f)
                                }
                            },
                            onMiniPlayerDrag = { delta ->
                                if (windowSizeClass != MelodiaWindowSizeClass.Expanded) playerSheet.onDrag(delta)
                            },
                            onMiniPlayerDragEnd = { velocity ->
                                if (windowSizeClass != MelodiaWindowSizeClass.Expanded) playerSheet.onDragEnd(velocity)
                            },
                            previousQueueItem = previousQueueItem,
                            nextQueueItem = nextQueueItem,
                            onMiniPlayerPrevious = { viewModel.playerManager.skipToPrevious() },
                            onCreateDismiss = { showCreateSheet = false },
                            onNavigate = { navigation.openTab(it) },
                            onCreateClick = { showCreateSheet = !showCreateSheet },
                            showCreateEntry = showCreateEntry,
                            onOverlayHeightChanged = { bottomOverlayHeight = it }
                        )

                        // 侧边栏打开时的遮罩与点击收起事件
                        if (sidebar.progress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f * sidebar.progress))
                                    .pressable(
                                        style = MelodiaPress.None,
                                        enabled = sidebar.isOpen,
                                        onClick = { sidebar.close() }
                                    )
                            )
                        }
                    }
                }

                // 平板常驻播放面板：仅 Tab 根页面 + Expanded + 展开态时显示
                AnimatedVisibility(
                    visible = isPanelVisible,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    PlayerDockPanel(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        currentPositionProvider = currentPositionProvider,
                        duration = duration,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onSeek = { viewModel.playerManager.seekTo(it) },
                        onClose = { isPanelExpanded = false },
                        onDragClose = { _, _ -> isPanelExpanded = false },
                        onArtistClick = { artistId -> navigation.openArtist(artistId) },
                        onAlbumClick = { albumId -> navigation.openPlaylist(albumId, isAlbum = true) },
                        onNavigateToProfile = { uid -> navigation.openProfile(uid) }
                    )
                }
            }
        }
```

- [x] **Step 5: 编译确认无误**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [x] **Step 6: 真机/模拟器验证**

> 实测记录：初版（面板仅在 Home/Search/Library 三个 Tab 根页面显示）在 Pixel Tablet AVD 上验证通过后，用户参照 Spotify 平板版截图给出了两点course correction，详见下方"实施记录（course correction）"，最终验证以 course correction 后的版本为准，全部通过。

- [x] **Step 7: Commit**

> 实际提交是 course correction 后的一次性 commit（见下方记录），不是本文档最初 Step 7 草拟的那条消息。

---

## 实施记录（与本文档初版计划的偏差）

初版按本文档 Task 1/2 原样实现并验证通过后，用户给出了两点关键修改意见（参照 Spotify 平板版截图），在此记录偏差原因和最终形态，供后续阅读者对照：

1. **面板可见性从"仅三个 Tab 根页面"放宽为"任意页面"**：初版遵照设计文档 5.3 节"二级页面打开时面板暂时隐藏"实现。用户对比 Spotify 平板版截图后指出，Spotify 的专辑/歌单详情页也是"单栏内容 + 常驻右侧面板"，面板不会因为进入详情页而隐藏。故删除了 `isTabRootScreen` 判断，`isPanelVisible` 简化为仅取决于 `windowSizeClass == Expanded && isPanelExpanded`。
2. **同时撤销了 Phase 2 的曲库列表-详情双栏**：面板扩展到所有页面后，真机实测发现"曲库双栏 + 播放面板"同时展开时（竖屏平板），详情栏被压缩到约 120dp，明显不可用——这正是 Phase 2 计划文档标注的三栏宽度预算风险。用户据此决定曲库直接改回和手机版一致的单栏内容（点开歌单整屏替换、带返回箭头），从根本上消除三栏共存的场景，而不是继续调窄列表栏宽度。`LibraryExpandedLayout.kt` 被删除，`PlaylistContent`/`PlaylistScreen` 的 `showTopBar` 开关随之回退。
3. **新增视觉处理**：内容区与面板之间加了 `MelodiaSpacing.sm` 间隙 + `InfoCardRadius`（16dp）圆角 + 0.5dp 白色描边（复用 Phase 0 已经用过的浮动卡片样式），面板展开时两者呈现为并排的两张独立圆角卡片，贴合 Spotify 参考图；面板不展开时内容区保持原样贴边，不加多余圆角。
4. **返回键不再收起面板**：面板扩展到所有页面后，实测发现"先收起面板再返回"的优先级会导致在详情页按一次返回键只收起面板、页面不退出，需要按两次。用户确认后改为返回键完全不处理面板——面板作为跨页面常驻工具栏，只能通过自身的收起箭头/下拉手势关闭，返回键始终只处理内容导航。

最终验证清单（course correction 后，Pixel Tablet AVD 实测全部通过）：
- 手机（窄宽度）：点迷你播放条仍拉起整屏播放器，行为和改动前完全一致——无回归。
- 平板 Expanded：点迷你播放条 → 右侧展开常驻面板（竖屏340dp/横屏400dp），内容区与面板呈两张圆角卡片+间隙；面板收起箭头/下拉 → 收回迷你播放条；导航到任意页面（含歌单详情等二级页）→ 面板保持展开，不隐藏；系统返回键 → 只处理内容导航（从歌单详情返回首页），面板不受影响；曲库变回单栏内容+返回箭头，不再有三栏拥挤问题。

## Commit

```
feat(player): 平板 Expanded 断点支持播放器常驻侧栏面板

新增 PlayerDockPanel，原样复用 FullPlayerScreen 内容，只是渲染宽度
从整屏变成固定宽度列（竖屏340/横屏400dp）；面板在任意页面都保持展开
（参照 Spotify 平板版），不再局限于三个 Tab 根页面。内容区与面板加上
圆角卡片+间隙的视觉处理。返回键不再收起面板，只处理内容导航，面板
只能通过自身的收起箭头/下拉手势关闭。

同时撤销曲库的列表-详情双栏（Phase 2）：改回和手机版一致的单栏内容+
返回箭头，消除了双栏叠加播放面板时详情栏被压缩到不可用宽度的问题。
PlaylistContent/PlaylistScreen 的 showTopBar 开关随之回退。

手机端抽屉式全屏播放器分支零改动。
```

提交哈希：`ee6586f`

---

## 完成后的状态

- 平板适配设计文档的断点体系（Phase 0）、首页自适应（Phase 1）、播放器常驻面板（本 Phase）均已落地并按 Spotify 参考图做了一轮 course correction。
- **曲库列表-详情双栏（Phase 2）已撤销**，不再是本轮适配范围的一部分——曲库现在和其余所有页面一样，遵循统一规则："内容区（单栏，跟手机版一致）+ 可选常驻播放面板"，不再有额外的列表/详情拆分。这是当前架构的最终简化形态，后续阶段不需要再对齐 Phase 2 曾经的双栏设计。
- 其余模块（搜索、播客、我的、设置等）在这套"内容区 + 可选面板"的统一规则下已经自动获得面板支持，不需要逐个模块单独适配——这比设计文档 2.2 节原先设想的"逐个模块套用双栏模式"要简单，因为双栏模式本身已经不存在了。
