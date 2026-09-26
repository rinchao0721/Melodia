package com.lin0721.linmusic.core.network

import android.os.Build

internal actual object DeviceInfo {
    actual val brand: String get() = Build.BRAND
    actual val model: String get() = Build.MODEL
    actual val board: String get() = Build.BOARD
    actual val osRelease: String get() = Build.VERSION.RELEASE
}
