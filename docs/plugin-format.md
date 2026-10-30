# 插件格式（format 2）

插件包是扩展名为 `.melodiaplugin` 的 zip 文件，在「设置 → 插件」（桌面端）或「设置 → 扩展 → 插件」（Android）中导入。导入时会先显示插件需要的权限，确认后才安装并启用。

## 包结构

| 文件 | 说明 | 上限 |
|---|---|---|
| `plugin.json` | 清单，必需 | 256KB |
| `main.js` | 脚本入口；声明了页面、音源、歌词源或事件时必需 | 2MB |
| `ui/*.json` | 页面与布局描述，只允许一层 | 单个 256KB |
| `assets/*.png/jpg/jpeg/webp` | 图片，只允许一层 | 单个 2MB |

整个包最多 64 个文件，解压后不超过 10MB。白名单之外的文件会被忽略，并在导入时提示。

## 清单

```json
{
  "format": 1,
  "id": "com.example.lastfm",
  "name": "Last.fm 记录",
  "version": "1.0.0",
  "author": "someone",
  "description": "把听过的歌记录到 Last.fm",
  "minAppVersion": "1.6.0",
  "permissions": ["network", "playback.read", "storage"],
  "network": { "hosts": ["ws.audioscrobbler.com"] },
  "contributes": {
    "playerLayouts": { "desktop.playerBar": "ui/bar.json" },
    "pages": [{ "id": "stats", "title": "听歌统计", "icon": "chart", "ui": "ui/stats.json", "placement": "sidebar" }],
    "audioSources": [{ "id": "main", "name": "示例音源" }],
    "lyricsSources": [{ "id": "main", "name": "示例歌词" }],
    "events": ["songChanged", "playStateChanged"]
  }
}
```

| 字段 | 说明 |
|---|---|
| `format` | 必需，`1` 或 `2`；高于应用支持的版本时拒绝导入 |
| `id` | 必需；反向域名形式，只能用小写字母、数字和 `_`，如 `com.example.demo`。同 id 再次导入即升级 |
| `name` / `version` | 必需 |
| `author` / `description` / `minAppVersion` | 可选 |
| `permissions` | 见下文；未知权限会被忽略并提示 |
| `network.hosts` | 申请 `network` 时必需；`*.example.com` 只匹配子域名，不匹配 `example.com` 本身 |
| `contributes` | 至少提供一项能力，否则插件无效 |
| `kind` | format 2；`sandbox`（默认）。`native` 为桌面端原生插件，当前版本尚不支持，导入时拒绝 |
| `platforms` | format 2；可选，`desktop`、`android`，不写表示所有平台。不包含当前平台时拒绝导入，未知平台会被忽略并提示 |
| `apiVersion` | format 2；可选，写成 `1.0`。主版本必须与应用一致，次版本不能高于应用，否则拒绝导入。当前应用提供 `1.0` |

format 1 的插件继续有效，其中的 `kind`、`platforms`、`apiVersion` 会被忽略。

### contributes

- `playerLayouts`：key 为区域名（`android.fullPlayer`、`android.fullPlayerWide`、`android.miniPlayer`、`desktop.playerBar`、`desktop.nowPlaying`），值为 `ui/` 下的文件路径，或直接内联的描述对象。
- `pages`：每项需要 `id`（小写字母、数字、`-`、`_`，不超过 32 个字符）、`title` 与 `ui`。`placement` 可选，取值为 `sidebar`（默认）或 `nowPlayingCard`。
- `audioSources` / `lyricsSources`：每项需要 `id`，`name` 可选。
- `events`：`songChanged`、`playStateChanged`、`progress`。
- `commands`（format 2）：每项需要 `id` 与 `title`，脚本用 `melodia.register.command` 实现。
- `menus`（format 2）：每项需要 `command` 与 `location`。`location` 取 `song` 或 `toolbar`。只显示脚本已注册的命令。`song` 出现在桌面歌曲右键菜单、Android 歌曲「更多」弹层与全屏播放页「更多」弹层（播客节目除外）；`toolbar` 在桌面是顶栏按钮，在 Android 是「设置 → 扩展 → 插件」里的「插件命令」。
- `settings`（format 2）：插件设置项，见「设置」一节。

单项写错时只忽略这一项，并在导入时提示。

## 权限

| 权限 | 允许的操作 | 对应能力 |
|---|---|---|
| `network` | 访问 `network.hosts` 列出的域名 | |
| `playback.read` | 读取当前歌曲、播放状态与进度 | `events` 必需 |
| `playback.control` | 播放、暂停、切歌、加入队列 | |
| `storage` | 插件私有的键值存储，上限 1MB | |
| `audioSource` | 作为音源提供播放链接 | `audioSources` 必需 |
| `lyricsSource` | 作为歌词源提供歌词 | `lyricsSources` 必需 |
| `ui.notify` | 显示应用内提示，提示前会带上插件名称 | |

