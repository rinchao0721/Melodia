# 平板适配 Phase 4：全应用页面密度重设计 + 横竖屏分方向设计文档

日期：2026-09-22
状态：**草案，待确认**
工作区：`worktree-tablet-adaptation`（`F:\dev\Melodia-worktree-tablet-adaptation`）
前置文档：`docs/superpowers/specs/2026-09-12-tablet-adaptation-design.md`（Phase 0-3，已实现，本文档会**正式推翻**其中两条决策，见第 2 节）

## 1. 背景

Phase 0-3 已经落地：断点体系（`MelodiaWindowSizeClass.Compact/Expanded`，仅看 `screenWidthDp ≥ 600`）、首页最近播放 3 列、播放器常驻侧栏面板（`PlayerDockPanel`）、底部 Tab 栏与迷你播放条并排悬浮。这些今天在 Pixel Tablet 模拟器上跑起来后，暴露了两个问题：

1. **首页等页面"元素太小"**——货架卡片（`HomeShelfSection`/`HomeForYouSection`）用的是手机端定死的 `150dp`/`132dp` 固定宽度，宽屏下不会跟着放大或重排，只是同一批小卡片摊在更宽的画布上，显得稀疏、留白失控。
2. **横屏体验缺失**——Phase 0-3 的断点故意不区分横竖屏（"断点触发维度：仅看 screenWidthDp 宽度"），横屏平板等同于一个更宽的竖屏平板，没有针对横屏可用高度变矮、宽度更富余的特点做任何调整。

同时全应用还有 13+ 个模块（Phase 0-3 文档 2.2 节列出的"其余模块"）完全是手机单栏布局，从未响应过断点。

## 2. 本文档正式推翻的两条 Phase 0-3 决策

| 决策点 | 原决策（Phase 0-3） | 新决策（本文档） | 理由 |
|---|---|---|---|
| 断点触发维度 | 仅看宽度，横竖屏行为一致 | **新增方向维度**，宽度 × 方向组合判断 | 用户明确要求横屏要有专属布局，不能只是"更宽的竖屏" |
| 首页内容结构 | 不做平板专属重设计，指望 `LazyRow`/网格自然重排解决 | **按断点重新设计各货架的卡片密度与尺寸**（非等比缩放） | 自然重排不够——货架卡片宽度是手机端写死的 dp 值，不会响应容器宽度变化，必须显式介入 |

`docs/superpowers/specs/2026-09-12-tablet-adaptation-design.md` 6.2 节（曲库列表-详情双栏）已经在实现过程中被 `ee6586f` 提交撤销，本文档视其为**已废弃**，不再引用；6.2 节的撤销理由（见第 4 节）直接影响本文档对其余"列表+详情"型页面的设计原则。

## 3. 断点体系修订

新增方向维度，与现有宽度维度独立组合，不改动 `MelodiaWindowSizeClass` 本身（现有调用点不用改）：

```kotlin
// core/ui/theme/WindowSize.kt

enum class MelodiaWindowSizeClass { Compact, Expanded }   // 不变

enum class MelodiaOrientationClass { Portrait, Landscape }  // 新增

@Composable
fun rememberMelodiaOrientationClass(): MelodiaOrientationClass {
    val configuration = LocalConfiguration.current
    return if (configuration.screenWidthDp >= configuration.screenHeightDp) {
        MelodiaOrientationClass.Landscape
    } else {
        MelodiaOrientationClass.Portrait
    }
}

val LocalMelodiaOrientationClass = staticCompositionLocalOf { MelodiaOrientationClass.Portrait }
```

在 `MelodiaApp.kt` 顶层与 `LocalMelodiaWindowSizeClass` 一起 `provides`。调用点按需组合两个断点，例如：

```kotlin
val columns = when {
    windowSizeClass == Expanded && orientationClass == Landscape -> 5
    windowSizeClass == Expanded -> 4
    else -> 2
}
```

**不新增"横屏专属的第三个宽度阈值"**——同一台设备横屏时 `screenWidthDp` 本来就更大，宽度维度和方向维度组合后自然产生"竖屏 Expanded / 横屏 Expanded"两种子状态，不需要在宽度枚举里再拆细。

## 4. 列表+详情型页面：吸取曲库双栏教训，不做二级双栏

**受影响页面**：搜索结果、歌手主页、歌单/专辑详情、播客节目/电台详情——这几个页面结构上都是"一个列表，点进某一项看详情"。

Phase 2 给曲库做过列表-详情双栏，`ee6586f` 撤销时的原话："消除了双栏叠加播放面板时详情栏被压缩到不可用宽度的问题"。播放面板固定占 340-400dp，平板竖屏可用宽度约 800dp，双栏再切一刀后详情栏经常不够放。这个问题对搜索结果/歌手/歌单详情同样成立，且这些页面内容形态（封面+长文案+评论区）比曲库列表更不耐挤压。

