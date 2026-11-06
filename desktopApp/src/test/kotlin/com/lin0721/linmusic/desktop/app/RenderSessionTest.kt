package com.lin0721.linmusic.desktop.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RenderSessionTest {

    private val dir: File = Files.createTempDirectory("render-session").toFile()
    private val marker = File(dir, "render_session.marker")

    @After
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun session(envOverride: String? = null, isWindows: Boolean = true) =
        RenderSession(marker, envOverride, isWindows)

    private class Recorder(private val answer: Boolean = true) {
        var asked = 0
        var persisted: RenderBackend? = null
        val confirm: () -> Boolean = { asked++; answer }
        val persist: (RenderBackend) -> Unit = { persisted = it }
    }

    @Test
    fun cleanStartDoesNotAskAndWritesEffectiveApi() {
        val recorder = Recorder()

        val chosen = session().begin(RenderBackend.AUTO, recorder.confirm, recorder.persist)

        assertEquals(RenderBackend.AUTO, chosen)
        assertEquals(0, recorder.asked)
        assertTrue(marker.readText().startsWith("DIRECT3D|"))
    }

    @Test
    fun direct3dCrashAsksAndFallsBackWhenAccepted() {
        marker.writeText("DIRECT3D|1")
        val recorder = Recorder(answer = true)

        val chosen = session().begin(RenderBackend.AUTO, recorder.confirm, recorder.persist)

        assertEquals(RenderBackend.OPENGL, chosen)
        assertEquals(1, recorder.asked)
        assertEquals(RenderBackend.OPENGL, recorder.persisted)
        assertTrue(marker.readText().startsWith("OPENGL|"))
    }

    @Test
    fun declinedFallbackKeepsRequestedBackend() {
        marker.writeText("DIRECT3D|1")
        val recorder = Recorder(answer = false)

        val chosen = session().begin(RenderBackend.DIRECT3D, recorder.confirm, recorder.persist)

        assertEquals(RenderBackend.DIRECT3D, chosen)
        assertEquals(1, recorder.asked)
        assertNull(recorder.persisted)
    }

    @Test
    fun openGlCrashIsNotTreatedAsDirect3dCrash() {
        marker.writeText("OPENGL|1")
        val recorder = Recorder()

        val chosen = session().begin(RenderBackend.AUTO, recorder.confirm, recorder.persist)

        assertEquals(RenderBackend.AUTO, chosen)
        assertEquals(0, recorder.asked)
    }

    @Test
    fun legacyAutoMarkerCountsAsDirect3dOnWindows() {
        marker.writeText("AUTO|1")
        val recorder = Recorder()

        session().begin(RenderBackend.AUTO, recorder.confirm, recorder.persist)

        assertEquals(1, recorder.asked)
    }

    @Test
    fun autoIsNotDirect3dOutsideWindows() {
        marker.writeText("AUTO|1")
        val recorder = Recorder()

        session(isWindows = false).begin(RenderBackend.AUTO, recorder.confirm, recorder.persist)

        assertEquals(0, recorder.asked)
        assertTrue(marker.readText().startsWith("AUTO|"))
    }

    @Test
    fun nonDirect3dRequestIsLeftAlone() {
        marker.writeText("DIRECT3D|1")
        val recorder = Recorder()

        val chosen = session().begin(RenderBackend.SOFTWARE, recorder.confirm, recorder.persist)

        assertEquals(RenderBackend.SOFTWARE, chosen)
        assertEquals(0, recorder.asked)
    }

    @Test
    fun envOverrideSkipsFallbackAndIsRecorded() {
        marker.writeText("DIRECT3D|1")
        val recorder = Recorder()

        val chosen = session(envOverride = "direct3d").begin(RenderBackend.AUTO, recorder.confirm, recorder.persist)

        assertEquals(RenderBackend.AUTO, chosen)
        assertEquals(0, recorder.asked)
        assertTrue(marker.readText().startsWith("DIRECT3D|"))
    }

    @Test
    fun failingPromptKeepsRequestedBackend() {
        marker.writeText("DIRECT3D|1")

        val chosen = session().begin(RenderBackend.AUTO, { error("无法显示对话框") }, {})

        assertEquals(RenderBackend.AUTO, chosen)
    }

    @Test
    fun endRemovesMarker() {
        val target = session()
        target.begin(RenderBackend.AUTO, { false }, {})

        target.end()

        assertFalse(marker.exists())
    }
}
