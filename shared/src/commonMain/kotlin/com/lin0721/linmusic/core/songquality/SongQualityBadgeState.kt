package com.lin0721.linmusic.core.songquality

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.lin0721.linmusic.core.model.QualityBadge
import com.lin0721.linmusic.core.model.qualityBadgeOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// 桌面端由应用壳提供；未提供时不显示徽标
val LocalSongQualityCache = staticCompositionLocalOf<SongQualityCache?> { null }

// 由应用壳提供已下载歌曲 id -> 音质等级映射流；未提供时由调用方直接传 downloadedLevel
val LocalDownloadedQualities = staticCompositionLocalOf<StateFlow<Map<Long, String>>?> { null }

// 列表行的最高音质徽标：开关关闭时为 null；
// 有网易 songId 时优先显示已下载实际音质（未知值回退线上逻辑），否则仅显示真实最高音质（接口自带 → 补查缓存），补查完成前返回 null；
// 无网易 songId 时对本地歌曲显示推断音质，外部平台与播客保持为 null
@Composable
fun rememberSongQualityBadge(
    cache: SongQualityCache?,
    songId: Long?,
    exactLevel: String?,
    downloadedLevel: String? = null,
    localLevel: String? = null
): QualityBadge? {
    if (cache == null) return null
    val enabled by cache.enabled.collectAsState()
    val id = songId?.takeIf { it > 0 }
    val downloadedQualities = LocalDownloadedQualities.current
    val downloadedFromMap = if (downloadedLevel == null && downloadedQualities != null) {
        val flow = remember(downloadedQualities, id) {
            downloadedQualities.map { levels -> id?.let(levels::get) }.distinctUntilChanged()
        }
        flow.collectAsState(initial = id?.let(downloadedQualities.value::get)).value
    } else {
        null
    }
    val effectiveDownloaded = downloadedLevel ?: downloadedFromMap
    val cachedFlow = remember(cache, id) {
        cache.levels.map { levels -> id?.let(levels::get) }.distinctUntilChanged()
    }
    val cachedLevel by cachedFlow.collectAsState(initial = id?.let(cache.levels.value::get))
    val needsLookup = needsSongQualityLookup(id, exactLevel, effectiveDownloaded)
    LaunchedEffect(enabled, id, needsLookup) {
        if (enabled && id != null && needsLookup) cache.request(id)
    }
    return resolveSongQualityBadge(
        enabled = enabled,
        songId = id,
        exactLevel = exactLevel,
        downloadedLevel = effectiveDownloaded,
        cachedLevel = cachedLevel,
        localLevel = localLevel
    )
}

internal fun needsSongQualityLookup(
    songId: Long?,
    exactLevel: String?,
    downloadedLevel: String? = null
): Boolean {
    songId?.takeIf { it > 0 } ?: return false
    if (qualityBadgeOf(downloadedLevel) != null) return false
    return exactLevel.isNullOrBlank()
}

internal fun resolveSongQualityBadge(
    enabled: Boolean,
    songId: Long?,
    exactLevel: String?,
    downloadedLevel: String? = null,
    cachedLevel: String? = null,
    localLevel: String? = null
): QualityBadge? {
    if (!enabled) return null
    songId?.takeIf { it > 0 } ?: return qualityBadgeOf(localLevel)
    val downloadedBadge = qualityBadgeOf(downloadedLevel)
    if (downloadedBadge != null) return downloadedBadge
    // 文件档位推断与真实最高音质偏差大，补查返回前宁可不显示
    val level = exactLevel?.takeIf { it.isNotBlank() } ?: cachedLevel
    return qualityBadgeOf(level)
}
