# 平板适配 Phase 0：断点体系 + 横屏解锁 + 底部悬浮组件并排 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地平板适配的基础设施——宽度断点体系、横屏解锁、以及 Expanded 断点下"Tab 组件 + 迷你播放组件"从手机的垂直堆叠改为左右并排的两个独立悬浮卡片。不涉及播放面板本体、曲库双栏、首页内容自适应，这些是 Phase 1-3 的范围。

**Architecture:** 消费 [平板适配设计文档](../specs/2026-09-12-tablet-adaptation-design.md) 第 4/5 节。新增 `MelodiaWindowSizeClass` 枚举 + `LocalMelodiaWindowSizeClass` CompositionLocal，在 `MelodiaApp()` 顶层 provide，供本阶段的 `MelodiaBottomOverlay` 消费，也供后续 Phase 1-3 复用。`MelodiaNavigationBar`/`MiniPlayerCard` 本身的内容逻辑不改，只调整外层容器的排布方式。

**Tech Stack:** Jetpack Compose / Material3，不引入新依赖（`NavigationRail`/`material3-window-size-class` 等均未使用，断点判断基于 `LocalConfiguration.screenWidthDp` 手写）。

## Global Constraints

- 断点阈值固定 `screenWidthDp >= 600` 为 `Expanded`，与横竖屏无关（设计文档 3.1 已确认）。
- Compact 分支（手机）行为和现状**完全一致**，是本计划里每一步都要回归验证的基线。
- 两个独立悬浮组件视觉圆角复用现有 `InfoCardRadius`（16dp）token，不新增圆角常量。
- 迷你播放组件在 Expanded 下的固定宽度先给一个可用的起始值（240dp），真机验证后如需调整在后续小改动里改常量即可，不影响架构。
- 这个仓库没有 Compose UI 自动化测试，沿用既定约定靠真机手动验证。
- 注释中文简洁；不写占位符，每步都是完整代码。

---

### Task 1: 断点体系基础设施

**Files:**
- Create: `app/src/main/java/com/lin0721/linmusic/core/ui/theme/WindowSize.kt`

**Interfaces:**
- Produces: `MelodiaWindowSizeClass`（枚举）、`rememberMelodiaWindowSizeClass()`、`LocalMelodiaWindowSizeClass`

- [x] **Step 1: 新增 WindowSize.kt**

创建 `app/src/main/java/com/lin0721/linmusic/core/ui/theme/WindowSize.kt`：

```kotlin
package com.lin0721.linmusic.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

// 平板适配断点：仅按可用宽度分两档，横竖屏行为一致（见平板适配设计文档第 4 节）
enum class MelodiaWindowSizeClass {
    Compact,
    Expanded
}

private const val EXPANDED_MIN_WIDTH_DP = 600

@Composable
fun rememberMelodiaWindowSizeClass(): MelodiaWindowSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) {
        if (widthDp >= EXPANDED_MIN_WIDTH_DP) {
            MelodiaWindowSizeClass.Expanded
        } else {
            MelodiaWindowSizeClass.Compact
        }
    }
}

val LocalMelodiaWindowSizeClass = staticCompositionLocalOf { MelodiaWindowSizeClass.Compact }
```

- [x] **Step 2: 编译确认无误**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 2: 顶层 provide 断点 CompositionLocal

**Files:**
- Modify: `app/src/main/java/com/lin0721/linmusic/MelodiaApp.kt`

**Interfaces:**
- Consumes: `rememberMelodiaWindowSizeClass()`、`LocalMelodiaWindowSizeClass`（Task 1）

- [x] **Step 1: 声明断点状态**

打开 `app/src/main/java/com/lin0721/linmusic/MelodiaApp.kt`，在 import 区追加：

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaWindowSizeClass
```

（`CompositionLocalProvider` 这个文件已经从别处间接可用的话以实际编译报错为准，没有则按上面补；`LocalBottomOverlayInset` 已经在用 `CompositionLocalProvider`，说明该 import 大概率已存在，实际操作时看编译器提示去重即可。）

把：

```kotlin
    // 悬浮播放卡片 + 导航栏的实际高度，下发给各页面用作列表底部留白
    var bottomOverlayHeight by remember { mutableStateOf(0.dp) }
```

改为：

```kotlin
    // 悬浮播放卡片 + 导航栏的实际高度，下发给各页面用作列表底部留白
    var bottomOverlayHeight by remember { mutableStateOf(0.dp) }
    // 平板适配断点，顶层下发供 MelodiaBottomOverlay 及后续各阶段消费
    val windowSizeClass = rememberMelodiaWindowSizeClass()