提供了某项能力却没有申请对应权限时，整个插件无效。插件升级后如果申请了新的权限，导入预览会标出新增的权限。

## 脚本

`main.js` 在隔离的 JS 环境中运行：Android 上是无界面的 WebView，桌面端是 GraalJS。脚本不能访问文件系统、Cookie、账号信息与宿主类，只能通过全局对象 `melodia` 调用宿主能力。所有调用都返回 Promise。

```js
await melodia.http.request({ url, method, headers, body })   // 返回 { status, headers, body }
await melodia.storage.get(key)
await melodia.storage.set(key, value)                        // value 为 null 等同于删除
await melodia.storage.remove(key)
await melodia.player.current()                               // { id, songId, title, artist, cover } 或 null
await melodia.player.state()                                 // { playing, positionMs, durationMs }
await melodia.player.play() / pause() / next() / previous()
await melodia.player.enqueue({ songId, title, artist, cover })
await melodia.ui.setState(pageId, patch)                     // 浅合并到页面数据
await melodia.ui.toast(text)                                 // 不超过 120 个字符
await melodia.settings.get(key)
await melodia.settings.all()
melodia.on('songChanged', handler)
melodia.register.audioSource('main', { resolve, search })
melodia.register.lyricsSource('main', { fetch })
melodia.register.action('refresh', handler)
melodia.register.command('share', handler)
```

- 网络请求不会自动跟随到白名单外的域名；每次重定向都会重新核对域名。响应体上限 4MB。
- 宿主调用失败时 Promise 会被拒绝，错误信息说明原因（如缺少权限、不允许访问的域名）。
- 脚本加载失败（语法错误、加载时抛错或超过 8 秒）时，插件会被自动停用。
- 宿主调用插件注册的能力时，如果插件出错或超时，计为一次失败；连续失败 5 次后插件会被自动停用。重新启用后计数清零。

## 音源

```js
melodia.register.audioSource('main', {
  resolve: async function(song) {
    // song: { songId, title, artists, album, durationMs, origin, quality }
    return 'https://...';            // 或 { url: 'https://...' }
  }
});
```

- 只在官方链接不可用或只有试听、且开启了换源时调用，与 LX 插件一起参与社区源轮询。
- `origin` 为 `songId` 所属平台（`netease`、`qq`、`kugou`、`kuwo`、`migu`）；`quality` 为当前音质档位（`standard`、`exhigh`、`lossless`、`hires`）。
- 只接受 http(s) 链接；返回空、试听链接或超过 8 秒没有返回时视为没有结果，继续尝试下一个音源。
- 多个插件音源按插件名称顺序尝试；`register` 的名称必须在清单 `audioSources` 里声明过。

## 歌词源

```js
melodia.register.lyricsSource('main', {
  fetch: async function(song) {
    // song: { songId, title, artists, album, durationMs, origin }
    return '[00:01.00]第一句';       // LRC 文本，或 { lrc: '...' } / { ttml: '...' }
  }
});
```

- 与网易、AMLL、QQ 歌词同时查询，优先级最低：只有其他来源都没有歌词时才使用插件歌词。
- 多个插件歌词源并发查询，按插件名称顺序取第一份非空结果；单个歌词源超过 8 秒按没有结果处理。

## 命令

```json
"contributes": {
  "commands": [{ "id": "scrobble", "title": "记录到 Last.fm" }],
  "menus": [{ "command": "scrobble", "location": "song" }]
}
```

```js
melodia.register.command('scrobble', async function(song) {
  // song 只在 location 为 song 时有值：{ songId, title, artist, album, cover }
});
```

外部平台的歌曲没有 `songId`。命令 5 秒内没有返回或抛错时，应用会提示执行失败，并计入连续出错。

## 设置

设置项写在清单里，应用生成设置页，入口在插件列表的设置按钮。

```json
"settings": [
  { "id": "enabled", "type": "toggle", "title": "启用记录", "default": true },
  { "id": "token", "type": "text", "title": "令牌" },
  { "id": "mode", "type": "select", "title": "模式", "options": [{ "value": "fast", "label": "快速" }, { "value": "safe" }] },
  { "id": "interval", "type": "number", "title": "间隔", "min": 1, "max": 60, "step": 1, "default": 30 }
]
```

