package com.melodia.sample.hello

import com.lin0721.linmusic.plugin.api.BackdropFrame
import com.lin0721.linmusic.plugin.api.BackdropSource
import kotlin.math.PI
import kotlin.math.sin

// 低分辨率渲染，由宿主拉伸铺满
class AuroraBackdrop : BackdropSource {
    override val maxWidth: Int = 320

    override fun render(frame: BackdropFrame) {
        val time = frame.timeMs / 1000f
        for (y in 0 until frame.height) {
            val v = y.toFloat() / frame.height
            for (x in 0 until frame.width) {
                val u = x.toFloat() / frame.width
                val wave = sin(2 * PI * (u * 1.5f + time * 0.08f)) * 0.5 + 0.5
                val band = (1 - kotlin.math.abs(v - 0.45f - 0.2f * wave.toFloat()) * 3).coerceIn(0.0f, 1.0f)
                val red = (20 + 60 * band).toInt()
                val green = (24 + 190 * band * wave).toInt()
                val blue = (48 + 140 * band).toInt()
                frame.pixels[y * frame.width + x] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
    }
}
