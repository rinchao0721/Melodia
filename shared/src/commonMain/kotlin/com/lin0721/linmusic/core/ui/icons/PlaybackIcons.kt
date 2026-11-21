package com.lin0721.linmusic.core.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

// 播放控制类图标，挂在 MelodiaIcons 下按类别分文件

private const val SPEAKER = "M4 10v4a1 1 0 0 0 1 1h2.5l4.5 4V5L7.5 9H5a1 1 0 0 0 -1 1z"
private const val HEART = "M12 20C8 17.4 3 13.5 3 8.9A4.9 4.9 0 0 1 12 6.2 4.9 4.9 0 0 1 21 8.9c0 4.6 -5 8.5 -9 11.1z"
private const val REPEAT_TOP = "M3.5 11.5V10a3.5 3.5 0 0 1 3.5 -3.5h13.5"
private const val REPEAT_BOTTOM = "M20.5 12.5V14a3.5 3.5 0 0 1 -3.5 3.5H3.5"
private const val SCREEN = "M6 5h12a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z"

// 播放：纯填充小圆角三角，参照 SP 的轻重
private val play by lazy {
    fillIcon("Play", "M7.4 4.2l12.1 7a.9 .9 0 0 1 0 1.6l-12.1 7A.9 .9 0 0 1 6 19V5a.9 .9 0 0 1 1.4 -.8z")
}
// 暂停：纯填充双竖条
private val pause by lazy {
    fillIcon(
        "Pause",
        "M6 3.5h2.6a.8 .8 0 0 1 .8 .8v15.4a.8 .8 0 0 1 -.8 .8H6a.8 .8 0 0 1 -.8 -.8V4.3A.8 .8 0 0 1 6 3.5z",
        "M15.4 3.5h2.6a.8 .8 0 0 1 .8 .8v15.4a.8 .8 0 0 1 -.8 .8h-2.6a.8 .8 0 0 1 -.8 -.8V4.3a.8 .8 0 0 1 .8 -.8z"
    )
}
// 下一首：纯填充三角加竖条
private val skipNext by lazy {
    fillIcon(
        "SkipNext",
        "M5.4 4.3l10.3 6.9a1 1 0 0 1 0 1.6L5.4 19.7A.9 .9 0 0 1 4 19V5a.9 .9 0 0 1 1.4 -.7z",
        "M17.6 3.5h1.6a.8 .8 0 0 1 .8 .8v15.4a.8 .8 0 0 1 -.8 .8h-1.6a.8 .8 0 0 1 -.8 -.8V4.3a.8 .8 0 0 1 .8 -.8z"
    )
}
// 上一首：下一首的镜像
private val skipPrevious by lazy {
    fillIcon(
        "SkipPrevious",
        "M18.6 4.3l-10.3 6.9a1 1 0 0 0 0 1.6l10.3 6.9A.9 .9 0 0 0 20 19V5a.9 .9 0 0 0 -1.4 -.7z",
        "M6.4 3.5H4.8a.8 .8 0 0 0 -.8 .8v15.4a.8 .8 0 0 0 .8 .8h1.6a.8 .8 0 0 0 .8 -.8V4.3a.8 .8 0 0 0 -.8 -.8z"
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
    lineIcon("Queue", "M7 4.5h10a2.5 2.5 0 0 1 0 5H7a2.5 2.5 0 0 1 0 -5z", "M4 14.5h16", "M4 19.5h16")
}
private val favorite by lazy { lineIcon("Favorite", HEART) }
private val favoriteFilled by lazy { solidIcon("FavoriteFilled", listOf(HEART)) }
private val volumeUp by lazy { lineIcon("VolumeUp", SPEAKER, "M15.5 9.2a4 4 0 0 1 0 5.6", "M18.3 6.5a7.8 7.8 0 0 1 0 11") }
private val volumeDown by lazy { lineIcon("VolumeDown", SPEAKER, "M15.5 9.2a4 4 0 0 1 0 5.6") }
private val volumeOff by lazy { lineIcon("VolumeOff", SPEAKER, "M16 9.5l5 5", "M21 9.5l-5 5") }
private val fullscreen by lazy {
    lineIcon(
        "Fullscreen",
        "M4.5 9V6a1.5 1.5 0 0 1 1.5 -1.5h3",
        "M15 4.5h3A1.5 1.5 0 0 1 19.5 6v3",
        "M19.5 15v3a1.5 1.5 0 0 1 -1.5 1.5h-3",
        "M9 19.5H6A1.5 1.5 0 0 1 4.5 18v-3"
    )
}
private val fullscreenExit by lazy {
    lineIcon(
        "FullscreenExit",
        "M4.5 9H7.5A1.5 1.5 0 0 0 9 7.5V4.5",
        "M15 4.5v3A1.5 1.5 0 0 0 16.5 9h3",
        "M19.5 15h-3a1.5 1.5 0 0 0 -1.5 1.5v3",
        "M9 19.5v-3A1.5 1.5 0 0 0 7.5 15h-3"
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
        "M7.5 8.5H6a2 2 0 0 0 -2 2v7a2 2 0 0 0 2 2h1.5",
        "M12.5 4.5h5.5a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2h-5.5a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z",
        "M15.25 12.3a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0 -4.4z",
        "M15.25 8.5v.01"
    )
}
private val miniPlayer by lazy { solidIcon("MiniPlayer", listOf("M13 12.5h4v3.5h-4z"), listOf(SCREEN)) }

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

// 插播：左上加号接正在播放胶囊，下接两行待播
private val addToQueue by lazy {
    lineIcon("AddToQueue", "M5 3.5v5", "M2.5 6h5", "M10.5 4h7.5a2.5 2.5 0 0 1 0 5h-8", "M4 14.5h16", "M4 19.5h16")
}

val MelodiaIcons.AddToQueue: ImageVector get() = addToQueue
