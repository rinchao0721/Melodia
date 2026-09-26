package com.lin0721.linmusic.core.log

import com.lin0721.linmusic.core.AppEnvironment
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    enum class LogLevel(val priority: Int) { DEBUG(0), INFO(1), WARN(2), ERROR(3) }

    private const val TAG = "AppLogger"
    private const val MAX_FILE_SIZE = 1024 * 1024 // 超过 1MB 自动滚动
    private val defaultLevel get() = if (AppEnvironment.isDebug) LogLevel.DEBUG else LogLevel.WARN

    // 未初始化前跟随运行环境的默认级别
    @Volatile
    private var currentLevel: LogLevel? = null

    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logChannel = Channel<String>(capacity = 1000)

    private var logDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    // 日志目录与已保存的级别由平台传入
    fun init(logDir: File, savedLevel: () -> String) {
        this.logDir = logDir.apply {
            if (!exists()) mkdirs()
        }

        currentLevel = runCatching {
            LogLevel.valueOf(savedLevel())
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
        platformLog(LogLevel.DEBUG, tag, msg, tr)
        log("D", tag, "$msg\n${tr?.stackTraceToString() ?: ""}")
    }

    fun i(tag: String, msg: String) {
        if (!isLoggable(LogLevel.INFO)) return
        platformLog(LogLevel.INFO, tag, msg, null)
        log("I", tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (!isLoggable(LogLevel.WARN)) return
        platformLog(LogLevel.WARN, tag, msg, tr)
        log("W", tag, "$msg\n${tr?.stackTraceToString() ?: ""}")
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (!isLoggable(LogLevel.ERROR)) return
        platformLog(LogLevel.ERROR, tag, msg, tr)
        log("E", tag, "$msg\n${tr?.stackTraceToString() ?: ""}")
    }

    private fun isLoggable(level: LogLevel) = level.priority >= (currentLevel ?: defaultLevel).priority

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
            platformLog(LogLevel.ERROR, TAG, "Failed to write log file", e)
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
