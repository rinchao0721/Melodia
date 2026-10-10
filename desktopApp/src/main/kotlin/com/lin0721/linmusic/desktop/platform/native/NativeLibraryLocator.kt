package com.lin0721.linmusic.desktop.platform.native

// 原生动态库的定位契约：不同平台的文件名与搜索机制不同。
//
// 与其余平台能力的差异：本接口不通过 Koin 注册（原生库在播放引擎初始化时直接用），
// 由 NativeLibraryLocator.current 按 DesktopPlatform.current 给出实现。
interface NativeLibraryLocator {

    // 该平台可尝试的库名候选（按优先级排列）。
    // JNA 会把裸名映射为平台文件名（如 mpv → libmpv.so），带路径的候选则原样使用。
    fun candidates(resourcesDir: String?): List<String>

    // 需要额外注册到 JNA 搜索路径的目录（仅当 resourcesDir 非空且平台需要时返回）。
    // 返回 null 代表无需预注册搜索路径。
    fun searchPathRoot(resourcesDir: String?): String? = null

    // 预注册搜索路径时使用的库名（JNA 的 addSearchPath 需要库名作 key）。
    fun searchPathLibraryName(): String? = null

    companion object {
        val current: NativeLibraryLocator by lazy {
            when (DesktopPlatform.current) {
                DesktopPlatform.WINDOWS -> WindowsNativeLibraryLocator()
                DesktopPlatform.LINUX -> LinuxNativeLibraryLocator()
            }
        }
    }
}

// Windows：libmpv-2.dll，随包分发时需把资源目录加入 JNA 搜索路径
internal class WindowsNativeLibraryLocator : NativeLibraryLocator {
    private val name = "libmpv-2"

    override fun candidates(resourcesDir: String?): List<String> = listOf(name)

    override fun searchPathRoot(resourcesDir: String?): String? = resourcesDir

    override fun searchPathLibraryName(): String = name
}

// Linux：优先随包分发的 .so，再回退系统库；JNA 会把 "mpv" 映射为 libmpv.so
internal class LinuxNativeLibraryLocator : NativeLibraryLocator {
    override fun candidates(resourcesDir: String?): List<String> = buildList {
        if (resourcesDir != null) {
            add("$resourcesDir/libmpv.so.2")
            add("$resourcesDir/libmpv.so")
        }
        add("mpv")
        add("libmpv.so.2")
    }
}
