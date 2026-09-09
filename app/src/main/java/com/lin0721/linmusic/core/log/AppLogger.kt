package com.lin0721.linmusic.core.log

import android.content.Context
import android.util.Log
import com.lin0721.linmusic.BuildConfig
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    enum class LogLevel(val priority: Int) { DEBUG(0), INFO(1), WARN(2), ERROR(3) }

    private const val TAG = "AppLogger"
    private const val MAX_FILE_SIZE = 1024 * 1024 // 超过 1MB 自动滚动
    private val defaultLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN

    @Volatile
    private var currentLevel: LogLevel = defaultLevel

    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logChannel = Channel<String>(capacity = 1000)

    private var logDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        logDir = File(context.cacheDir, "logs").apply {
            if (!exists()) mkdirs()
        }

        currentLevel = runCatching {
            val saved = runBlocking { SettingsPreferences(context).logLevel.first() }
            LogLevel.valueOf(saved)
        }.onFailure { w(TAG, "读取日志级别设置失败，使用默认值", it) }.getOrDefault(defaultLevel)

        // 启动后台单协程，以非阻塞管道形式执行磁盘写入
        logScope.launch {
            for (logLine in logChannel) {
                writeLogToFile(logLine)
            }
        }
    }

    // 供设置页在用户切换日志级别时立即生效，持久化由调用方负责
    fun setLevel(level: LogLevel) {
        currentLevel = level
    }

    fun d(tag: String, msg: String, tr: Throwable? = null) {
        if (!isLoggable(LogLevel.DEBUG)) return
        Log.d(tag, msg, tr)
        log("D", tag, "$msg\n${tr?.stackTraceToString() ?: ""}")
    }

    fun i(tag: String, msg: String) {
        if (!isLoggable(LogLevel.INFO)) return
        Log.i(tag, msg)
        log("I", tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (!isLoggable(LogLevel.WARN)) return
        Log.w(tag, msg, tr)
        log("W", tag, "$msg\n${tr?.stackTraceToString() ?: ""}")
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (!isLoggable(LogLevel.ERROR)) return
        Log.e(tag, msg, tr)
        log("E", tag, "$msg\n${tr?.stackTraceToString() ?: ""}")
    }

    private fun isLoggable(level: LogLevel) = level.priority >= currentLevel.priority

    private fun log(level: String, tag: String, msg: String) {
        val time = dateFormat.format(Date())
        val formattedMsg = "[$time][$level][$tag] $msg\n"
        logChannel.trySend(formattedMsg)
    }

    private fun writeLogToFile(text: String) {
        val dir = logDir ?: return
        val logFile = File(dir, "app_log_0.txt")

        // 循环重命名的滚动机制，限制最大日志大小
        if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
            val backupFile = File(dir, "app_log_1.txt")
            if (backupFile.exists()) backupFile.delete()
            logFile.renameTo(backupFile)
        }

        try {
            FileWriter(logFile, true).use { writer ->
                writer.write(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log file", e)
        }
    }

    fun getLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return existingLogFiles(dir)
    }

    // 供储存空间页展示日志占用大小
    fun getLogsSize(): Long = getLogFiles().sumOf { it.length() }

    // 清空本地已落盘的日志文件，供设置页手动清理使用
    fun clearLogs(): Boolean {
        val dir = logDir ?: return false
        return existingLogFiles(dir).fold(true) { allDeleted, file -> file.delete() && allDeleted }
    }

    private fun existingLogFiles(dir: File) =
        listOf(File(dir, "app_log_0.txt"), File(dir, "app_log_1.txt")).filter { it.exists() }
}
