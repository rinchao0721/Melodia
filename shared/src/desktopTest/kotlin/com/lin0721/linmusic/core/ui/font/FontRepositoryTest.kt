package com.lin0721.linmusic.core.ui.font

import androidx.compose.ui.text.font.FontFamily
import com.lin0721.linmusic.core.ui.skin.Skin
import com.lin0721.linmusic.core.ui.skin.SkinMode
import com.lin0721.linmusic.core.ui.theme.MelodiaStyle
import com.lin0721.linmusic.core.ui.theme.ThemeFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FontRepositoryTest {

    @Test
    fun `resolves priority order and emits fallback event on missing font`() = runBlocking {
        val tempDir = Files.createTempDirectory("font-repo-test").toFile()
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = FontStore(tempDir) { _, _, _ -> true }
            val settingsFlow = MutableStateFlow(FontSettings())
            val skinFlow = MutableStateFlow<Skin?>(
                Skin(
                    id = "mono-skin",
                    name = "Mono",
                    author = null,
                    version = null,
                    modes = setOf(SkinMode.DARK),
                    darkColors = emptyMap(),
                    lightColors = emptyMap(),
                    background = null,
                    style = MelodiaStyle(font = ThemeFont.MONOSPACE),
                    layouts = null
                )
            )

            val repo = FontRepository(
                store = store,
                fontSettingsFlow = settingsFlow,
                activeSkinFlow = skinFlow,
                builtinFonts = BuiltinFonts(File(tempDir, "builtin")),
                scope = repoScope,
                ioDispatcher = Dispatchers.Unconfined,
                familyLoader = { entry, _ ->
                    if (entry.id == FONT_ID_BUILTIN_MONOSPACE) FontFamily.Monospace else null
                }
            )

            // 默认 global=skin -> 命中皮肤推荐 Monospace
            val initial = withTimeout(5000) { repo.resolvedFonts.first { it.global == FontFamily.Monospace } }
            assertEquals(FontFamily.Monospace, initial.lyrics)

            // 角色指定 builtin:default 覆盖全局
            settingsFlow.value = FontSettings(lyricsFontId = FONT_ID_BUILTIN_DEFAULT)
            val overridden = withTimeout(5000) { repo.resolvedFonts.first { it.lyrics == null } }
            assertEquals(FontFamily.Monospace, overridden.global)
            assertNull(overridden.lyrics)

            // 指定不存在的字体，触发回退事件并回退到 global
            val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000) { repo.fallbackEvents.first() }
            }
            settingsFlow.value = FontSettings(titleFontId = "missing-font")
            val event = eventDeferred.await()
            assertEquals(FontFamily.Monospace, repo.resolvedFonts.value.title)
            assertEquals(FontRole.TITLE, event.role)
        } finally {
            repoScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `uses figtree as default when skin is null or default without bundled font`() = runBlocking {
        val tempDir = Files.createTempDirectory("font-repo-figtree").toFile()
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = FontStore(tempDir) { _, _, _ -> true }
            val builtinFonts = BuiltinFonts(File(tempDir, "builtin"))
            val settingsFlow = MutableStateFlow(FontSettings())
            val skinFlow = MutableStateFlow<Skin?>(null)
            val figtreeFamily = FontFamily.SansSerif
            val skinFamily = FontFamily.Serif

            val repo = FontRepository(
                store = store,
                fontSettingsFlow = settingsFlow,
                activeSkinFlow = skinFlow,
                builtinFonts = builtinFonts,
                scope = repoScope,
                ioDispatcher = Dispatchers.Unconfined,
                familyLoader = { entry, _ ->
                    when {
                        entry.id == FONT_ID_BUILTIN_FIGTREE -> figtreeFamily
                        entry.source == FontSource.SKIN -> skinFamily
                        else -> null
                    }
                }
            )

            // 无皮肤时回落到内置 Figtree
            val noSkin = withTimeout(5000) { repo.resolvedFonts.first { it.global == figtreeFamily } }
            assertEquals(figtreeFamily, noSkin.title)
            assertEquals(figtreeFamily, noSkin.lyrics)
            assertEquals(figtreeFamily, noSkin.numbers)

            // ThemeFont.DEFAULT 且无随包字体时仍使用 Figtree
            skinFlow.value = Skin(
                id = "default-skin",
                name = "Default",
                author = null,
                version = null,
                modes = setOf(SkinMode.DARK),
                darkColors = emptyMap(),
                lightColors = emptyMap(),
                background = null,
                style = MelodiaStyle(font = ThemeFont.DEFAULT),
                layouts = null
            )
            assertEquals(figtreeFamily, repo.resolvedFonts.value.global)

            // 皮肤推荐 MONOSPACE 优先于 Figtree
            skinFlow.value = Skin(
                id = "mono-skin",
                name = "Mono",
                author = null,
                version = null,
                modes = setOf(SkinMode.DARK),
                darkColors = emptyMap(),
                lightColors = emptyMap(),
                background = null,
                style = MelodiaStyle(font = ThemeFont.MONOSPACE),
                layouts = null
            )
            val monoSkin = withTimeout(5000) { repo.resolvedFonts.first { it.global == FontFamily.Monospace } }
            assertEquals(FontFamily.Monospace, monoSkin.global)

            // 皮肤随包字体优先于 Figtree
            skinFlow.value = Skin(
                id = "bundled-skin",
                name = "Bundled",
                author = null,
                version = null,
                modes = setOf(SkinMode.DARK),
                darkColors = emptyMap(),
                lightColors = emptyMap(),
                background = null,
                style = MelodiaStyle(
                    font = ThemeFont.DEFAULT,
                    fontFaces = listOf(FontFaceSpec("Skin.ttf"))
                ),
                layouts = null
            )
            val bundled = withTimeout(5000) { repo.resolvedFonts.first { it.global == skinFamily } }
            assertEquals(skinFamily, bundled.global)

            // 显式选择「系统默认」仍解析为 null
            settingsFlow.value = FontSettings(globalFontId = FONT_ID_BUILTIN_DEFAULT)
            val systemDefault = withTimeout(5000) { repo.resolvedFonts.first { it.global == null } }
            assertNull(systemDefault.global)
            assertNull(systemDefault.title)
        } finally {
            repoScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `figtree failure falls back silently without fallback event`() = runBlocking {
        val tempDir = Files.createTempDirectory("font-repo-figtree-fail").toFile()
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = FontStore(tempDir) { _, _, _ -> true }
            val missingBuiltin = BuiltinFonts(File(tempDir, "builtin"), source = { null })
            val settingsFlow = MutableStateFlow(FontSettings())
            val skinFlow = MutableStateFlow<Skin?>(null)

            val repo = FontRepository(
                store = store,
                fontSettingsFlow = settingsFlow,
                activeSkinFlow = skinFlow,
                builtinFonts = missingBuiltin,
                scope = repoScope,
                ioDispatcher = Dispatchers.Unconfined,
                familyLoader = { _, _ -> null }
            )

            assertNull(repo.resolvedFonts.value.global)

            settingsFlow.value = FontSettings(
                globalFontId = FONT_ID_BUILTIN_MONOSPACE,
                titleFontId = FONT_ID_BUILTIN_FIGTREE
            )
            val explicit = withTimeout(5000) { repo.resolvedFonts.first { it.global == FontFamily.Monospace } }
            assertNull(explicit.title)

            val event = withTimeoutOrNull(100) { repo.fallbackEvents.first() }
            assertNull(event)
        } finally {
            repoScope.cancel()
            tempDir.deleteRecursively()
        }
    }
}
