package com.lin0721.linmusic.desktop.platform.native.windows

import com.lin0721.linmusic.desktop.platform.native.SystemAccentProvider
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl

private const val DWM_KEY = "HKCU\\Software\\Microsoft\\Windows\\DWM"
private val DwordLine = Regex("""^\s*(\w+)\s+REG_DWORD\s+0x([0-9a-fA-F]+)""", RegexOption.MULTILINE)

// 系统强调色；阻塞调用，需在 IO 线程执行
@PlatformImpl(DesktopPlatform.WINDOWS)
class WindowsSystemAccentProvider : SystemAccentProvider {

    // 仅当用户开启“在标题栏和窗口边框显示强调色”时返回 0xRRGGBB，否则为 null
    override fun windowBorderAccent(): Int? {
        val result = RegistryCli.run("query", DWM_KEY)?.takeIf { it.exitCode == 0 } ?: return null
        val values = DwordLine.findAll(result.output).associate { it.groupValues[1] to it.groupValues[2].toLongOrNull(16) }
        if (values["ColorPrevalence"] != 1L) return null
        val abgr = values["AccentColor"] ?: return null
        val red = (abgr and 0xFF).toInt()
        val green = ((abgr shr 8) and 0xFF).toInt()
        val blue = ((abgr shr 16) and 0xFF).toInt()
        return (red shl 16) or (green shl 8) or blue
    }
}
