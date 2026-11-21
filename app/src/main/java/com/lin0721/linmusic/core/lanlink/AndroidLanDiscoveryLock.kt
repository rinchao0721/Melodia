package com.lin0721.linmusic.core.lanlink

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

// 仅在 UDP 发现期间持有多播锁，结束后立即释放以避免增加耗电
class AndroidLanDiscoveryLock(context: Context) : LanDiscoveryLock {

    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    override fun <T> withLock(block: () -> T): T {
        val lock = runCatching {
            wifiManager?.createMulticastLock("melodia_lan_link")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        return try {
            block()
        } finally {
            runCatching {
                if (lock?.isHeld == true) lock.release()
            }
        }
    }
}

fun androidDeviceDisplayName(): String {
    val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
    val model = Build.MODEL?.trim().orEmpty()
    val combined = when {
        manufacturer.isEmpty() -> model
        model.startsWith(manufacturer, ignoreCase = true) -> model
        else -> "$manufacturer $model"
    }
    return combined.ifBlank { "Android 设备" }
}
