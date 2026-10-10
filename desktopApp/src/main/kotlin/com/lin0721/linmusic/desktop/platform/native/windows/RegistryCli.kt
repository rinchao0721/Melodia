package com.lin0721.linmusic.desktop.platform.native.windows

import com.lin0721.linmusic.core.log.AppLogger
import java.util.concurrent.TimeUnit

private const val TAG = "RegistryCli"
private const val REG_TIMEOUT_SECONDS = 5L

// 通过 reg.exe 读写注册表；阻塞调用，需在 IO 线程执行
internal object RegistryCli {

    class Result(val exitCode: Int, val output: String)

    fun run(vararg args: String): Result? = try {
        val process = ProcessBuilder(listOf("reg") + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(REG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            AppLogger.w(TAG, "reg ${args.first()} 超时")
            null
        } else {
            Result(process.exitValue(), output)
        }
    } catch (e: Exception) {
        AppLogger.w(TAG, "reg ${args.first()} 执行失败", e)
        null
    }
}
