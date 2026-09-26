package com.lin0721.linmusic.core.log

internal actual fun platformLog(level: AppLogger.LogLevel, tag: String, msg: String, tr: Throwable?) {
    val stream = if (level >= AppLogger.LogLevel.WARN) System.err else System.out
    stream.println("${level.name.first()}/$tag: $msg")
    tr?.printStackTrace(stream)
}
