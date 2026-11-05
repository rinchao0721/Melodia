package com.lin0721.linmusic.desktop.platform.windows.taskbar

import com.lin0721.linmusic.desktop.platform.windows.RegistryCli
import com.sun.jna.Pointer

private const val PERSONALIZE_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
private const val IMMERSIVE_COLOR_SET = "ImmersiveColorSet"
private val DwordLine = Regex("""^\s*(\w+)\s+REG_DWORD\s+0x([0-9a-fA-F]+)""", RegexOption.MULTILINE)

// 系统（任务栏）的深浅色主题；阻塞调用，需在 IO 线程执行
internal object SystemTheme {

    // 读取失败时按深色处理
    fun taskbarUsesLightTheme(): Boolean {
        val result = RegistryCli.run("query", PERSONALIZE_KEY)?.takeIf { it.exitCode == 0 } ?: return false
        return parseSystemUsesLightTheme(result.output) ?: false
    }

    // 应用（非任务栏）的深浅色；读取失败按深色处理
    fun appsUseLightTheme(): Boolean {
        val result = RegistryCli.run("query", PERSONALIZE_KEY)?.takeIf { it.exitCode == 0 } ?: return false
        return parseAppsUseLightTheme(result.output) ?: false
    }
}

// WM_SETTINGCHANGE 中系统深浅色或强调色变化的通知
internal fun isImmersiveColorSetChange(wParam: Long, lParam: Long): Boolean =
    wParam == 0L && lParam != 0L && Pointer(lParam).getWideString(0) == IMMERSIVE_COLOR_SET

// 解析 reg query 的输出；缺少 SystemUsesLightTheme 时返回 null
internal fun parseSystemUsesLightTheme(regOutput: String): Boolean? {
    val values = DwordLine.findAll(regOutput).associate { it.groupValues[1] to it.groupValues[2].toLongOrNull(16) }
    return values["SystemUsesLightTheme"]?.let { it != 0L }
}

// 解析 reg query 的输出；缺少 AppsUseLightTheme 时返回 null
internal fun parseAppsUseLightTheme(regOutput: String): Boolean? {
    val values = DwordLine.findAll(regOutput).associate { it.groupValues[1] to it.groupValues[2].toLongOrNull(16) }
    return values["AppsUseLightTheme"]?.let { it != 0L }
}