| `type` | 说明 | 专属字段 |
|---|---|---|
| `toggle` | 开关 | |
| `text` | 文本，最长 500 个字符 | |
| `select` | 单选，最多 32 项 | `options`，每项 `value` 必需，`label` 可选 |
| `number` | 滑块 | `min`、`max` 必需，`step` 默认 1 |

- 设置值保存在插件数据目录，升级时保留，不占用 `storage` 权限。
- 没保存过时取 `default`，不合法的值回到默认值；未写 `default` 时，`toggle` 为 `false`、`text` 为空、`select` 为第一项、`number` 为 `min`。
- 如果注册了动作 `settingsChanged`，每次修改都会以 `{ key, value }` 调用一次。

## 播放事件

| 事件 | 数据 |
|---|---|
| `songChanged` | `{ id, songId, title, artist, cover }`，停止播放时为 `null` |
| `playStateChanged` | `{ playing }` |
| `progress` | `{ positionMs, durationMs }`，播放中每秒一次 |

事件只通知插件，不等待处理结果，也不能改变播放行为。插件启动时不会补发当前状态，需要时调用 `melodia.player.current()` 与 `melodia.player.state()`。

## 播放器布局

布局是由容器和组件组成的树，写在 `ui/*.json` 里，或直接内联在清单的 `playerLayouts` 下。只提供布局的插件不需要 `main.js`，也不需要申请权限。

```json
{
  "type": "row", "padding": [0, 16], "spacing": 12, "align": "center", "height": 96,
  "children": [
    { "type": "cover", "shape": "circle", "maxSize": 64 },
    { "type": "songInfo", "weight": 1 },
    { "type": "column", "weight": 2, "align": "center", "children": [
      { "type": "controls", "buttons": ["shuffle", "previous", "playPause", "next", "repeat"], "size": "large" },
      { "type": "progress", "showTime": false }
    ]},
    { "type": "actions", "buttons": ["queue", "volume"] }
  ]
}
```

### 区域

| key | 位置 | 必需 | 不可用的组件 |
|---|---|---|---|
| `desktop.playerBar` | 桌面底部播放栏 | `controls` 的 `playPause`、`progress` | `miniLyric`、`lyricsCard` |
| `desktop.nowPlaying` | 桌面「正在播放」面板的封面与标题区 | 无 | `miniLyric`、`lyricsCard`、`actions` |
| `android.fullPlayer` | Android 全屏播放页首屏 | `playPause` | |
| `android.fullPlayerWide` | Android 宽屏播放页 | `playPause` | `lyricsCard` |
| `android.miniPlayer` | Android 迷你播放器 | `playPause` | `actions`、`lyricsCard`、`miniLyric` |

`desktop.playerBar` 根节点的 `height` 即播放栏高度，范围 64–160，默认 80。`android.miniPlayer` 根节点的 `height` 范围 48–140，`controls` 只支持 `previous`、`playPause`、`next`；左右滑动切歌仍然可用，但封面和标题不会跟手移动。

`android.fullPlayer` 占满一屏，顶部保留收起和更多按钮，下方的信息卡片不变；放了 `lyricsCard` 时，卡片列表里不再重复显示歌词卡。`android.fullPlayerWide` 只替换左栏，右栏歌词与卡片不变。

### 通用属性

| 属性 | 说明 |
|---|---|
| `padding` | 一个数字为四边相同；`[上下, 左右]`；`[上, 右, 下, 左]`。范围 0–64 |
| `spacing` | 容器内子节点间距，范围 0–64 |
| `align` | `start`、`center`、`end` |
| `weight` | 在 `row` / `column` 里按比例分配剩余空间，范围 0.1–10 |
| `width` / `height` | 固定尺寸，范围 0–1000 |
| `background` | 色板项，如 `pane`、`cardSurface`；跟随当前皮肤 |
| `shape` / `radius` | `rect`、`rounded`、`circle`；`radius` 为圆角半径，默认 8 |

### 节点

| type | 说明 | 专属参数 |
|---|---|---|
| `column` / `row` / `box` | 容器，`box` 为叠放 | `children` |
| `cover` | 封面，点击切换「正在播放」 | `maxSize`（24–1000）、`aspect`（0.25–4，仅 Android）、`size` |
| `songTitle` / `artist` / `songInfo` | 标题、歌手、两者组合 | `lines`（1–4）、`size` |
| `likeButton` | 收藏到歌单；播客为订阅电台 | |
| `miniLyric` | 单行歌词（Android） | |
| `progress` | 进度条 | `showTime` |
| `controls` | 播放控制 | `buttons`：`shuffle`、`previous`、`playPause`、`next`、`repeat`；`size` |
| `actions` | 操作按钮 | `buttons`，桌面：`desktopLyric`、`lyricsView`、`queue`、`device`、`volume`、`miniPlayer`、`fullscreen`；Android：`device`、`queue`、`share`、`quality`、`more` |
| `lyricsCard` | 歌词卡（Android） | |
| `spacer` | 留白，配合 `weight` 或固定尺寸 | |
| `text` | 固定文字，不超过 40 个字符 | `text`、`style`（`title`、`body`、`caption`）、`lines`、`size` |

