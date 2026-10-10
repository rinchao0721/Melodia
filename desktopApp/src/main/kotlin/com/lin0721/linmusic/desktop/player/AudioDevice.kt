package com.lin0721.linmusic.desktop.player

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// mpv 的“跟随系统默认设备”取值
const val AUTO_AUDIO_DEVICE = "auto"

enum class AudioDeviceKind { SYSTEM_DEFAULT, SPEAKER, HEADPHONES, DISPLAY }

// mpv 只给设备名与描述；描述形如“扬声器 (Realtek High Definition Audio)”，括号内作副标题
data class AudioDevice(val name: String, val description: String, val kind: AudioDeviceKind) {
    val isSystemDefault: Boolean get() = name == AUTO_AUDIO_DEVICE

    val title: String
        get() = if (isSystemDefault) "系统默认" else splitDescription().first

    val subtitle: String?
        get() = if (isSystemDefault) null else splitDescription().second

    private fun splitDescription(): Pair<String, String?> {
        val open = description.indexOf(" (")
        if (open <= 0 || !description.endsWith(")")) return description to null
        val detail = description.substring(open + 2, description.length - 1).trim()
        return description.substring(0, open).trim() to detail.ifEmpty { null }
    }
}

// 桌面端音频输出设备的读取与切换，由 mpv 控制器实现
interface AudioOutputControl {
    val audioDevices: StateFlow<List<AudioDevice>>
    val audioDevice: StateFlow<String>
    fun refreshAudioDevices()
    fun setAudioDevice(name: String)
}

private const val WASAPI_PREFIX = "wasapi/"

// 真实设备的后端前缀（按优先级）。
// Linux 上同一批设备会以 pipewire/ 与 pulse/ 两种协议各出现一遍，mpv 还会附带 alsa 的
// 大量虚拟条目（surround 声道变体、插件别名等）与 jack/sdl/openal 等后端占位项，
// 不做筛选时面板会"设备特别多"。
private val BACKEND_PREFIXES = listOf("pipewire/", "pulse/", WASAPI_PREFIX)

private val HEADPHONE_KEYWORDS = listOf("耳机", "头戴", "headphone", "headset", "airpods", "buds", "bluetooth", "蓝牙")
private val DISPLAY_KEYWORDS = listOf("hdmi", "displayport", "显示器", "monitor", "display audio", "nvidia high definition", "amd high definition")

internal fun classifyAudioDevice(name: String, description: String): AudioDeviceKind {
    if (name == AUTO_AUDIO_DEVICE) return AudioDeviceKind.SYSTEM_DEFAULT
    val text = description.lowercase()
    return when {
        HEADPHONE_KEYWORDS.any { it in text } -> AudioDeviceKind.HEADPHONES
        DISPLAY_KEYWORDS.any { it in text } -> AudioDeviceKind.DISPLAY
        else -> AudioDeviceKind.SPEAKER
    }
}

// 解析 mpv 的 audio-device-list（JSON 数组）；格式不符或缺少 name 的条目跳过，系统默认置顶
internal fun parseAudioDevices(json: String): List<AudioDevice> {
    val array = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val entry = element as? JsonObject ?: return@mapNotNull null
        val name = (entry["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val description = (entry["description"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: name
        AudioDevice(name, description, classifyAudioDevice(name, description))
    }.distinctBy { it.name }.sortedByDescending { it.isSystemDefault }
}

// 从完整设备表里挑出适合展示的条目：有真实后端设备时只保留"系统默认 + 该后端"
// （Windows 只剩 WASAPI、Linux 只剩当前声音服务），没有匹配的后端时全部保留。
// currentName 始终保留，避免列表与当前播放状态不一致
internal fun selectAudioDevices(devices: List<AudioDevice>, currentName: String = AUTO_AUDIO_DEVICE): List<AudioDevice> {
    val backend = BACKEND_PREFIXES.firstOrNull { prefix ->
        devices.any { !it.isSystemDefault && it.name.startsWith(prefix) }
    } ?: return devices
    return devices
        .filter { it.isSystemDefault || it.name.startsWith(backend) || it.name == currentName }
        .sortedByDescending { it.isSystemDefault }
}
