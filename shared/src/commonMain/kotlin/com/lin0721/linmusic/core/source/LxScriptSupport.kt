package com.lin0721.linmusic.core.source

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// 两端运行时共用的脚本头解析与参数转换
object LxScriptSupport {

    // 沙盒内等待 inited 事件的上限
    const val INIT_TIMEOUT_MS = 8000L

    // 单次解析直链的上限；多源脚本（依次试 kw/kg/tx）经常不够用，可在音源设置里开启宽松模式
    const val RESOLVE_TIMEOUT_MS = 12000L

    // 宽松模式下的单次解析上限，覆盖社区脚本里较慢的平台（实测 tx 曾到 12.5s，kg 可达 20s+）
    const val LOOSE_RESOLVE_TIMEOUT_MS = 30000L

    fun parseScriptHeader(script: String): LxPluginInfo {
        var name = "未命名插件"
        var version = "1.0.0"
        var author = "未知"
        var desc = ""

        for (line in script.lines().take(50)) {
            val trimmed = line.trim()
            when {
                trimmed.contains("@name") -> name = trimmed.substringAfter("@name").trim()
                trimmed.contains("@version") -> version = trimmed.substringAfter("@version").trim()
                trimmed.contains("@author") -> author = trimmed.substringAfter("@author").trim()
                trimmed.contains("@description") -> desc = trimmed.substringAfter("@description").trim()
            }
        }
        return LxPluginInfo(name = name, version = version, author = author, description = desc)
    }

    fun mapQualityToLx(quality: String): String = when (quality) {
        "lossless" -> "flac"
        "hires" -> "flac24bit"
        "exhigh" -> "320k"
        else -> "128k"
    }

    fun formatDuration(durationMs: Long): String {
        val totalSec = durationMs / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    // source 为目标平台代号；songId 必须是该平台自己的歌曲标识，酷狗的 hash 与 songmid 取同一个值
    fun buildMusicInfoJson(
        source: String,
        songId: String,
        songName: String,
        singer: String,
        albumName: String,
        durationMs: Long
    ): String = buildJsonObject {
        put("id", songId)
        put("songmid", songId)
        if (source == "kg") put("hash", songId)
        put("name", songName)
        put("singer", singer)
        put("albumName", albumName)
        put("interval", formatDuration(durationMs))
    }.toString()
}
