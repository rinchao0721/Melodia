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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
}
