package com.lin0721.linmusic.desktop.platform.native

import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.flow.StateFlow

// 系统媒体控制会话：Windows 走 SMTC，Linux 后续走 MPRIS over D-Bus。
// 实现分别位于 platform/windows 与 platform/linux，由 PlatformModule 按平台选择。
@RequireAllPlatforms
interface SystemMediaSession {

    val available: StateFlow<Boolean>

    fun start(): Boolean

    fun setEnabled(value: Boolean)

    fun shutdown()

    suspend fun bind(controller: PlaybackController, playerViewModel: PlayerViewModel)
}
