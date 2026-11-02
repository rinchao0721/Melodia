package com.lin0721.linmusic.desktop.data.local

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TAG = "LocalLibraryStore"

// 整库一个 JSON 文件；先写临时文件再替换，写到一半崩溃不会损坏原文件
class LocalLibraryStore(private val file: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun load(): LocalLibraryData {
        if (!file.isFile) return LocalLibraryData()
        return try {
            json.decodeFromString(LocalLibraryData.serializer(), file.readText(Charsets.UTF_8))
        } catch (e: IOException) {
            AppLogger.w(TAG, "读取本地音乐库失败", e)
            LocalLibraryData()
        } catch (e: IllegalArgumentException) {
            // 内容损坏时保留现场，避免下次保存直接覆盖
            AppLogger.w(TAG, "本地音乐库文件损坏，已备份后重建", e)
            runCatching { file.copyTo(File(file.parentFile, "${file.name}.broken"), overwrite = true) }
            LocalLibraryData()
        }
    }

    fun save(data: LocalLibraryData) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(LocalLibraryData.serializer(), data), Charsets.UTF_8)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            AppLogger.w(TAG, "保存本地音乐库失败", e)
        }
    }
}
