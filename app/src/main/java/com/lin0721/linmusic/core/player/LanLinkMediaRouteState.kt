package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.lanlink.ConnectedPeerInfo
import com.lin0721.linmusic.core.lanlink.PlaybackTarget

// 远程设备系统音量步进映射（0..100 百分比 <-> 0..20 系统音量格，每格 5%）
object LanLinkRemoteVolume {
    const val MAX_STEPS = 20
    const val STEP_PERCENT = 5

    fun percentToStep(percent: Int): Int =
        ((percent.coerceIn(0, 100) + 2) / STEP_PERCENT).coerceIn(0, MAX_STEPS)

    fun stepToPercent(step: Int): Int =
        step.coerceIn(0, MAX_STEPS) * STEP_PERCENT

    fun increaseStep(currentPercent: Int): Int =
        (percentToStep(currentPercent) + 1).coerceAtMost(MAX_STEPS)

    fun decreaseStep(currentPercent: Int): Int =
        (percentToStep(currentPercent) - 1).coerceAtLeast(0)

    fun unmutePercent(lastAudiblePercent: Int): Int =
        lastAudiblePercent.takeIf { it > 0 }?.coerceIn(0, 100) ?: 100
}

// 系统输出路由发布快照
data class LanLinkRouteSnapshot(
    val routeId: String,
    val peerDeviceId: String,
    val peerDeviceName: String,
    val volumeStep: Int
)

fun lanLinkRouteIdFor(deviceId: String): String = "lanlink_route_$deviceId"

fun lanLinkSessionIdFor(deviceId: String): String = "lanlink_session_$deviceId"

fun resolvePublishedLanLinkRoute(
    lanLinkEnabled: Boolean,
    keepaliveEnabled: Boolean,
    connectedPeer: ConnectedPeerInfo?,
    volumePercent: Int
): LanLinkRouteSnapshot? {
    if (!lanLinkEnabled || !keepaliveEnabled || connectedPeer == null) return null
    val displayName = connectedPeer.deviceName.ifBlank { "桌面端" }
    return LanLinkRouteSnapshot(
        routeId = lanLinkRouteIdFor(connectedPeer.deviceId),
        peerDeviceId = connectedPeer.deviceId,
        peerDeviceName = displayName,
        volumeStep = LanLinkRemoteVolume.percentToStep(volumePercent)
    )
}

fun shouldUseRemoteSessionPlayer(
    keepaliveEnabled: Boolean,
    target: PlaybackTarget
): Boolean = keepaliveEnabled && target is PlaybackTarget.Remote

fun shouldKeepServiceOnTaskRemoved(
    mediaPlaybackForegroundActive: Boolean,
    keepaliveEnabled: Boolean,
    lanLinkEnabled: Boolean,
    hasConnectedPeer: Boolean,
    hasPairedDevices: Boolean
): Boolean = mediaPlaybackForegroundActive || (
    keepaliveEnabled &&
        lanLinkEnabled &&
        (hasConnectedPeer || hasPairedDevices)
    )
