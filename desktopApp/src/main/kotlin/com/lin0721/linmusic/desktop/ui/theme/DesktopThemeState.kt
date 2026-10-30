package com.lin0721.linmusic.desktop.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.ThemeSettings
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.core.ui.skin.Skin
import com.lin0721.linmusic.core.ui.skin.resolveSkinPalette
import com.lin0721.linmusic.desktop.platform.WindowEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DesktopThemeState"

// 系统应用深浅色，供“跟随系统”使用
object SystemDarkTheme {

    private val _isDark = MutableStateFlow(true)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    // 阻塞读取，供首帧前同步初始值
    fun refreshBlocking(effects: WindowEffects) {
        _isDark.value = readIsDark(effects)
    }

    suspend fun refresh(effects: WindowEffects) {
        _isDark.value = withContext(Dispatchers.IO) { readIsDark(effects) }
    }

    private fun readIsDark(effects: WindowEffects): Boolean = try {
        !effects.systemAppsUseLightTheme()
    } catch (e: Exception) {
        AppLogger.w(TAG, "读取系统深浅色失败", e)
        true
    }
}

fun resolveDesktopPalette(settings: ThemeSettings, systemDark: Boolean, skin: Skin?): MelodiaPalette {
    val isDark = settings.mode.isDark(systemDark)
    return resolveSkinPalette(isDark, settings.accentId(isDark), settings.customHex(isDark), skin)
}

@Composable
fun rememberDesktopPalette(settings: ThemeSettings, skin: Skin?, effects: WindowEffects): MelodiaPalette {
    val systemDark by SystemDarkTheme.isDark.collectAsState()
    val scope = rememberCoroutineScope()
    DisposableEffect(effects) {
        val unregister = effects.observeSystemTheme { scope.launch { SystemDarkTheme.refresh(effects) } }
        onDispose { unregister() }
    }
    return remember(settings, systemDark, skin) { resolveDesktopPalette(settings, systemDark, skin) }
}
