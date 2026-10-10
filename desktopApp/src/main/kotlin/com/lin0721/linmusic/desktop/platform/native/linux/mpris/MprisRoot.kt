package com.lin0721.linmusic.desktop.platform.native.linux.mpris

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface

// MPRIS 2 规范中的根接口 org.mpris.MediaPlayer2。
//
// 桌面环境依赖它做播放器识别与归类：KDE 的 libkmpris 会读取 Identity（显示名）、
// DesktopEntry（关联 .desktop 图标）等属性；缺失该接口时媒体控件会把播放器判为无效，
// 表现为"播放正常但媒体中心里没有这个播放器"。
@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MprisRoot : DBusInterface {

    // CanQuit / CanRaise 均为 false，规范允许实现为空操作
    fun Raise()

    fun Quit()

    override fun getObjectPath(): String = MprisPlayer.OBJECT_PATH

    companion object {
        const val ROOT_INTERFACE = "org.mpris.MediaPlayer2"

        const val PROP_CAN_QUIT = "CanQuit"
        const val PROP_CAN_RAISE = "CanRaise"
        const val PROP_HAS_TRACK_LIST = "HasTrackList"
        const val PROP_IDENTITY = "Identity"
        const val PROP_DESKTOP_ENTRY = "DesktopEntry"
        const val PROP_FULLSCREEN = "Fullscreen"
        const val PROP_CAN_SET_FULLSCREEN = "CanSetFullscreen"
        const val PROP_SUPPORTED_URI_SCHEMES = "SupportedUriSchemes"
        const val PROP_SUPPORTED_MIME_TYPES = "SupportedMimeTypes"

        const val IDENTITY = "Melodia"
        // 与 jpackage 产物生成的 .desktop 文件名保持一致（melodia.desktop）
        const val DESKTOP_ENTRY = "melodia"
    }
}
