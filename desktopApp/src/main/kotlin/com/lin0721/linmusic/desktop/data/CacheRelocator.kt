package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.app.DesktopPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption

private const val TAG = "CacheRelocator"
private const val BUFFER_BYTES = 256 * 1024
private const val PROGRESS_INTERVAL_MS = 100L
private const val TEMP_SUFFIX = ".tmp"
private const val RECORDING_MARK = ".rec."

class RelocationProgress(val copiedBytes: Long, val totalBytes: Long) {
    val fraction: Float get() = if (totalBytes > 0) (copiedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

sealed interface RelocationResult {
    data object Unchanged : RelocationResult
    data object Switched : RelocationResult
    class Failed(val reason: String) : RelocationResult
}

// root 是缓存实际存放的目录；pendingRestart 表示所选目录还没有生效
class CacheDirState(val root: File, val isCustom: Boolean, val pendingRestart: Boolean)

// 更换缓存目录：复制到新目录后记下选择，重启时才切换，旧目录随后清理
class CacheRelocator(
    private val preferences: DesktopPreferences,
    private val effectiveRoot: File = DesktopPaths.localCacheDir,
    private val defaultRoot: File = DesktopPaths.defaultCacheRoot,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    // 图片缓存由 Coil 管理，复制中途日志可能不一致，改在新目录重建
    private val relocatedNames = listOf(
        DesktopPaths.AUDIO_CACHE_NAME,
        DesktopPaths.METADATA_CACHE_NAME,
        DesktopPaths.LYRICS_CACHE_NAME
    )
    private val allNames = relocatedNames + DesktopPaths.IMAGE_CACHE_NAME

    val state: Flow<CacheDirState> = preferences.cacheRoot.map { stateFor(it) }

    fun stateFor(selected: String?): CacheDirState {
        val root = rootFor(selected?.let(::File))
        return CacheDirState(root, selected != null, !sameLocation(root, effectiveRoot))
    }

    // selected 为 null 表示恢复默认位置
    suspend fun switchTo(selected: File?, onProgress: (RelocationProgress) -> Unit): RelocationResult = withContext(ioDispatcher) {
        val target = rootFor(selected)
        val pendingRoot = rootFor(preferences.cacheRoot.first()?.let(::File))
        val oldCleanup = preferences.pendingCacheCleanup.first()?.let(::File)

        if (sameLocation(target, effectiveRoot)) {
            preferences.saveCacheRelocation(selected?.absolutePath, null)
            if (!sameLocation(pendingRoot, effectiveRoot)) deleteCacheDirs(pendingRoot)
            return@withContext RelocationResult.Unchanged
        }
        val created = ArrayList<File>()
        if (!target.isDirectory) created += target
        checkTarget(selected, target)?.let { problem ->
            rollback(created)
            return@withContext RelocationResult.Failed(problem)
        }
        try {
            copyCaches(target, created, onProgress)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { rollback(created) }
            throw e
        } catch (e: IOException) {
            AppLogger.w(TAG, "迁移缓存失败：${target.path}", e)
            rollback(created)
            return@withContext RelocationResult.Failed("复制失败：${e.message ?: e.javaClass.simpleName}")
        }

        preferences.saveCacheRelocation(selected?.absolutePath, effectiveRoot.absolutePath)
        // 被这次选择取代的中间目录不会再用到
        listOfNotNull(pendingRoot, oldCleanup)
            .filterNot { sameLocation(it, effectiveRoot) || sameLocation(it, target) }
            .forEach { deleteCacheDirs(it) }
        RelocationResult.Switched
    }

    // 切换真正生效后（所选目录就是当前目录）才删旧目录
    suspend fun cleanupPending() {
        try {
            withContext(ioDispatcher) {
                val old = preferences.pendingCacheCleanup.first()?.let(::File) ?: return@withContext
                val configured = rootFor(preferences.cacheRoot.first()?.let(::File))
                if (!sameLocation(configured, effectiveRoot)) return@withContext
                deleteCacheDirs(old)
                preferences.clearPendingCacheCleanup()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "清理旧缓存目录失败", e)
        }
    }

    private fun rootFor(selected: File?): File = selected?.let { File(it, DesktopPaths.CACHE_SUBDIR) } ?: defaultRoot

    private fun checkTarget(selected: File?, target: File): String? {
        if (selected != null && !selected.isDirectory) return "所选目录不存在"
        if (isInside(target, effectiveRoot) || isInside(effectiveRoot, target)) return "不能选择缓存目录内部或包含缓存目录的位置"
        return try {
            if (!target.isDirectory && !target.mkdirs()) return "无法在所选目录创建缓存文件夹"
            val probe = File(target, ".write_test_${System.nanoTime()}")
            probe.writeBytes(ByteArray(0))
            probe.delete()
            null
        } catch (e: IOException) {
            "所选目录不可写"
        } catch (e: SecurityException) {
            "所选目录不可写"
        }
    }

    private suspend fun copyCaches(target: File, created: MutableList<File>, onProgress: (RelocationProgress) -> Unit) {
        val files = relocatedNames.flatMap { name ->
            File(effectiveRoot, name).walkTopDown().filter { it.isFile && isCopyable(it) }.toList()
        }
        val total = files.sumOf { it.length() }
        val free = target.usableSpace
        if (free in 1 until total) throw IOException("目标磁盘空间不足")

        var copied = 0L
        var lastReport = 0L
        onProgress(RelocationProgress(0L, total))
        for (file in files) {
            currentCoroutineContext().ensureActive()
            val dest = File(target, file.relativeTo(effectiveRoot).path)
            ensureDirs(dest.parentFile, created)
            val existed = dest.exists()
            val temp = File(dest.parentFile, dest.name + TEMP_SUFFIX)
            try {
                val input = try {
                    file.inputStream()
                } catch (e: FileNotFoundException) {
                    // 复制期间被淘汰的文件直接跳过
                    if (!file.exists()) continue
                    throw e
                }
                input.use { source ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            currentCoroutineContext().ensureActive()
                            val now = System.nanoTime() / 1_000_000
                            if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                                lastReport = now
                                onProgress(RelocationProgress(copied.coerceAtMost(total), total))
                            }
                        }
                    }
                }
                Files.move(temp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                if (!existed) created += dest
            } catch (e: NoSuchFileException) {
                continue
            } finally {
                temp.delete()
            }
        }
        onProgress(RelocationProgress(total, total))
    }

    private fun ensureDirs(dir: File, created: MutableList<File>) {
        if (dir.isDirectory) return
        dir.parentFile?.let { ensureDirs(it, created) }
        if (dir.mkdir()) created += dir
    }

    // 只撤销这次新建的文件和空目录，目标里原有的内容不动
    private fun rollback(created: List<File>) {
        created.asReversed().forEach { it.delete() }
    }

    // 只删缓存子目录；download_tmp 等其他内容仍在时保留根目录
    fun deleteCacheDirs(root: File) {
        if (sameLocation(root, effectiveRoot)) return
        allNames.forEach { File(root, it).deleteRecursively() }
        root.delete()
    }

    private fun isCopyable(file: File): Boolean =
        !file.name.endsWith(TEMP_SUFFIX) && !file.name.contains(RECORDING_MARK)

    private fun sameLocation(a: File, b: File): Boolean = normalized(a) == normalized(b)

    private fun isInside(child: File, parent: File): Boolean = normalized(child).startsWith(normalized(parent))

    private fun normalized(file: File) = file.toPath().toAbsolutePath().normalize()
}
