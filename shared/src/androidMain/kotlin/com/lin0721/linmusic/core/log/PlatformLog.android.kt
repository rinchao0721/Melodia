package com.lin0721.linmusic.core.log

import android.util.Log

internal actual fun platformLog(level: AppLogger.LogLevel, tag: String, msg: String, tr: Throwable?) {
    when (level) {
        AppLogger.LogLevel.DEBUG -> Log.d(tag, msg, tr)
        AppLogger.LogLevel.INFO -> Log.i(tag, msg)
        AppLogger.LogLevel.WARN -> Log.w(tag, msg, tr)
        AppLogger.LogLevel.ERROR -> Log.e(tag, msg, tr)
    }
}
