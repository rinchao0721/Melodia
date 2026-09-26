package com.lin0721.linmusic.core.network

// 请求头与设备注册里伪装 Android 客户端所需的机型信息
internal expect object DeviceInfo {
    val brand: String
    val model: String
    val board: String
    val osRelease: String
}
