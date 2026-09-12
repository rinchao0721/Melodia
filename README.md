<div align="center">
  <img src="docs/images/app_icon.png" width="96" height="96" alt="Melodia 图标" />

# Melodia

基于 Kotlin + Jetpack Compose 构建的轻量、现代的第三方网易云音乐 Android 客户端


[![GitHub Release](https://img.shields.io/github/v/release/rinchao0721/Melodia?style=flat-square&color=blue)](https://github.com/rinchao0721/Melodia/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green?style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple?style=flat-square)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-GPL--3.0-orange?style=flat-square)](LICENSE)

</div>

---

## 截图预览

<div align="center">
  <img src="docs/screenshots/home.png" width="19%" alt="首页" />
  <img src="docs/screenshots/search.png" width="19%" alt="搜索" />
  <img src="docs/screenshots/library.png" width="19%" alt="音乐库" />
  <img src="docs/screenshots/player.png" width="19%" alt="播放器" />
  <img src="docs/screenshots/lyrics.png" width="19%" alt="歌词" />
</div>

---

## 下载与安装

您可以从 [GitHub Releases](https://github.com/rinchao0721/Melodia/releases) 下载最新签名的 Release APK 安装包：

- **正式版 (Release)**：经过完整验证的稳定版本，更新提示遵循正式发布渠道。
- **预发布版 (Beta / RC)**：包含最新功能与实验性改动，供尝鲜体验。
- **应用内更新**：进入应用后，可通过「设置 -> 关于」检查新版本并直接在应用内完成下载与更新。

---

## 项目架构

项目按**业务域 (Feature-Driven)** 组织代码：`core` 承载全局共享基础设施与通用能力，`feature` 下每个独立业务域自持 `data` / `domain` / `ui` 结构。依赖方向单向收敛——`feature` 依赖 `core`，`core` 不反向依赖 `feature`，各业务域之间解耦无循环依赖。

```text
app/src/main/java/com/lin0721/linmusic/
├── MelodiaApplication.kt        # 应用程序入口：Koin 依赖注入初始化与 Coil 预热
├── MainActivity.kt              # 单 Activity 架构：系统窗口适应与悬浮窗权限引导
├── MelodiaApp.kt                # 顶层组合：导航骨架编排与播放器图层联动
├── MelodiaNavHost.kt            # 核心路由分发与平滑转场动画配置
├── MelodiaOverlays.kt           # 全屏播放器底栏、底部弹窗与全局反馈浮层
├── MelodiaNavigationState.kt    # 自定义导航回退栈管理与参数上下文恢复
├── MelodiaPlayerSheetState.kt   # 全屏播放器展开/收起手势状态机
├── MelodiaSidebarState.kt       # 侧边栏抽屉滑动状态机
│
├── core/                        # 跨域共享基础能力
│   ├── api/                     # 账号鉴权与公共接口定义
│   ├── auth/                    # 登录状态、凭据持久化与用户信息同步
│   ├── comment/                 # 评论通用数据流与 UI 组件
│   ├── contentfilter/           # 内容屏蔽与黑名单过滤
│   ├── log/                     # 日志输出与异常捕获
│   ├── model/                   # 全局领域数据模型与实体映射
│   ├── network/                 # 原生加密引擎、OkHttp 拦截器与统一网络适配
│   ├── player/                  # 播放器核心引擎（ExoPlayer、队列调度、进度广播）
│   ├── playlistmutation/        # 歌单创建/编辑、歌曲增删与排序操作
│   ├── preferences/             # DataStore 与轻量级配置持久化
│   ├── songlike/                # 歌曲红心收藏状态管理
│   ├── ui/                      # Material 3 主题、通用交互组件与动画规范
│   ├── update/                  # 应用内更新检测、下载管理器与安装器组件
│   ├── userartist/              # 用户关注歌手列表管理
│   └── userplaylist/            # 用户歌单状态与列表维护
│
├── di/                          # Koin 依赖注入模块装配
│
└── feature/                     # 独立业务模块
    ├── account/                 # 账号登录与授权管理
    ├── artist/                  # 歌手主页、热门单曲与全部专辑
    ├── cloud/                   # 用户云盘资产管理
    ├── create/                  # 歌单新建与快捷操作
    ├── home/                    # 首页聚合流（推荐单曲/歌单、雷达）
    ├── library/                 # 个人音乐库（分类筛选与检索）
    ├── listendata/              # 年度/周听歌数据与足迹统计
    ├── message/                 # 私信与系统通知中心
    ├── music/                   # 音乐发现 Tab（风格流派与个性化推荐）
    ├── newworks/                # 新歌首发与数字专辑
    ├── player/                  # 全屏播放、动态歌词与歌曲详情交互
    ├── playlist/                # 歌单/专辑详情与音轨列表
    ├── podcast/                 # 电台播客节目
    ├── profile/                 # 用户个人主页与听歌排行
    ├── recent/                  # 最近播放历史记录
    ├── search/                  # 多模式实时搜索与热搜榜
    └── settings/                # 主题、音质、网络与缓存配置
```

---

## 技术栈与选型

- **UI 框架**：Jetpack Compose（基于 Material Design 3 ）
- **媒体引擎**：AndroidX Media3（ExoPlayer + MediaSession）
- **依赖注入**：Koin
- **网络通信**：Retrofit2 + OkHttp3 + kotlinx.serialization
- **异步与响应式**：Kotlin Coroutines + Flow / StateFlow
- **图片加载**：Coil（支持多级内存/磁盘缓存与渐进式展示）
- **持久化方案**：Jetpack DataStore & SharedPreferences
- **代码分析与规范**：Detekt + ktlint

---

## 原生网络加密实现

项目所有请求签名与参数加密均在 Kotlin 端原生实现：

- **EApi 路由**：MD5 签名 + 128 位 AES-ECB 加密，请求重定向至移动端网关 `interface.music.163.com` 并自动附带移动端特征，应用于排行榜、收藏列表、用户歌单及搜索等主要接口。
- **WeApi 路由**：AES-CBC + 1024 位 RSA 联合加密，路由至 `music.163.com`，用于历史日推及用户账户等特定交互接口。
- **设备指纹伪装**：网络拦截层自动注入真实设备指纹与地域伪装头信息，保障会话与接口调用的连续稳定性。

---

## 统一错误处理

Repository 边界统一产出 Kotlin `Result<T>`，异常类型抽象为领域错误模型 `AppError`（包括网络中断、风控拦截、鉴权过期、数据解析失败及业务错误码）。UI 表现层针对不同错误语义分流呈现对应的重试引导或提示。

---

## 构建与开发

### 环境要求

- **JDK**：21
- **Android SDK**：Compile / Target SDK 36，Min SDK 26
- **IDE**：Android Studio Ladybug (2024.2.1) 或更高版本

### 常用命令

- **调试构建**：
  ```bash
  ./gradlew assembleDebug
  ```
- **运行单元测试**：
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **构建 Release 安装包**：
  ```bash
  ./gradlew assembleRelease
  ```

### 混淆与签名说明

- **代码裁剪与混淆**：`release` 构建已开启 R8 压缩与混淆保护。项目对 Retrofit 接口与 `@Serializable` 数据传输类配置了显式 Keep 规则，确保混淆后的运行安全。
- **签名机制**：签名材料从版本控制外部注入（读取 `local.properties` 或 CI 环境变量）。若未配置正式签名，将自动回退为 Debug 签名，确保本地可编译出可运行的 APK。完整签名与自动化发版配置请参阅 [RELEASE_SIGNING.md](RELEASE_SIGNING.md)。
- **CI 流水线**：向 `main` 分支推送或提交 PR 时自动触发单元测试与 Release 构建验证；推送版本 Tag 时触发正式签名打包并发布 GitHub Release。

---

## 非常感谢

- [NeteaseCloudMusicApi](https://github.com/binaryify/NeteaseCloudMusicApi) - 网易云音乐 API 分析与实现参考
- [api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced) - 增强接口与加密逻辑参考
- [SPlayer](https://github.com/SPlayer-Dev/SPlayer) - 现代流媒体架构设计启发
- [Spotify](https://spotify.com) - 优秀的移动端流媒体交互范式与 UI/UX 体验灵感

---

## 免责声明

1. 本项目为个人技术交流与 Android 现代原生架构学习研究所用。
2. 本项目不包含任何商业目的，不提供任何形式的商业变现或增值服务。
3. 软件中调用的所有音频及元数据均来源于公开网络接口，版权归属于原权利人所有。请在下载安装后 24 小时内删除，严禁用于任何商业用途。
