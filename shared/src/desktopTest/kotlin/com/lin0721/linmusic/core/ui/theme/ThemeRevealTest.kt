package com.lin0721.linmusic.core.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ThemeRevealTest {

    @Test
    fun `扩散半径取到最远角的距离`() {
        val size = Size(1200f, 800f)
        assertEquals(hypot(1200f, 800f), revealRadius(Offset.Zero, size), 0.01f)
        assertEquals(hypot(1100f, 700f), revealRadius(Offset(100f, 100f), size), 0.01f)
        assertEquals(hypot(600f, 400f), revealRadius(Offset(600f, 400f), size), 0.01f)
    }

    @Test
    fun `任意起点的扩散圆都覆盖四个角`() {
        val size = Size(390f, 844f)
        val corners = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
        listOf(Offset(20f, 300f), Offset(380f, 10f), Offset(195f, 840f)).forEach { origin ->
            val radius = revealRadius(origin, size)
            corners.forEach { corner -> assertTrue((corner - origin).getDistance() <= radius + 0.01f) }
        }
    }
}
