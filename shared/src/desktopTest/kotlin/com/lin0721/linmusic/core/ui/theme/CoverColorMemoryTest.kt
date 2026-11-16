package com.lin0721.linmusic.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.lin0721.linmusic.core.preferences.PreferencesStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CoverColorMemoryTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-cover-color-test").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun store() = PreferencesStores.get(File(dir, "cover_color.preferences_pb"))

    private suspend fun awaitColor(memory: CoverColorMemory, slot: String): Int? = withTimeout(5_000) {
        while (memory.colors.value[slot] == null) delay(10)
        memory.colors.value[slot]
    }

    @Test
    fun `写入的颜色落盘后重新创建仍能读回`() = runBlocking {
        val first = CoverColorMemory(store(), scope)
        val color = Color(0xFF3A5F8C)
        first.put("home_daily", color)
        withTimeout(5_000) {
            while (store().data.first().asMap().isEmpty()) delay(10)
        }

        val second = CoverColorMemory(store(), scope)
        assertEquals(color.toArgb(), awaitColor(second, "home_daily"))
    }

    @Test
    fun `空卡位不记录`() = runBlocking {
        val memory = CoverColorMemory(store(), scope)
        memory.put(" ", Color.Red)
        assertNull(memory.colors.value[" "])
    }
}