```

- [x] **Step 2: 包裹根 Box**

把函数体内根 `Box` 的开头：

```kotlin
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { playerSheet.onScreenSizeChanged(it.height.toFloat()) }
```

改为：

```kotlin
    CompositionLocalProvider(LocalMelodiaWindowSizeClass provides windowSizeClass) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { playerSheet.onScreenSizeChanged(it.height.toFloat()) }
```

（有意不重新缩进整个 Box 内部——Kotlin 不要求缩进匹配作用域，这样此步 diff 只在首尾两处，不触碰中间近 200 行不变的内容。IDE 里可以用"重新格式化代码"一次性修正缩进，不影响编译。）

`MelodiaApp` 函数体最后是：

```kotlin
        if (isDialogVisible && updateState !is UpdateUiState.Idle) {
            UpdateDialog(
                state = updateState,
                onDismiss = { updateManager.dismiss() },
                onIgnore = { updateManager.ignoreCurrentVersion() },
                onStartDownload = { updateManager.startDownload() },
                onInstall = { updateManager.retryInstall() }
            )
        }
    }
}
```

改为（在根 `Box` 的闭合 `}` 后面补一个 `}` 收掉 `CompositionLocalProvider`）：

```kotlin
        if (isDialogVisible && updateState !is UpdateUiState.Idle) {
            UpdateDialog(
                state = updateState,
                onDismiss = { updateManager.dismiss() },
                onIgnore = { updateManager.ignoreCurrentVersion() },
                onStartDownload = { updateManager.startDownload() },
                onInstall = { updateManager.retryInstall() }
            )
        }
    }
    }
}
```

- [x] **Step 3: 编译确认无误**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL（若报 `CompositionLocalProvider` 未导入，按提示补 import）

---

### Task 3: Manifest 解锁横屏

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [x] **Step 1: 移除 screenOrientation 锁定**

把：

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.Melodia"
            android:screenOrientation="portrait"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden">
```

