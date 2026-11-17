package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.preferences.PreferencesStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TitleBarLayoutCodecTest {

    private lateinit var dir: File
    private lateinit var preferences: DesktopPreferences

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-titlebar-layout-test").toFile()
        preferences = DesktopPreferences(PreferencesStores.get(File(dir, "desktop.preferences_pb")))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `默认布局符合预期且剩余五项默认隐藏`() {
        val default = TitleBarLayout.DEFAULT
        assertEquals(listOf(TitleBarButton.HOME), default.centerStart)
        assertEquals(listOf(TitleBarButton.RECOGNITION), default.centerEnd)
        assertEquals(listOf(TitleBarButton.MESSAGES, TitleBarButton.DOWNLOADS), default.trailing)
        assertEquals(
            listOf(
                TitleBarButton.PERSONAL_FM,
                TitleBarButton.DAILY_RECOMMEND,
                TitleBarButton.SETTINGS,
                TitleBarButton.LOCAL_MUSIC,
                TitleBarButton.RECENT_PLAY
            ),
            default.hiddenButtons
        )
        assertEquals(TitleBarSlot.CENTER_START, default.slotOf(TitleBarButton.HOME))
        assertNull(default.slotOf(TitleBarButton.SETTINGS))
    }

    @Test
    fun `空值空白或非法格式回落默认布局`() {
        assertEquals(TitleBarLayout.DEFAULT, TitleBarLayoutCodec.decode(null))
        assertEquals(TitleBarLayout.DEFAULT, TitleBarLayoutCodec.decode(""))
        assertEquals(TitleBarLayout.DEFAULT, TitleBarLayoutCodec.decode("   "))
        assertEquals(TitleBarLayout.DEFAULT, TitleBarLayoutCodec.decode("invalid_payload"))
        assertEquals(TitleBarLayout.DEFAULT, TitleBarLayoutCodec.decode("unknown_slot=home"))
        assertEquals(TitleBarLayout.DEFAULT, TitleBarLayoutCodec.decode("cs=foo;ce=bar;tr=baz"))
    }

    @Test
    fun `编解码保持三槽顺序并支持全部隐藏`() {
        val custom = TitleBarLayout(
            centerStart = listOf(TitleBarButton.HOME, TitleBarButton.PERSONAL_FM),
            centerEnd = listOf(TitleBarButton.RECOGNITION, TitleBarButton.DAILY_RECOMMEND),
            trailing = listOf(TitleBarButton.LOCAL_MUSIC, TitleBarButton.RECENT_PLAY, TitleBarButton.SETTINGS)
        )
        val encoded = TitleBarLayoutCodec.encode(custom)
        assertEquals(
            "cs=home,personal_fm;ce=recognition,daily_recommend;tr=local_music,recent_play,settings",
            encoded
        )
        assertEquals(custom, TitleBarLayoutCodec.decode(encoded))

        val allHidden = TitleBarLayout(emptyList(), emptyList(), emptyList())
        val encodedHidden = TitleBarLayoutCodec.encode(allHidden)
        assertEquals("cs=;ce=;tr=", encodedHidden)
        assertEquals(allHidden, TitleBarLayoutCodec.decode(encodedHidden))
        assertEquals(TitleBarButton.entries, allHidden.hiddenButtons)
    }

    @Test
    fun `解析忽略未知按钮并对槽内与跨槽重复项去重`() {
        val decoded = TitleBarLayoutCodec.decode(
            "cs=unknown,home,home,settings;ce=home,recognition,future_btn;tr=settings,messages,messages;other=downloads"
        )
        assertEquals(
            TitleBarLayout(
                centerStart = listOf(TitleBarButton.HOME, TitleBarButton.SETTINGS),
                centerEnd = listOf(TitleBarButton.RECOGNITION),
                trailing = listOf(TitleBarButton.MESSAGES)
            ),
            decoded
        )
    }

    @Test
    fun `setVisible 显示时追加到右侧末尾且已显示项保持原位`() {
        val hiddenSettings = TitleBarLayout.DEFAULT
        assertFalse(hiddenSettings.isVisible(TitleBarButton.SETTINGS))

        val shown = TitleBarLayoutCodec.setVisible(hiddenSettings, TitleBarButton.SETTINGS, true)
        assertEquals(
            listOf(TitleBarButton.MESSAGES, TitleBarButton.DOWNLOADS, TitleBarButton.SETTINGS),
            shown.trailing
        )
        assertEquals(hiddenSettings.centerStart, shown.centerStart)
        assertEquals(hiddenSettings.centerEnd, shown.centerEnd)

        // 对已在中左槽的 HOME 再次 setVisible(true) 不改变其所在槽与位置
        val keepHome = TitleBarLayoutCodec.setVisible(shown, TitleBarButton.HOME, true)
        assertEquals(shown, keepHome)

        val hideDownloads = TitleBarLayoutCodec.setVisible(shown, TitleBarButton.DOWNLOADS, false)
        assertTrue(hideDownloads.isVisible(TitleBarButton.MESSAGES))
        assertFalse(hideDownloads.isVisible(TitleBarButton.DOWNLOADS))
        assertEquals(listOf(TitleBarButton.MESSAGES, TitleBarButton.SETTINGS), hideDownloads.trailing)
    }

    @Test
    fun `move 支持同槽重排、跨槽移动及越界索引钳制`() {
        val base = TitleBarLayout(
            centerStart = listOf(TitleBarButton.HOME, TitleBarButton.PERSONAL_FM),
            centerEnd = listOf(TitleBarButton.RECOGNITION),
            trailing = listOf(TitleBarButton.MESSAGES, TitleBarButton.DOWNLOADS, TitleBarButton.SETTINGS)
        )

        // 同槽后移
        val movedWithin = TitleBarLayoutCodec.move(base, TitleBarButton.MESSAGES, TitleBarSlot.TRAILING, 2)
        assertEquals(
            listOf(TitleBarButton.DOWNLOADS, TitleBarButton.SETTINGS, TitleBarButton.MESSAGES),
            movedWithin.trailing
        )

        // 跨槽移动到中右槽头部
        val movedAcross = TitleBarLayoutCodec.move(movedWithin, TitleBarButton.HOME, TitleBarSlot.CENTER_END, 0)
        assertEquals(listOf(TitleBarButton.PERSONAL_FM), movedAcross.centerStart)
        assertEquals(listOf(TitleBarButton.HOME, TitleBarButton.RECOGNITION), movedAcross.centerEnd)

        // 隐藏按钮直接移入指定槽指定位置，越界索引夹到末尾
        val movedHidden = TitleBarLayoutCodec.move(movedAcross, TitleBarButton.LOCAL_MUSIC, TitleBarSlot.CENTER_START, 99)
        assertEquals(listOf(TitleBarButton.PERSONAL_FM, TitleBarButton.LOCAL_MUSIC), movedHidden.centerStart)

        // 负索引夹到开头
        val movedNegative = TitleBarLayoutCodec.move(movedHidden, TitleBarButton.RECENT_PLAY, TitleBarSlot.CENTER_START, -5)
        assertEquals(
            listOf(TitleBarButton.RECENT_PLAY, TitleBarButton.PERSONAL_FM, TitleBarButton.LOCAL_MUSIC),
            movedNegative.centerStart
        )
    }

    @Test
    fun `偏好存储读写保持一致`() = runBlocking {
        assertEquals(TitleBarLayout.DEFAULT, preferences.titleBarLayout.first())

        val updated = TitleBarLayout(
            centerStart = listOf(TitleBarButton.PERSONAL_FM, TitleBarButton.HOME),
            centerEnd = emptyList(),
            trailing = listOf(TitleBarButton.SETTINGS, TitleBarButton.DOWNLOADS)
        )
        preferences.saveTitleBarLayout(updated)

        assertEquals(updated, preferences.titleBarLayout.first())
    }
}
