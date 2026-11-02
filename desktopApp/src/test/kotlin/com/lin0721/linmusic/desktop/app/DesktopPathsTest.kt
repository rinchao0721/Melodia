package com.lin0721.linmusic.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DesktopPathsTest {

    @Test
    fun cacheRootDefaultsWhenNothingSelected() {
        assertEquals(DesktopPaths.defaultCacheRoot, DesktopPaths.cacheRootFor(null))
    }

    @Test
    fun selectedFolderGetsADedicatedSubfolder() {
        val selected = File("D:/Music")

        assertEquals(File(selected, "MelodiaCache"), DesktopPaths.cacheRootFor(selected))
    }

    @Test
    fun downloadTempStaysInDefaultLocation() {
        assertEquals(DesktopPaths.defaultCacheRoot, DesktopPaths.downloadTempDir.parentFile)
    }
}
