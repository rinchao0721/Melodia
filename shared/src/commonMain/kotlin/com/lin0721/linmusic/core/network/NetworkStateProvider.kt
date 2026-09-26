package com.lin0721.linmusic.core.network

// 当前网络是否为 Wi-Fi，决定取用哪档播放音质
fun interface NetworkStateProvider {
    fun isWifiConnected(): Boolean
}
