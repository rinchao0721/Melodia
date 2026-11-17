package com.lin0721.linmusic.core.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

// 设置、弹窗与设备等零散图标，挂在 MelodiaIcons 下按类别分文件

// 手机
private val phone by lazy {
    lineIcon(
        "Phone",
        "M8 3h8a1.5 1.5 0 0 1 1.5 1.5v15A1.5 1.5 0 0 1 16 21H8a1.5 1.5 0 0 1 -1.5 -1.5v-15A1.5 1.5 0 0 1 8 3z",
        "M11 18h2"
    )
}
// 电脑
private val computer by lazy {
    lineIcon(
        "Computer",
        "M5 4h14a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 19 16H5a1.5 1.5 0 0 1 -1.5 -1.5v-9A1.5 1.5 0 0 1 5 4z",
        "M8.5 20.5h7",
        "M12 16v4.5"
    )
}
// 电视
private val tv by lazy {
    lineIcon(
        "Tv",
        "M4.5 7h15A1.5 1.5 0 0 1 21 8.5v10a1.5 1.5 0 0 1 -1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5v-10A1.5 1.5 0 0 1 4.5 7z",
        "M8.5 3l3.5 4 3.5 -4"
    )
}
// 音箱
private val speaker by lazy {
    lineIcon(
        "Speaker",
        "M7.5 3h9A1.5 1.5 0 0 1 18 4.5v15a1.5 1.5 0 0 1 -1.5 1.5h-9A1.5 1.5 0 0 1 6 19.5v-15A1.5 1.5 0 0 1 7.5 3z",
        "M12 11.5a3 3 0 1 0 0 6 3 3 0 0 0 0 -6z",
        "M12 7.5v.01"
    )
}
// 蓝牙
private val bluetooth by lazy {
    lineIcon(
        "Bluetooth",
        "M7 7.5l10 9 -5 4.5V3l5 4.5 -10 9"
    )
}
// 网络
private val wifi by lazy {
    lineIcon(
        "Wifi",
        "M2.5 9a13.5 13.5 0 0 1 19 0",
        "M5.8 12.6a8.8 8.8 0 0 1 12.4 0",
        "M9 16.2a4.2 4.2 0 0 1 6 0",
        "M12 20v.01"
    )
}
// 无网络
private val wifiOff by lazy {
    lineIcon(
        "WifiOff",
        "M2.5 9a13.5 13.5 0 0 1 19 0",
        "M5.8 12.6a8.8 8.8 0 0 1 12.4 0",
        "M9 16.2a4.2 4.2 0 0 1 6 0",
        "M12 20v.01",
        "M4 4l16 16"
    )
}
// 浅色/白天
private val sun by lazy {
    lineIcon(
        "Sun",
        "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0 -8z",
        "M12 3v1.5",
        "M12 19.5V21",
        "M3 12h1.5",
        "M19.5 12H21",
        "M5.6 5.6l1.1 1.1",
        "M17.3 17.3l1.1 1.1",
        "M5.6 18.4l1.1 -1.1",
        "M17.3 6.7l1.1 -1.1"
    )
}
// 午后
private val sunset by lazy {
    lineIcon(
        "Sunset",
        "M7 16a5 5 0 0 1 10 0",
        "M3 19.5h18",
        "M12 4v3.5",
        "M4.6 9.6l1.6 1.6",
        "M19.4 9.6l-1.6 1.6"
    )
}
// 主题色
private val palette by lazy {
    solidIcon(
        "Palette",
        listOf("M8 9.3a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0 -2.4z", "M12 6.3a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0 -2.4z", "M16 9.3a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0 -2.4z"),
        listOf("M12 3a9 9 0 0 0 0 18c1.2 0 2 -.8 2 -1.8 0 -1.4 -1.2 -1.6 -1.2 -2.7 0 -.8 .7 -1.5 1.5 -1.5H17a4 4 0 0 0 4 -4C21 6.5 17 3 12 3z")
    )
}
// 语言
private val globe by lazy {
    lineIcon(
        "Globe",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M3 12h18",
        "M12 3a13.5 13.5 0 0 1 0 18",
        "M12 3a13.5 13.5 0 0 0 0 18"
    )
}
// 文字
private val text by lazy {
    lineIcon(
        "Text",
        "M4.5 6.5v-2h15v2",
        "M12 4.5v15",
        "M9 19.5h6"
    )
}
// 存储
private val storage by lazy {
    lineIcon(
        "Storage",
        "M5 4h14a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 19 11H5a1.5 1.5 0 0 1 -1.5 -1.5v-4A1.5 1.5 0 0 1 5 4z",
        "M5 13h14a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 19 20H5a1.5 1.5 0 0 1 -1.5 -1.5v-4A1.5 1.5 0 0 1 5 13z",
        "M7 7.5v.01",
        "M7 16.5v.01"
    )
}
// 检查更新
private val systemUpdate by lazy {
    lineIcon(
        "SystemUpdate",
        "M8 3h8a1.5 1.5 0 0 1 1.5 1.5v15A1.5 1.5 0 0 1 16 21H8a1.5 1.5 0 0 1 -1.5 -1.5v-15A1.5 1.5 0 0 1 8 3z",
        "M12 7.5v7",
        "M9 11.5l3 3 3 -3"
    )
}
// 切换/交换
private val swap by lazy {
    lineIcon(
        "Swap",
        "M3.5 8h16",
        "M15.5 4l4 4 -4 4",
        "M20.5 16h-16",
        "M8.5 12l-4 4 4 4"
    )
}
// 云同步
private val cloudSync by lazy {
    lineIcon(
        "CloudSync",
        "M7 20h10.5a4 4 0 0 0 .6 -7.95A6 6 0 0 0 6.3 11.1 4.5 4.5 0 0 0 7 20z",
        "M10 14.5l2 -2 2 2",
        "M12 12.5v5"
    )
}
// 二维码
private val qrCode by lazy {
    lineIcon(
        "QrCode",
        "M4.5 3.5h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z",
        "M15.5 3.5h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z",
        "M4.5 14.5h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z",
        "M14.5 14.5v2",
        "M18 14.5h2.5",
        "M14.5 20.5h2",
        "M20.5 18v2.5",
        "M17.5 17.5v.01"
    )
}
// 代码
private val code by lazy {
    lineIcon(
        "Code",
        "M8 7l-5 5 5 5",
        "M16 7l5 5 -5 5",
        "M13.5 4.5l-3 15"
    )
}
// 视频
private val video by lazy {
    lineIcon(
        "Video",
        "M4.5 6h10a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1 -1.5 1.5h-10A1.5 1.5 0 0 1 3 16.5v-9A1.5 1.5 0 0 1 4.5 6z",
        "M16 10.5l5 -3v9l-5 -3"
    )
}
// 高音质
private val highQuality by lazy {
    lineIcon(
        "HighQuality",
        "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z",
        "M7 9v6",
        "M10.5 9v6",
        "M7 12h3.5",
        "M15.5 9a2.5 2.5 0 0 1 2.5 2.5v1a2.5 2.5 0 0 1 -5 0v-1A2.5 2.5 0 0 1 15.5 9z",
        "M16.8 14.5l1.5 1.5"
    )
}
// 新品
private val newBadge by lazy {
    lineIcon(
        "NewBadge",
        "M12 3l2.2 1.8 2.8 -.3 .9 2.7 2.5 1.3 -.8 2.7 1.4 2.5 -2.3 1.6 -.3 2.8 -2.8 .3 -1.8 2.2 -2.4 -1.4 -2.6 1.1 -1.4 -2.4 -2.7 -.8 .4 -2.8 -1.8 -2.2 1.8 -2.2 -.4 -2.8 2.7 -.8 1.4 -2.4 2.6 1.1z",
        "M12 8v4.5",
        "M12 15.5v.01"
    )
}
// 勋章
private val medal by lazy {
    lineIcon(
        "Medal",
        "M12 9.0a5.5 5.5 0 1 0 0 11.0 5.5 5.5 0 0 0 0 -11.0z",
        "M8.5 10.2L6 3h4l2 5 2 -5h4l-2.5 7.2"
    )
}
// 奖杯
private val trophy by lazy {
    lineIcon(
        "Trophy",
        "M7 4h10v5a5 5 0 0 1 -10 0z",
        "M7 6H4.5v1.5A3 3 0 0 0 7.5 10.5",
        "M17 6h2.5v1.5a3 3 0 0 1 -3 3",
        "M12 14v3.5",
        "M8 20.5h8",
        "M9.5 17.5h5v3h-5z"
    )
}
// 定时关闭·停用
private val timerOff by lazy {
    lineIcon(
        "TimerOff",
        "M12 7a7 7 0 1 0 0 14 7 7 0 0 0 0 -14z",
        "M10 3h4",
        "M12 11v3.5",
        "M4 4l16 16"
    )
}
// 无搜索结果
private val searchOff by lazy {
    lineIcon(
        "SearchOff",
        "M10.8 3a7.8 7.8 0 1 0 0 15.6 7.8 7.8 0 0 0 0 -15.6z",
        "M16.6 16.6L21 21",
        "M8 8.3l5.6 5.6",
        "M13.6 8.3L8 13.9"
    )
}
// 无音乐
private val musicOff by lazy {
    lineIcon(
        "MusicOff",
        "M13 17.5V3.5l6 2.5",
        "M10.5 15.0a2.5 2.5 0 1 0 0 5.0 2.5 2.5 0 0 0 0 -5.0z",
        "M4 4l16 16"
    )
}
// 不喜欢
private val heartBroken by lazy {
    lineIcon(
        "HeartBroken",
        "M12 20C8 17.4 3 13.5 3 8.9A4.9 4.9 0 0 1 12 6.2 4.9 4.9 0 0 1 21 8.9c0 4.6 -5 8.5 -9 11.1z",
        "M12 6.2l-1.5 4 3 2 -1.5 4"
    )
}
// 新建
private val addSquare by lazy {
    lineIcon(
        "AddSquare",
        "M5 3.5h14a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1 -1.5 1.5H5A1.5 1.5 0 0 1 3.5 19V5A1.5 1.5 0 0 1 5 3.5z",
        "M12 8v8",
        "M8 12h8"
    )
}
// 加入音乐库
private val libraryAdd by lazy {
    lineIcon(
        "LibraryAdd",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M12 8v8",
        "M8 12h8"
    )
}
// 共享文件夹
private val folderShared by lazy {
    lineIcon(
        "FolderShared",
        "M3.5 7V6a2 2 0 0 1 2 -2h3.6l2 2.5h7.4a2 2 0 0 1 2 2V18a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2z",
        "M12 9.7a1.8 1.8 0 1 0 0 3.6 1.8 1.8 0 0 0 0 -3.6z",
        "M8.8 17.5a3.2 3.2 0 0 1 6.4 0"
    )
}
// 文件夹不可用
private val folderOff by lazy {
    lineIcon(
        "FolderOff",
        "M3.5 7V6a2 2 0 0 1 2 -2h3.6l2 2.5h7.4a2 2 0 0 1 2 2V18a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2z",
        "M3 3.5l18 17"
    )
}
// 筛选
private val filter by lazy {
    lineIcon(
        "Filter",
        "M3.5 6h17",
        "M6.5 12h11",
        "M10 18h4"
    )
}
// 粘贴
private val paste by lazy {
    lineIcon(
        "Paste",
        "M9 4.5H7A1.5 1.5 0 0 0 5.5 6v13.5A1.5 1.5 0 0 0 7 21h10a1.5 1.5 0 0 0 1.5 -1.5V6A1.5 1.5 0 0 0 17 4.5h-2",
        "M9.5 3h5a.5 .5 0 0 1 .5 .5v2a.5 .5 0 0 1 -.5 .5h-5a.5 .5 0 0 1 -.5 -.5v-2a.5 .5 0 0 1 .5 -.5z"
    )
}
// 定位当前
private val locate by lazy {
    lineIcon(
        "Locate",
        "M12 5.5a6.5 6.5 0 1 0 0 13.0 6.5 6.5 0 0 0 0 -13.0z",
        "M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0 -4z",
        "M12 3v2.5",
        "M12 18.5V21",
        "M3 12h2.5",
        "M18.5 12H21"
    )
}
// 填入搜索词
private val arrowUpLeft by lazy {
    lineIcon(
        "ArrowUpLeft",
        "M18 18L6.5 6.5",
        "M6.5 15V6.5H15"
    )
}
// 收起侧栏：侧栏框加左尖括号
private val sidebarCollapse by lazy {
    lineIcon(
        "SidebarCollapse",
        "M5 4.5h14a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z",
        "M9 4.5v15",
        "M15.5 9.5L13 12l2.5 2.5"
    )
}
// 展开侧栏：侧栏框加右尖括号
private val sidebarExpand by lazy {
    lineIcon(
        "SidebarExpand",
        "M5 4.5h14a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z",
        "M9 4.5v15",
        "M13 9.5l2.5 2.5 -2.5 2.5"
    )
}
// 小网格视图
private val apps by lazy {
    solidIcon(
        "Apps",
        listOf("M5 3.4a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2z", "M12 3.4a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2z", "M19 3.4a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2z", "M5 10.4a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2z", "M12 10.4a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2z", "M19 10.4a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2z", "M5 17.4a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2z", "M12 17.4a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2z", "M19 17.4a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2z")
    )
}
// @ 提及
private val at by lazy {
    lineIcon(
        "At",
        "M12 8.5a3.5 3.5 0 1 0 0 7.0 3.5 3.5 0 0 0 0 -7.0z",
        "M15.5 8.5v5a2.5 2.5 0 0 0 5 0V12a8.5 8.5 0 1 0 -3.3 6.7"
    )
}
// 已下载
private val downloaded by lazy {
    lineIcon(
        "Downloaded",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M12 7.5v7",
        "M9 11.5l3 3 3 -3"
    )
}
// 窗口最大化
private val maximize by lazy {
    lineIcon("Maximize", "M7 6h10a1 1 0 0 1 1 1v10a1 1 0 0 1 -1 1H7a1 1 0 0 1 -1 -1V7a1 1 0 0 1 1 -1z")
}
// 窗口还原
private val restoreWindow by lazy {
    lineIcon(
        "RestoreWindow",
        "M6.5 9h8a1 1 0 0 1 1 1v8a1 1 0 0 1 -1 1h-8a1 1 0 0 1 -1 -1v-8a1 1 0 0 1 1 -1z",
        "M8.5 6h9a1 1 0 0 1 1 1v9"
    )
}

