package com.lin0721.linmusic.desktop.platform.native.windows

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.WString

// melodia_smtc.dll 的 JNA 映射，签名对照 native-src/smtc/smtc.cpp
@Suppress("FunctionName")
internal interface SmtcLibrary : Library {

    // 在 WinRT 线程池线程上回调
    interface CommandCallback : Callback {
        fun invoke(command: Int, value: Long)
    }

    fun smtc_init(callback: CommandCallback): Int
    fun smtc_set_enabled(enabled: Int)
    fun smtc_set_metadata(title: WString, artist: WString, album: WString, coverUrl: WString)
    fun smtc_set_status(status: Int)
    fun smtc_set_timeline(positionMs: Long, durationMs: Long)
    fun smtc_set_shuffle(enabled: Int)
    fun smtc_set_repeat(mode: Int)
    fun smtc_shutdown()

    companion object {
        private const val LIBRARY_NAME = "melodia_smtc"

        fun load(): SmtcLibrary {
            System.getProperty("compose.application.resources.dir")?.let {
                NativeLibrary.addSearchPath(LIBRARY_NAME, it)
            }
            return Native.load(LIBRARY_NAME, SmtcLibrary::class.java)
        }
    }
}

// 与 smtc.cpp 中 Command 枚举保持一致
internal object SmtcCommand {
    const val PLAY = 1
    const val PAUSE = 2
    const val NEXT = 3
    const val PREVIOUS = 4
    const val SEEK = 5
    const val SHUFFLE = 6
    const val REPEAT = 7
}

internal object SmtcStatus {
    const val STOPPED = 0
    const val PLAYING = 1
    const val PAUSED = 2
}

internal object SmtcRepeat {
    const val NONE = 0
    const val TRACK = 1
    const val LIST = 2
}
