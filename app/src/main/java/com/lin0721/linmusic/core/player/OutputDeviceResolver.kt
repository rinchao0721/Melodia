package com.lin0721.linmusic.core.player

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

// 物理音频输出设备类型集合，供设备切换弹层与当前输出设备查询共用
internal val RELEVANT_OUTPUT_DEVICE_TYPES = setOf(
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_USB_HEADSET
)

internal fun listOutputDevices(audioManager: AudioManager): List<AudioDeviceInfo> =
    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type in RELEVANT_OUTPUT_DEVICE_TYPES }

// 有线插入优先级最高；其次是在弹层手动选过的设备；再退到蓝牙启发式；最后手机扬声器
internal fun resolveEffectiveDeviceId(devices: List<AudioDeviceInfo>, preferredId: Int?): Int {
    val wired = devices.firstOrNull {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }
    if (wired != null) return wired.id
    if (preferredId != null && devices.any { it.id == preferredId }) return preferredId
    val bluetooth = devices.firstOrNull {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }
    if (bluetooth != null) return bluetooth.id
    return devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.id ?: -1
}

// 未授予 BLUETOOTH_CONNECT 时系统只返回占位名，兜底成按类型区分的通用名称
internal fun formatOutputDeviceLabel(device: AudioDeviceInfo): String {
    val name = device.productName?.toString().orEmpty()
    if (name.isNotBlank() && name != "?") return name
    return fallbackOutputDeviceTypeName(device.type) ?: "未知设备"
}

private fun fallbackOutputDeviceTypeName(type: Int): String? = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "此手机"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙设备"
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 耳机"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB 音频"
    else -> null
}

// 解析当前媒体输出设备名称；拿不到时返回 null
internal fun resolveCurrentMediaOutputDeviceName(
    audioManager: AudioManager?,
    preferredDeviceId: Int?
): String? {
    if (audioManager == null) return null
    return runCatching {
        val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val explicit = preferredDeviceId?.takeIf { it != -1 }?.let { id ->
            allOutputs.firstOrNull { it.id == id }
        }
        val activeDevice = explicit ?: run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                audioManager.getAudioDevicesForAttributes(attrs).firstOrNull()
            } else {
                null
            }
        } ?: run {
            val relevant = allOutputs.filter { it.type in RELEVANT_OUTPUT_DEVICE_TYPES }
            val effectiveId = resolveEffectiveDeviceId(relevant, preferredDeviceId)
            relevant.firstOrNull { it.id == effectiveId }
        } ?: return@runCatching null

        activeDevice.productName?.toString()?.takeIf { it.isNotBlank() && it != "?" }
            ?: fallbackOutputDeviceTypeName(activeDevice.type)
    }.getOrNull()
}
