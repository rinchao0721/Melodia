package com.lin0721.linmusic.core.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

// 导航与页面类图标，挂在 MelodiaIcons 下按类别分文件

// 专辑：唱片
private val album by lazy {
    lineIcon(
        "Album",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M12 9.5a2.5 2.5 0 1 0 0 5.0 2.5 2.5 0 0 0 0 -5.0z"
    )
}
// 单曲：带旗音符
private val musicNote by lazy {
    lineIcon(
        "MusicNote",
        "M13 17.5V3.5l6 2.5",
        "M10.5 15.0a2.5 2.5 0 1 0 0 5.0 2.5 2.5 0 0 0 0 -5.0z"
    )
}
// 麦克风
private val mic by lazy {
    lineIcon(
        "Mic",
        "M12 3a3 3 0 0 0 -3 3v5a3 3 0 0 0 6 0V6a3 3 0 0 0 -3 -3z",
        "M5.5 11a6.5 6.5 0 0 0 13 0",
        "M12 17.5V21"
    )
}
// 麦克风关闭：加斜线
private val micOff by lazy {
    lineIcon(
        "MicOff",
        "M12 3a3 3 0 0 0 -3 3v5a3 3 0 0 0 6 0V6a3 3 0 0 0 -3 -3z",
        "M5.5 11a6.5 6.5 0 0 0 13 0",
        "M12 17.5V21",
        "M4 4l16 16"
    )
}
// 耳机
private val headphones by lazy {
    lineIcon(
        "Headphones",
        "M3.5 17v-4a8.5 8.5 0 0 1 17 0v4",
        "M3.5 14.5h2a1.5 1.5 0 0 1 1.5 1.5v3.5a1.5 1.5 0 0 1 -1.5 1.5H5a1.5 1.5 0 0 1 -1.5 -1.5z",
        "M20.5 14.5h-2a1.5 1.5 0 0 0 -1.5 1.5v3.5a1.5 1.5 0 0 0 1.5 1.5H19a1.5 1.5 0 0 0 1.5 -1.5z"
    )
}
// 群组：双人
private val group by lazy {
    lineIcon(
        "Group",
        "M9 4.0a3.5 3.5 0 1 0 0 7.0 3.5 3.5 0 0 0 0 -7.0z",
        "M2.5 20.5a6.5 6.5 0 0 1 13 0",
        "M15.5 4.2a3.5 3.5 0 0 1 0 6.6",
        "M17.5 14.3a6.5 6.5 0 0 1 4 6.2"
    )
}
// 邀请：单人加号
private val groupAdd by lazy {
    lineIcon(
        "GroupAdd",
        "M9 4.0a3.5 3.5 0 1 0 0 7.0 3.5 3.5 0 0 0 0 -7.0z",
        "M2.5 20.5a6.5 6.5 0 0 1 13 0",
        "M19 8v6",
        "M16 11h6"
    )
}
// 移除成员：单人减号
private val personRemove by lazy {
    lineIcon(
        "PersonRemove",
        "M9 3.5a4 4 0 1 0 0 8 4 4 0 0 0 0 -8z",
        "M2.5 21a6.5 6.5 0 0 1 13 0",
        "M15 11h6"
    )
}
// 菜单：三横线
private val menu by lazy {
    lineIcon(
        "Menu",
        "M3.5 6h17",
        "M3.5 12h17",
        "M3.5 18h17"
    )
}
// 评论：对话气泡
private val comment by lazy {
    lineIcon(
        "Comment",
        "M12 3.5c-4.7 0 -8.5 3.4 -8.5 7.6 0 2.2 1 4.1 2.7 5.5L5.5 20.5l4.3 -1.9c.7 .1 1.4 .2 2.2 .2 4.7 0 8.5 -3.4 8.5 -7.6S16.7 3.5 12 3.5z"
    )
}
// 发送：纸飞机
private val send by lazy {
    lineIcon(
        "Send",
        "M20.5 3.5L3.5 10.5l7 3 3 7z",
        "M20.5 3.5l-10 10"
    )
}
// 回复：左回转箭头
private val reply by lazy {
    lineIcon(
        "Reply",
        "M9.5 5L3.5 11l6 6",
        "M3.5 11H14a6.5 6.5 0 0 1 6.5 6.5V20"
    )
}
// 点赞
private val thumbUp by lazy {
    lineIcon(
        "ThumbUp",
        "M7.5 10.5L11 3.5a2.2 2.2 0 0 1 2.5 2.4L13 9.5h5.3a2 2 0 0 1 2 2.4l-1.5 7a2 2 0 0 1 -2 1.6H7.5z",
        "M3.5 10.5h4v10h-4z"
    )
}
// 星标
private val star by lazy {
    lineIcon(
        "Star",
        "M12 3.5l2.6 5.4 5.9 .8 -4.3 4.1 1 5.9L12 16.9l-5.2 2.8 1 -5.9 -4.3 -4.1 5.9 -.8z"
    )
}
// 时间：表盘
private val clock by lazy {
    lineIcon(
        "Clock",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M12 7.5V12l3 2"
    )
}
// 推荐：大小星芒
private val sparkle by lazy {
    lineIcon(
        "Sparkle",
        "M11 3.5l1.8 4.7 4.7 1.8 -4.7 1.8L11 16.5l-1.8 -4.7L4.5 10l4.7 -1.8z",
        "M18 14.5l.9 2.1 2.1 .9 -2.1 .9 -.9 2.1 -.9 -2.1 -2.1 -.9 2.1 -.9z"
    )
}
// 统计：柱状图
private val insights by lazy {
    lineIcon(
        "Insights",
        "M3.5 20.5h17",
        "M6.5 17v-4",
        "M11 17V8",
        "M15.5 17v-6",
        "M20 17V4.5"
    )
}
// 调节：双旋钮滑杆
private val tune by lazy {
    lineIcon(
        "Tune",
        "M3.5 7h9",
        "M17.5 7h3",
        "M3.5 17h3",
        "M11.5 17h9",
        "M15 4.5a2.5 2.5 0 1 0 0 5.0 2.5 2.5 0 0 0 0 -5.0z",
        "M9 14.5a2.5 2.5 0 1 0 0 5.0 2.5 2.5 0 0 0 0 -5.0z"
    )
}
// 睡眠：弯月
private val moon by lazy {
    lineIcon(
        "Moon",
        "M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5z"
    )
}
// 无限：横8字
private val infinity by lazy {
    lineIcon(
        "Infinity",
        "M12 12c-2 -2.7 -3.6 -4 -5.5 -4a4 4 0 0 0 0 8c1.9 0 3.5 -1.3 5.5 -4s3.6 -4 5.5 -4a4 4 0 0 1 0 8c-1.9 0 -3.5 -1.3 -5.5 -4z"
    )
}
// 播放：实心圆镂空三角
private val playCircle by lazy {
    solidIcon(
        "PlayCircle",
        listOf("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18zM9 6.8v10.4l8.2 -5.2z")
    )
}
// 暂停：实心圆镂空双竖条
private val pauseCircle by lazy {
    solidIcon(
        "PauseCircle",
        listOf("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18zM7.5 7h4v10h-4zM12.5 7h4v10h-4z")
    )
}
// 打开文件夹
private val folderOpen by lazy {
    lineIcon(
        "FolderOpen",
        "M3.5 18V6a2 2 0 0 1 2 -2h3.6l2 2.5h6.4a2 2 0 0 1 2 2V10",
        "M3.5 18l2.3 -6.6A1.5 1.5 0 0 1 7.2 10.5h13.2a1 1 0 0 1 .9 1.3L19.4 19a1.5 1.5 0 0 1 -1.4 1H5.5a2 2 0 0 1 -2 -2z"
    )
}
// 新建文件夹：文件夹加号
private val newFolder by lazy {
    lineIcon(
        "NewFolder",
        "M3.5 7V6a2 2 0 0 1 2 -2h3.6l2 2.5h7.4a2 2 0 0 1 2 2V18a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2z",
        "M12 10.5v6",
        "M9 13.5h6"
    )
}
// 网格视图：四宫格
private val grid by lazy {
    lineIcon(
        "Grid",
        "M4.5 3.5h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z",
        "M14.5 3.5h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z",
        "M4.5 13.5h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z",
        "M14.5 13.5h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z"
    )
}
// 列表视图：圆点列表
private val list by lazy {
    solidIcon(
        "List",
        listOf("M4.5 4.8a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0 -2.4z", "M4.5 10.8a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0 -2.4z", "M4.5 16.8a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0 -2.4z"),
        listOf("M9 6h11.5", "M9 12h11.5", "M9 18h11.5")
    )
}
// 侧栏：右侧分栏
private val sidebar by lazy {
    lineIcon(
        "Sidebar",
        "M5 4.5h14a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z",
        "M15 4.5v15"
    )
}
// 锁定
private val lock by lazy {
    lineIcon(
        "Lock",
        "M6.5 10.5h11a1.5 1.5 0 0 1 1.5 1.5v7a1.5 1.5 0 0 1 -1.5 1.5h-11A1.5 1.5 0 0 1 5 19v-7a1.5 1.5 0 0 1 1.5 -1.5z",
        "M8 10.5V7.5a4 4 0 0 1 8 0v3"
    )
}
// 信息：圆内 i
private val info by lazy {
    lineIcon(
        "Info",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M12 11v5.5",
        "M12 7.5v.01"
    )
}
// 错误：圆内感叹号
private val error by lazy {
    lineIcon(
        "Error",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M12 7.5V13",
        "M12 16.5v.01"
    )
}
// 警告：三角感叹号
private val warning by lazy {
    lineIcon(
        "Warning",
        "M10.3 4.5a2 2 0 0 1 3.4 0l7.5 13a2 2 0 0 1 -1.7 3H4.5a2 2 0 0 1 -1.7 -3z",
        "M12 9.5v4",
        "M12 17v.01"
    )
}
// 排行榜：领奖台
private val leaderboard by lazy {
    lineIcon(
        "Leaderboard",
        "M3.5 20.5V14h5.5v6.5",
        "M9 20.5V8h6v12.5",
        "M15 20.5V11.5h5.5v9",
        "M3.5 20.5h17"
    )
}
// 热门：火焰
private val fire by lazy {
    lineIcon(
        "Fire",
        "M12 21a6.5 6.5 0 0 0 6.5 -6.5c0 -4 -3 -6 -4.5 -10.5 -2 2 -3 4 -3 6 -1 -.5 -2 -1.5 -2.5 -3C6.8 9 5.5 11.5 5.5 14.5A6.5 6.5 0 0 0 12 21z"
    )
}