`size` 取 `small`、`medium`、`large`。没写 `buttons` 时使用该区域的全部按钮。

### 校验与回退

- 树不超过 8 层、128 个节点；超限或缺少必需组件时，这个区域回到内置布局，其他区域不受影响。
- 未知组件、不支持的按钮与无效参数只忽略这一项，并在导入时提示；数值超出范围时收到边界内。
- 同一区域有多个插件提供布局时，默认使用最近启用的插件，也可以在插件设置里按区域选择，或改回内置布局。

## 页面

页面描述的写法与播放器布局相同，可用容器 `column`、`row`、`box`，以及 `spacer`、`text` 和下面的组件。提供页面的插件需要 `main.js`。

```json
{
  "type": "column", "spacing": 12, "children": [
    { "type": "heading", "text": "$state.title" },
    { "type": "loading", "visible": "$state.loading" },
    { "type": "button", "text": "刷新", "action": "refresh" },
    { "type": "list", "bind": "$state.items", "spacing": 6, "children": [
      { "type": "row", "children": [
        { "type": "text", "text": "$item.name", "weight": 1 },
        { "type": "button", "text": "删除", "action": "remove" }
      ]}
    ]}
  ]
}
```

| type | 说明 | 必需字段 |
|---|---|---|
| `heading` | 标题 | `text` |
| `text` | 正文，可绑定 | `text` |
| `image` | 图片，`bind` 可写绑定或地址；只允许 `assets/` 下的文件与 `network.hosts` 里的域名 | `bind` |
| `list` | 绑定数组，每一项按 `children` 渲染，最多 200 项；`columns`（1–8）大于 1 时按网格排列 | `bind` |
| `trackList` | 绑定 `{ songId, title, artist, cover }` 数组，点击从这首开始播放整个列表；需要 `playback.control` | `bind` |
| `button` | 触发 `action`；在列表里时，当前项作为参数传给脚本 | `text`、`action` |
| `toggle` | 开关，写回 `$state` 字段；有 `action` 时以 `{ value }` 调用 | `text`、`bind` |
| `input` | 输入框，写回 `$state` 字段；有 `action` 时在回车后以 `{ value }` 调用 | `text`（提示文字）、`bind` |
| `loading` / `empty` | 加载中与空态 | |
| `select` | 单选，写回 `$state` 字段；有 `action` 时以 `{ value }` 调用 | `text`（标题）、`bind`、`options` |
| `slider` | 滑块，写回 `$state` 字段；松手后调用 `action` | `text`（标题）、`bind`；`min`、`max` 默认 0、1，`step` 可选 |
| `progressBar` | 进度条，绑定 0–1 的数字 | `bind` |
| `badge` | 小标签 | `text` |
| `tabs` | 标签页，每个子节点是一页，子节点的 `text` 为标题 | `children` |

- **绑定**：`$state.xxx` 取页面数据，`$item.xxx` 取列表当前项，可以用点号取下一层。`text` 可以写固定文字，也可以整段写成绑定。
- **`visible`**：任何节点都可以写，值为绑定，取到空、`false`、`0` 或空数组时不显示。
- **数据**：脚本用 `melodia.ui.setState(pageId, patch)` 更新页面数据，渲染时不会调用脚本。
- **动作**：用 `melodia.register.action(name, handler)` 注册，名称只能用字母、数字、`_`、`:`、`-`，5 秒内没有返回按失败处理。
- **打开页面**：如果注册了 `open:<页面 id>`，每次打开页面都会调用一次，可以用来刷新数据。
- **入口**：`placement` 为 `sidebar` 时，桌面端在音乐库侧栏底部的「插件」分组，Android 在「设置 → 扩展 → 插件」；为 `nowPlayingCard` 时，页面作为卡片显示在桌面「正在播放」面板与 Android 全屏播放页，可以在卡片编辑器里排序和隐藏。宽屏播放页的插件卡片排在内置卡片之后。

## 存储

- 已安装的插件放在应用数据目录的 `plugins/<id>/` 下，启用状态与已确认的权限记录在 `plugins/.state.json`。
- 插件数据放在 `plugins/.data/<id>/`，升级时保留，删除插件时一并删除。
- 导出时只打包安装文件，不包含插件数据。
