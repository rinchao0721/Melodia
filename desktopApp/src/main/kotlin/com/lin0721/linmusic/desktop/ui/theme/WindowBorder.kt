package com.lin0721.linmusic.desktop.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.lin0721.linmusic.desktop.platform.native.SystemAccentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

private const val NEUTRAL_ALPHA = 0.40f
private const val INACTIVE_DIM = 0.65f

// 主窗口外框线颜色：系统开启“窗口边框显示强调色”时跟随，否则用中性细线
object WindowBorder {

    private val _accentRgb = MutableStateFlow<Int?>(null)
    val accentRgb: StateFlow<Int?> = _accentRgb.asStateFlow()

    suspend fun refresh() {
        val provider = GlobalContext.get().get<SystemAccentProvider>()
        _accentRgb.value = withContext(Dispatchers.IO) { provider.windowBorderAccent() }
    }

    // 系统边框不支持透明度，中性线按透明度预先混合到窗口底色上；失焦时 DWM 会把边框压暗到约 65%，需提前补偿
    fun opaqueRgb(accentRgb: Int?, focused: Boolean): Int {
        val opaque = accentRgb?.let { Color(0xFF000000.toInt() or it) }
            ?: lerp(
                DesktopColors.WindowBackground,
                Color.White,
                if (focused) NEUTRAL_ALPHA else (NEUTRAL_ALPHA / INACTIVE_DIM).coerceAtMost(1f)
            )
        return opaque.toArgb() and 0xFFFFFF
    }
}
