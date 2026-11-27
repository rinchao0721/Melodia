package com.lin0721.linmusic.core.player

import kotlin.math.roundToInt

/**
 * 播客倍速档位策略：0.5x ~ 3.0x，步进 0.1x（内部用十分位整数 5..30 表示以消除浮点误差）。
 */
object PodcastSpeedPolicy {
    const val MIN_TENTH = 5
    const val MAX_TENTH = 30
    const val DEFAULT_TENTH = 10
    const val STEP_COUNT = MAX_TENTH - MIN_TENTH + 1

    const val MIN_SPEED = MIN_TENTH / 10f
    const val MAX_SPEED = MAX_TENTH / 10f
    const val DEFAULT_SPEED = DEFAULT_TENTH / 10f
    const val STEP = 0.1f

    val PRESETS: List<Float> = listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f, 2.5f, 3.0f)

    fun toTenth(speed: Float): Int {
        if (!speed.isFinite()) return DEFAULT_TENTH
        return (speed * 10f).roundToInt().coerceIn(MIN_TENTH, MAX_TENTH)
    }

    fun fromTenth(tenth: Int): Float = tenth.coerceIn(MIN_TENTH, MAX_TENTH) / 10f

    fun normalize(speed: Float): Float = fromTenth(toTenth(speed))

    fun indexOf(speed: Float): Int = toTenth(speed) - MIN_TENTH

    fun speedAt(index: Int): Float = fromTenth(MIN_TENTH + index.coerceIn(0, STEP_COUNT - 1))

    fun format(speed: Float): String {
        val tenth = toTenth(speed)
        return "${tenth / 10}.${tenth % 10}x"
    }
}
