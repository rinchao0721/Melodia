package com.lin0721.linmusic.desktop.app

import java.io.File

// 用户数据目录：Windows 取 %APPDATA%\Melodia，其他平台回退到用户主目录
object DesktopPaths {
    const val CACHE_SUBDIR = "MelodiaCache"
    const val AUDIO_CACHE_NAME = "audio_cache"
    const val METADATA_CACHE_NAME = "meta_cache"
    const val IMAGE_CACHE_NAME = "image_cache"
    const val LYRICS_CACHE_NAME = "lyrics"
    const val LOCAL_COVER_CACHE_NAME = "local_covers"

    val dataDir: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home")
        File(base, "Melodia").apply { mkdirs() }
    }

    val logDir: File by lazy { File(dataDir, "logs") }

    // 默认缓存根目录：放 %LOCALAPPDATA%，取不到时回退数据目录
    val defaultCacheRoot: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        if (base != null) File(File(base, "Melodia"), "cache") else File(dataDir, "cache")
    }

    @Volatile
    private var customCacheRoot: File? = null

    // 缓存只放进所选目录下的专用子文件夹；未指定时用默认位置
    fun cacheRootFor(selected: File?): File = selected?.let { File(it, CACHE_SUBDIR) } ?: defaultCacheRoot

    // 须在任何缓存目录被读取之前调用
    fun configureCacheRoot(selected: File?) {
        customCacheRoot = selected?.let { cacheRootFor(it) }
    }

    val localCacheDir: File get() = customCacheRoot ?: defaultCacheRoot

    val imageCacheDir: File get() = File(localCacheDir, IMAGE_CACHE_NAME)
    val audioCacheDir: File get() = File(localCacheDir, AUDIO_CACHE_NAME)
    val metadataCacheDir: File get() = File(localCacheDir, METADATA_CACHE_NAME)

    val lyricsCacheDir: File get() = File(localCacheDir, LYRICS_CACHE_NAME)

    val localCoverDir: File get() = File(localCacheDir, LOCAL_COVER_CACHE_NAME)

    val localLibraryFile: File by lazy { File(dataDir, "local_library.json") }

    // 下载中间文件不算缓存，不跟随所选缓存目录
    val downloadTempDir: File by lazy { File(defaultCacheRoot, "download_tmp") }

    val defaultDownloadDir: File by lazy { File(File(System.getProperty("user.home"), "Music"), "Melodia") }

    fun preferencesFile(name: String): File = File(File(dataDir, "datastore").apply { mkdirs() }, "$name.preferences_pb")
}
