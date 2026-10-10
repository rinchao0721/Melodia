package com.lin0721.linmusic.desktop.platform.native.linux.mpris

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

// MPRIS 2 规范中的 org.mpris.MediaPlayer2.Player 接口（仅声明本项目用到的成员）。
// 规范：https://specifications.freedesktop.org/mpris-spec/2.2/
//
// 属主须同时实现 org.freedesktop.DBus.Properties（Get/Set/GetAll），
// 因此本接口继承 Properties。导出对象路径固定为 /org/mpris/MediaPlayer2。
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MprisPlayer : DBusInterface, Properties {

    fun Next()

    fun Previous()

    fun Pause()

    fun PlayPause()

    fun Stop()

    fun Play()

    // offsetMicros 为相对偏移（微秒），负值表示快退
    fun Seek(offsetMicros: Long)

    // positionMicros 为绝对位置（微秒）
    fun SetPosition(trackId: String, positionMicros: Long)

    // 对象路径固定
    override fun getObjectPath(): String = OBJECT_PATH

    // org.freedesktop.DBus.Properties.PropertiesChanged 信号。
    // 桌面环境依赖它刷新媒体卡片；dbus-java 要求信号类嵌套在 DBusInterface 内。
    //
    // 必须调用 8 参重载 (endianess, source, path, iface, member, sig, args...)：
    // 若走 (path, args...) 的短重载，接口名会退化为 body 参数，导致信号接口变成 Player 而非 Properties。
    // 变更所属接口（org.mpris...Player）是 body 的首个字段（签名中的 s）。
    class PropertiesChanged(
        objectPath: String,
        changedProperties: Map<String, Variant<*>>,
        invalidatedProperties: List<String> = emptyList(),
    ) : DBusSignal(
        ENDIANESS,
        null,
        objectPath,
        PROPERTIES_INTERFACE,
        "PropertiesChanged",
        "sa{sv}as",
        PLAYER_INTERFACE,
        changedProperties,
        invalidatedProperties,
    )

    companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.melodia"
        const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
        const val PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
        private const val PROPERTIES_INTERFACE = "org.freedesktop.DBus.Properties"

        // DBusSignal 8 参重载的首参（字节序），0 表示由库按平台决定
        private const val ENDIANESS: Byte = 0

        // Player 接口的属性名，供 Get/GetAll 分派使用
        const val PROP_PLAYBACK_STATUS = "PlaybackStatus"
        const val PROP_POSITION = "Position"
        const val PROP_RATE = "Rate"
        const val PROP_VOLUME = "Volume"
        const val PROP_METADATA = "Metadata"
        const val PROP_LOOP_STATUS = "LoopStatus"
        const val PROP_SHUFFLE = "Shuffle"
        const val PROP_CAN_SEEK = "CanSeek"
        const val PROP_CAN_PLAY = "CanPlay"
        // KDE(libkmpris) 的 PlayPause/Pause 会先读 CanPause；缺省会被当成 false 并直接短路，
        // 表现为媒体卡片里点暂停无反应（连 D-Bus 调用都不会发出）
        const val PROP_CAN_PAUSE = "CanPause"
        const val PROP_CAN_STOP = "CanStop"
        const val PROP_CAN_GO_NEXT = "CanGoNext"
        const val PROP_CAN_GO_PREVIOUS = "CanGoPrevious"
        const val PROP_CAN_CONTROL = "CanControl"
        const val PROP_MINIMUM_RATE = "MinimumRate"
        const val PROP_MAXIMUM_RATE = "MaximumRate"
    }
}

// MPRIS 规范中 PlaybackStatus 的取值
internal object MprisPlaybackStatus {
    const val PLAYING = "Playing"
    const val PAUSED = "Paused"
    const val STOPPED = "Stopped"
}

// MPRIS 规范中 LoopStatus 的取值
internal object MprisLoopStatus {
    const val NONE = "None"
    const val TRACK = "Track"
    const val PLAYLIST = "Playlist"
}
