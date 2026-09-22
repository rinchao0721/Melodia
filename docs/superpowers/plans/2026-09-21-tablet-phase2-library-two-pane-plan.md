# 平板适配 Phase 2：曲库「列表-详情」双栏（歌单/专辑） 实现计划

> **⚠️ 已撤销（见 [Phase 3 计划文档](2026-09-22-tablet-phase3-player-dock-panel-plan.md) 的"实施记录"）**：本文档描述的曲库双栏在 Phase 3 落地播放器常驻面板后被撤销。原因：面板扩展到所有页面后，"曲库双栏 + 播放面板"竖屏同时展开会把详情栏压缩到约 120dp，不可用；用户据此决定曲库改回和手机版一致的单栏内容+返回箭头，彻底消除三栏共存场景，而不是继续调窄列表栏宽度。`LibraryExpandedLayout.kt` 已删除，`PlaylistContent`/`PlaylistScreen` 的 `showTopBar` 开关已回退。本文档保留作为历史记录，不代表当前代码状态。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地平板适配设计文档第 6.2 节——Expanded 断点下曲库变成「左：列表，右：详情」双栏。本轮范围收窄为歌单/专辑（已与用户确认，见下方“范围与已确认决策”），歌手嵌入版留到后续阶段；歌手点击仍走现状整屏跳转。

**Architecture:** 消费 Phase 0 的 `LocalMelodiaWindowSizeClass`。关键发现：`LibraryScreen` 的点击回调（`onPlaylistClick`/`onArtistClick`/`onAlbumClick`）本来就是外部注入的 lambda（不是内部直接调用导航），因此双栏所需的“点击改为更新选中态而不是整屏跳转”**不需要改动 `LibraryScreen.kt`/`LibraryViewModel.kt` 一行代码**——只需要在 `MelodiaNavHost.kt` 的 `Screen.Library` 分支按断点传入不同的回调实现，并新增一个承载“选中态 + 左右两栏布局”的 `LibraryExpandedLayout` 组件。详情栏复用 `PlaylistScreen`（本身已经承载了收藏/评论/编辑/重排等全部弹层逻辑，不是只有 `PlaylistContent`），新增 `showTopBar` 开关跳过折叠头部的顶栏渲染。

**Tech Stack:** Jetpack Compose，复用 Phase 0 的 `LocalMelodiaWindowSizeClass`，不引入新依赖，不新建 ViewModel 状态（选中态是纯 UI 导航状态，用 `remember` 放在 Compose 层，不下沉到 `LibraryViewModel`）。

## 范围与已确认决策（与用户对齐）

1. **详情内容复用方式**：给 `PlaylistScreen`/`PlaylistContent` 加 `showTopBar: Boolean = true` 开关，右栏调用时传 `false`，跳过顶栏渲染分支。Compact 完整版走默认值 `true`，不受影响。
2. **本轮范围**：只做歌单/专辑（`PLAYLIST`/`ALBUM`，共用同一套 `PlaylistScreen`，靠 `isAlbum` 区分）。歌手点击（`onArtistClick`）在 Expanded 下**仍走现状整屏跳转**，不做嵌入版——这也是设计文档 5.3 节"二级页面维持整屏覆盖"决策的自然延伸，本轮没有把 Artist 纳入双栏范围，直接复用该决策即可，不用另外发明规则。
3. **列表栏宽度策略**：目标行为是「播放面板展开时列表栏联动收紧（340dp→240dp）」。**但目前 Phase 3（播放面板常驻侧栏）还没实现**——现状的 `FullPlayerScreen` 仍然是整屏覆盖式播放器（见 `MelodiaOverlays.kt` 的 `MelodiaFullPlayerOverlay`，`isPlayerOpen` 为真时直接 `fillMaxSize` 盖住包括曲库双栏的一切），不存在"播放面板以侧栏形式和内容区并排"这个状态，因此也没有真实的"面板是否展开"信号可以绑定收紧逻辑。**本阶段列表栏固定 340dp**（对应"面板收起"这一唯一现实存在的状态），联动收紧到 240dp 的逻辑放到 Phase 3 落地播放面板、真正产生"面板展开"信号时再接上——这不是范围缩水，是被依赖顺序推迟，Phase 3 计划里需要回来补这一块。

## Global Constraints

