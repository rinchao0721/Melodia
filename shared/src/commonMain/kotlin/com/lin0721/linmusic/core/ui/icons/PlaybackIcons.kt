package com.lin0721.linmusic.core.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

// 播放控制类图标，挂在 MelodiaIcons 下按类别分文件

private const val SPEAKER = "M3.5 9.8v4.4a1 1 0 0 0 1 1h2.8L12 19.5v-15L7.3 8.8H4.5a1 1 0 0 0 -1 1z"
private const val HEART = "M12 20C8 17.4 3 13.5 3 8.9A4.9 4.9 0 0 1 12 6.2 4.9 4.9 0 0 1 21 8.9c0 4.6 -5 8.5 -9 11.1z"
private const val REPEAT_TOP = "M3.5 11.5V10a3.5 3.5 0 0 1 3.5 -3.5h13.5"
private const val REPEAT_BOTTOM = "M20.5 12.5V14a3.5 3.5 0 0 1 -3.5 3.5H3.5"
private const val SCREEN = "M5 4.5h14a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z"

private val play by lazy {
    solidIcon("Play", listOf("M8 6.2v11.6a1.8 1.8 0 0 0 2.7 1.55l8.9 -5.8a1.8 1.8 0 0 0 0 -3.1L10.7 4.65A1.8 1.8 0 0 0 8 6.2z"))
}
private val pause by lazy {
    solidIcon(
        "Pause",
        listOf(
            "M6.5 4h2.5a.5 .5 0 0 1 .5 .5v15a.5 .5 0 0 1 -.5 .5H6.5a.5 .5 0 0 1 -.5 -.5v-15a.5 .5 0 0 1 .5 -.5z",
            "M15 4h2.5a.5 .5 0 0 1 .5 .5v15a.5 .5 0 0 1 -.5 .5H15a.5 .5 0 0 1 -.5 -.5v-15a.5 .5 0 0 1 .5 -.5z"
        )
    )
}
private val skipNext by lazy {
    solidIcon(
        "SkipNext",
        listOf("M4.5 5.2v13.6a1 1 0 0 0 1.6 .8l8.8 -6.8a1 1 0 0 0 0 -1.6L6.1 4.4a1 1 0 0 0 -1.6 .8z"),
        listOf("M19.5 4v16")
    )
}
private val skipPrevious by lazy {
    solidIcon(
        "SkipPrevious",
        listOf("M19.5 5.2v13.6a1 1 0 0 1 -1.6 .8l-8.8 -6.8a1 1 0 0 1 0 -1.6l8.8 -6.8a1 1 0 0 1 1.6 .8z"),
        listOf("M4.5 4v16")
    )
}
private val shuffle by lazy {
    lineIcon(
        "Shuffle",
        "M3 7h3.2c2 0 3.3 1 4.2 2.6l2.6 4.8c.9 1.6 2.2 2.6 4.2 2.6H21",
        "M3 17h3.2c1.4 0 2.5 -.5 3.3 -1.4",
        "M14.5 8.4c.8 -.9 1.9 -1.4 3.3 -1.4H21",
        "M18 4l3 3 -3 3",
        "M18 14l3 3 -3 3"
    )
}
private val repeat by lazy {
    lineIcon("Repeat", REPEAT_TOP, "M17.5 3.5l3 3 -3 3", REPEAT_BOTTOM, "M6.5 20.5l-3 -3 3 -3")
}
private val repeatOne by lazy {
    lineIcon("RepeatOne", REPEAT_TOP, "M17.5 3.5l3 3 -3 3", REPEAT_BOTTOM, "M6.5 20.5l-3 -3 3 -3", "M11 10.8l1.3 -.8v4")
}
private val queue by lazy {
    solidIcon(
        "Queue",
        listOf("M10.6 12.5v5l4.2 -2.5z"),
        listOf(
            "M6.5 4h11",
            "M5.5 9h13a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z"
        )
    )
}
private val favorite by lazy { lineIcon("Favorite", HEART) }
private val favoriteFilled by lazy { solidIcon("FavoriteFilled", listOf(HEART)) }
private val volumeUp by lazy { lineIcon("VolumeUp", SPEAKER, "M15.5 9a4.2 4.2 0 0 1 0 6", "M18.5 6a8.5 8.5 0 0 1 0 12") }
private val volumeDown by lazy { lineIcon("VolumeDown", SPEAKER, "M15.5 9a4.2 4.2 0 0 1 0 6") }
private val volumeOff by lazy { lineIcon("VolumeOff", SPEAKER, "M16 9.5l5 5", "M21 9.5l-5 5") }
private val fullscreen by lazy {
    lineIcon(
        "Fullscreen",
        "M3.5 8.5V5a1.5 1.5 0 0 1 1.5 -1.5h3.5",
        "M15.5 3.5H19a1.5 1.5 0 0 1 1.5 1.5v3.5",
        "M20.5 15.5V19a1.5 1.5 0 0 1 -1.5 1.5h-3.5",
        "M8.5 20.5H5A1.5 1.5 0 0 1 3.5 19v-3.5"
    )
}
private val fullscreenExit by lazy {
    lineIcon(
        "FullscreenExit",
        "M3.5 8.5H7A1.5 1.5 0 0 0 8.5 7V3.5",
        "M15.5 3.5V7A1.5 1.5 0 0 0 17 8.5h3.5",
        "M20.5 15.5H17a1.5 1.5 0 0 0 -1.5 1.5v3.5",
        "M8.5 20.5V17A1.5 1.5 0 0 0 7 15.5H3.5"
    )
}
private val openInFull by lazy {
    lineIcon("OpenInFull", "M14 3.5h6.5V10", "M20.5 3.5l-7 7", "M10 20.5H3.5V14", "M3.5 20.5l7 -7")
}
private val closeFullscreen by lazy {
    lineIcon("CloseFullscreen", "M13.5 4v6.5H20", "M13.5 10.5l7 -7", "M10.5 20v-6.5H4", "M10.5 13.5l-7 7")
}
private val subtitles by lazy {
    lineIcon("Subtitles", SCREEN, "M7 14.5h4", "M14 14.5h3", "M7 10.5h6", "M16 10.5h1")
}
private val timer by lazy {
    lineIcon("Timer", "M12 7a7 7 0 1 0 0 14 7 7 0 0 0 0 -14z", "M10 3h4", "M12 11v3.5", "M18.3 6.7l1.5 -1.5")
}
private val devices by lazy {
    lineIcon(
        "Devices",
        "M7.5 8H5a2 2 0 0 0 -2 2v8a2 2 0 0 0 2 2h2.5",
        "M12.5 4h6A2.5 2.5 0 0 1 21 6.5v11A2.5 2.5 0 0 1 18.5 20h-6A2.5 2.5 0 0 1 10 17.5v-11A2.5 2.5 0 0 1 12.5 4z",
        "M15.5 11.9a2.6 2.6 0 1 0 0 5.2 2.6 2.6 0 0 0 0 -5.2z",
        "M15.5 8.3v.01"
    )
}
private val miniPlayer by lazy { solidIcon("MiniPlayer", listOf("M13.5 12.5h4v3.5h-4z"), listOf(SCREEN)) }

