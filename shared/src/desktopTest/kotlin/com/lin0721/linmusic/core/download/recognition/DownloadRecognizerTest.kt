package com.lin0721.linmusic.core.download.recognition

import com.lin0721.linmusic.core.download.DownloadRecord
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadRecognizerTest {

    private lateinit var dir: File
    private lateinit var store: LocalAudioIndexStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-recognizer").toFile()
        store = LocalAudioIndexStore(dir.resolve("index.json"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun entry(uri: String, title: String) = LocalAudioEntry(uri, title, "周杰伦", 200_000L, 10L, "exhigh")

    private fun target(id: Long, title: String) = RecognitionTarget(id, title, listOf("周杰伦"), 200_000L)

    private fun recognizer(
        saved: MutableList<DownloadRecord>,
        isAlive: suspend (String) -> Boolean = { true }
    ) = DownloadRecognizer(
        indexStore = store,
        records = { saved.toList() },
        addRecords = { saved += it },
        isAlive = isAlive
    )

    @Test
    fun rescannedIndexInvalidatesCache() = runBlocking {
        val saved = mutableListOf<DownloadRecord>()
        val recognizer = recognizer(saved)
        store.replaceRoot("root", listOf(entry("a", "晴天")))
        assertEquals(listOf(1L), recognizer.recognize(listOf(target(1, "晴天"))).map { it.songId })

        store.replaceRoot("root", listOf(entry("a", "晴天"), entry("b", "七里香")))
        assertEquals(listOf(2L), recognizer.recognize(listOf(target(2, "七里香"))).map { it.songId })
    }

    @Test
    fun largeBatchIsProcessedInChunks() = runBlocking {
        val saved = mutableListOf<DownloadRecord>()
        store.replaceRoot("root", (1..120).map { entry("u$it", "歌$it") })
        val added = recognizer(saved).recognize((1..120L).map { target(it, "歌$it") })
        assertEquals(120, added.size)
        assertEquals(120, saved.size)
    }

    @Test
    fun singleQueryTimesOutWhileBatchHoldsLock() = runBlocking {
        val saved = mutableListOf<DownloadRecord>()
        val gate = CompletableDeferred<Unit>()
        val lockHeld = CompletableDeferred<Unit>()
        store.replaceRoot("root", listOf(entry("slow", "晴天"), entry("fast", "七里香")))
        val recognizer = recognizer(saved) { uri ->
            if (uri == "slow") {
                lockHeld.complete(Unit)
                gate.await()
            }
            true
        }
        val batch = launch(Dispatchers.Default) { recognizer.recognize(listOf(target(1, "晴天"))) }
        // 整单识别拿到锁并卡在文件校验
        lockHeld.await()
        assertNull(recognizer.recognizeSingle(target(2, "七里香"), timeoutMs = 100))
        gate.complete(Unit)
        batch.join()
        assertEquals(2L, recognizer.recognizeSingle(target(2, "七里香"))?.songId)
    }

    @Test
    fun cancellationIsNotSwallowed() = runBlocking {
        val saved = mutableListOf<DownloadRecord>()
        val blocked = CompletableDeferred<Unit>()
        store.replaceRoot("root", listOf(entry("a", "晴天")))
        val recognizer = recognizer(saved) {
            blocked.complete(Unit)
            CompletableDeferred<Unit>().await()
            true
        }
        var continued = false
        val job = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            recognizer.recognize(listOf(target(1, "晴天")))
            continued = true
        }
        blocked.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(continued)
        assertTrue(saved.isEmpty())
    }
}
