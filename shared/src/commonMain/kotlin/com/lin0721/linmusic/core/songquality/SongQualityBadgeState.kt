package com.lin0721.linmusic.core.songquality

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.lin0721.linmusic.core.model.QualityBadge
import com.lin0721.linmusic.core.model.isFinalInferredLevel
import com.lin0721.linmusic.core.model.qualityBadgeOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// 桌面端由应用壳提供；未提供时不显示徽标
val LocalSongQualityCache = staticCompositionLocalOf<SongQualityCache?> { null }

// 列表行的最高音质徽标：songId 为空（本地、外部平台、播客）或开关关闭时为 null。
// 取值顺序：接口自带的 privilege 等级 → 由档位文件推断的等级 → 补查缓存；推断为 Hi-Res 时先显示，再补查确认是否更高
@Composable
fun rememberSongQualityBadge(
    cache: SongQualityCache?,
    songId: Long?,
    exactLevel: String?,
    inferredLevel: String? = null
): QualityBadge? {
    if (cache == null) return null
    val enabled by cache.enabled.collectAsState()
    val id = songId?.takeIf { it > 0 }
    val cachedFlow = remember(cache, id) {
        cache.levels.map { levels -> id?.let(levels::get) }.distinctUntilChanged()
    }
    val cachedLevel by cachedFlow.collectAsState(initial = id?.let(cache.levels.value::get))
    val exact = exactLevel?.takeIf { it.isNotBlank() }
    val needsLookup = exact == null && !isFinalInferredLevel(inferredLevel)
    LaunchedEffect(enabled, id, needsLookup) {
        if (enabled && id != null && needsLookup) cache.request(id)
    }
    if (!enabled || id == null) return null
    val level = exact ?: if (isFinalInferredLevel(inferredLevel)) inferredLevel else cachedLevel ?: inferredLevel
    return qualityBadgeOf(level)
}