val MelodiaIcons.Play: ImageVector get() = play
val MelodiaIcons.Pause: ImageVector get() = pause
val MelodiaIcons.SkipNext: ImageVector get() = skipNext
val MelodiaIcons.SkipPrevious: ImageVector get() = skipPrevious
val MelodiaIcons.Shuffle: ImageVector get() = shuffle
val MelodiaIcons.Repeat: ImageVector get() = repeat
val MelodiaIcons.RepeatOne: ImageVector get() = repeatOne
val MelodiaIcons.Queue: ImageVector get() = queue
val MelodiaIcons.Favorite: ImageVector get() = favorite
val MelodiaIcons.FavoriteFilled: ImageVector get() = favoriteFilled
val MelodiaIcons.VolumeUp: ImageVector get() = volumeUp
val MelodiaIcons.VolumeDown: ImageVector get() = volumeDown
val MelodiaIcons.VolumeOff: ImageVector get() = volumeOff
val MelodiaIcons.Fullscreen: ImageVector get() = fullscreen
val MelodiaIcons.FullscreenExit: ImageVector get() = fullscreenExit
val MelodiaIcons.OpenInFull: ImageVector get() = openInFull
val MelodiaIcons.CloseFullscreen: ImageVector get() = closeFullscreen
val MelodiaIcons.Subtitles: ImageVector get() = subtitles
val MelodiaIcons.Timer: ImageVector get() = timer
val MelodiaIcons.Devices: ImageVector get() = devices
val MelodiaIcons.MiniPlayer: ImageVector get() = miniPlayer
