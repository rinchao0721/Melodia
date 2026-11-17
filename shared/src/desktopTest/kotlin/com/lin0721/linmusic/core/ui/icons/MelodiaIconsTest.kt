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
            MelodiaIcons.Plus,
            MelodiaIcons.Shuffle,
            MelodiaIcons.Repeat,
            MelodiaIcons.RepeatOne,
            MelodiaIcons.Favorite,
            MelodiaIcons.VolumeUp,
            MelodiaIcons.VolumeDown,
            MelodiaIcons.VolumeOff,
            MelodiaIcons.Fullscreen,
            MelodiaIcons.FullscreenExit,
            MelodiaIcons.OpenInFull,
            MelodiaIcons.CloseFullscreen,
            MelodiaIcons.Subtitles,
            MelodiaIcons.Timer,
            MelodiaIcons.Devices,
            MelodiaIcons.Close,
            MelodiaIcons.Check,
            MelodiaIcons.CheckCircle,
            MelodiaIcons.Unchecked,
            MelodiaIcons.Checklist,
            MelodiaIcons.AddCircle,
            MelodiaIcons.Remove,
            MelodiaIcons.PlaylistAdd,
            MelodiaIcons.PlaylistRemove,
            MelodiaIcons.PlaylistPlay,
            MelodiaIcons.Delete,
            MelodiaIcons.Share,
            MelodiaIcons.Download,
            MelodiaIcons.Upload,
            MelodiaIcons.Refresh,
            MelodiaIcons.Restore,
            MelodiaIcons.Link,
            MelodiaIcons.Copy,
            MelodiaIcons.Edit,
            MelodiaIcons.PushPin,
            MelodiaIcons.ToTop,
            MelodiaIcons.Sort,
            MelodiaIcons.ArrowBack,
            MelodiaIcons.ArrowUp,
            MelodiaIcons.ArrowDown,
            MelodiaIcons.ChevronDown,
            MelodiaIcons.ChevronUp,
            MelodiaIcons.ChevronRight,
            MelodiaIcons.ChevronLeft
        )

        icons.forEach { assertIconSpec(it, 2f) }
    }

    @Test
    fun solidIconsHaveFillAndStroke() {
        val icons = listOf(
            MelodiaIcons.Play,
            MelodiaIcons.Pause,
            MelodiaIcons.SkipNext,
            MelodiaIcons.SkipPrevious,
            MelodiaIcons.Queue,
            MelodiaIcons.FavoriteFilled,
            MelodiaIcons.MiniPlayer,
            MelodiaIcons.MoreHoriz,
            MelodiaIcons.MoreVert,
            MelodiaIcons.Drag
        )

        icons.forEach { icon ->
            val paths = collectPaths(icon.root)
            assertTrue("${icon.name} should have fill", paths.any { it.fill != null })
            paths.forEach { assertEquals("${icon.name} strokeLineWidth", 2f, it.strokeLineWidth) }
        }
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
