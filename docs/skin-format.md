# 皮肤包格式

Melodia 的皮肤是一个扩展名为 `.melodiaskin` 的 zip 包，Android 与桌面端通用。在「设置 → 主题 → 皮肤（实验性）」中导入。

```
skin.json          必需
assets/bg.jpg      可选，背景图（png / jpg / jpeg / webp）
```

## 限制

- 包内文件不超过 32 个，解压后总大小不超过 20MB，单个资源不超过 10MB，`skin.json` 不超过 256KB。
- 资源只能放在 `assets/` 下一层，文件名只能由字母、数字、`.`、`_`、`-` 组成；其他文件会被忽略。
- 背景图最长边不超过 4096px。
- 出现 `../`、绝对路径或重复文件时整个包拒绝导入。

## skin.json

```json
{
  "format": 1,
  "id": "night-paper",
  "name": "夜纸",
  "author": "作者",
  "version": "1.0",
  "modes": ["dark", "light"],
  "colors": {
    "dark": { "pane": "#141A22", "accent": "#5AA9E6" },
    "light": { "pane": "#FBF8F2" }
  },
  "background": { "image": "assets/bg.jpg", "blur": 16, "dim": 0.35, "paneOpacity": 0.82 },
  "style": {
    "radius": { "pane": 12 },
    "font": { "family": "monospace" },
    "border": { "panes": true, "width": 1 }
  }
}
```

| 字段 | 说明 |
|---|---|
| `format` | 必需，目前为 `1`。高于应用支持的版本时拒绝导入 |
| `id` | 必需，小写字母、数字、`-`、`_`，最长 64 位，不能为 `builtin`。同 id 再次导入时可选择覆盖或另存 |
| `name` | 必需，最长 40 字 |
| `author` / `version` | 可选 |
| `modes` | 可选，默认两种都支持。只写一种时，使用该皮肤期间深浅色固定为这一种 |

无法识别的字段会被忽略；取值不合法的单项会被跳过或收到边界内，并在导入预览里列出。

### colors

`colors.dark` 与 `colors.light` 分别覆盖深色、浅色色板，没写的项沿用内置主题。色值写 `#RRGGBB` 或 `#AARRGGBB`。

| 分组 | 字段 |
|---|---|
| 底色 | `windowBackground`（画布）、`pane`（面板）、`surface`、`cardSurface`、`popupSurface`、`surfaceLight`、`fieldOnCanvas`、`fieldOnCanvasHover`、`coverPlaceholder` |
| 文字 | `textPrimary`、`textSecondary`、`textTertiary`、`textDisabled`、`inverseText` |
| 状态层（建议带透明度） | `overlay`、`hover`、`pressed`、`selected`、`fill`、`divider`、`outline`、`scrim`、`shadowAmbient`、`shadowSpot` |
| 语义色 | `success`、`vip`、`error` |
| 主色 | `accent`、`onAccent`、`accentText`、`accentContainer` |

- 写了 `accent` 时它就是该模式的主色；用户在主题设置里选择自定义颜色或系统动态色时以用户的为准。
- `onAccent`、`accentText`、`accentContainer` 只有在同时写了 `accent` 时才生效，没写则按主色自动推导。

导入时会检查文字与主色的对比度，不达标的自动调整亮度并写回皮肤，预览里会列出调整了哪些颜色：

| 检查项 | 最低对比度 |
|---|---|
| `textPrimary` 对 `pane`、`windowBackground` | 7 |
| `textSecondary` 对 `pane`、`windowBackground` | 4.5 |
| `textTertiary` 对 `pane`、`windowBackground` | 3 |
| `accentText` 对 `pane` | 深色 3，浅色 4.5 |
| `onAccent` 对 `accent` | 4.5 |

有背景图时，按背景图平均色叠上遮罩和半透明面板后的实际底色检查。

### background

| 字段 | 取值 | 说明 |
|---|---|---|
| `image` | 必需 | 包内路径，如 `assets/bg.jpg` |
| `blur` | 0–40，默认 0 | 模糊程度 |
| `dim` | 0–0.9，默认 0 | 背景图上叠一层画布色（`windowBackground`）的不透明度 |
| `paneOpacity` | 0.5–1，默认 1 | 面板的不透明度，越低背景透出越多 |
| `playerDim` | 0–0.9，默认同 `dim` | 播放区透出背景图时的遮罩（见下方 cover），导入时不够深会自动加深 |

背景图铺满整个窗口。面板只在外层铺一次半透明底色，页面内部改为透明；吸顶栏、顶栏等需要挡住滚动内容的位置会画出与周围一致的背景，不会透出下方内容。

### cover

封面取色的区域分三组，每组可选取色来源：

| 分组 | 范围 |
|---|---|
| `header` | 页面头部渐变：歌单、歌手、个人主页、云盘、本地歌单等页头与顶栏 |
| `cards` | 封面色卡片：首页每日推荐、新作、搜索热门、听歌识曲结果 |
| `player` | 播放区：播放页、歌词页、正在播放面板、迷你播放器 |

```json
"cover": {
  "header": { "source": "backdrop" },
  "cards":  { "source": "color", "color": "#3A2F5B" },
  "player": { "source": "color", "color": { "dark": "#2B2140", "light": "#6B5BB5" } }
}
```

| `source` | 效果 |
|---|---|
| `cover`（默认） | 跟随封面取色 |
| `color` | 用 `color` 代替封面颜色，仍按原有规则压暗或取浅调，文字可读性不受影响。`color` 可写一个色值，或按 `dark` / `light` 分开写；缺的模式跟随封面 |
| `backdrop` | 不铺封面色，透出背景图；皮肤没有背景图时按 `cover` 处理 |

透出背景图时：页面头部渐变消失；封面色卡片改为半透明面板底、正文色；播放区按沉浸式深色处理（深色遮罩配白字），浅色主题下也一样，遮罩深度取 `background.playerDim`。

播放区以外的沉浸区（歌词卡、全屏歌词等）使用皮肤的深色配色 `colors.dark`，所以只支持浅色的皮肤写了 `colors.dark` 时同样会检查对比度。

### style

| 字段 | 取值 | 当前生效范围 |
|---|---|---|
| `radius.pane` | 0–24，默认 8 | 桌面端面板圆角 |
| `font.family` | `default` / `monospace` | 全局字体 |
| `border.panes` / `border.width` | 布尔 / 0.5–2 | 面板与浮层描边 |
| `radius.card`、`radius.cover`、`radius.button`、`density`、`progress`、`controlStyle` | 见格式定义 | 暂只解析，后续版本接入 |

`layouts`（播放器布局）在后续版本支持，目前导入时原样保留。
