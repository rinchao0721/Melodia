package com.lin0721.linmusic.core.player

import android.content.Context
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.lin0721.linmusic.core.lanlink.LanLinkAndroidClient
import com.lin0721.linmusic.core.lanlink.PlaybackTarget
import com.lin0721.linmusic.core.lanlink.RoutingPlaybackController
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "LanLinkMediaRoute"

// 局域网联动系统输出路由协调器：同步 MediaRoute2ProviderService、MediaRouter2 与远端遥控状态
class LanLinkMediaRouteCoordinator(
    context: Context,
    private val lanLinkClient: LanLinkAndroidClient,
    private val routingPlaybackController: RoutingPlaybackController,
    private val settingsPreferences: SettingsPreferences
) {
    interface ProviderBridge {
        fun publishRoutes(snapshot: LanLinkRouteSnapshot?)
        fun syncSession(snapshot: LanLinkRouteSnapshot?, isRemoteTarget: Boolean)
        fun releaseAllSessions()
    }

    companion object {
        const val EXTRA_PEER_DEVICE_ID = "com.lin0721.linmusic.extra.PEER_DEVICE_ID"
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _routingControllerId = MutableStateFlow<String?>(null)
    val routingControllerId: StateFlow<String?> = _routingControllerId.asStateFlow()

    private val _routeSnapshot = MutableStateFlow<LanLinkRouteSnapshot?>(null)
    val routeSnapshot: StateFlow<LanLinkRouteSnapshot?> = _routeSnapshot.asStateFlow()

    @Volatile
    private var started = false

    @Volatile
    private var providerBridge: ProviderBridge? = null

    private var routerBridge: MediaRouter2Bridge? = null

    fun start() {
        if (started) return
        started = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val remoteController = routingPlaybackController.coordinator.remoteController
        scope.launch {
            combine(
                lanLinkClient.enabled,
                settingsPreferences.lanLinkBackgroundKeepalive,
                lanLinkClient.connectedDevice,
                remoteController.volume,
                routingPlaybackController.activeTarget
            ) { enabled, keepalive, peer, volumePercent, target ->
                val snapshot = resolvePublishedLanLinkRoute(enabled, keepalive, peer, volumePercent)
                val isRemote = snapshot != null &&
                    target is PlaybackTarget.Remote &&
                    target.peer.deviceId == snapshot.peerDeviceId
                Triple(enabled && keepalive, snapshot, isRemote)
            }.distinctUntilChanged().collect { (routerActive, snapshot, isRemoteTarget) ->
                _routeSnapshot.value = snapshot

                val bridge = if (routerActive) ensureRouterBridge() else routerBridge
                if (routerActive) {
                    bridge?.ensureRegistered()
                } else {
                    bridge?.releaseAndUnregister()
                    _routingControllerId.value = null
                }

                providerBridge?.publishRoutes(snapshot)
                providerBridge?.syncSession(snapshot, isRemoteTarget)

                if (isRemoteTarget && snapshot != null) {
                    bridge?.ensureTransferredTo(snapshot)
                } else {
                    bridge?.ensureReleasedIfActive()
                    _routingControllerId.value = null
                }
            }
        }
    }

    fun attachProvider(bridge: ProviderBridge) {
        providerBridge = bridge
        val snapshot = _routeSnapshot.value
        bridge.publishRoutes(snapshot)
        bridge.syncSession(snapshot, isCurrentRemoteTarget(snapshot))
    }

    fun detachProvider(bridge: ProviderBridge) {
        if (providerBridge === bridge) {
            providerBridge = null
        }
    }

    fun createSessionFromProvider(routeId: String): LanLinkRouteSnapshot? {
        val snapshot = _routeSnapshot.value ?: return null
        if (snapshot.routeId != routeId) return null
        val switched = routingPlaybackController.switchToRemote()
        return if (switched) snapshot else null
    }

    fun releaseSessionFromProvider(sessionId: String) {
        _routingControllerId.value = null
        if (routingPlaybackController.activeTarget.value is PlaybackTarget.Remote) {
            routingPlaybackController.switchToLocal()
        }
    }

    fun setVolumeStepFromProvider(step: Int) {
        routingPlaybackController.coordinator.remoteController.setVolume(
            LanLinkRemoteVolume.stepToPercent(step)
        )
    }

    private fun isCurrentRemoteTarget(snapshot: LanLinkRouteSnapshot? = _routeSnapshot.value): Boolean {
        val target = routingPlaybackController.activeTarget.value
        return snapshot != null &&
            target is PlaybackTarget.Remote &&
            target.peer.deviceId == snapshot.peerDeviceId
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ensureRouterBridge(): MediaRouter2Bridge {
        return routerBridge ?: MediaRouter2Bridge().also { routerBridge = it }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private inner class MediaRouter2Bridge {
        private val mediaRouter2: MediaRouter2 = MediaRouter2.getInstance(appContext)
        private val executor = ContextCompat.getMainExecutor(appContext)
        private val discoveryPreference = RouteDiscoveryPreference.Builder(
            listOf(
                MediaRoute2Info.FEATURE_REMOTE_AUDIO_PLAYBACK,
                MediaRoute2Info.FEATURE_REMOTE_PLAYBACK
            ),
            false
        ).build()

        private var registered = false
        private var selfTransferTargetRouteId: String? = null
        private var lastRequestedRouteId: String? = null

        private val routeCallback = object : MediaRouter2.RouteCallback() {
            override fun onRoutesAdded(routes: MutableList<MediaRoute2Info>) {
                maybeCompletePendingTransfer()
            }

            override fun onRoutesChanged(routes: MutableList<MediaRoute2Info>) {
                maybeCompletePendingTransfer()
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onRoutesUpdated(routes: MutableList<MediaRoute2Info>) {
                maybeCompletePendingTransfer()
            }
        }

        private val transferCallback = object : MediaRouter2.TransferCallback() {
            override fun onTransfer(
                oldController: MediaRouter2.RoutingController,
                newController: MediaRouter2.RoutingController
            ) {
                val snapshot = _routeSnapshot.value
                val isOurNewController = !newController.isReleased &&
                    newController.selectedRoutes.any(::matchesAnyOurRoute)

                if (isOurNewController) {
                    val wasSelfTransfer = selfTransferTargetRouteId != null
                    selfTransferTargetRouteId = null
                    if (isCurrentRemoteTarget(snapshot)) {
                        _routingControllerId.value = newController.id
                    } else if (!wasSelfTransfer && snapshot != null) {
                        // 从系统输出选择器切到桌面端路由
                        if (routingPlaybackController.switchToRemote()) {
                            _routingControllerId.value = newController.id
                        } else {
                            runCatching { newController.release() }
                        }
                    } else {
                        // 本机已先切回本地，释放滞后到达的远端控制器
                        runCatching { newController.release() }
                    }
                    return
                }

                val wasOurOldController = oldController.selectedRoutes.any(::matchesAnyOurRoute)
                if (wasOurOldController && newController.id == mediaRouter2.systemController.id) {
                    selfTransferTargetRouteId = null
                    lastRequestedRouteId = null
                    _routingControllerId.value = null
                    if (routingPlaybackController.activeTarget.value is PlaybackTarget.Remote) {
                        routingPlaybackController.switchToLocal()
                    }
                }
            }

            override fun onStop(controller: MediaRouter2.RoutingController) {
                if (!controller.selectedRoutes.any(::matchesAnyOurRoute)) return
                selfTransferTargetRouteId = null
                lastRequestedRouteId = null
                _routingControllerId.value = null
                if (routingPlaybackController.activeTarget.value is PlaybackTarget.Remote) {
                    routingPlaybackController.switchToLocal()
                }
            }

            override fun onTransferFailure(requestedRoute: MediaRoute2Info) {
                selfTransferTargetRouteId = null
                lastRequestedRouteId = null
                AppLogger.w(TAG, "系统路由切换失败: ${requestedRoute.name}")
            }
        }

        private val controllerCallback = object : MediaRouter2.ControllerCallback() {
            override fun onControllerUpdated(controller: MediaRouter2.RoutingController) {
                val snapshot = _routeSnapshot.value ?: return
                if (!controller.isReleased &&
                    controller.selectedRoutes.any { matchesSnapshotRoute(it, snapshot) } &&
                    isCurrentRemoteTarget(snapshot)
                ) {
                    _routingControllerId.value = controller.id
                }
            }
        }

        fun ensureRegistered() {
            if (registered) return
            registered = true
            runCatching {
                mediaRouter2.registerRouteCallback(executor, routeCallback, discoveryPreference)
                mediaRouter2.registerTransferCallback(executor, transferCallback)
                mediaRouter2.registerControllerCallback(executor, controllerCallback)
            }.onFailure {
                AppLogger.w(TAG, "注册 MediaRouter2 回调失败", it)
            }
        }

        fun releaseAndUnregister() {
            ensureReleasedIfActive()
            if (!registered) return
            registered = false
            runCatching {
                mediaRouter2.unregisterRouteCallback(routeCallback)
                mediaRouter2.unregisterTransferCallback(transferCallback)
                mediaRouter2.unregisterControllerCallback(controllerCallback)
            }
        }

        fun ensureTransferredTo(snapshot: LanLinkRouteSnapshot) {
            val activeController = mediaRouter2.controllers.firstOrNull { controller ->
                !controller.isReleased && controller.selectedRoutes.any { matchesSnapshotRoute(it, snapshot) }
            }
            if (activeController != null) {
                _routingControllerId.value = activeController.id
                lastRequestedRouteId = snapshot.routeId
                return
            }
            if (lastRequestedRouteId == snapshot.routeId) return

            val route = mediaRouter2.routes.firstOrNull { matchesSnapshotRoute(it, snapshot) } ?: return
            lastRequestedRouteId = snapshot.routeId
            selfTransferTargetRouteId = snapshot.routeId
            runCatching {
                mediaRouter2.transferTo(route)
            }.onFailure {
                selfTransferTargetRouteId = null
                lastRequestedRouteId = null
                AppLogger.w(TAG, "请求 MediaRouter2 切至远程设备失败", it)
            }
        }

        fun ensureReleasedIfActive() {
            selfTransferTargetRouteId = null
            lastRequestedRouteId = null
            for (controller in mediaRouter2.controllers) {
                if (!controller.isReleased && controller.selectedRoutes.any(::matchesAnyOurRoute)) {
                    runCatching { controller.release() }
                }
            }
        }

        private fun maybeCompletePendingTransfer() {
            val snapshot = _routeSnapshot.value ?: return
            if (isCurrentRemoteTarget(snapshot) && _routingControllerId.value == null) {
                ensureTransferredTo(snapshot)
            }
        }

        private fun matchesSnapshotRoute(route: MediaRoute2Info, snapshot: LanLinkRouteSnapshot): Boolean {
            if (route.isSystemRoute) return false
            val extraDeviceId = route.extras?.getString(EXTRA_PEER_DEVICE_ID)
            if (extraDeviceId != null) return extraDeviceId == snapshot.peerDeviceId
            return route.clientPackageName == appContext.packageName && route.id.endsWith(snapshot.routeId)
        }

        private fun matchesAnyOurRoute(route: MediaRoute2Info): Boolean {
            if (route.isSystemRoute) return false
            if (route.extras?.containsKey(EXTRA_PEER_DEVICE_ID) == true) return true
            return route.clientPackageName == appContext.packageName
        }
    }
}