改为：

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.Melodia"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden">
```

`configChanges` 已经包含 `orientation|screenSize|screenLayout|smallestScreenSize`，旋转/窗口尺寸变化不会重建 Activity（设计文档第 1 节已确认），这里只是去掉强制锁定，不需要额外处理 `onConfigurationChanged`。

- [x] **Step 2: 真机验证**

Run: `./gradlew installDebug`

在手机上验证：应用可以跟着系统旋转到横屏（此时 Phase 0 后续 Task 还没做完，各页面横屏下的样子还是手机单栏拉伸，属于预期中间态，不是本 Task 的验收范围——本 Task 只验证"能转、不闪退、不重建"）。

> 实测记录：Pixel Tablet AVD 上验证，横屏 w1280dp/竖屏 w800dp 均正常，未重建 Activity。

---

### Task 4: MelodiaNavigationBar 支持 Expanded 悬浮态

**Files:**
- Modify: `app/src/main/java/com/lin0721/linmusic/core/ui/components/MelodiaBottomBar.kt`

**Interfaces:**
- Produces: `MelodiaNavigationBar` 新增参数 `expanded: Boolean = false`

- [x] **Step 1: 新增 expanded 参数与圆角/insets 分支**

打开 `app/src/main/java/com/lin0721/linmusic/core/ui/components/MelodiaBottomBar.kt`，在 import 区追加：

```kotlin
import androidx.compose.ui.graphics.RectangleShape
```

把：

```kotlin
//底部导航栏  
@Composable
fun MelodiaNavigationBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onCreateClick: () -> Unit,
    isCreateMenuOpen: Boolean,
    showCreateEntry: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BackgroundDark,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp)
                .padding(top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
```

改为：

```kotlin
//底部导航栏。expanded=true 时用于平板宽屏下的独立悬浮卡片（圆角、不贴屏幕边缘），
//expanded=false 时是手机上贴底通栏的现状实现
@Composable
fun MelodiaNavigationBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onCreateClick: () -> Unit,
    isCreateMenuOpen: Boolean,
    showCreateEntry: Boolean = true,
    expanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BackgroundDark,
        shape = if (expanded) RoundedCornerShape(InfoCardRadius) else RectangleShape,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier else Modifier.navigationBarsPadding())
                .height(60.dp)
                .padding(top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
```

`expanded` 态跳过 `navigationBarsPadding()`：这条悬浮卡片和迷你播放卡片是并排的两个独立组件，系统手势条的避让统一由外层 `MelodiaBottomOverlay` 的 Expanded 分支一次性处理（Task 5），两个组件各自再叠加一次会导致底部空隙翻倍。

- [x] **Step 2: 编译确认无误**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 5: MelodiaBottomOverlay 的 Expanded 分支——两个独立悬浮组件并排

**Files:**
- Modify: `app/src/main/java/com/lin0721/linmusic/MelodiaOverlays.kt`

**Interfaces:**
- Consumes: `LocalMelodiaWindowSizeClass`（Task 1/2）、`MelodiaNavigationBar(expanded = true)`（Task 4）

- [x] **Step 1: 新增 Expanded 宽度常量 + import**

打开 `app/src/main/java/com/lin0721/linmusic/MelodiaOverlays.kt`，在 import 区追加：

```kotlin
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
```

在文件顶部 `LocalBottomOverlayInset` 声明附近追加：

```kotlin
// Expanded 断点下迷你播放悬浮组件的固定宽度，真机验证后可再调整这个数值
private val ExpandedMiniPlayerWidth = 240.dp
```

- [x] **Step 2: MelodiaBottomOverlay 按断点分两条渲染路径**

把 `MelodiaBottomOverlay` 函数体里"悬浮播放卡片 + 导航栏"这一段：

```kotlin
        // 悬浮播放卡片 + 导航栏：实际占用高度上报出去，供页面内容计算底部留白
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    onOverlayHeightChanged(with(density) { it.height.toDp() })
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 浮动播放卡片
            AnimatedVisibility(
                visible = currentTrack != null && !isLoginScreenVisible && !isMvFullscreen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxWidth()
            ) {
                MiniPlayerCard(
                    hazeState = hazeState,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    currentPositionProvider = currentPositionProvider,
                    duration = duration,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onClick = onMiniPlayerClick,
                    onDrag = onMiniPlayerDrag,
                    onDragEnd = onMiniPlayerDragEnd,
                    previousQueueItem = previousQueueItem,
                    nextQueueItem = nextQueueItem,
                    onPrevious = onMiniPlayerPrevious,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MelodiaSpacing.sm)
                )
            }

            // 2. M3 导航栏 (在非登录状态下显示)
            AnimatedVisibility(
                visible = !isLoginScreenVisible && !isMvFullscreen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                MelodiaNavigationBar(
                    currentScreen = currentScreen,
                    onNavigate = onNavigate,
                    onCreateClick = onCreateClick,
                    isCreateMenuOpen = showCreateSheet,
                    showCreateEntry = showCreateEntry
                )
            }
        }
```

改为：

```kotlin
        // 悬浮播放卡片 + 导航栏：实际占用高度上报出去，供页面内容计算底部留白
        val windowSizeClass = LocalMelodiaWindowSizeClass.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    onOverlayHeightChanged(with(density) { it.height.toDp() })
                }
        ) {
            if (windowSizeClass == MelodiaWindowSizeClass.Expanded) {
                // 平板宽屏：Tab 组件 + 迷你播放组件左右并排，两个独立悬浮卡片，
                // 都不贴屏幕边缘；系统手势条避让在这一层统一处理一次
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = MelodiaSpacing.sm)
                        .padding(bottom = MelodiaSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm),
                    verticalAlignment = Alignment.Bottom
                ) {
                    AnimatedVisibility(
                        visible = !isLoginScreenVisible && !isMvFullscreen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                        modifier = Modifier.weight(1f)
                    ) {
                        MelodiaNavigationBar(
                            currentScreen = currentScreen,
                            onNavigate = onNavigate,
                            onCreateClick = onCreateClick,
                            isCreateMenuOpen = showCreateSheet,
                            showCreateEntry = showCreateEntry,
                            expanded = true
                        )
                    }

                    AnimatedVisibility(
                        visible = currentTrack != null && !isLoginScreenVisible && !isMvFullscreen,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        MiniPlayerCard(
                            hazeState = hazeState,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            currentPositionProvider = currentPositionProvider,
                            duration = duration,
                            onTogglePlay = onTogglePlay,
                            onNext = onNext,
                            onClick = onMiniPlayerClick,
                            onDrag = onMiniPlayerDrag,
                            onDragEnd = onMiniPlayerDragEnd,
                            previousQueueItem = previousQueueItem,
                            nextQueueItem = nextQueueItem,
                            onPrevious = onMiniPlayerPrevious,
                            modifier = Modifier.width(ExpandedMiniPlayerWidth)
                        )
                    }
                }
            } else {
                // 手机：迷你播放卡在上、导航栏在下，垂直堆叠（现状不变）
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(
                        visible = currentTrack != null && !isLoginScreenVisible && !isMvFullscreen,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MiniPlayerCard(
                            hazeState = hazeState,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            currentPositionProvider = currentPositionProvider,
                            duration = duration,
                            onTogglePlay = onTogglePlay,
                            onNext = onNext,
                            onClick = onMiniPlayerClick,
                            onDrag = onMiniPlayerDrag,
                            onDragEnd = onMiniPlayerDragEnd,
                            previousQueueItem = previousQueueItem,
                            nextQueueItem = nextQueueItem,
                            onPrevious = onMiniPlayerPrevious,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MelodiaSpacing.sm)
                        )
                    }

                    AnimatedVisibility(
                        visible = !isLoginScreenVisible && !isMvFullscreen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        MelodiaNavigationBar(
                            currentScreen = currentScreen,
                            onNavigate = onNavigate,
                            onCreateClick = onCreateClick,
                            isCreateMenuOpen = showCreateSheet,
                            showCreateEntry = showCreateEntry
                        )
                    }
                }
            }
        }