val MelodiaIcons.Phone: ImageVector get() = phone
val MelodiaIcons.Computer: ImageVector get() = computer
val MelodiaIcons.Tv: ImageVector get() = tv
val MelodiaIcons.Speaker: ImageVector get() = speaker
val MelodiaIcons.Bluetooth: ImageVector get() = bluetooth
val MelodiaIcons.Wifi: ImageVector get() = wifi
val MelodiaIcons.WifiOff: ImageVector get() = wifiOff
val MelodiaIcons.Sun: ImageVector get() = sun
val MelodiaIcons.Sunset: ImageVector get() = sunset
val MelodiaIcons.Palette: ImageVector get() = palette
val MelodiaIcons.Globe: ImageVector get() = globe
val MelodiaIcons.Text: ImageVector get() = text
val MelodiaIcons.Storage: ImageVector get() = storage
val MelodiaIcons.SystemUpdate: ImageVector get() = systemUpdate
val MelodiaIcons.Swap: ImageVector get() = swap
val MelodiaIcons.CloudSync: ImageVector get() = cloudSync
val MelodiaIcons.QrCode: ImageVector get() = qrCode
val MelodiaIcons.Code: ImageVector get() = code
val MelodiaIcons.Video: ImageVector get() = video
val MelodiaIcons.HighQuality: ImageVector get() = highQuality
val MelodiaIcons.NewBadge: ImageVector get() = newBadge
val MelodiaIcons.Medal: ImageVector get() = medal
val MelodiaIcons.Trophy: ImageVector get() = trophy
val MelodiaIcons.TimerOff: ImageVector get() = timerOff
val MelodiaIcons.SearchOff: ImageVector get() = searchOff
val MelodiaIcons.MusicOff: ImageVector get() = musicOff
val MelodiaIcons.HeartBroken: ImageVector get() = heartBroken
val MelodiaIcons.AddSquare: ImageVector get() = addSquare
val MelodiaIcons.LibraryAdd: ImageVector get() = libraryAdd
val MelodiaIcons.FolderShared: ImageVector get() = folderShared
val MelodiaIcons.FolderOff: ImageVector get() = folderOff
val MelodiaIcons.Filter: ImageVector get() = filter
val MelodiaIcons.Paste: ImageVector get() = paste
val MelodiaIcons.Locate: ImageVector get() = locate
val MelodiaIcons.ArrowUpLeft: ImageVector get() = arrowUpLeft
val MelodiaIcons.SidebarCollapse: ImageVector get() = sidebarCollapse
val MelodiaIcons.SidebarExpand: ImageVector get() = sidebarExpand
val MelodiaIcons.Apps: ImageVector get() = apps
val MelodiaIcons.At: ImageVector get() = at
val MelodiaIcons.Downloaded: ImageVector get() = downloaded
val MelodiaIcons.Maximize: ImageVector get() = maximize
val MelodiaIcons.RestoreWindow: ImageVector get() = restoreWindow

// 右侧面板收起：右分栏加右尖括号
private val panelCollapse by lazy {
    lineIcon("PanelCollapse", "M6 4.5h12a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z", "M15 4.5v15", "M8.5 9.5L11 12l-2.5 2.5")
}
// 右侧面板展开：右分栏加左尖括号
private val panelExpand by lazy {
    lineIcon("PanelExpand", "M6 4.5h12a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z", "M15 4.5v15", "M11 9.5L8.5 12l2.5 2.5")
}
// 窗口最小化：短横线
private val minimize by lazy { lineIcon("Minimize", "M6 12h12") }
// 窗口关闭：细叉
private val closeWindow by lazy { lineIcon("CloseWindow", "M6.5 6.5l11 11", "M17.5 6.5l-11 11") }

val MelodiaIcons.PanelCollapse: ImageVector get() = panelCollapse
val MelodiaIcons.PanelExpand: ImageVector get() = panelExpand
val MelodiaIcons.Minimize: ImageVector get() = minimize
val MelodiaIcons.CloseWindow: ImageVector get() = closeWindow
