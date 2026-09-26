package com.lin0721.linmusic.core.network

import java.security.MessageDigest

// 网易云设备标识：eapi 请求头与 xeapi 请求头/设备注册必须使用同一个 deviceId，
// 原逻辑内联在 CryptoInterceptor 里，这里抽出防止多处实现漂移
object NeteaseDeviceId {

    // 纯函数，便于脱离 Android 环境单测；MD5 在 JVM/Android 上恒定可用，异常分支仅作防御
    fun compute(brand: String, model: String, board: String): String {
        return try {
            val rawId = "${brand}_${model}_${board}"
            val digest = MessageDigest.getInstance("MD5").digest(rawId.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            model
        }
    }

    fun current(): String = compute(
        brand = DeviceInfo.brand,
        model = DeviceInfo.model,
        board = DeviceInfo.board
    )
}
