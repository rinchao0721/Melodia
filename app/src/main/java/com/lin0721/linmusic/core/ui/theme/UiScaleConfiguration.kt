package com.lin0721.linmusic.core.ui.theme

import android.content.res.Configuration
import kotlin.math.roundToInt

// 界面缩放直接落在 Activity 的资源配置上：弹层、对话框、菜单这类独立窗口的 Compose 根会按自己的上下文重新取密度，
// 只在主题里覆盖 LocalDensity 进不去这些窗口；配置层的密度对所有窗口一致。
// dp 尺寸同步换算，窗口断点与方向判断读的是 screenWidthDp/screenHeightDp
fun Configuration.scaledBy(factor: Float): Configuration = Configuration(this).apply {
    densityDpi = (densityDpi * factor).roundToInt()
    screenWidthDp = (screenWidthDp / factor).roundToInt()
    screenHeightDp = (screenHeightDp / factor).roundToInt()
    smallestScreenWidthDp = (smallestScreenWidthDp / factor).roundToInt()
}
