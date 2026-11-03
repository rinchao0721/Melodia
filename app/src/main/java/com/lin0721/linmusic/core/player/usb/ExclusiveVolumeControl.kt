package com.lin0721.linmusic.core.player.usb

// 独占期间系统音量键与通知栏音量条改为调这里的音量；isActive 为假时仍由系统调音量
interface ExclusiveVolumeControl {
    val isActive: Boolean
    val maxLevel: Int
    val level: Int
    fun setLevel(level: Int)
    fun addVolumeListener(listener: () -> Unit)
    fun removeVolumeListener(listener: () -> Unit)
}
