package com.lin0721.linmusic.desktop.platform.windows.taskbar

internal enum class ThumbCommand { PREVIOUS, TOGGLE, NEXT }

internal enum class ThumbIcon { PREVIOUS, PLAY, PAUSE, NEXT }

internal const val BUTTON_PREVIOUS = 0
internal const val BUTTON_TOGGLE = 1
internal const val BUTTON_NEXT = 2
internal const val BUTTON_COUNT = 3

internal data class ThumbButtonSpec(val id: Int, val icon: ThumbIcon, val tooltip: String, val hidden: Boolean)

// 无曲目时三个按钮都隐藏；暂停时保留，中间按钮显示“播放”
internal fun thumbButtonSpecs(hasTrack: Boolean, playing: Boolean): List<ThumbButtonSpec> = listOf(
    ThumbButtonSpec(BUTTON_PREVIOUS, ThumbIcon.PREVIOUS, "上一首", !hasTrack),
    ThumbButtonSpec(BUTTON_TOGGLE, if (playing) ThumbIcon.PAUSE else ThumbIcon.PLAY, if (playing) "暂停" else "播放", !hasTrack),
    ThumbButtonSpec(BUTTON_NEXT, ThumbIcon.NEXT, "下一首", !hasTrack)
)

internal fun commandForButton(id: Int): ThumbCommand? = when (id) {
    BUTTON_PREVIOUS -> ThumbCommand.PREVIOUS
    BUTTON_TOGGLE -> ThumbCommand.TOGGLE
    BUTTON_NEXT -> ThumbCommand.NEXT
    else -> null
}

// 缩略图位图不得超过系统给出的最大宽高；封面是正方形，取较短边
internal fun squareSizeWithin(maxWidth: Int, maxHeight: Int): Int = minOf(maxWidth, maxHeight).coerceAtLeast(1)
