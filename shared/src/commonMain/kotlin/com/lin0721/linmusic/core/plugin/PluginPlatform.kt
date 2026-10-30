package com.lin0721.linmusic.core.plugin

// 清单 platforms 里的取值
enum class PluginPlatform(val key: String) {
    DESKTOP("desktop"),
    ANDROID("android");

    companion object {
        fun fromKey(key: String): PluginPlatform? = entries.firstOrNull { it.key == key }
    }
}

internal expect val currentPluginPlatform: PluginPlatform