**决策：本轮不引入任何"列表+详情"二级双栏**，统一按第 6 节的"内容重密度"模式处理——保持整屏单栏跳转（继承 Phase 0-3 5.3 节的既有行为：二级页面在 Expanded 下依然整屏覆盖，含播放面板区域），只是单栏内部的内容宽度、卡片尺寸、列数按断点重新计算，不引入横向分栏。

这与 Phase 0-3 5.3 节的"二级页面不做平板专属改造"也不同——不改**结构**（不分栏、不做嵌入版），但改**内容密度**（网格列数、卡片尺寸、内容最大宽度）。

## 5. 内容宽度上限：设置类/纯文字列表页统一模式

`SettingsScreen` 及其 7 个子页、`MessageScreen`/`AccountScreen`（空态桩页面）、`CloudScreen`、`RecentPlayScreen`、`ListenDataScreen`、`FollowListScreen`、`ProfileScreen` 这类以"一列设置项/一列数据行"为主的页面，在宽屏下最大的问题不是"元素小"而是"一行文字拉到 1600dp 宽，视线要来回扫很远"。

统一方案：新增一个共享 wrapper（建议放在 `core/ui/components/`，命名 `AdaptiveContentWidth` 或类似），Expanded 下把内容居中，限制最大宽度（建议 680dp，参考 Material 规范里长文本单列最佳可读宽度），两侧留白；Compact 下等价于 `fillMaxWidth()`，零视觉变化。这是**一次实现、多页面复用**的模式，改动量小。

```kotlin
@Composable
fun AdaptiveContentWidth(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val windowSizeClass = LocalMelodiaWindowSizeClass.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (windowSizeClass == MelodiaWindowSizeClass.Expanded) {
                    Modifier.widthIn(max = 680.dp).align(...)  // 需要外层 Box 配合居中
                } else Modifier
            )
    ) { content() }
}
```

（具体 API 形状到实现计划阶段再定，这里只定方向。）

## 6. 首页内容重密度：3 个方案对比

首页是本轮唯一需要"设计取舍"而非"套用统一模式"的页面，给 3 个方案：

### 方案 A：卡片尺寸随断点重新定义（推荐）

`HomeShelfSection` 的两列网格 / 横向滚动阈值判断，以及卡片固定宽度（`HorizontalCardWidth=150dp`、`EntryCardSize=132dp`）都改成按 `MelodiaWindowSizeClass`/`MelodiaOrientationClass` 取值的 token，而不是复用手机端数值：

- 两列网格 → Expanded 竖屏 3-4 列、Expanded 横屏 5-6 列（`GRID_MAX_CARDS` 阈值同步调整）
- 横向滚动卡片宽度：150dp → Expanded 下 190-210dp（卡片本身略微放大，同时一屏能看到的完整卡片数也增加，不是纯粹放大）
- `ForYouSection` 功能入口卡片：132dp → Expanded 下 160dp 左右，横向排布数量相应增加

不改变 `HomeContent.kt` 的单 `LazyColumn` 货架流结构，不改变滚动交互、数据加载逻辑。**改动集中在几个尺寸常量 + 让它们变成断点感知的函数**，风险最低，符合 Phase 0-3 建立的"内容结构不动，只调参数"的既有工作方式。

**缺点**：横屏下依然是"从上到下滚动货架"的手机式浏览路径，没有用上横屏"高度矮、宽度富余"的特点做结构性优化。

### 方案 B：首页拆分主栏 + 侧栏

Expanded 断点下首页变成左右两栏：左边（`weight(1f)`）保留现有货架流，右边（固定宽度 320-400dp）放一个"今日"摘要栏（每日推荐大卡 + 最近播放迷你列表 + 心动模式入口），横屏下侧栏常驻，竖屏下侧栏折叠回方案 A 的单栏。

**缺点**：改动面最大，需要新设计侧栏内容取舍（放哪些模块），且首页已经有播放面板在 Expanded 下常驻于最右侧，"首页侧栏 + 播放面板"叠加是否会重复第 4 节提到的宽度预算问题需要单独评估——本质上是把 Phase 2 曲库双栏踩过的坑在首页重新踩一遍，不推荐在没有验证的情况下直接做。

### 方案 C：仅横屏改列数，竖屏维持现状不动

只在 `orientationClass == Landscape` 时应用方案 A 的尺寸/列数调整，竖屏（不管手机还是竖屏平板）保持 Phase 0-3 现状不变。