val MelodiaIcons.Album: ImageVector get() = album
val MelodiaIcons.MusicNote: ImageVector get() = musicNote
val MelodiaIcons.Mic: ImageVector get() = mic
val MelodiaIcons.MicOff: ImageVector get() = micOff
val MelodiaIcons.Headphones: ImageVector get() = headphones
val MelodiaIcons.Group: ImageVector get() = group
val MelodiaIcons.GroupAdd: ImageVector get() = groupAdd
val MelodiaIcons.PersonRemove: ImageVector get() = personRemove
val MelodiaIcons.Menu: ImageVector get() = menu
val MelodiaIcons.Comment: ImageVector get() = comment
val MelodiaIcons.Send: ImageVector get() = send
val MelodiaIcons.Reply: ImageVector get() = reply
val MelodiaIcons.ThumbUp: ImageVector get() = thumbUp
val MelodiaIcons.Star: ImageVector get() = star
val MelodiaIcons.Clock: ImageVector get() = clock
val MelodiaIcons.Sparkle: ImageVector get() = sparkle
val MelodiaIcons.Insights: ImageVector get() = insights
val MelodiaIcons.Tune: ImageVector get() = tune
val MelodiaIcons.Moon: ImageVector get() = moon
val MelodiaIcons.Infinity: ImageVector get() = infinity
val MelodiaIcons.PlayCircle: ImageVector get() = playCircle
val MelodiaIcons.PauseCircle: ImageVector get() = pauseCircle
val MelodiaIcons.FolderOpen: ImageVector get() = folderOpen
val MelodiaIcons.NewFolder: ImageVector get() = newFolder
val MelodiaIcons.Grid: ImageVector get() = grid
val MelodiaIcons.List: ImageVector get() = list
val MelodiaIcons.Sidebar: ImageVector get() = sidebar
val MelodiaIcons.Lock: ImageVector get() = lock
val MelodiaIcons.Info: ImageVector get() = info
val MelodiaIcons.Error: ImageVector get() = error
val MelodiaIcons.Warning: ImageVector get() = warning
val MelodiaIcons.Leaderboard: ImageVector get() = leaderboard
val MelodiaIcons.Fire: ImageVector get() = fire

// 播放·空心：线条圆圈加线条三角
private val playCircleOutline by lazy {
    lineIcon("PlayCircleOutline", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z", "M10 8.6v6.8l5.3 -3.4z")
}

val MelodiaIcons.PlayCircleOutline: ImageVector get() = playCircleOutline
