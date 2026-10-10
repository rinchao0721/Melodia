package com.lin0721.linmusic.desktop.platform.native

// 桌面缩放系数：解决 Linux 分数缩放下 AWT 无法上报、Compose 界面偏小的问题。
//
// JVM/AWT 在 Linux 上只支持整数缩放（sun.java2d.uiScale 的分数值会被静默忽略，
// 实测 1.5 无效、2 生效），而 KDE 等桌面常见 125%/150%；此时 GraphicsConfiguration
// 的 defaultTransform 恒为 1.0，Compose 的 LocalDensity 随之=1.0。
// 实现分别位于 native/windows 与 native/linux，由 PlatformFactory 按运行平台创建。
@RequireAllPlatforms
interface UiScale {

    // 需要注入 Compose 的缩放；null 表示该平台无需干预（AWT 已正确上报）
    val detected: Float?

    // 窗口几何（逻辑坐标 ↔ AWT 像素）换算系数
    val factor: Float get() = detected ?: 1f

    companion object {
        // 与 AppPaths 同款：窗口几何换算散布在各处，统一走不依赖容器的入口；
        // 实例由 PlatformFactory 生成，与 Koin 中注册的是同一个
        val current: UiScale by lazy {
            PlatformFactory.create(platformImplClass(DesktopPlatform.current, WINDOWS_IMPL, LINUX_IMPL))
        }

        private const val WINDOWS_IMPL = "com.lin0721.linmusic.desktop.platform.native.windows.WindowsUiScale"
        private const val LINUX_IMPL = "com.lin0721.linmusic.desktop.platform.native.linux.LinuxUiScale"

        private fun platformImplClass(platform: DesktopPlatform, windows: String, linux: String): String =
            when (platform) {
                DesktopPlatform.WINDOWS -> windows
                DesktopPlatform.LINUX -> linux
            }
    }
}