- 不改 `LibraryScreen.kt`/`LibraryViewModel.kt` 内部实现，只在 `MelodiaNavHost.kt` 按断点传入不同回调 + 用 `Box(Modifier.width(...))` 从外部约束宽度。
- 选中态用 `remember { mutableStateOf<LibrarySelection?>(null) }`，不用 `rememberSaveable`——Phase 0 已验证本应用旋转不重建 Activity，`remember` 状态天然survive 旋转。
- 返回键：本地 `BackHandler(enabled = selection != null) { selection = null }`，仿照 `LibraryScreen.kt:121-123` 现有的 `isReorderMode` 本地拦截惯例，不下沉到 `MelodiaApp.kt` 的全局返回优先级链。`LibraryScreen` 内部已有的重排模式 `BackHandler` 是它的子节点，Compose 的 `OnBackPressedDispatcher` 按注册顺序（内层先注册先拦截）已经能保证"先取消重排 → 再清空选中 → 最后才退出曲库"的正确优先级，不需要额外处理。
- 详情栏里点专辑（`onAlbumClick`）更新选中态、留在双栏内；点歌手（`onArtistClick`）/跳转个人主页（`onNavigateToProfile`）继续调用外部整屏导航——这是二级页面维持整屏覆盖的既有决策，不是新规则。
- 中文注释精简，不写占位符，每步都是完整代码。

---

### Task 1: PlaylistContent/PlaylistScreen 支持 showTopBar 开关

**Files:**
- Modify: `app/src/main/java/com/lin0721/linmusic/feature/playlist/ui/PlaylistContent.kt`
- Modify: `app/src/main/java/com/lin0721/linmusic/feature/playlist/ui/PlaylistScreen.kt`

- [x] **Step 1: `PlaylistContent` 新增 `showTopBar` 参数，跳过顶栏渲染，状态栏高度只在有顶栏时计入**

把签名里的：

```kotlin
fun PlaylistContent(
    playlist: PlaylistDetail,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedSongIds: Set<Long>,
    collectState: PlaylistCollectState,
    isLoggedIn: Boolean,
    recommendedSongs: List<Track>,
    onBack: () -> Unit,
```

改为：

```kotlin
fun PlaylistContent(
    playlist: PlaylistDetail,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedSongIds: Set<Long>,
    collectState: PlaylistCollectState,
    isLoggedIn: Boolean,
    recommendedSongs: List<Track>,
    showTopBar: Boolean = true,
    onBack: () -> Unit,
```

把函数体里的：

```kotlin
    // 获取系统状态栏高度
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Overlay 总高度：状态栏 + 操作区(56dp)
    val overlayHeight = TOP_BAR_HEIGHT + statusBarHeight
```

改为：

```kotlin
    // 获取系统状态栏高度；嵌入到平板双栏详情栏时（showTopBar=false）不在系统状态栏下，不计入
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val effectiveStatusBarHeight = if (showTopBar) statusBarHeight else 0.dp
    // Overlay 总高度：状态栏 + 操作区(56dp)，无顶栏时仍保留 56dp 作为吸底播放按钮/搜索栏的定位基准
    val overlayHeight = TOP_BAR_HEIGHT + effectiveStatusBarHeight
```

把 `PlaylistHeaderItem` 调用里的：

```kotlin
                PlaylistHeaderItem(
                    playlist            = playlist,
                    coverSize           = coverSize,
                    coverAlpha          = coverAlpha,
                    progress            = progress,
                    statusBarHeight     = statusBarHeight,
                    dominantColor       = dominantColor,
```

改为：

```kotlin
                PlaylistHeaderItem(
                    playlist            = playlist,
                    coverSize           = coverSize,
                    coverAlpha          = coverAlpha,
                    progress            = progress,
                    statusBarHeight     = effectiveStatusBarHeight,
                    dominantColor       = dominantColor,
```

把：

```kotlin
        // ── 3. 固定 Overlay ───────────────────────────────────────────────
        PlaylistTopBar(
            title           = playlist.name,
            progress        = progress,
            overlayHeight   = overlayHeight,
            statusBarHeight = statusBarHeight,
            dominantColor   = dominantColor,
            onBack          = onBack
        )
```

改为：

```kotlin
        // ── 3. 固定 Overlay（平板双栏详情栏内嵌入时不需要顶栏，由右栏容器自己处理返回）────
        if (showTopBar) {
            PlaylistTopBar(
                title           = playlist.name,
                progress        = progress,
                overlayHeight   = overlayHeight,
                statusBarHeight = effectiveStatusBarHeight,
                dominantColor   = dominantColor,
                onBack          = onBack
            )
        }
```

- [x] **Step 2: `PlaylistScreen` 新增 `showTopBar` 参数并透传**

把签名：

