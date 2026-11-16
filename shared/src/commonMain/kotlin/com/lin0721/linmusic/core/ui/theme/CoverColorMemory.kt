package com.lin0721.linmusic.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CoverColorMemory"
private const val KEY_PREFIX = "cover_base_"
private val NoColors: StateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())

// 桌面端由应用壳提供
val LocalCoverColorMemory = staticCompositionLocalOf<CoverColorMemory?> { null }

// 首页封面卡按卡位记住上次取到的封面主色（未经皮肤与深浅色换算），冷启动时直接铺底，避免先闪默认色
class CoverColorMemory(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope
) {

    private val _colors = MutableStateFlow<Map<String, Int>>(emptyMap())
    val colors: StateFlow<Map<String, Int>> = _colors.asStateFlow()

    init {
        scope.launch {
            val loaded = try {
                dataStore.data.first().asMap().mapNotNull { (key, value) ->
                    val slot = key.name.removePrefix(KEY_PREFIX)
                    if (slot != key.name && value is Int) slot to value else null
                }.toMap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "读取封面卡颜色失败", e)
                emptyMap()
            }
            // 加载期间已写入的新颜色优先
            _colors.update { loaded + it }
        }
    }

    fun put(slot: String, color: Color) {
        if (slot.isBlank()) return
        val argb = color.toArgb()
        if (_colors.value[slot] == argb) return
        _colors.update { it + (slot to argb) }
        scope.launch {
            try {
                dataStore.edit { it[intPreferencesKey(KEY_PREFIX + slot)] = argb }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "保存封面卡颜色失败: $slot", e)
            }
        }
    }
}

// 卡位的封面主色：取色完成前用上次记住的颜色，无封面时返回 null；extract 返回 null 表示取色失败，不覆盖记忆。
// persist 为 false 时只取色不记忆（如悬停临时切换的封面）；memory 为空时退化为普通取色
@Composable
fun rememberCoverBase(
    memory: CoverColorMemory?,
    slot: String,
    coverUrl: String,
    persist: Boolean = true,
    extract: suspend (String) -> Color?
): Color? {
    val stored by (memory?.colors ?: NoColors).collectAsState()
    var extracted by remember(slot) { mutableStateOf<Color?>(null) }
    LaunchedEffect(slot, coverUrl, persist) {
        if (coverUrl.isBlank()) return@LaunchedEffect
        val color = extract(coverUrl) ?: return@LaunchedEffect
        extracted = color
        if (persist) memory?.put(slot, color)
    }
    if (coverUrl.isBlank()) return null
    return extracted ?: stored[slot]?.let { Color(it) }
}
