package com.lin0721.linmusic.desktop.platform.native.linux.mpris

import org.freedesktop.dbus.types.Variant

// 构造 MPRIS 的 Metadata 属性（a{sv}）。
//
// 关键约定：mpris:trackid 必填且须为对象路径（DBus 类型 'o'），
// 否则部分桌面环境（如 GNOME）会忽略整条元数据。
internal object MprisMetadata {

    const val KEY_TRACK_ID = "mpris:trackid"
    const val KEY_LENGTH = "mpris:length"
    const val KEY_ART_URL = "mpris:artUrl"
    const val KEY_TITLE = "xesam:title"
    const val KEY_ARTIST = "xesam:artist"
    const val KEY_ALBUM = "xesam:album"

    // 无曲目时的占位 trackid：MPRIS 要求该字段始终存在
    const val NO_TRACK_PATH = "/org/mpris/MediaPlayer2/TrackList/NoTrack"

    // 每首曲目一个稳定的对象路径，供 trackid 使用
    fun trackPath(songId: Long): String = "/org/mpris/MediaPlayer2/Track/$songId"

    fun build(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        coverUrl: String?,
        durationMs: Long,
    ): Map<String, Variant<*>> {
        // 注意：不能用 buildMap —— 它返回 kotlin 的 MapBuilder，
        // dbus-java 序列化 a{sv} 时无法包装该实现类，必须用标准 LinkedHashMap
        val map = LinkedHashMap<String, Variant<*>>()
        map[KEY_TRACK_ID] = Variant(trackPath(songId), "o")
        map[KEY_TITLE] = Variant(title, "s")
        // xesam:artist 是字符串数组（D-Bus 签名 as），须显式声明类型
        map[KEY_ARTIST] = Variant(arrayOf(artist), "as")
        if (album.isNotBlank()) map[KEY_ALBUM] = Variant(album, "s")
        if (durationMs > 0) map[KEY_LENGTH] = Variant(durationMs * 1000, "x") // 微秒，int64
        if (!coverUrl.isNullOrBlank()) map[KEY_ART_URL] = Variant(coverUrl, "s")
        return map
    }

    fun empty(): Map<String, Variant<*>> =
        linkedMapOf(KEY_TRACK_ID to Variant(NO_TRACK_PATH, "o"))
}