```kotlin
fun PlaylistScreen(
    playlistId: Long,
    isAlbum: Boolean = false,
    viewModel: PlaylistViewModel = koinViewModel(),
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onNavigateToProfile: (Long) -> Unit = {}
) {
```

改为：

```kotlin
fun PlaylistScreen(
    playlistId: Long,
    isAlbum: Boolean = false,
    showTopBar: Boolean = true,
    viewModel: PlaylistViewModel = koinViewModel(),
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onNavigateToProfile: (Long) -> Unit = {}
) {
```

在调用 `PlaylistContent(...)` 处（`playlist = state.playlist,` 那一段开头）新增一行：

```kotlin
                    PlaylistContent(
                        playlist       = state.playlist,
                        trackPlayCounts = state.trackPlayCounts,
                        showTopBar     = showTopBar,
                        canRemoveFromPlaylist = isOwnedPlaylist,
```

（即在既有的 `playlist`/`trackPlayCounts` 两行后面插入 `showTopBar = showTopBar,`，其余参数不变。）

- [x] **Step 3: 编译确认无误**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 2: 新增 LibraryExpandedLayout——左栏列表 + 右栏详情

**Files:**
- Create: `app/src/main/java/com/lin0721/linmusic/feature/library/ui/LibraryExpandedLayout.kt`

**Interfaces:**
- Consumes: `LibraryScreen`（原样复用，不改）、`PlaylistScreen`（Task 1 产出的 `showTopBar` 开关）、`EmptyState`（`core/ui/components/EmptyState.kt`）

- [x] **Step 1: 新建文件，完整实现**

```kotlin
package com.lin0721.linmusic.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistScreen

// 曲库 Expanded 断点下的列表栏固定宽度。目标是"播放面板展开时联动收紧到 240dp"，
// 但播放面板常驻侧栏（Phase 3）还没落地，现状只有这一个宽度，收紧逻辑等 Phase 3
// 产出"面板是否展开"的真实信号后再接上（见平板适配设计文档 6.2/7 节）
private val LibraryListColumnWidth = 340.dp

// 曲库双栏右栏当前选中的详情项。歌手本轮不做嵌入版，点击仍走整屏跳转，故这里只有歌单/专辑一种
private sealed class LibrarySelection {
    data class Playlist(val id: Long, val isAlbum: Boolean) : LibrarySelection()
}

// Expanded 断点下的曲库：左栏是原样的 LibraryScreen（点击不再整屏跳转，改为更新选中态），
// 右栏渲染选中项的嵌入版详情（复用 PlaylistScreen，showTopBar=false）
@Composable
fun LibraryExpandedLayout(
    onArtistClick: (Long) -> Unit,
    onNavigateToProfile: (Long) -> Unit,
    onOpenSidebar: () -> Unit,
    onLoginScreenVisibilityChanged: (Boolean) -> Unit
) {
    var selection by remember { mutableStateOf<LibrarySelection?>(null) }

    // 返回键先清空选中回空态，再按一次才走外层默认返回（退出曲库/切 Tab）——已与用户确认
    BackHandler(enabled = selection != null) {
        selection = null
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .width(LibraryListColumnWidth)
                .fillMaxHeight()
        ) {
            LibraryScreen(
                onPlaylistClick = { id -> selection = LibrarySelection.Playlist(id, isAlbum = false) },
                onArtistClick = onArtistClick,
                onAlbumClick = { id -> selection = LibrarySelection.Playlist(id, isAlbum = true) },
                onBack = {},
                onOpenSidebar = onOpenSidebar,
                onLoginScreenVisibilityChanged = onLoginScreenVisibilityChanged
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (val current = selection) {
                null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "选择左侧的歌单或专辑",
                            subtitle = "详情会显示在这里"
                        )
                    }
                }
                is LibrarySelection.Playlist -> {
                    PlaylistScreen(
                        playlistId = current.id,
                        isAlbum = current.isAlbum,
                        showTopBar = false,
                        onBack = { selection = null },
                        onArtistClick = onArtistClick,
                        onAlbumClick = { id -> selection = LibrarySelection.Playlist(id, isAlbum = true) },
                        onNavigateToProfile = onNavigateToProfile
                    )
                }
            }
        }
    }
}
```

- [x] **Step 2: 编译确认无误**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 3: MelodiaNavHost 按断点分流 Screen.Library

**Files:**
- Modify: `app/src/main/java/com/lin0721/linmusic/MelodiaNavHost.kt`

- [x] **Step 1: 新增断点相关 import**

在既有 import 块最后新增：

```kotlin
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.feature.library.ui.LibraryExpandedLayout
```

