package com.lin0721.linmusic.desktop.platform.native

import java.io.File

// 应用各类文件目录的平台契约。
//
// 差异点：Windows 用 %APPDATA%/%LOCALAPPDATA%，Linux 走 XDG（$XDG_DATA_HOME / ~/.cache）。
// 实现分别位于 native/windows 与 native/linux，由 PlatformFactory 按运行平台创建。
//
// 之所以是接口而非各平台各写一份工具类：目录命名与层级（logs/、datastore/、*_cache/）是跨平台
// 共享的约定，只有"根目录怎么取"因平台而异，因此把根目录下沉到实现，派生目录留在默认实现里。
@RequireAllPlatforms
interface AppPaths {

    // 数据根目录（配置、登录态、日志等持久数据）
    val dataDir: File

    // 缓存根目录（可随时删除）
    val cacheDir: File

    // 日志目录
    val logDir: File get() = File(dataDir, "logs")

    // 图片缓存
    val imageCacheDir: File get() = File(cacheDir, "image_cache")

    // 音频缓存（流录制落盘）
    val audioCacheDir: File get() = File(cacheDir, "audio_cache")

    // 元数据缓存
    val metadataCacheDir: File get() = File(cacheDir, "meta_cache")

    // 下载临时目录
    val downloadTempDir: File get() = File(cacheDir, "download_tmp")

    // 默认下载目录（用户可改）
    val defaultDownloadDir: File get() = File(File(System.getProperty("user.home"), "Music"), "Melodia")

    // DataStore 偏好文件
    fun preferencesFile(name: String): File =
        File(File(dataDir, "datastore").apply { mkdirs() }, "$name.preferences_pb")

    companion object {
        // 全局实例：Koin 启动前（如单实例锁、日志初始化）也需要目录，故提供一个不依赖容器的入口。
        // 实例由 PlatformFactory 生成，与 Koin 中注册的是同一个。
        val current: AppPaths by lazy {
            PlatformFactory.create(platformImplClass(DesktopPlatform.current, WINDOWS_IMPL, LINUX_IMPL))
        }

        // 各平台实现的全限定名。AppPaths 需要在 Koin 之前可用，无法走容器解析，
        // 因此这里显式登记；其余平台能力由 KSP 生成的模块统一注册。
        private const val WINDOWS_IMPL = "com.lin0721.linmusic.desktop.platform.native.windows.WindowsAppPaths"
        private const val LINUX_IMPL = "com.lin0721.linmusic.desktop.platform.native.linux.LinuxAppPaths"

        private fun platformImplClass(platform: DesktopPlatform, windows: String, linux: String): String =
            when (platform) {
                DesktopPlatform.WINDOWS -> windows
                DesktopPlatform.LINUX -> linux
            }
    }
}
