package com.lin0721.linmusic.desktop.platform.native

// 系统强调色（窗口边框）。取不到返回 null，上层退回中性线。
// 阻塞调用，需在 IO 线程执行。
@RequireAllPlatforms
interface SystemAccentProvider {

    // 仅当系统开启“在标题栏和窗口边框显示强调色”时返回 0xRRGGBB，否则为 null
    fun windowBorderAccent(): Int?
}
