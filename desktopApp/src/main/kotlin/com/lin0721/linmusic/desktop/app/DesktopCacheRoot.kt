package com.lin0721.linmusic.desktop.app

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

private const val TAG = "DesktopCacheRoot"

object DesktopCacheRoot {

    // 须先于任何缓存目录的读取；所选目录不可用时回退默认位置
    fun configure(preferences: DesktopPreferences) {
        val selected = try {
            runBlocking { preferences.cacheRoot.first() }
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取缓存目录设置失败，使用默认位置", e)
            null
        } ?: return
        val dir = File(selected)
        if (isUsable(dir)) {
            DesktopPaths.configureCacheRoot(dir)
        } else {
            AppLogger.w(TAG, "所选缓存目录不可用，已使用默认位置：${dir.path}")
        }
    }

    private fun isUsable(selected: File): Boolean = try {
        val root = DesktopPaths.cacheRootFor(selected)
        selected.isDirectory && (root.isDirectory || root.mkdir()) && root.canWrite()
    } catch (e: SecurityException) {
        false
    }
}
