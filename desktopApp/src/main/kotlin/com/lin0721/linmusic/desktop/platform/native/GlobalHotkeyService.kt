package com.lin0721.linmusic.desktop.platform.native

import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import kotlinx.coroutines.flow.StateFlow

// 全局快捷键服务：Windows 用 RegisterHotKey + WM_HOTKEY，
// 其他平台暂用 no-op（X11 XGrabKey / Wayland 另行实现）。
@RequireAllPlatforms
interface GlobalHotkeyService {

    var onAction: ((HotkeyAction) -> Unit)?

    // 注册失败（多为被其他程序占用）的自定义快捷键
    val failed: StateFlow<Set<HotkeyAction>>

    fun start()

    fun stop()

    fun apply(custom: Map<HotkeyAction, HotkeyCombo?>, mediaKeys: Boolean)

    // 录制新组合期间暂停，避免按键被已注册的热键拦截
    fun pause()

    fun resume()

    // 把 AWT 键码与修饰键状态翻译成本平台可注册的组合。
    // 返回 null 表示该按键在本平台不支持，调用方据此提示用户。
    //
    // 之所以放在服务上而不是 UI 里：各平台的键码空间不同（Windows 用虚拟键码，X11 用 keysym），
    // 转换规则属平台细节，UI 只负责把 AWT 事件交出去。
    fun toHotkeyCombo(awtKeyCode: Int, ctrl: Boolean, alt: Boolean, shift: Boolean): HotkeyCombo?
}
