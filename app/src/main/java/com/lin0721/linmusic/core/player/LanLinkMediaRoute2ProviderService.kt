package com.lin0721.linmusic.core.player

import android.media.MediaRoute2Info
import android.media.MediaRoute2ProviderService
import android.media.RoutingSessionInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import org.koin.android.ext.android.inject

// 系统媒体路由提供服务：将已连接的局域网桌面端作为远程输出设备暴露给系统（API 30+）
@RequiresApi(Build.VERSION_CODES.R)
class LanLinkMediaRoute2ProviderService : MediaRoute2ProviderService(), LanLinkMediaRouteCoordinator.ProviderBridge {

    private val coordinator: LanLinkMediaRouteCoordinator by inject()

    override fun onCreate() {
        super.onCreate()
        coordinator.attachProvider(this)
    }

    override fun onDestroy() {
        coordinator.detachProvider(this)
        super.onDestroy()
    }

    override fun publishRoutes(snapshot: LanLinkRouteSnapshot?) {
        if (snapshot == null) {
            notifyRoutes(emptyList())
        } else {
            notifyRoutes(listOf(buildMediaRoute2Info(snapshot)))
        }
    }

    override fun syncSession(snapshot: LanLinkRouteSnapshot?, isRemoteTarget: Boolean) {
        if (snapshot == null || !isRemoteTarget) {
            releaseAllSessions()
            return
        }
        for (session in allSessionInfo.toList()) {
            if (session.selectedRoutes.contains(snapshot.routeId)) {
                val updated = buildRoutingSessionInfo(session.id, session.clientPackageName, snapshot)
                notifySessionUpdated(updated)
            } else {
                notifySessionReleased(session.id)
            }
        }
    }

    override fun releaseAllSessions() {
        for (session in allSessionInfo.toList()) {
            notifySessionReleased(session.id)
        }
    }

    override fun onCreateSession(
        requestId: Long,
        packageName: String,
        routeId: String,
        sessionHints: Bundle?
    ) {
        val snapshot = coordinator.createSessionFromProvider(routeId)
        if (snapshot == null) {
            notifyRequestFailed(requestId, REASON_REJECTED)
            return
        }
        val sessionId = lanLinkSessionIdFor(snapshot.peerDeviceId)
        notifySessionCreated(
            requestId,
            buildRoutingSessionInfo(sessionId, packageName, snapshot)
        )
    }

    override fun onReleaseSession(requestId: Long, sessionId: String) {
        coordinator.releaseSessionFromProvider(sessionId)
        if (getSessionInfo(sessionId) != null) {
            notifySessionReleased(sessionId)
        }
    }

    override fun onSelectRoute(requestId: Long, sessionId: String, routeId: String) {
        notifyRequestFailed(requestId, REASON_INVALID_COMMAND)
    }

    override fun onDeselectRoute(requestId: Long, sessionId: String, routeId: String) {
        notifyRequestFailed(requestId, REASON_INVALID_COMMAND)
    }

    override fun onTransferToRoute(requestId: Long, sessionId: String, routeId: String) {
        val existing = getSessionInfo(sessionId)
        val current = coordinator.routeSnapshot.value
        if (existing != null && current != null && current.routeId == routeId) {
            notifySessionUpdated(buildRoutingSessionInfo(sessionId, existing.clientPackageName, current))
        } else {
            notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
        }
    }

    override fun onSetRouteVolume(requestId: Long, routeId: String, volume: Int) {
        val current = coordinator.routeSnapshot.value
        if (current == null || current.routeId != routeId) {
            notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
            return
        }
        coordinator.setVolumeStepFromProvider(volume)
    }

    override fun onSetSessionVolume(requestId: Long, sessionId: String, volume: Int) {
        val session = getSessionInfo(sessionId)
        if (session == null) {
            notifyRequestFailed(requestId, REASON_INVALID_COMMAND)
            return
        }
        coordinator.setVolumeStepFromProvider(volume)
        coordinator.routeSnapshot.value?.let { snap ->
            notifySessionUpdated(
                buildRoutingSessionInfo(
                    sessionId,
                    session.clientPackageName,
                    snap.copy(volumeStep = volume.coerceIn(0, LanLinkRemoteVolume.MAX_STEPS))
                )
            )
        }
    }

    private fun buildMediaRoute2Info(snapshot: LanLinkRouteSnapshot): MediaRoute2Info {
        val extras = Bundle().apply {
            putString(LanLinkMediaRouteCoordinator.EXTRA_PEER_DEVICE_ID, snapshot.peerDeviceId)
        }
        val builder = MediaRoute2Info.Builder(snapshot.routeId, snapshot.peerDeviceName)
            .addFeature(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK)
            .addFeature(MediaRoute2Info.FEATURE_REMOTE_AUDIO_PLAYBACK)
            .setConnectionState(MediaRoute2Info.CONNECTION_STATE_CONNECTED)
            .setDescription("Melodia 局域网联动")
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolumeMax(LanLinkRemoteVolume.MAX_STEPS)
            .setVolume(snapshot.volumeStep)
            .setClientPackageName(packageName)
            .setExtras(extras)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setVisibilityPublic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                builder.setType(MediaRoute2Info.TYPE_REMOTE_COMPUTER)
                builder.setSuitabilityStatus(MediaRoute2Info.SUITABILITY_STATUS_SUITABLE_FOR_DEFAULT_TRANSFER)
            } else {
                builder.setType(MediaRoute2Info.TYPE_REMOTE_SPEAKER)
            }
        }
        return builder.build()
    }

    private fun buildRoutingSessionInfo(
        sessionId: String,
        clientPackageName: String,
        snapshot: LanLinkRouteSnapshot
    ): RoutingSessionInfo {
        return RoutingSessionInfo.Builder(sessionId, clientPackageName)
            .setName(snapshot.peerDeviceName)
            .addSelectedRoute(snapshot.routeId)
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolumeMax(LanLinkRemoteVolume.MAX_STEPS)
            .setVolume(snapshot.volumeStep)
            .build()
    }
}
