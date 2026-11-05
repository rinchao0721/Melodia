package com.lin0721.linmusic.core.source

// 音源平台枚举
enum class MusicPlatform(val key: String, val displayName: String) {
    NETEASE("netease", "网易云音乐"),
    KUGOU("kugou", "酷狗音乐"),
    MIGU("migu", "咪咕音乐"),
    KUWO("kuwo", "酷我音乐"),
    QQ("qq", "QQ音乐"),
    LX("lx", "自定义插件");

    // LX Music 源脚本使用的平台代号映射
    val lxKey: String
        get() = when (this) {
            NETEASE -> "wy"
            QQ -> "tx"
            KUGOU -> "kg"
            KUWO -> "kw"
            MIGU -> "mg"
            LX -> "lx"
        }

    // 搜索 Tab 的平台品牌色（ARGB）
    val brandColorArgb: Long
        get() = when (this) {
            NETEASE -> 0xFFE60026
            KUGOU -> 0xFF2979FF
            KUWO -> 0xFFF5A623
            QQ -> 0xFF31C27C
            MIGU -> 0xFFFF2E8B
            LX -> 0xFF9E9E9E
        }

    // 歌曲行来源徽标上的简称，比搜索 Tab 更短，避免挤占歌名
    val badgeName: String
        get() = if (this == QQ) "QQ" else shortName

    // 搜索 Tab 上的简称
    val shortName: String
        get() = when (this) {
            NETEASE -> "网易云"
            KUGOU -> "酷狗"
            KUWO -> "酷我"
            QQ -> "QQ音乐"
            MIGU -> "咪咕"
            LX -> "插件"
        }

    companion object {
        fun fromKey(key: String): MusicPlatform? = entries.find { it.key == key }
        fun fromLxKey(lxKey: String): MusicPlatform? = entries.find { it.lxKey == lxKey }
    }
}
