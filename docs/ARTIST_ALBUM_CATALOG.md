# 按音乐资料库筛选歌手主要录音室专辑

MusicBrainz 专辑目录筛选默认开启，使用资料库的作品分类，不按热度或评分推荐“最好”的专辑。曲数筛选与资料库筛选可分别设置。

## 使用

**设置 → 扩展 → 歌手专辑**统一管理资料库筛选，默认开启。未单独设置的歌手跟随全局开关；只有实际访问歌手时才按需读取资料，不批量抓取所有歌手。

歌手详情 → 三个竖点 → **主要录音室专辑（资料库）**，可单独开启或关闭，或恢复跟随全局。最低曲数入口仍保留，每位歌手独立保存；0 首表示不限制曲数。beta.6 已保存的明确开关与歌手绑定原样保留，全局切换不会覆盖个人例外。

第一次按名称、别名和已有专辑核对身份：只有唯一精确名称候选与至少两张不同专辑交叉命中时自动绑定；否则点选正确的资料库歌手。之后复用绑定，不需要反复选。

加载后只展示网易列表中与主要专辑目录匹配的代表版本，仍可打开原网易专辑。最低曲数设置继续生效；设为 0 即只使用资料库分类。菜单可更换资料库歌手、重试目录或恢复全部列表。

当同一专辑有多个发行版本时，先优先选择达到最低曲数的版本，再按原版、标题与发行日期选择代表。例如原版 7 首、豪华版 12 首，设置至少 8 首时显示豪华版；恢复 0 首后重新优先原版。曲数条件不会把现场、EP 或有歧义的发行组提升为主要录音室专辑。

## 分类与版本

以 MusicBrainz Release Group 为原专辑组：`primary-type=Album`，且明确提供空的 `secondary-types`，才进入普通专辑候选。Single、EP、Live、Compilation、Remix 及其他附加类型排除；缺少类型信息不当作录音室专辑。不会因为《Live for Today》的标题含 Live 就排除。

同组的明确重制、地区、豪华、Super Deluxe 等发行后缀可归并，支持连续后缀及嵌套括号。按完整标题、原发行日期等证据选择网易代表专辑，并按资料库原专辑发行时间排列。不同录音、现场及语言版本不靠模糊相似度强行归并。匹配支持大小写、常见标点以及简繁字符折叠；翻译标题或歧义名称可能仍未识别。

网易云明确的 `type=专辑/Album/Single/EP` 可帮助区分同名专辑与单曲、访谈。不会用 `subType=录音室版` 覆盖 MusicBrainz 的 Live、Remix 等分类，因为源数据存在误标。

开启资料库筛选并进入专辑页时，会自动读完网易云所有专辑页，无需不断滚动。读取期间显示已加载数量和进度，暂时可见的匹配继续展示；失败或重复空页会暂停并提供重试。分页依据服务器 `more` 和实际原始行数，不使用可能过时的歌手专辑总数。关闭筛选继续按原列表滚动加载。

页面显示已加载网易专辑的匹配、未识别、合并和排除数量。未识别项不在筛选列表，可以恢复全部查看。

## U2 回归核查

2026-10-15 读取网易云 164 条专辑和 MusicBrainz 203 个发行组。beta.6 漏项原因包括没有读完源分页、《The Joshua Tree》的同名访谈冲突、《The Unforgettable Fire》的同名单曲冲突和 Super Deluxe 后缀。新增完整元数据回放测试，要求 14 张已发行且无次类型的 Album 全部匹配。

《Rattle and Hum》在资料库标为 Soundtrack，官方也说明它混合现场表演与新录音，当前主要录音室规则仍排除。可在该歌手菜单关闭筛选查看原列表。数据库并不保证每位歌手的目录完整或分类正确。

## 数据边界与联网

资料库不保证涵盖每位中外歌手，也不保证类型标注完整无误。例如 MusicBrainz 将 Suchmos《THE ASHTRAY》标为 Album，而 Sony 官方称 mini-album；本功能遵循数据库字段，不能承诺完整权威的录音室目录。

