package com.lin0721.linmusic.desktop.player.cache

import com.lin0721.linmusic.core.log.AppLogger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "AudioCache"
private const val EXTENSION = "mka"
private const val TEMP_MARK = ".rec."
private const val COMMIT_ATTEMPTS = 16
private const val COMMIT_RETRY_MS = 250L

// mpv 把整首播完的流录成 mka，文件名 歌曲id_音质.mka
// 录制中为 歌曲id_音质.rec.序号.mka，播完且 mpv 释放文件后才改名生效
class AudioCache(private val dir: File) {

    private val tempCounter = AtomicLong(System.currentTimeMillis())

    init {
        dir.mkdirs()
        deleteTemps()
    }

    // 命中时刷新修改时间，缓存总量淘汰按它排序
    fun completeFile(songId: Long, level: String): File? =
        File(dir, "${songId}_$level.$EXTENSION").takeIf { it.isFile && it.length() > 0 }?.also { it.setLastModified(System.currentTimeMillis()) }

    // 任意音质的完整缓存，联网取地址失败时兜底
    fun anyCompleteFile(songId: Long): File? =
        completeFiles().filter { it.name.startsWith("${songId}_") }.maxByOrNull { it.lastModified() }

    // 每次录制独立文件名，避免和上次未释放的残留冲突
    fun tempFile(songId: Long, level: String): File =
        File(dir, "${songId}_$level$TEMP_MARK${tempCounter.incrementAndGet()}.$EXTENSION")

    fun playableIds(ids: Collection<Long>): Set<Long> {
        val idSet = ids.toHashSet()
        return completeFiles().mapNotNullTo(HashSet()) { file ->
            file.name.substringBefore('_').toLongOrNull()?.takeIf { it in idSet }
        }
    }

    fun totalSize(): Long = completeFiles().sumOf { it.length() }

    // 临时文件在 mpv 关闭前改名会失败，重试到成功即说明文件已完整落盘
    suspend fun commit(temp: File): Boolean = withContext(Dispatchers.IO) {
        val target = File(dir, "${temp.name.substringBefore(TEMP_MARK)}.$EXTENSION")
        repeat(COMMIT_ATTEMPTS) {
            if (!temp.isFile || temp.length() == 0L) return@withContext false
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return@withContext true
            } catch (e: IOException) {
                delay(COMMIT_RETRY_MS)
            }
        }
        AppLogger.w(TAG, "缓存文件一直被占用，放弃 ${temp.name}")
        temp.delete()
        false
    }

    fun clear() {
        dir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }

    fun completeFiles(): List<File> =
        dir.listFiles { file -> file.isFile && file.name.endsWith(".$EXTENSION") && !file.name.contains(TEMP_MARK) }.orEmpty().toList()

    private fun deleteTemps() {
        dir.listFiles { file -> file.isFile && file.name.contains(TEMP_MARK) }?.forEach { it.delete() }
    }
}
