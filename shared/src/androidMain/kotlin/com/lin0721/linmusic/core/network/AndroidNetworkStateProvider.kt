package com.lin0721.linmusic.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "AndroidNetworkState"

class AndroidNetworkStateProvider(private val context: Context) : NetworkStateProvider {
    override fun isWifiConnected(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Wi-Fi 状态检测异常，按移动网络处理", e)
            false
        }
    }
}
