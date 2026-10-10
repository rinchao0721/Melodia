package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.desktop.platform.native.AppPaths

import com.lin0721.linmusic.core.log.AppLogger
import java.io.File
import java.io.IOException
import java.nio.file.Files

private const val TAG = "DesktopCacheMigration"

object DesktopCacheMigration {

    // 须在首个实例确认后、缓存对象创建前调用
    fun migrateAll() {
        val data = AppPaths.current.dataDir
        migrate(File(data, "audio_cache"), AppPaths.current.audioCacheDir)
        migrate(File(data, "meta_cache"), AppPaths.current.metadataCacheDir)
        migrate(File(data, "download_tmp"), AppPaths.current.downloadTempDir)
        // 歌词缓存旧位置是 数据目录/cache/lyrics，迁完清掉空的 cache 目录
        migrate(File(File(data, "cache"), "lyrics"), File(AppPaths.current.cacheDir, "lyrics"))
        File(data, "cache").takeIf { it.absoluteFile != AppPaths.current.cacheDir.absoluteFile }?.delete()
    }

    fun migrate(old: File, new: File) {
        if (old.absoluteFile == new.absoluteFile || !old.exists()) return
        // 空目录视为不存在
        if (new.isDirectory && new.list().isNullOrEmpty()) new.delete()
        if (new.exists()) {
            deleteInBackground(old)
            return
        }
        try {
            new.absoluteFile.parentFile?.mkdirs()
            Files.move(old.toPath(), new.toPath())
        } catch (e: IOException) {
            AppLogger.w(TAG, "迁移缓存目录失败，丢弃旧目录：${old.path}", e)
            deleteInBackground(old)
        }
    }

    private fun deleteInBackground(dir: File) {
        Thread({
            if (!dir.deleteRecursively()) AppLogger.w(TAG, "旧缓存目录未能完全删除：${dir.path}")
        }, "legacy-cache-cleanup").apply { isDaemon = true }.start()
    }
}
