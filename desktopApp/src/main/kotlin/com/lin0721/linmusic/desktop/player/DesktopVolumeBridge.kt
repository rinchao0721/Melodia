package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.lanlink.RemoteVolumeBridge
import kotlinx.coroutines.flow.StateFlow

// 桌面端音量桥接：把本机控制器的音量读写适配给局域网联动遥控协调器
class DesktopVolumeBridge(
    private val localController: DesktopPlaybackController
) : RemoteVolumeBridge {
    override val volume: StateFlow<Int>
        get() = localController.volume

    override val lastAudibleVolume: StateFlow<Int>
        get() = localController.lastAudibleVolume

    override fun setVolume(percent: Int) {
        localController.setVolume(percent)
    }
}
