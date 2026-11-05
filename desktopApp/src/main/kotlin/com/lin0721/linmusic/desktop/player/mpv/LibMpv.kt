package com.lin0721.linmusic.desktop.player.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

// libmpv client API 的最小 JNA 映射，签名对照 mpv/client.h
@Suppress("FunctionName")
internal interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int

    // 返回值由 mpv 分配，读完须交给 mpv_free 释放
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)

    // args 须以 null 结尾，对应 C 端 const char**
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer?
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_error_string(error: Int): String?

    companion object {
        private const val LIBRARY_NAME = "libmpv-2"

        fun load(): LibMpv {
            // Compose 在开发运行与安装包里都会通过该属性给出平台资源目录，DLL 放在那里
            System.getProperty("compose.application.resources.dir")?.let {
                NativeLibrary.addSearchPath(LIBRARY_NAME, it)
            }
            return Native.load(
                LIBRARY_NAME,
                LibMpv::class.java,
                mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")
            )
        }
    }
}

// client.h 中用到的枚举值
internal object MpvConst {
    const val FORMAT_FLAG = 3
    const val FORMAT_DOUBLE = 5

    const val EVENT_NONE = 0
    const val EVENT_SHUTDOWN = 1
    const val EVENT_END_FILE = 7
    const val EVENT_FILE_LOADED = 8
    const val EVENT_PROPERTY_CHANGE = 22

    const val END_FILE_REASON_EOF = 0
    const val END_FILE_REASON_ERROR = 4

    const val ERROR_AO_INIT_FAILED = -14

    // mpv_event：event_id(int) error(int) reply_userdata(uint64) data(void*)
    const val EVENT_OFFSET_ID = 0L
    const val EVENT_OFFSET_USERDATA = 8L
    const val EVENT_OFFSET_DATA = 16L

    // mpv_event_end_file：reason(int) error(int) ...
    const val END_FILE_OFFSET_ERROR = 4L

    // mpv_event_property：name(char*) format(int, 按指针对齐) data(void*)
    const val PROPERTY_OFFSET_FORMAT = 8L
    const val PROPERTY_OFFSET_DATA = 16L
}