**缺点**：不解决用户截图里那张"竖屏平板"下元素小的问题——截图本身就是竖屏平板，方案 C 对此无效，不满足本次诉求，仅作为方案 A 的备选降级路径记录在案（如果方案 A 竖屏效果验证后不理想，可退回此方案只保留横屏改动）。

**推荐方案 A**，横屏下在 A 的尺寸调整基础上叠加"列数进一步增加"（复用同一套 token，横屏只是取更大的档位），不需要单独设计横屏结构，理由：横屏和竖屏平板的首页内容形态本质相同（都是货架流），横屏只是可视宽度更大，用同一套"按断点取值"机制自然覆盖，不需要方案 B 那种结构性拆分。

## 7. 各模块具体处理方式（按模式分组，不逐屏单独设计）

| 分组 | 页面 | 处理方式 | 复杂度 |
|---|---|---|---|
| 网格列数响应 | 搜索发现页网格、歌单分类网格(`PlaylistCategoryScreen`)、曲库网格视图(`LibraryScreen` 的 `chunked(3)`)、新作 feed(`NewWorksFeedContent`) | `GridCells.Fixed(n)`/`chunked(n)` 的 n 按断点取值，复用 Phase 1 首页最近播放已验证的模式 | 低 |
| 内容宽度上限 | 设置及 7 个子页、消息/账号空态页、云盘、最近播放全列表、听歌数据、关注列表、个人主页(`ProfileScreen`) | 套用第 5 节 `AdaptiveContentWidth` | 低 |
| 单栏内容重密度 | 搜索结果列表、歌手主页(`ArtistContent`)、歌单/专辑详情(`PlaylistContent`)、播客节目/电台详情 | 套用第 6 节方案 A 的"尺寸按断点取值"思路：卡片/行高、封面尺寸、内边距按断点给不同 token，结构不变、不分栏 | 中 |
| 视频+评论横向布局 | `ArtistMvPlayerScreen` | Expanded 横屏下（视频区天然适合更宽）改为左右布局：视频区固定/占比宽度 + 评论区占剩余宽度；Expanded 竖屏、Compact 保持现状上下堆叠 | 中 |
| 个人中心侧边栏 | `ProfileSidebar`（当前 310dp 抽屉，Expanded 下也是覆盖式抽屉，未常驻化） | 本轮**不改**——是否要在 Expanded 下变成常驻 Rail 涉及 `MelodiaApp.kt` 顶层壳层结构调整，且和已有播放面板抢位置，影响面接近方案 B 的风险级别，建议单独立项评估，不纳入本轮 | 不纳入本轮 |
| 空态桩页面 | `MessageScreen`、`AccountScreen` | 无实际内容，套第 5 节宽度上限模式即可，不单独设计 | 低 |

## 8. 阶段划分

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| Phase 4a | 断点体系加方向维度（第 3 节）+ `AdaptiveContentWidth` 共享组件（第 5 节） | 横屏下 `LocalMelodiaOrientationClass` 正确翻转；竖屏平板回归无变化 |
| Phase 4b | 首页方案 A 落地（第 6 节） | 平板竖屏/横屏下货架卡片尺寸与列数按断点切换；手机不受影响 |
| Phase 4c | 网格列数响应模块批量套用（第 7 节第一组） | 4 个页面网格列数随断点变化，交互逻辑不变 |
| Phase 4d | 内容宽度上限模块批量套用（第 7 节第二组） | 7+ 个页面 Expanded 下内容居中限宽，手机不受影响 |
| Phase 4e | 单栏内容重密度模块逐个套用（第 7 节第三组） | 搜索结果/歌手/歌单详情/播客详情尺寸随断点变化，不引入分栏 |
| Phase 4f | `ArtistMvPlayerScreen` 横屏视频+评论布局 | 横屏下视频/评论左右布局，竖屏/手机不受影响 |

Phase 4a → 4b 建议先做（首页是用户本次反馈的直接触发点），4c/4d 可并行（模式简单、互不依赖），4e/4f 排最后（改动面相对大，且依赖 4a 的方向维度）。

## 9. 待确认问题

1. **第 6 节首页方案**——推荐方案 A，需要你确认或换方案。
2. **`ProfileSidebar` 是否要常驻化**——第 7 节建议本轮不做，如果你认为这个优先级应该提上来，需要单独补一节设计（涉及顶层壳层结构，类似 Phase 0 的工作量）。
3. **`AdaptiveContentWidth` 的最大宽度取值（680dp）**——这是个初始建议值，实际是否合适需要真机/模拟器上视觉验证，可能要调。
4. **Phase 4e 单栏页面的具体尺寸 token**（卡片高度、封面尺寸等具体 dp 值）——本文档只定方向，具体数值留到各自的实现计划阶段，参照真机效果调整，不在设计阶段写死。
