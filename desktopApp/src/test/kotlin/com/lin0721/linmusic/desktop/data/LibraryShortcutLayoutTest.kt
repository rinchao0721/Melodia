package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.preferences.PreferencesStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LibraryShortcutLayoutTest {

    private lateinit var dir: File
    private lateinit var preferences: DesktopPreferences

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-library-shortcuts-test").toFile()
        preferences = DesktopPreferences(PreferencesStores.get(File(dir, "desktop.preferences_pb")))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `空值或空白串回退默认布局`() {
        assertEquals(LibraryShortcutLayout.DEFAULT, LibraryShortcutLayout.decode(null))
        assertEquals(LibraryShortcutLayout.DEFAULT, LibraryShortcutLayout.decode("   "))
        assertEquals(
            setOf(LibraryShortcut.PERSONAL_FM, LibraryShortcut.DAILY_RECOMMEND),
            LibraryShortcutLayout.DEFAULT.filter { it.visible }.map { it.item }.toSet()
        )
    }

    @Test
    fun `编码后再解码保持顺序与显隐`() {
        val custom = listOf(
            LibraryShortcutSetting(LibraryShortcut.LOCAL_MUSIC, true),
            LibraryShortcutSetting(LibraryShortcut.CLOUD, true),
            LibraryShortcutSetting(LibraryShortcut.DAILY_RECOMMEND, false),
            LibraryShortcutSetting(LibraryShortcut.PERSONAL_FM, true),
            LibraryShortcutSetting(LibraryShortcut.SIMILAR_ROAMING, true),
            LibraryShortcutSetting(LibraryShortcut.INTELLIGENCE, false)
        )
        val encoded = LibraryShortcutLayout.encode(custom)
        assertEquals(custom, LibraryShortcutLayout.decode(encoded))
    }

    @Test
    fun `忽略未知项与重复项并按默认显隐补齐缺失项`() {
        val decoded = LibraryShortcutLayout.decode("unknown_future:1, cloud:1, cloud:0, local_music:0, bad_item:1")
        assertEquals(
            listOf(
                LibraryShortcutSetting(LibraryShortcut.CLOUD, true),
                LibraryShortcutSetting(LibraryShortcut.LOCAL_MUSIC, false),
                LibraryShortcutSetting(LibraryShortcut.PERSONAL_FM, true),
                LibraryShortcutSetting(LibraryShortcut.INTELLIGENCE, false),
                LibraryShortcutSetting(LibraryShortcut.SIMILAR_ROAMING, false),
                LibraryShortcutSetting(LibraryShortcut.DAILY_RECOMMEND, true)
            ),
            decoded
        )
    }

    @Test
    fun `全部未知项时回退默认布局`() {
        assertEquals(LibraryShortcutLayout.DEFAULT, LibraryShortcutLayout.decode("foo:1,bar:0"))
    }

    @Test
    fun `setVisible 与 toggleVisible 保持顺序并更新目标项`() {
        val initial = listOf(
            LibraryShortcutSetting(LibraryShortcut.CLOUD, false),
            LibraryShortcutSetting(LibraryShortcut.PERSONAL_FM, true)
        )
        val toggled = LibraryShortcutLayout.toggleVisible(initial, LibraryShortcut.CLOUD)
        assertEquals(LibraryShortcut.CLOUD, toggled.first().item)
        assertEquals(true, toggled.first().visible)

        val hiddenAll = LibraryShortcutLayout.setVisible(toggled, LibraryShortcut.PERSONAL_FM, false)
        assertEquals(false, hiddenAll.first { it.item == LibraryShortcut.PERSONAL_FM }.visible)
    }

    @Test
    fun `偏好存储读写保持一致`() = runBlocking {
        assertEquals(LibraryShortcutLayout.DEFAULT, preferences.libraryShortcuts.first())

        val updated = listOf(
            LibraryShortcutSetting(LibraryShortcut.SIMILAR_ROAMING, true),
            LibraryShortcutSetting(LibraryShortcut.PERSONAL_FM, true),
            LibraryShortcutSetting(LibraryShortcut.INTELLIGENCE, true),
            LibraryShortcutSetting(LibraryShortcut.DAILY_RECOMMEND, false),
            LibraryShortcutSetting(LibraryShortcut.CLOUD, false),
            LibraryShortcutSetting(LibraryShortcut.LOCAL_MUSIC, true)
        )
        preferences.saveLibraryShortcuts(updated)

        assertEquals(updated, preferences.libraryShortcuts.first())
    }
}
