package com.lin0721.linmusic.desktop.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SingleInstanceTest {

    private lateinit var dir: File
    private val opened = mutableListOf<SingleInstance>()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-single-instance-test").toFile()
    }

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        dir.deleteRecursively()
    }

    private fun instance(retries: Int = 20, retryDelayMs: Long = 50) =
        SingleInstance(dir, retries, retryDelayMs).also { opened += it }

    private fun awaitCondition(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    // 另起 JVM 运行探针，返回进程与它报告的角色
    private fun startProbe(holdMs: Long): Pair<Process, String> {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(
            java, "-cp", System.getProperty("java.class.path"),
            "com.lin0721.linmusic.desktop.app.SingleInstanceProbe", dir.absolutePath, holdMs.toString()
        ).redirectErrorStream(true).start()
        val role = process.inputStream.bufferedReader().readLine().orEmpty()
        return process to role
    }

    @Test
    fun secondInstanceActivatesFirstAndReportsNotPrimary() {
        val activations = AtomicInteger(0)
        assertTrue(instance().acquire({ activations.incrementAndGet() }))

        assertFalse("已有实例时后来者不应成为首个实例", instance().acquire({ error("后来者的回调不应被触发") }))
        assertTrue(awaitCondition { activations.get() == 1 })
    }

    @Test
    fun requestQuitNotifiesPrimaryInstance() {
        val quits = AtomicInteger(0)
        assertTrue(instance().acquire(onActivate = { }, onQuit = { quits.incrementAndGet() }))

        assertTrue(instance().requestQuit())
        assertTrue(awaitCondition { quits.get() == 1 })
    }

    @Test
    fun lockIsAvailableAgainAfterFirstCloses() {
        val first = instance()
        assertTrue(first.acquire { })
        first.close()

        assertTrue(instance().acquire { })
    }

    @Test
    fun stalePortFileFromCrashedInstanceDoesNotBlockStartup() {
        File(dir, "instance.port").writeText("1")

        assertTrue(instance().acquire { })
        assertNotEquals("1", File(dir, "instance.port").readText().trim())
    }

    @Test
    fun unresponsivePrimaryReturnsNotPrimaryWithoutActivation() {
        val activations = AtomicInteger(0)
        assertTrue(instance().acquire { activations.incrementAndGet() })
        File(dir, "instance.port").delete()

        assertFalse(instance(retries = 3, retryDelayMs = 10).acquire { })
        assertEquals(0, activations.get())
    }

    @Test
    fun separateProcessSeesTheLockAndActivatesThisOne() {
        val activations = AtomicInteger(0)
        assertTrue(instance().acquire { activations.incrementAndGet() })

        val (process, role) = startProbe(holdMs = 0)
        assertTrue(process.waitFor(15, TimeUnit.SECONDS))
        assertEquals("SECONDARY", role)
        assertTrue(awaitCondition { activations.get() == 1 })
    }

    @Test
    fun lockHeldByAnotherProcessBlocksUsAndFreesAfterItExits() {
        val (process, role) = startProbe(holdMs = 2_500)
        try {
            assertEquals("PRIMARY", role)
            assertFalse("另一个进程持有锁时本进程不应成为首个实例", instance(retries = 3, retryDelayMs = 10).acquire { })
        } finally {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS))
        }
        assertTrue("持有者退出后锁应释放", awaitCondition { instance().acquire { } })
    }
}