- [x] **Step 2: `Screen.Library` 分支按断点渲染**

把：

```kotlin
            is Screen.Library -> {
                com.lin0721.linmusic.feature.library.ui.LibraryScreen(
                    onPlaylistClick = { id -> onNavigateToPlaylist(id, false) },
                    onArtistClick = onNavigateToArtist,
                    onAlbumClick = { id -> onNavigateToPlaylist(id, true) },
                    onBack = onBack,
                    onOpenSidebar = onOpenSidebar,
                    onLoginScreenVisibilityChanged = onLoginScreenVisibilityChanged
                )
            }
```

改为：

```kotlin
            is Screen.Library -> {
                if (LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded) {
                    LibraryExpandedLayout(
                        onArtistClick = onNavigateToArtist,
                        onNavigateToProfile = onNavigateToProfile,
                        onOpenSidebar = onOpenSidebar,
                        onLoginScreenVisibilityChanged = onLoginScreenVisibilityChanged
                    )
                } else {
                    com.lin0721.linmusic.feature.library.ui.LibraryScreen(
                        onPlaylistClick = { id -> onNavigateToPlaylist(id, false) },
                        onArtistClick = onNavigateToArtist,
                        onAlbumClick = { id -> onNavigateToPlaylist(id, true) },
                        onBack = onBack,
                        onOpenSidebar = onOpenSidebar,
                        onLoginScreenVisibilityChanged = onLoginScreenVisibilityChanged
                    )
                }
            }
```

- [x] **Step 3: 编译确认无误**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [x] **Step 4: 真机/模拟器验证**

Run: `./gradlew installDebug`

验证清单：
- 手机（或模拟器窄宽度，`screenWidthDp` < 600）：曲库和改动前完全一致，点歌单/专辑/歌手都整屏跳转（回归项，重点看）。
- 平板模拟器（`screenWidthDp` ≥ 600）：曲库变成左右双栏；左栏点歌单/专辑更新右栏详情（不整屏跳转），右栏没有顶栏和返回箭头；右栏详情里点专辑能在右栏内切换到新专辑；点歌手仍整屏跳转覆盖双栏（含左栏）；系统返回键：有选中时先清空选中回空态，再按一次才退出曲库回首页；未选中时右栏显示居中空态提示。
- 左栏的搜索/筛选/排序/创建歌单/登录弹层等原有功能在双栏下正常可用（复用的是原样 `LibraryScreen`，不应该有额外问题，但要看一眼）。
- 右栏嵌入的详情页收藏、评论、更多菜单等弹层正常弹出（这些是系统级 Dialog/BottomSheet，不受父容器宽度限制，应该和手机上表现一致）。
- 详情栏内容在窄宽度（约 340-460dp）下没有明显跑版；封面/进度条/按钮行看起来是"变小"而不是被截断或重叠。

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/lin0721/linmusic/feature/playlist/ui/PlaylistContent.kt
git add app/src/main/java/com/lin0721/linmusic/feature/playlist/ui/PlaylistScreen.kt
git add app/src/main/java/com/lin0721/linmusic/feature/library/ui/LibraryExpandedLayout.kt
git add app/src/main/java/com/lin0721/linmusic/MelodiaNavHost.kt
git commit -m "$(cat <<'EOF'
feat(library): 曲库 Expanded 断点支持列表-详情双栏（歌单/专辑）

新增 LibraryExpandedLayout：左栏复用现有 LibraryScreen（点击改为更新
选中态而非整屏跳转），右栏用 showTopBar=false 的 PlaylistScreen 渲染
详情。歌手本轮仍整屏跳转，嵌入版留后续阶段。列表栏固定 340dp，播放面板
展开时联动收紧的逻辑要等 Phase 3 落地面板后才有信号可接。手机分支不变。
EOF
)"
```

---

## 完成后的状态

- 曲库在 Expanded 断点下有了「列表-详情」双栏（歌单/专辑），手机分支零改动。
- `PlaylistScreen`/`PlaylistContent` 多了一个 `showTopBar` 开关，为后续任何需要嵌入式复用这套详情页的场景（不限于曲库）留了一个通用口子。
- 已知遗留项（不是 bug，是显式推迟）：列表栏宽度暂时固定 340dp，"播放面板展开时联动收紧到 240dp" 的联动要等 Phase 3 做完播放面板常驻侧栏后才能接上真实信号，需要在 Phase 3 计划里回来处理。
- 歌手嵌入版详情栏留到后续阶段，当前歌手点击行为不变（整屏跳转）。