只在开启后按需读取公开资料，串行请求间隔至少 1.1 秒；成功目录缓存 7 天，失败短暂冷却，不后台定时抓取。网络失败保留旧缓存；没有缓存时保留原列表并明确显示错误，不把错误当成“没有专辑”。目录与绑定使用独立存储，不改变官方曲库数据库。

中文比较使用 OpenCC 官方固定版本的 TSCharacters 字表，附带许可与来源说明；这是字符折叠，不是完整短语或地区用语转换。

## 实现边界与评审入口

| 模块 | 职责与稳定边界 |
| --- | --- |
| `shared/.../artist/domain/ArtistAlbumSelection.kt` | 纯曲数筛选与网易分页合并。偏移量只按原始响应行数增加；筛选不改源列表。重复分页、空但仍有后页、偏移溢出停止自动加载并允许明确重试。 |
| `shared/.../preferences/ArtistAlbumFilterSettings.kt` | 全局和逐歌手最低曲数共用 0–100 范围；0 表示不限。已有保存键与默认值保持兼容。 |
| `app/.../artist/domain/catalog/` | 纯资料库分类、歌手身份核对、标题归一化及代表版本选择。无需 Android Context、网络或存储即可测试。原始名称与返回的网易专辑 ID 保留。 |
| `app/.../artist/data/catalog/ArtistCatalogRepository.kt` | 独立公开资料客户端、统一限频、完整分页验证、成功缓存及失败冷却。缓存仅由完整成功结果替换；取消传播，不记成网络错误。User-Agent 使用当前构建版本。 |
| `app/.../artist/data/catalog/ArtistCatalogPreferences.kt` | 独立 DataStore 保存全局开关、逐歌手覆盖及 MBID 绑定。切换全局开关不覆盖单独选择；关闭功能保留绑定及目录缓存。 |
| `app/.../artist/ui/catalog/ArtistCatalogViewModel.kt` | 协程请求与保存状态。以歌手上下文代次和请求代次拒绝过期回调；选择相同已绑定歌手也会恢复被选择器取消的目录读取。 |
| `app/.../artist/ui/ArtistContent.kt` | 组合现有列表、曲数设置与资料库结果。设置变化重新选择代表版本；没有可用资料库时展示网易原列表及状态。 |

筛选不会写网易云接口，不复用账号 Cookie，不改 Room 表、歌曲播放或下载记录。MusicBrainz 类型不完整或无法唯一识别的条目保留在统计中，由“恢复全部”查看原目录。匹配统计覆盖全部已加载记录，最低曲数仅影响代表版本和实际显示，因此“匹配数”可能大于当前显示数。

新增测试覆盖阈值与去重的组合、未知曲数、重选原绑定导致的请求取消，以及原有分页失败、冷却、离线缓存、过期上下文、同名歌手、Live/EP、U2 完整目录回放。按仓库功能 PR 模板提交时，分别记录 Android/shared 单测、桌面编译、Release 构建的实际结果；本说明本身不代替执行记录。界面截图应注明 Android，资料库 UI 当前未移植到桌面端。

后续合并官方更新时，重点检查 `ArtistAlbum` 的曲数/类型字段、网易 `more` 分页契约、共享 `SettingsPreferences` 接口以及歌手页面参数。资料库代码集中于 `catalog` 子目录；独立存储的 schema 版本与未知字段兼容策略不依赖官方数据库版本。上游若改变接口，应调整上述接入点并重跑契约测试，而非按歌手名称添加特例。

## 来源

- [MusicBrainz Release Group](https://musicbrainz.org/doc/Release_Group)
- [MusicBrainz 类型定义](https://musicbrainz.org/doc/Release_Group/Type)
- [U2《Rattle and Hum》官方说明](https://www.u2.com/products/rattle-and-hum)
- [MusicBrainz API 限频和 User-Agent](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting)
- [Sony《THE ASHTRAY》发行说明](https://www.sonymusic.co.jp/artist/Suchmos/discography/KSJL-6200)
- [OpenCC TSCharacters 字表](https://github.com/BYVoid/OpenCC/blob/master/data/dictionary/TSCharacters.txt)
