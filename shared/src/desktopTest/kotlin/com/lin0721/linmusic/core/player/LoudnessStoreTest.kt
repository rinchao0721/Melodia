package com.lin0721.linmusic.core.player

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LoudnessStoreTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-loudness-store").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `缓存写盘后可重新读回响度、峰值与来源`() = runBlocking {
        val file = File(dir, "loudness_v2.json")
        val store = LoudnessStore(file)
        store.put(1L, -11.5, 0.92, LoudnessSource.MEASURED_SAMPLED)
        store.put(2L, -17.0, null, LoudnessSource.TAG)

        val reloaded = LoudnessStore(file)
        assertEquals(LoudnessEntry(-11.5, 0.92, LoudnessSource.MEASURED_SAMPLED), reloaded.get(1L))
        assertEquals(LoudnessEntry(-17.0, null, LoudnessSource.TAG), reloaded.get(2L))
        assertNull(reloaded.get(3L))
    }

    @Test
    fun `损坏的缓存文件按空处理且可继续写入`() = runBlocking {
        val file = File(dir, "loudness_v2.json").apply { writeText("{broken") }
        val store = LoudnessStore(file)
        assertNull(store.get(1L))
        store.put(1L, -14.0, 0.8, LoudnessSource.NETEASE)
        assertEquals(LoudnessEntry(-14.0, 0.8, LoudnessSource.NETEASE), LoudnessStore(file).get(1L))
    }

    @Test
    fun `超过上限时按 LRU 淘汰最久未用的条目`() = runBlocking {
        val store = LoudnessStore(null)
        repeat(2000) { store.put(it.toLong(), -14.0, 0.5, LoudnessSource.MEASURED_FULL) }
        // 访问 0L 使其刷新为最近使用，再插入第 2001 条时应淘汰 1L
        assertEquals(-14.0, store.get(0L)!!.lufs, 1e-9)
        store.put(2000L, -12.0, 0.8, LoudnessSource.MEASURED_FULL)
        assertEquals(-14.0, store.get(0L)!!.lufs, 1e-9)
        assertNull(store.get(1L))
        assertEquals(-12.0, store.get(2000L)!!.lufs, 1e-9)
    }
}
