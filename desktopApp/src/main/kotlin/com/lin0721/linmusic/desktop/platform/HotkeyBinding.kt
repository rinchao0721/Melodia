package com.lin0721.linmusic.desktop.platform

import java.awt.event.KeyEvent as AwtKeyEvent

enum class HotkeyAction(val label: String) {
    PlayPause("播放/暂停"),
    Previous("上一首"),
    Next("下一首"),
    VolumeUp("音量加"),
    VolumeDown("音量减"),
    ToggleDesktopLyric("桌面歌词开关"),
    ToggleDesktopLyricLock("锁定/解锁桌面歌词")
}

// 与平台无关的组合键；修饰键位与键码取值沿用 Win32 的 MOD_* 与虚拟键码，已保存的配置因此无需迁移，
// 各平台实现自行转换成本平台的注册参数
data class HotkeyCombo(val modifiers: Int, val vk: Int) {

    val label: String
        get() = buildList {
            if (modifiers and MOD_CTRL != 0) add("Ctrl")
            if (modifiers and MOD_ALT != 0) add("Alt")
            if (modifiers and MOD_SHIFT != 0) add("Shift")
            add(keyName(vk) ?: "0x%02X".format(vk))
        }.joinToString("+")

    fun encode(): String = "$modifiers:$vk"

    companion object {
        const val MOD_ALT = 0x0001
        const val MOD_CTRL = 0x0002
        const val MOD_SHIFT = 0x0004

        private const val VK_SPACE = 0x20
        private const val VK_PRIOR = 0x21
        private const val VK_NEXT = 0x22
        private const val VK_END = 0x23
        private const val VK_HOME = 0x24
        private const val VK_LEFT = 0x25
        private const val VK_UP = 0x26
        private const val VK_RIGHT = 0x27
        private const val VK_DOWN = 0x28
        private const val VK_INSERT = 0x2D
        private const val VK_DELETE = 0x2E
        private const val VK_F1 = 0x70
        private const val VK_F12 = 0x7B

        private val namedKeys = mapOf(
            VK_SPACE to "Space",
            VK_PRIOR to "PgUp",
            VK_NEXT to "PgDn",
            VK_END to "End",
            VK_HOME to "Home",
            VK_LEFT to "←",
            VK_UP to "↑",
            VK_RIGHT to "→",
            VK_DOWN to "↓",
            VK_INSERT to "Insert",
            VK_DELETE to "Delete"
        )

        val defaults: Map<HotkeyAction, HotkeyCombo> = run {
            val ctrlAlt = MOD_CTRL or MOD_ALT
            mapOf(
                HotkeyAction.PlayPause to HotkeyCombo(ctrlAlt, 'P'.code),
                HotkeyAction.Previous to HotkeyCombo(ctrlAlt, VK_LEFT),
                HotkeyAction.Next to HotkeyCombo(ctrlAlt, VK_RIGHT),
                HotkeyAction.VolumeUp to HotkeyCombo(ctrlAlt, VK_UP),
                HotkeyAction.VolumeDown to HotkeyCombo(ctrlAlt, VK_DOWN),
                HotkeyAction.ToggleDesktopLyric to HotkeyCombo(ctrlAlt, 'L'.code),
                HotkeyAction.ToggleDesktopLyricLock to HotkeyCombo(ctrlAlt, 'K'.code)
            )
        }

        fun keyName(vk: Int): String? = when (vk) {
            in 'A'.code..'Z'.code, in '0'.code..'9'.code -> vk.toChar().toString()
            in VK_F1..VK_F12 -> "F${vk - VK_F1 + 1}"
            else -> namedKeys[vk]
        }

        fun isSupportedKey(vk: Int): Boolean = keyName(vk) != null

        // AWT 键码在支持范围内与本键码一致，只有 Insert、Delete 不同
        fun keyCodeFromAwt(awtCode: Int): Int = when (awtCode) {
            AwtKeyEvent.VK_INSERT -> VK_INSERT
            AwtKeyEvent.VK_DELETE -> VK_DELETE
            else -> awtCode
        }

        fun modifiersOf(ctrl: Boolean, alt: Boolean, shift: Boolean): Int {
            var modifiers = 0
            if (ctrl) modifiers = modifiers or MOD_CTRL
            if (alt) modifiers = modifiers or MOD_ALT
            if (shift) modifiers = modifiers or MOD_SHIFT
            return modifiers
        }

        // 全局热键必须带 Ctrl 或 Alt，避免单键误触
        fun isValid(modifiers: Int, vk: Int): Boolean =
            modifiers and (MOD_CTRL or MOD_ALT) != 0 && isSupportedKey(vk)

        fun decode(raw: String): HotkeyCombo? {
            val parts = raw.split(':')
            if (parts.size != 2) return null
            val modifiers = parts[0].toIntOrNull() ?: return null
            val vk = parts[1].toIntOrNull() ?: return null
            return if (isValid(modifiers, vk)) HotkeyCombo(modifiers, vk) else null
        }
    }
}
