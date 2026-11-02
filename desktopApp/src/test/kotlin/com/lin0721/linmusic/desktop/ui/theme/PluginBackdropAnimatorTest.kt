package com.lin0721.linmusic.desktop.ui.theme

import org.jetbrains.skia.Image
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginBackdropAnimatorTest {

    @Test
    fun `帧大小按窗口比例且不超过上限`() {
        assertEquals(960 to 540, backdropFrameSize(1920f, 1080f, 960))
        assertEquals(640 to 360, backdropFrameSize(640f, 360f, 960))
        assertNull(backdropFrameSize(0f, 360f, 960))
    }

    @Test
    fun `ARGB 像素按原色写入位图`() {
        val frame = BackdropFrameBuffer(2, 1)
        frame.pixels[0] = 0xFF112233.toInt()
        frame.pixels[1] = 0xFFA0B0C0.toInt()
        val bitmap = frame.toBitmap()
        assertEquals(0xFF112233.toInt(), bitmap.getColor(0, 0))
        assertEquals(0xFFA0B0C0.toInt(), bitmap.getColor(1, 0))
        val image = Image.makeFromBitmap(bitmap)
        assertEquals(2, image.width)
        image.close()
        bitmap.close()
    }

    @Test
    fun `换帧时重用缓冲不影响已生成的位图`() {
        val frame = BackdropFrameBuffer(1, 1)
        frame.pixels[0] = 0xFF010203.toInt()
        val first = frame.toBitmap()
        frame.pixels[0] = 0xFF040506.toInt()
        val second = frame.toBitmap()
        assertEquals(0xFF010203.toInt(), first.getColor(0, 0))
        assertEquals(0xFF040506.toInt(), second.getColor(0, 0))
        first.close()
        second.close()
    }
}