```

- [x] **Step 3: 编译确认无误**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [x] **Step 4: 真机/模拟器验证**

Run: `./gradlew installDebug`

验证清单：
- 手机（或模拟器窄宽度）：迷你播放卡 + 导航栏垂直堆叠，和改动前视觉、动效完全一致（这是回归项，重点看）。
- 平板模拟器（如 Pixel Tablet AVD，`screenWidthDp` ≥ 600）：Tab 组件与迷你播放组件左右并排，各自圆角、不贴左右/底部边缘，中间有可见缝隙；旋转横竖屏都保持这个并排布局（因为断点只看宽度）；未播放歌曲时迷你播放组件消失、Tab 组件独占宽度；点创建按钮弹出的菜单位置和层级不受影响。
- 登录网页/MV 全屏时，两个组件按现状逻辑一起隐藏（`isLoginScreenVisible`/`isMvFullscreen` 分支未改）。

> 实测记录（Pixel Tablet AVD，emulator-5554）：
> - 横屏 w1280dp、竖屏 w800dp 下均为 Tab 组件+迷你播放组件左右并排悬浮，圆角、四周留边距，未播放时 Tab 组件独占全宽——符合预期。
> - 有track播放时，迷你播放组件与 Tab 组件各自独立圆角卡片，中间留可见缝隙，未合并——符合预期。
> - 用 `wm size 1080x1920`（w540dp，模拟手机）验证 Compact 分支：迷你播放卡+导航栏垂直堆叠、贴底贴边通栏，和改动前视觉一致，无回归。

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/lin0721/linmusic/core/ui/theme/WindowSize.kt app/src/main/java/com/lin0721/linmusic/MelodiaApp.kt app/src/main/AndroidManifest.xml app/src/main/java/com/lin0721/linmusic/core/ui/components/MelodiaBottomBar.kt app/src/main/java/com/lin0721/linmusic/MelodiaOverlays.kt
git commit -m "$(cat <<'EOF'
feat(tablet): 建立平板断点体系，解锁横屏，底部导航与迷你播放条改为并排悬浮

新增 MelodiaWindowSizeClass 按 screenWidthDp>=600 分两档，顶层下发。
Expanded 断点下 Tab 栏与迷你播放卡从垂直堆叠改为左右并排的两个独立
悬浮卡片，手机分支行为不变。为后续曲库双栏、播放面板阶段打基础。
EOF
)"
```

---

## 完成后的状态

- 断点体系就绪：`rememberMelodiaWindowSizeClass()` / `LocalMelodiaWindowSizeClass` 可在任意 Compose 层级读取，后续 Phase 1（首页）、Phase 2（曲库双栏）、Phase 3（播放面板）都消费同一套断点，不用各自重复实现。
- 横屏解锁，Activity 不因旋转重建。
- Expanded 断点下应用壳层的"两个独立悬浮组件并排"落地，是 Phase 3 播放面板展开/收起交互的直接基础（迷你播放组件后续在 Phase 3 里补上"点击展开面板"的行为，本阶段先只做尺寸/布局）。
- **尚未做**：迷你播放组件的固定宽度（240dp）、两组件间距、外边距目前是给的起始经验值，真机上具体观感如何、要不要微调，需要你在真机上看一眼后决定要不要在此基础上继续，还是先调整数值再进 Phase 1。
- 首页/曲库内容本身、播放面板本体在这个阶段都还没有开始动，是 Phase 1/2/3 的范围。
