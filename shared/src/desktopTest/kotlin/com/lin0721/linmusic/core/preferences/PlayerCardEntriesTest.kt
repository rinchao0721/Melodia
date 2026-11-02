package com.lin0721.linmusic.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCardEntriesTest {

    private val keyA = PlayerCardEntries.pluginKey("com.example.a", "main")
    private val keyB = PlayerCardEntries.pluginKey("com.example.b", "main")
    private val cardA = PluginCardRef(keyA, "卡片甲")
    private val cardB = PluginCardRef(keyB, "卡片乙")
    private val builtInCount = FullPlayerCard.entries.size

    @Test
    fun `没有插件时与内置配置完全一致`() {
        val raw = "song_detail:0,lyrics:1"
        val entries = PlayerCardEntries.merge(raw, emptyList())
        assertEquals(FullPlayerCardLayout.decode(raw).map { it.card }, entries.map { it.card })
        assertEquals(FullPlayerCardLayout.decode(raw).map { it.visible }, entries.map { it.visible })
        assertEquals(FullPlayerCardLayout.DEFAULT.map { it.card }, PlayerCardEntries.merge(null, emptyList()).map { it.card })
    }

    @Test
    fun `旧版解析含插件卡片的配置时直接跳过，内置卡片不受影响`() {
        val raw = "song_detail:0,$keyA:1,lyrics:1"
        val decoded = FullPlayerCardLayout.decode(raw)
        assertEquals(FullPlayerCard.SONG_DETAIL, decoded[0].card)
        assertFalse(decoded[0].visible)
        assertEquals(FullPlayerCard.LYRICS, decoded[1].card)
        assertEquals(builtInCount, decoded.size)
    }

    @Test
    fun `插件卡片保持配置里的位置与显隐，新卡片追加到末尾`() {
        val entries = PlayerCardEntries.merge("song_detail:1,$keyA:0,lyrics:1", listOf(cardB, cardA))
        assertEquals(FullPlayerCard.SONG_DETAIL.key, entries[0].key)
        assertEquals(keyA, entries[1].key)
        assertFalse(entries[1].visible)
        assertEquals("卡片甲", entries[1].title)
        assertEquals(FullPlayerCard.LYRICS.key, entries[2].key)
        assertEquals(keyB, entries.last().key)
        assertTrue(entries.last().visible)
        assertEquals(builtInCount + 2, entries.size)
    }

    @Test
    fun `保存往返不变，暂时停用的插件卡片记录保留`() {
        val raw = "$keyA:0,lyrics:1,$keyB:1"
        val active = PlayerCardEntries.merge(raw, listOf(cardB))
        assertFalse(active.any { it.key == keyA })
        val saved = PlayerCardEntries.encode(active, raw)
        assertTrue(saved.contains("$keyA:0"))

        val restored = PlayerCardEntries.merge(saved, listOf(cardA, cardB))
        assertFalse(restored.first { it.key == keyA }.visible)
        assertEquals(active.map { it.key }, restored.filter { it.key != keyA }.map { it.key })
    }

    @Test
    fun `只改内置卡片时不丢插件记录`() {
        val encoded = FullPlayerCardLayout.encode(FullPlayerCardLayout.DEFAULT)
        val saved = PlayerCardEntries.withPluginTokens(encoded, "lyrics:1,$keyA:0")
        assertTrue(saved.startsWith(encoded))
        assertTrue(saved.endsWith("$keyA:0"))
        assertEquals(FullPlayerCardLayout.DEFAULT, FullPlayerCardLayout.decode(saved))
    }

    @Test
    fun `恢复默认时内置按默认顺序，插件卡片排在后面并显示`() {
        val entries = PlayerCardEntries.merge("$keyA:0,song_detail:0", listOf(cardA))
        val defaults = PlayerCardEntries.defaults(entries)
        assertEquals(FullPlayerCardLayout.DEFAULT.map { it.card.key } + keyA, defaults.map { it.key })
        assertTrue(defaults.all { it.visible })
    }

    @Test
    fun `按用户顺序合并显示结果，隐藏项不出现`() {
        val entries = PlayerCardEntries.merge("lyrics:1,$keyA:1,song_detail:1,$keyB:0", listOf(cardA, cardB))
        val ordered = PlayerCardEntries.ordered(entries, listOf(FullPlayerCard.LYRICS, FullPlayerCard.SONG_DETAIL), allSettled = true)
        assertEquals(listOf(FullPlayerCard.LYRICS.key, keyA, FullPlayerCard.SONG_DETAIL.key), ordered.map { it.key })
    }

    @Test
    fun `前面的内置卡片还没出结论时插件卡片先不出现`() {
        val entries = PlayerCardEntries.merge("lyrics:1,comments_preview:1,$keyA:1", listOf(cardA))
        val waiting = PlayerCardEntries.ordered(entries, listOf(FullPlayerCard.LYRICS), allSettled = false)
        assertEquals(listOf(FullPlayerCard.LYRICS.key), waiting.map { it.key })
        val settled = PlayerCardEntries.ordered(entries, listOf(FullPlayerCard.LYRICS), allSettled = true)
        assertEquals(listOf(FullPlayerCard.LYRICS.key, keyA), settled.map { it.key })
    }

    @Test
    fun `编辑后的顺序填回可编辑的位置`() {
        val full = PlayerCardEntries.merge("lyrics:1,$keyA:1,song_detail:1", listOf(cardA)).take(3)
        val editable: (PlayerCardEntry) -> Boolean = { it.card != FullPlayerCard.SONG_DETAIL }
        val ordered = full.filter(editable).reversed()
        val merged = PlayerCardEntries.mergeOrdered(full, ordered, editable)
        assertEquals(listOf(keyA, FullPlayerCard.LYRICS.key, FullPlayerCard.SONG_DETAIL.key), merged.map { it.key })
        assertEquals(full, PlayerCardEntries.mergeOrdered(full, emptyList(), editable))
        assertFalse(PlayerCardEntries.setVisible(full, keyA, false).first { it.key == keyA }.visible)
    }
}
