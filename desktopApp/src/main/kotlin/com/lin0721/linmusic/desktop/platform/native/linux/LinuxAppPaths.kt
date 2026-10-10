package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.desktop.platform.native.AppPaths
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl
import java.io.File

// Linux 目录约定遵循 XDG Base Directory：
//   数据 → $XDG_DATA_HOME/Melodia（默认 ~/.local/share/Melodia）
//   缓存 → $XDG_CACHE_HOME/Melodia（默认 ~/.cache/Melodia）
@PlatformImpl(DesktopPlatform.LINUX)
class LinuxAppPaths : AppPaths {

    private val home = System.getProperty("user.home")

    override val dataDir: File by lazy {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share"
        File(base, "Melodia").apply { mkdirs() }
    }

    override val cacheDir: File by lazy {
        val base = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.cache"
        File(base, "Melodia").apply { mkdirs() }
    }
}
