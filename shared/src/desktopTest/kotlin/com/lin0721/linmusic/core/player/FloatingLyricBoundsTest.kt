package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricBounds
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingLyricBoundsTest {

    @Test
    fun sameScreenOnlyClamps() {
        val bounds = FloatingLyricBounds(x = 100, y = 200, width = 800, screenWidth = 1080, screenHeight = 2400)

        assertEquals(bounds, bounds.fitTo(1080, 2400, minWidth = 400, viewHeight = 150))
    }

    @Test
    fun scalesProportionallyToNewScreen() {
        // 外屏 1080x2400 → 内屏 2160x1800
        val outer = FloatingLyricBounds(x = 108, y = 600, width = 864, screenWidth = 1080, screenHeight = 2400)

        val inner = outer.fitTo(2160, 1800, minWidth = 400, viewHeight = 150)

        assertEquals(FloatingLyricBounds(216, 450, 1728, 2160, 1800), inner)
    }

    @Test
    fun legacyBoundsWithoutScreenSizeAreClampedIntoScreen() {
        val legacy = FloatingLyricBounds(x = 1500, y = 3000, width = 2000)

        val fitted = legacy.fitTo(1080, 2400, minWidth = 400, viewHeight = 150)

        assertEquals(FloatingLyricBounds(0, 2250, 1080, 1080, 2400), fitted)
    }

    @Test
    fun widthNeverDropsBelowMinimum() {
        val bounds = FloatingLyricBounds(x = 0, y = 0, width = 420, screenWidth = 2160, screenHeight = 1800)

        val fitted = bounds.fitTo(1080, 2400, minWidth = 400, viewHeight = 150)

        assertEquals(400, fitted.width)
    }

    @Test
    fun keepsWindowInsideScreenAfterScaling() {
        val bounds = FloatingLyricBounds(x = 1900, y = 1700, width = 600, screenWidth = 2160, screenHeight = 1800)

        val fitted = bounds.fitTo(1080, 2400, minWidth = 400, viewHeight = 150)

        assertEquals(1080 - fitted.width, fitted.x)
        assertEquals(2250, fitted.y)
    }
}
