package com.lin0721.linmusic.core.lanlink

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "AndroidLanNetworkMonitor"

// 监听 Wi-Fi 连接变化，驱动局域网联动客户端防抖重连或立即清理断线会话
class AndroidLanNetworkMonitor(
    context: Context,
    private val lanLinkClient: LanLinkAndroidClient
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val lock = Any()
    private val activeWifiNetworks = LinkedHashSet<Network>()
    private var registered = false
    private var hasSeenInitialState = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val shouldTrigger = synchronized(lock) {
                activeWifiNetworks.add(network)
                if (!hasSeenInitialState) {
                    hasSeenInitialState = true
                    false
                } else {
                    true
                }
            }
            if (shouldTrigger) {
                AppLogger.d(TAG, "检测到 Wi-Fi 可用，安排防抖重连")
                lanLinkClient.onWifiAvailable()
            }
        }

        override fun onLost(network: Network) {
            val allLost = synchronized(lock) {
                hasSeenInitialState = true
                activeWifiNetworks.remove(network)
                activeWifiNetworks.isEmpty()
            }
            if (allLost) {
                AppLogger.d(TAG, "检测到 Wi-Fi 断开，立即清理当前会话")
                lanLinkClient.onNetworkLost()
            }
        }
    }

    fun start() {
        val cm = connectivityManager ?: return
        synchronized(lock) {
            if (registered) return
            registered = true
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, callback)
        }.onFailure {
            AppLogger.w(TAG, "注册 Wi-Fi 状态监听失败", it)
            synchronized(lock) { registered = false }
        }
    }

    fun stop() {
        val cm = connectivityManager ?: return
        val wasRegistered = synchronized(lock) {
            val r = registered
            registered = false
            activeWifiNetworks.clear()
            hasSeenInitialState = false
            r
        }
        if (wasRegistered) {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }
}
