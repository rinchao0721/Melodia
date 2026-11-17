package com.lin0721.linmusic.core.ui.icons

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodiaIconsTest {
    @Test
    fun allIconsMatchStrokeSpec() {
        val icons = listOf(
            MelodiaIcons.Home,
            MelodiaIcons.Recognition,
            MelodiaIcons.Browse,
            MelodiaIcons.Search,
            MelodiaIcons.Plugin,
            MelodiaIcons.Messages,
            MelodiaIcons.PersonAdd,
            MelodiaIcons.History,
            MelodiaIcons.Cloud,
            MelodiaIcons.Settings,
            MelodiaIcons.Logout,
            MelodiaIcons.Login,
            MelodiaIcons.Person,
            MelodiaIcons.Library,
            MelodiaIcons.Radio,
            MelodiaIcons.Heart,
            MelodiaIcons.Explore,
            MelodiaIcons.Calendar,
            MelodiaIcons.LocalMusic,
            MelodiaIcons.Plus
        )

        icons.forEach { assertIconSpec(it, 2f) }
    }

    private fun assertIconSpec(icon: ImageVector, expectedStrokeWidth: Float) {
        assertEquals("${icon.name} viewportWidth", 24f, icon.viewportWidth)
        assertEquals("${icon.name} viewportHeight", 24f, icon.viewportHeight)
        val paths = collectPaths(icon.root)
        assertTrue("${icon.name} should have paths", paths.isNotEmpty())
        paths.forEach { path ->
            assertTrue("${icon.name} pathData non-empty", path.pathData.isNotEmpty())
            assertNull("${icon.name} should not use fill", path.fill)
            assertEquals("${icon.name} strokeLineWidth", expectedStrokeWidth, path.strokeLineWidth)
            assertEquals("${icon.name} strokeLineCap", StrokeCap.Round, path.strokeLineCap)
            assertEquals("${icon.name} strokeLineJoin", StrokeJoin.Round, path.strokeLineJoin)
        }
    }

    private fun collectPaths(group: VectorGroup): List<VectorPath> = buildList {
        for (i in 0 until group.size) {
            when (val node = group[i]) {
                is VectorPath -> add(node)
                is VectorGroup -> addAll(collectPaths(node))
            }
        }
    }
}
