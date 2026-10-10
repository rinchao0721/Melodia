package com.lin0721.linmusic.desktop.platform.native

// 桌面端支持的目标平台。
// 说明：暂不纳入 macOS，等实际排期时在此追加枚举值，KSP 会立刻要求补齐所有实现与注册。
enum class DesktopPlatform {
    WINDOWS,
    LINUX;

    companion object {
        // 全应用唯一的平台判定。PlatformModule 注册、AppPaths 提前取目录等都从这里取，
        // 避免各处重复写 os.name 判断导致行为不一致。
        val current: DesktopPlatform by lazy {
            if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
                WINDOWS
            } else {
                LINUX
            }
        }
    }
}
