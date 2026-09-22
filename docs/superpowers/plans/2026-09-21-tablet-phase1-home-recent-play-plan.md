# 平板适配 Phase 1：首页「最近播放」列数适配 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地平板适配设计文档第 6.1 节唯一需要改代码的点——首页「最近播放」区块的列数按 `MelodiaWindowSizeClass` 自适应，手机 2 列不变，平板（Expanded）改为 3 列，行数固定 3 行。`HomeSharedHeader`/`HomeShelfSection`/`ForYouSection` 不在本阶段范围内（横滑区靠 `LazyRow` 自然响应可用宽度，无需改代码）。

**Architecture:** 消费 [平板适配设计文档](../specs/2026-09-12-tablet-adaptation-design.md) 第 6.1 节 与 Phase 0 落地的 `LocalMelodiaWindowSizeClass`（[Phase 0 计划](2026-09-12-tablet-phase0-shell-plan.md)）。`RecentPlaySection`（`HomeRecentPlaySection.kt`）是手写 `chunked()` 分行的 `Row` 列表（不是 `LazyVerticalGrid`——嵌在外层 `LazyColumn` 里用懒加载网格会因无界高度约束崩溃，见文件顶部既有注释），本阶段只调整 chunk 大小和残行占位逻辑，不改变整体实现方式。

**Tech Stack:** Jetpack Compose，复用 Phase 0 的 `LocalMelodiaWindowSizeClass`，不引入新依赖。

## Global Constraints

- 断点消费只读 `LocalMelodiaWindowSizeClass.current`，不重新计算宽度（Phase 0 已在顶层 provide）。
- 手机分支（2 列、9 条→6 条数据）行为和现状完全一致，是本阶段的回归验证基线。
- 固定 3 行不变，条目数 = 列数 × 3（手机 2×3=6，平板 3×3=9），与用户确认的方案一致。
- 残行占位逻辑需要泛化为「按缺口数补对应个数的 `Spacer(weight(1f))`」，不能再假设余数只会是 0 或 1（`chunked(3)` 的余数可能是 0/1/2）。
- 注释中文简洁；不写占位符，每步都是完整代码。

---

### Task 1: RecentPlaySection 列数按断点自适应

**Files:**
- Modify: `app/src/main/java/com/lin0721/linmusic/feature/home/ui/HomeRecentPlaySection.kt`

**Interfaces:**
- Consumes: `LocalMelodiaWindowSizeClass`、`MelodiaWindowSizeClass`（Phase 0 产出）

- [x] **Step 1: 新增断点相关 import，`MAX_ITEMS` 改为 `MAX_ROWS` 语义**

把文件顶部：

```kotlin
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist

// 双列紧凑横条，一屏放得下六条。
private const val MAX_ITEMS = 6
private val RowHeight = 56.dp
```

改为：

```kotlin
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist

// 紧凑横条列表，固定 3 行；手机 2 列、平板（Expanded）3 列（见平板适配设计文档 6.1 节）
private const val MAX_ROWS = 3
private val RowHeight = 56.dp
```

- [x] **Step 2: `RecentPlaySection` 按断点算列数，泛化残行占位**

把：

```kotlin
@Composable
fun RecentPlaySection(
    items: List<RecentPlaylist>,
    onClick: (RecentPlaylist) -> Unit
) {
    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.lg)) {
        Text(
            text = "最近播放",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = HomeEdgePadding, end = HomeEdgePadding, bottom = 13.dp)
        )

        Column(
            modifier = Modifier.padding(horizontal = HomeEdgePadding),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items.take(MAX_ITEMS).chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    rowItems.forEach { item ->
                        RecentPlayRow(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { onClick(item) }
                        )
                    }
                    // 奇数条时补等宽占位，避免最后一条被拉成整行
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
```

改为：

```kotlin
@Composable
fun RecentPlaySection(
    items: List<RecentPlaylist>,
    onClick: (RecentPlaylist) -> Unit
) {
    if (items.isEmpty()) return

    val columns = if (LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded) 3 else 2

    Column(modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.lg)) {
        Text(
            text = "最近播放",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = HomeEdgePadding, end = HomeEdgePadding, bottom = 13.dp)
        )

        Column(
            modifier = Modifier.padding(horizontal = HomeEdgePadding),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items.take(columns * MAX_ROWS).chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    rowItems.forEach { item ->
                        RecentPlayRow(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { onClick(item) }
                        )
                    }
                    // 残行补齐等宽占位，避免最后一行被拉宽（chunked 余数可能是 0~columns-1 条）
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
```

`RecentPlayRow` 私有 Composable 及其内部实现不变。

- [x] **Step 3: 编译确认无误**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

> 实测记录：BUILD SUCCESSFUL，无新增警告。已 installDebug 到 Pixel Tablet AVD，但「最近播放」区块依赖登录态数据（`recentPlaylists` 未登录时为空，`RecentPlaySection` 直接 return），未登录状态下截图看不到这块，需要登录后才能肉眼确认平板下确实变成 3 列——**待用户登录验证，验证通过后再执行 Step 5 提交**。

- [x] **Step 4: 真机/模拟器验证**

Run: `./gradlew installDebug`

验证清单：
- 手机（或模拟器窄宽度，`screenWidthDp` < 600）：「最近播放」仍是 2 列 3 行（最多 6 条），视觉、间距和改动前完全一致（回归项，重点看）。
- 平板模拟器（`screenWidthDp` ≥ 600）：「最近播放」变成 3 列 3 行（最多 9 条），横竖屏都是 3 列（断点只看宽度）；数据源不足 9 条时最后一行残行的空位置用等宽占位补齐，不会被拉宽；点击任意一条能正常跳转播放列表。
- 首页其余区块（推荐卡片、横滑区、货架）不受影响。

> 实测记录：用户登录账号后在 Pixel Tablet AVD 上确认通过。

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/lin0721/linmusic/feature/home/ui/HomeRecentPlaySection.kt
git commit -m "$(cat <<'EOF'
feat(home): 首页最近播放区块支持平板三列布局

Expanded 断点下「最近播放」从 2 列 6 条改为 3 列 9 条，仍固定 3 行；
残行占位逻辑同步泛化以支持任意列数余数。手机分支行为不变。
EOF
)"
```

---

## 完成后的状态

- 首页唯一需要平板专项改动的点（6.1 节）落地：「最近播放」列数随断点自适应，手机 2 列、平板 3 列。
- `HomeSharedHeader`/`HomeShelfSection`/`ForYouSection` 按设计文档保持原样，靠自身 `LazyRow`/可用宽度自然响应，本阶段未触碰。
- Phase 1 完成，可以转入 Phase 2（曲库列表-详情双栏）。
