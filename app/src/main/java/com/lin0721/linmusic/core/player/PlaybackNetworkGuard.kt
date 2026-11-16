package com.lin0721.linmusic.core.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "PlaybackNetworkGuard"

// 网络策略守卫：监听 Wi-Fi 切换到移动网络，并按“仅 Wi-Fi 播放”设置拦截联网起播
class PlaybackNetworkGuard(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsPreferences: SettingsPreferences,
    private val isPlaying: () -> Boolean,
    private val onPauseRequested: () -> Unit
) {

    private var lastWasWifi = false

    // 网络恢复为真正可用（INTERNET+VALIDATED）时触发一次性回调，供播放失败重试耗尽后挂起等待
    private var recoveryCallback: (() -> Unit)? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, capabilities)
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isMobile = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            if (lastWasWifi && isMobile && !isWifi) {
                scope.launch(Dispatchers.Main) {
                    val alertEnabled = settingsPreferences.mobileAlert.first()
                    val wifiOnly = settingsPreferences.wifiOnlyPlay.first()

                    if (wifiOnly) {
                        onPauseRequested()
                        Toast.makeText(context, "已为暂停播放（开启了“仅Wi-Fi联网播放”）", Toast.LENGTH_LONG).show()
                    } else if (alertEnabled && isPlaying()) {
                        Toast.makeText(context, "已切换至移动网络播放", Toast.LENGTH_LONG).show()
                    }
                }
            }
            if (isWifi) {
                lastWasWifi = true
            } else if (isMobile) {
                lastWasWifi = false
            }

            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) {
                recoveryCallback?.let { callback ->
                    recoveryCallback = null
                    scope.launch(Dispatchers.Main) { callback() }
                }
            }
        }
    }

    // 注册一次性网络恢复回调，触发后自动清空
    fun awaitNetworkRecovery(onRecovered: () -> Unit) {
        recoveryCallback = onRecovered
    }

    // 使挂起的网络恢复回调失效，供新的主动播放请求调用
    fun cancelRecoveryWait() {
        recoveryCallback = null
    }

    private var isRegistered = false

    // 注册网络回调
    fun register() {
        if (isRegistered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, networkCallback)
            isRegistered = true
        }.onFailure { AppLogger.w(TAG, "注册网络回调失败", it) }
    }

    fun unregister() {
        if (!isRegistered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        kotlin.runCatching { cm?.unregisterNetworkCallback(networkCallback) }
            .onFailure { AppLogger.w(TAG, "注销网络回调失败", it) }
        isRegistered = false
    }

    // 移动网络下开启了“仅 Wi-Fi 播放”时拦截起播并提示
    suspend fun blockPlaybackOnMobile(): Boolean {
        val wifiOnly = settingsPreferences.wifiOnlyPlay.first()
        if (wifiOnly && isMobileNetwork()) {
            Toast.makeText(context, "当前处于移动网络，已开启“仅Wi-Fi下联网播放”", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    private fun isMobileNetwork(): Boolean {
        return kotlin.runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }.onFailure { AppLogger.w(TAG, "移动网络检测异常", it) }.getOrDefault(false)
    }
}
