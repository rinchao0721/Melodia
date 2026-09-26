package com.lin0721.linmusic.core.log

// 输出到平台自身的日志通道（Logcat / 控制台）
internal expect fun platformLog(level: AppLogger.LogLevel, tag: String, msg: String, tr: Throwable?)
