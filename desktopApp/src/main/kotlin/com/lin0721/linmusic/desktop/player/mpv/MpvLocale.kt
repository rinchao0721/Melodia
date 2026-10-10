package com.lin0721.linmusic.desktop.player.mpv

import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer

// mpv 的选项解析依赖 C 区域的数值格式（小数点必须是 "."），进程 LC_NUMERIC 非 "C" 时
// mpv_create 会直接返回空句柄并打印 "Non-C locale detected. This is not supported."。
// JVM 启动时会按系统 locale 初始化 C 区域（如 zh_CN.UTF-8），因此这里在加载 mpv 前
// 显式把 LC_NUMERIC 纠正回 "C"；只影响 C 区域，不影响 Java 侧的 Locale。
private interface LibC : Library {
    fun setlocale(category: Int, locale: String): Pointer?
}

internal object MpvLocale {

    // 各平台 C 运行库的 LC_NUMERIC 常量值不同
    private val lcNumeric = when (DesktopPlatform.current) {
        DesktopPlatform.LINUX -> 1      // glibc / musl
        DesktopPlatform.WINDOWS -> 4    // MSVC CRT
    }

    private var applied = false

    fun ensureNumericLocaleC() {
        if (applied) return
        applied = true
        // 失败不阻断：平台不支持时保持原状，后续 mpv_create 会以既有方式报错
        runCatching {
            Native.load(Platform.C_LIBRARY_NAME, LibC::class.java).setlocale(lcNumeric, "C")
        }
    }
}
