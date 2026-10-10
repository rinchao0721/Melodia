package com.lin0721.linmusic.desktop.platform.native.windows

import com.lin0721.linmusic.desktop.platform.native.AutoStartManager
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl

private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
private const val VALUE_NAME = "Melodia"

// 开机自启以注册表 Run 项为准；阻塞调用，需在 IO 线程执行
@PlatformImpl(DesktopPlatform.WINDOWS)
class WindowsAutoStartManager : AutoStartManager {

    // jpackage 启动器注入的 exe 路径，开发环境运行时为空
    private val exePath: String? = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    override val isSupported: Boolean get() = exePath != null

    override fun isEnabled(): Boolean {
        if (!isSupported) return false
        val result = RegistryCli.run("query", RUN_KEY, "/v", VALUE_NAME) ?: return false
        return result.exitCode == 0 && result.output.contains(exePath!!, ignoreCase = true)
    }

    override fun setEnabled(enabled: Boolean): Boolean {
        val path = exePath ?: return false
        val result = if (enabled) {
            RegistryCli.run("add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", "\"$path\"", "/f")
        } else {
            RegistryCli.run("delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        }
        return result?.exitCode == 0
    }
}
