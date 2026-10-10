package com.lin0721.linmusic.desktop.platform.native.windows

import com.lin0721.linmusic.desktop.platform.native.AppPaths
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl
import java.io.File

// Windows 目录约定：数据放 %APPDATA%\Melodia，缓存放 %LOCALAPPDATA%\Melodia\cache。
// 两者取不到环境变量时回退用户主目录。
@PlatformImpl(DesktopPlatform.WINDOWS)
class WindowsAppPaths : AppPaths {

    private val home = System.getProperty("user.home")

    override val dataDir: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: home
        File(base, "Melodia").apply { mkdirs() }
    }

    override val cacheDir: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        val dir = if (base != null) File(File(base, "Melodia"), "cache") else File(dataDir, "cache")
        dir.apply { mkdirs() }
    }
}
