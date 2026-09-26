package com.lin0721.linmusic.core.network

// 桌面端没有真实机型，沿用 xeapi 设备注册里的同一台机型，保持身份一致
internal actual object DeviceInfo {
    actual val brand: String = "HUAWEI"
    actual val model: String = "HBN-AL00"
    actual val board: String = "HBN"
    actual val osRelease: String = "12"
}
