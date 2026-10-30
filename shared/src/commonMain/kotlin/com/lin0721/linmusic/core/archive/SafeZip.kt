package com.lin0721.linmusic.core.archive

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

class ZipLimitExceeded(message: String) : Exception(message)

sealed interface ZipExtractResult {
    data class Success(val warnings: List<String>) : ZipExtractResult
    data class Failure(val reason: String) : ZipExtractResult
}

// 按白名单解包，单文件与总量按实际读出的字节计，不信任 zip 头里声明的大小
object SafeZip {

    private const val BUFFER_SIZE = 8 * 1024

    // limitOf 返回该路径的单文件上限，null 表示不在白名单，跳过并记一条提示
    // 超出数量或体积时抛 ZipLimitExceeded，格式错误抛 ZipException / IllegalArgumentException
    fun extract(
        input: InputStream,
        directory: File,
        label: String,
        maxEntries: Int,
        maxTotalBytes: Long,
        limitOf: (String) -> Long?
    ): ZipExtractResult {
        val warnings = mutableListOf<String>()
        val seen = HashSet<String>()
        var totalBytes = 0L
        var entryCount = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > maxEntries) throw ZipLimitExceeded("${label}里的文件超过 $maxEntries 个")
                val name = entry.name
                if (!isSafePath(name)) return ZipExtractResult.Failure("${label}含有非法路径：$name")
                if (entry.isDirectory) continue
                val limit = limitOf(name)
                if (limit == null) {
                    warnings += "已忽略不支持的文件：$name"
                    continue
                }
                if (!seen.add(name)) return ZipExtractResult.Failure("${label}里有重复文件：$name")
                val target = File(directory, name)
                target.parentFile?.mkdirs()
                totalBytes += target.outputStream().use { out ->
                    copyLimited(zip, out, limit, maxTotalBytes - totalBytes) { exceededTotal ->
                        if (exceededTotal) "${label}解压后超过 ${formatSize(maxTotalBytes)}" else "$name 超过 ${formatSize(limit)}"
                    }
                }
            }
        }
        return ZipExtractResult.Success(warnings)
    }

    fun isSafePath(name: String): Boolean =
        !name.contains('\\') && !name.startsWith("/") && !name.contains(':') && name.split('/').none { it == ".." }

    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
        entryLimit: Long,
        totalRemaining: Long,
        message: (exceededTotal: Boolean) -> String
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            copied += read
            if (copied > entryLimit) throw ZipLimitExceeded(message(false))
            if (copied > totalRemaining) throw ZipLimitExceeded(message(true))
            output.write(buffer, 0, read)
        }
        return copied
    }

    private fun formatSize(bytes: Long): String =
        if (bytes >= 1024 * 1024) "${bytes / 1024 / 1024}MB" else "${bytes / 1024}KB"
}
