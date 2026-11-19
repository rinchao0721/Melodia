package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.lanlink.LanLinkRemoteCoordinator
import com.lin0721.linmusic.core.lanlink.PlaybackTarget
import com.lin0721.linmusic.core.lanlink.RoutingPlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

// 桌面端播放路由控制器：继承共享路由能力并实现 DesktopPlaybackController，
// 将音量与提示消息按当前目标路由，而本机输出设备枚举仍固定指向本机播放器
class RoutingDesktopPlaybackController(
    private val localDesktopController: DesktopPlaybackController,
    coordinator: LanLinkRemoteCoordinator,
    scope: CoroutineScope
) : RoutingPlaybackController(localDesktopController, coordinator, scope), DesktopPlaybackController {

    private val remoteController
        get() = coordinator.remoteController

    override val volume: StateFlow<Int> =
        combine(activeTarget, localDesktopController.volume, remoteController.volume) { target, l, r ->
            if (target is PlaybackTarget.Remote) r else l
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = if (activeTarget.value is PlaybackTarget.Remote) {
                remoteController.volume.value
            } else {
                localDesktopController.volume.value
            }
        )

    override val lastAudibleVolume: StateFlow<Int> =
        combine(
            activeTarget,
            localDesktopController.lastAudibleVolume,
            remoteController.lastAudibleVolume
        ) { target, l, r ->
            if (target is PlaybackTarget.Remote) r else l
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = if (activeTarget.value is PlaybackTarget.Remote) {
                remoteController.lastAudibleVolume.value
            } else {
                localDesktopController.lastAudibleVolume.value
            }
        )

    override fun setVolume(percent: Int) {
        if (activeTarget.value is PlaybackTarget.Remote) {
            remoteController.setVolume(percent)
        } else {
            localDesktopController.setVolume(percent)
        }
    }

    override fun playNextNow(item: QueueItem, startPositionMs: Long) {
        super<RoutingPlaybackController>.playNextNow(item, startPositionMs)
    }

    override val messages: Flow<String> = merge(localDesktopController.messages, coordinator.messages)

    val localUnavailableReason: String?
        get() = localDesktopController.unavailableReason

    override val unavailableReason: String?
        get() = if (activeTarget.value is PlaybackTarget.Remote) null else localDesktopController.unavailableReason

    override val audioDevices: StateFlow<List<AudioDevice>>
        get() = localDesktopController.audioDevices

    override val audioDevice: StateFlow<String>
        get() = localDesktopController.audioDevice

    override fun refreshAudioDevices() {
        localDesktopController.refreshAudioDevices()
    }

    override fun setAudioDevice(name: String) {
        localDesktopController.setAudioDevice(name)
    }

    override fun release() {
        localDesktopController.release()
    }
}
