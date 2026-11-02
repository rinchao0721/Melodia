package com.lin0721.linmusic.desktop.app

import com.lin0721.linmusic.core.update.domain.VersionCodeResolver
import java.util.Properties

private const val RESOURCE_NAME = "app-info.properties"
private const val UNKNOWN_VERSION = "unknown"

// 构建时生成的 app-info.properties
object AppInfo {
    private val properties: Properties by lazy {
        runCatching {
            Thread.currentThread().contextClassLoader?.getResourceAsStream(RESOURCE_NAME)?.use { stream ->
                Properties().apply { load(stream) }
            }
        }.getOrNull() ?: Properties()
    }

    val version: String by lazy {
        properties.getProperty("version")?.takeIf { it.isNotBlank() } ?: UNKNOWN_VERSION
    }

    val isRelease: Boolean by lazy { properties.getProperty("release") == "true" }

    val isPrerelease: Boolean get() = version.contains('-')

    // 开发构建返回 null，不参与检查更新
    val versionCode: Int? by lazy { if (isRelease) VersionCodeResolver.resolve(version) else null }
}
