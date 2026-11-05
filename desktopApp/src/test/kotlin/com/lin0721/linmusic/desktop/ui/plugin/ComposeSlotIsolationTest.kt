package com.lin0721.linmusic.desktop.ui.plugin

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.window.WindowExceptionHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.awt.GraphicsEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

class ComposeSlotIsolationTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `独立 ComposePanel 崩溃不影响旁边的界面`() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val failed = CountDownLatch(1)
        val recomposed = CountDownLatch(1)
        var caught: Throwable? = null
        var tick by mutableIntStateOf(0)
        var frame: JFrame? = null
        SwingUtilities.invokeAndWait {
            val healthy = ComposePanel().apply {
                setContent {
                    Text("tick $tick")
                    if (tick > 0) recomposed.countDown()
                }
            }
            val broken = ComposePanel().apply {
                exceptionHandler = WindowExceptionHandler {
                    caught = it
                    failed.countDown()
                }
                setContent {
                    LaunchedEffect(Unit) {}
                    error("插件组合失败")
                }
            }
            frame = JFrame().apply {
                layout = java.awt.GridLayout(2, 1)
                add(healthy)
                add(broken)
                setSize(320, 240)
                isVisible = true
            }
        }
        try {
            assertTrue("异常没有被插槽接住", failed.await(10, TimeUnit.SECONDS))
            assertEquals("插件组合失败", caught?.message)
            SwingUtilities.invokeAndWait { tick = 1 }
            assertTrue("旁边的界面不再重组", recomposed.await(10, TimeUnit.SECONDS))
        } finally {
            SwingUtilities.invokeAndWait { frame?.dispose() }
        }
    }
}
