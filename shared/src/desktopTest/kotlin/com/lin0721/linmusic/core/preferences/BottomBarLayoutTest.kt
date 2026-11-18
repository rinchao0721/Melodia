package com.lin0721.linmusic.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarLayoutTest {

    @Test
    fun `空值或空白串回退默认布局`() {
        assertEquals(BottomBarLayout.DEFAULT, BottomBarLayout.decode(null))
        assertEquals(BottomBarLayout.DEFAULT, BottomBarLayout.decode("   "))
    }

    @Test
    fun `旧存档中的create项会被忽略并保证可见项下限`() {
        val decoded = BottomBarLayout.decode("home:1,create:1,search:0,library:0,local_music:0,downloads:0,cloud:0,profile:0")
        assertEquals(BottomBarItem.entries.size, decoded.size)
        assertTrue(decoded.count { it.visible } >= BottomBarLayout.MIN_VISIBLE_ITEMS)

        val onlyCreate = BottomBarLayout.decode("create:1")
        assertEquals(BottomBarItem.entries.size, onlyCreate.size)
        assertTrue(onlyCreate.count { it.visible } >= BottomBarLayout.MIN_VISIBLE_ITEMS)
    }

    @Test
    fun `编码后再解码保持顺序与显隐`() {
        val layout = listOf(
            BottomBarItemSetting(BottomBarItem.SEARCH, true),
            BottomBarItemSetting(BottomBarItem.LOCAL_MUSIC, true),
            BottomBarItemSetting(BottomBarItem.HOME, false),
            BottomBarItemSetting(BottomBarItem.LIBRARY, false),
            BottomBarItemSetting(BottomBarItem.DOWNLOADS, false),
            BottomBarItemSetting(BottomBarItem.CLOUD, false),
            BottomBarItemSetting(BottomBarItem.PROFILE, false)
        )
        val encoded = BottomBarLayout.encode(layout)
        assertEquals(layout, BottomBarLayout.decode(encoded))
    }

    @Test
    fun `可见数量少于2个时自动补足下限`() {
        val layout = listOf(
            BottomBarItemSetting(BottomBarItem.LOCAL_MUSIC, true),
            BottomBarItemSetting(BottomBarItem.HOME, false),
            BottomBarItemSetting(BottomBarItem.SEARCH, false),
            BottomBarItemSetting(BottomBarItem.LIBRARY, false),
            BottomBarItemSetting(BottomBarItem.DOWNLOADS, false),
            BottomBarItemSetting(BottomBarItem.CLOUD, false),
            BottomBarItemSetting(BottomBarItem.PROFILE, false)
        )
        val normalized = BottomBarLayout.normalize(layout)
        val visibleCount = normalized.count { it.visible }
        assertTrue(visibleCount >= BottomBarLayout.MIN_VISIBLE_ITEMS)
    }

    @Test
    fun `可见数量超过5个时自动截断至上限`() {
        val layout = BottomBarItem.entries.map { BottomBarItemSetting(it, visible = true) }
        val normalized = BottomBarLayout.normalize(layout)
        val visibleCount = normalized.count { it.visible }
        assertEquals(BottomBarLayout.MAX_VISIBLE_ITEMS, visibleCount)
    }
}
