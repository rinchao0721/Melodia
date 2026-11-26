package com.lin0721.linmusic.core.ui.font

import androidx.compose.ui.text.font.FontFamily
import com.lin0721.linmusic.core.ui.skin.Skin
import com.lin0721.linmusic.core.ui.theme.ThemeFont
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import java.io.File

private const val SYSTEM_FONT_ID_PREFIX = "system:"

/**
 * 字体解析仓库：按优先级解析全局与各角色字体，并在 IO 线程构建与缓存 FontFamily。
 * 优先级：角色指定 > 全局 > 当前皮肤推荐字体（随包字体、MONOSPACE）> 内置 Figtree > 系统默认(null)。
 */
class FontRepository(
    private val store: FontStore,
    fontSettingsFlow: Flow<FontSettings>,
    activeSkinFlow: Flow<Skin?>,
    private val builtinFonts: BuiltinFonts? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val familyLoader: (FontEntry, File?) -> FontFamily? = FontPlatformLoader::buildFontFamily
) {
    private val revision = MutableStateFlow(0)
    private val cache = LinkedHashMap<String, FontFamily?>()
    // 同一角色同一字体的回退只提示一次，避免每次设置变动都重复提示
    private val reportedFallbacks = HashSet<String>()

    private val _resolvedFonts = MutableStateFlow(ResolvedFonts())
    val resolvedFonts: StateFlow<ResolvedFonts> = _resolvedFonts.asStateFlow()

    // 启动阶段 UI 尚未订阅时产生的回退也要留到界面接收后再提示
    private val _fallbackEvents = Channel<FontFallbackEvent>(capacity = 8)
    val fallbackEvents: Flow<FontFallbackEvent> = _fallbackEvents.receiveAsFlow()

    init {
        combine(fontSettingsFlow, activeSkinFlow, revision) { settings, skin, _ ->
            resolveAll(settings, skin)
        }
            .flowOn(ioDispatcher)
            .onEach { _resolvedFonts.value = it }
            .launchIn(scope)
    }

    fun invalidate() {
        synchronized(cache) {
            cache.clear()
            reportedFallbacks.clear()
        }
        revision.update { it + 1 }
    }

    fun listInstalledFonts(): List<FontEntry> = store.list()

    fun listSystemFonts(): List<FontEntry> =
        FontPlatformLoader.listSystemFontNames().map { name ->
            FontEntry(
                id = systemFontId(name),
                name = name,
                source = FontSource.SYSTEM,
                systemFontName = name
            )
        }

    private fun resolveAll(settings: FontSettings, skin: Skin?): ResolvedFonts {
        val skinRecommended = resolveSkinRecommended(skin)
        val global = resolveGlobal(settings.globalFontId, skinRecommended)
        val title = resolveRole(FontRole.TITLE, settings.titleFontId, global)
        val lyrics = resolveRole(FontRole.LYRICS, settings.lyricsFontId, global)
        val numbers = resolveRole(FontRole.NUMBERS, settings.numbersFontId, global)
        // 只保留当前在用的字体，切换后旧字体（桌面端整份字节在内存）可被回收
        val inUse = listOfNotNull(skinRecommended, global, title, lyrics, numbers)
        synchronized(cache) { cache.entries.removeAll { it.value !in inUse } }
        return ResolvedFonts(
            global = global,
            title = title,
            lyrics = lyrics,
            numbers = numbers
        )
    }

    private fun resolveGlobal(globalFontId: String, skinRecommended: FontFamily?): FontFamily? {
        if (globalFontId == FONT_ID_SKIN || globalFontId == FONT_ID_FOLLOW) {
            return skinRecommended
        }
        val loaded = loadById(FontRole.GLOBAL, globalFontId)
        return if (loaded.ok) loaded.family else skinRecommended
    }

    private fun resolveRole(role: FontRole, roleFontId: String, global: FontFamily?): FontFamily? {
        if (roleFontId == FONT_ID_FOLLOW) {
            return global
        }
        if (roleFontId == FONT_ID_SKIN) {
            return global
        }
        val loaded = loadById(role, roleFontId)
        return if (loaded.ok) loaded.family else global
    }

    private fun resolveSkinRecommended(skin: Skin?): FontFamily? {
        if (skin != null) {
            if (skin.style.fontFaces.isNotEmpty()) {
                val skinFontId = store.skinFontId(skin.id)
                val entry = store.get(skinFontId)
                if (entry != null) {
                    loadFontFamilyCached(entry, store.directoryOf(skinFontId))?.let { return it }
                }
                // 若尚未登记到字体库（例如预设皮肤），直接从皮肤目录加载
                val directEntry = FontEntry(
                    id = skinFontId,
                    name = skin.name,
                    source = FontSource.SKIN,
                    faces = skin.style.fontFaces,
                    skinId = skin.id
                )
                loadFontFamilyCached(directEntry, skin.directory)?.let { return it }
            }
            if (skin.style.font == ThemeFont.MONOSPACE) {
                return FontFamily.Monospace
            }
        }
        return loadBuiltinFigtree()
    }

    fun figtreeFamily(): FontFamily? = loadBuiltinFigtree()

    private fun loadBuiltinFigtree(): FontFamily? {
        val asset = builtinFonts?.figtree() ?: return null
        return loadFontFamilyCached(asset.entry, asset.directory)
    }

    private fun loadById(role: FontRole, fontId: String): LoadResult {
        return when {
            fontId == FONT_ID_BUILTIN_DEFAULT -> LoadResult(null, ok = true)
            fontId == FONT_ID_BUILTIN_MONOSPACE -> LoadResult(FontFamily.Monospace, ok = true)
            // 内置 Figtree 加载或解压失败时静默回退系统默认，不发送回退提示
            fontId == FONT_ID_BUILTIN_FIGTREE -> LoadResult(loadBuiltinFigtree(), ok = true)
            fontId.startsWith(SYSTEM_FONT_ID_PREFIX) -> {
                val systemName = fontId.removePrefix(SYSTEM_FONT_ID_PREFIX)
                val entry = FontEntry(
                    id = fontId,
                    name = systemName,
                    source = FontSource.SYSTEM,
                    systemFontName = systemName
                )
                val family = loadFontFamilyCached(entry, null)
                if (family == null) {
                    reportFallback(
                        FontFallbackEvent(
                            role = role,
                            fontId = fontId,
                            fontName = systemName,
                            reason = "系统字体不可用或已被卸载"
                        )
                    )
                    LoadResult(null, ok = false)
                } else {
                    LoadResult(family, ok = true)
                }
            }
            else -> {
                val entry = store.get(fontId)
                if (entry == null) {
                    reportFallback(
                        FontFallbackEvent(
                            role = role,
                            fontId = fontId,
                            fontName = null,
                            reason = "字体不存在或已被删除"
                        )
                    )
                    return LoadResult(null, ok = false)
                }
                val family = loadFontFamilyCached(entry, store.directoryOf(fontId))
                if (family == null) {
                    reportFallback(
                        FontFallbackEvent(
                            role = role,
                            fontId = fontId,
                            fontName = entry.name,
                            reason = "字体文件丢失或加载失败"
                        )
                    )
                    LoadResult(null, ok = false)
                } else {
                    LoadResult(family, ok = true)
                }
            }
        }
    }

    private fun loadFontFamilyCached(entry: FontEntry, directory: File?): FontFamily? {
        val key = cacheKey(entry, directory)
        synchronized(cache) {
            if (cache.containsKey(key)) {
                return cache[key]
            }
        }
        val built = familyLoader(entry, directory)
        synchronized(cache) {
            if (built != null) {
                cache[key] = built
            }
        }
        return built
    }

    // 覆盖安装同 id 皮肤时 id 不变而文件已换，键里带上文件指纹
    private fun cacheKey(entry: FontEntry, directory: File?): String {
        if (directory == null) return entry.id
        val faces = entry.faces.joinToString("|") { face ->
            val file = File(directory, face.file)
            "${face.file}:${face.weight}:${face.italic}:${file.length()}:${file.lastModified()}"
        }
        return "${entry.id}@${directory.path}#$faces"
    }

    private fun reportFallback(event: FontFallbackEvent) {
        val key = "${event.role.name}:${event.fontId}"
        val first = synchronized(cache) { reportedFallbacks.add(key) }
        if (first) _fallbackEvents.trySend(event)
    }

    private data class LoadResult(val family: FontFamily?, val ok: Boolean)

    companion object {
        fun systemFontId(name: String): String = "$SYSTEM_FONT_ID_PREFIX$name"
    }
}
