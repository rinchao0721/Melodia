package com.lin0721.linmusic.core.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

// 通用操作类图标，挂在 MelodiaIcons 下按类别分文件

// 关闭：对角交叉
private val close by lazy {
    lineIcon(
        "Close",
        "M5.5 5.5l13 13",
        "M18.5 5.5l-13 13"
    )
}
// 勾选
private val check by lazy {
    lineIcon(
        "Check",
        "M5.5 12.5l4.5 4.5L18.5 7.5"
    )
}
// 选中：圆内对勾
private val checkCircle by lazy {
    lineIcon(
        "CheckCircle",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M8 12.3l2.8 2.8 5.2 -5.4"
    )
}
// 未选中：空心圆
private val unchecked by lazy {
    lineIcon(
        "Unchecked",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z"
    )
}
// 多选：三行对勾列表
private val checklist by lazy {
    lineIcon(
        "Checklist",
        "M3.5 5.5l1.5 1.5 2.5 -2.5",
        "M3.5 12l1.5 1.5 2.5 -2.5",
        "M3.5 18.5l1.5 1.5 2.5 -2.5",
        "M11 6h9.5",
        "M11 12.5h9.5",
        "M11 19h9.5"
    )
}
// 添加：圆内加号
private val addCircle by lazy {
    lineIcon(
        "AddCircle",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M12 8v8",
        "M8 12h8"
    )
}
// 移除：圆内减号
private val remove by lazy {
    lineIcon(
        "Remove",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M8 12h8"
    )
}
// 加入歌单：加号
private val playlistAdd by lazy {
    lineIcon(
        "PlaylistAdd",
        "M12 4v16",
        "M4 12h16"
    )
}
// 移出歌单：圆内减号
private val playlistRemove by lazy {
    lineIcon(
        "PlaylistRemove",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0 -18z",
        "M8 12h8"
    )
}
// 播放歌单：横梁双音符
private val playlistPlay by lazy {
    lineIcon(
        "PlaylistPlay",
        "M8 4h11.5",
        "M19.5 4v12",
        "M8 4v15",
        "M17.5 14a2 2 0 1 0 0 4 2 2 0 0 0 0 -4z",
        "M6 17a2 2 0 1 0 0 4 2 2 0 0 0 0 -4z"
    )
}
// 删除：带盖垃圾桶
private val delete by lazy {
    lineIcon(
        "Delete",
        "M3.5 6.5h17",
        "M9 6.5V4.5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v2",
        "M5.5 6.5l1 12.6a2 2 0 0 0 2 1.9h7a2 2 0 0 0 2 -1.9l1 -12.6",
        "M10 11v5.5",
        "M14 11v5.5"
    )
}
// 分享：三节点连线
private val share by lazy {
    lineIcon(
        "Share",
        "M18 3.3a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0 -4.4z",
        "M6 9.8a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0 -4.4z",
        "M18 16.3a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0 -4.4z",
        "M8 10.9l8 -4.3",
        "M8 13.1l8 4.3"
    )
}
// 下载：箭头入托盘
private val download by lazy {
    lineIcon(
        "Download",
        "M12 3.5v11",
        "M7.5 10.5L12 15l4.5 -4.5",
        "M4 16.5v1.5A3 3 0 0 0 7 21h10a3 3 0 0 0 3 -3v-1.5"
    )
}
// 上传：箭头出托盘
private val upload by lazy {
    lineIcon(
        "Upload",
        "M12 15.5V4",
        "M7.5 8.5L12 4l4.5 4.5",
        "M4 16.5v1.5A3 3 0 0 0 7 21h10a3 3 0 0 0 3 -3v-1.5"
    )
}
// 刷新：顺时针回转
private val refresh by lazy {
    lineIcon(
        "Refresh",
        "M20 12a8 8 0 1 1 -2.3 -5.6",
        "M20 3.5v4h-4"
    )
}
// 恢复：向左回转箭头
private val restore by lazy {
    lineIcon(
        "Restore",
        "M7 3L3.5 6.5 7 10",
        "M3.5 6.5H14a7 7 0 0 1 0 14H5"
    )
}
// 更多：横排三点
private val moreHoriz by lazy {
    solidIcon(
        "MoreHoriz",
        listOf("M5 10.9a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0 -2.2z", "M12 10.9a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0 -2.2z", "M19 10.9a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0 -2.2z")
    )
}
// 更多：竖排三点
private val moreVert by lazy {
    solidIcon(
        "MoreVert",
        listOf("M12 3.9a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0 -2.2z", "M12 10.9a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0 -2.2z", "M12 17.9a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0 -2.2z")
    )
}
// 拖动：两列六点
private val drag by lazy {
    solidIcon(
        "Drag",
        listOf("M9 5.4a0.6 0.6 0 1 0 0 1.2 0.6 0.6 0 0 0 0 -1.2z", "M15 5.4a0.6 0.6 0 1 0 0 1.2 0.6 0.6 0 0 0 0 -1.2z", "M9 11.4a0.6 0.6 0 1 0 0 1.2 0.6 0.6 0 0 0 0 -1.2z", "M15 11.4a0.6 0.6 0 1 0 0 1.2 0.6 0.6 0 0 0 0 -1.2z", "M9 17.4a0.6 0.6 0 1 0 0 1.2 0.6 0.6 0 0 0 0 -1.2z", "M15 17.4a0.6 0.6 0 1 0 0 1.2 0.6 0.6 0 0 0 0 -1.2z")
    )
}
// 链接：双环扣
private val link by lazy {
    lineIcon(
        "Link",
        "M10 14a4 4 0 0 0 5.66 0l3.17 -3.17a4 4 0 0 0 -5.66 -5.66L11.5 6.8",
        "M14 10a4 4 0 0 0 -5.66 0l-3.17 3.17a4 4 0 0 0 5.66 5.66l1.67 -1.63"
    )
}
// 复制：前后错叠两页
private val copy by lazy {
    lineIcon(
        "Copy",
        "M5 7h9a1.5 1.5 0 0 1 1.5 1.5v11a1.5 1.5 0 0 1 -1.5 1.5H5a1.5 1.5 0 0 1 -1.5 -1.5v-11A1.5 1.5 0 0 1 5 7z",
        "M8.5 4.5A1.5 1.5 0 0 1 10 3h9a1.5 1.5 0 0 1 1.5 1.5v11A1.5 1.5 0 0 1 19 17h-1.5"
    )
}
// 编辑：斜放铅笔
private val edit by lazy {
    lineIcon(
        "Edit",
        "M14.5 5.5l4 4",
        "M4 20l1 -4.5L16.3 4.2a1.4 1.4 0 0 1 2 0l1.5 1.5a1.4 1.4 0 0 1 0 2L8.5 19z"
    )
}
// 置顶：斜放图钉
private val pushPin by lazy {
    lineIcon(
        "PushPin",
        "M15.3 3.9a1.6 1.6 0 0 1 2.3 0l2.5 2.5a1.6 1.6 0 0 1 0 2.3l-4.6 4.6 -1 4.1 -8.1 -8.1 4.1 -1z",
        "M10.5 13.5l-7 7"
    )
}
// 回到顶部：顶线加上箭头
private val toTop by lazy {
    lineIcon(
        "ToTop",
        "M4.5 4h15",
        "M12 20V9",
        "M7 13.5l5 -5 5 5"
    )
}
// 排序：递减三线
private val sort by lazy {
    lineIcon(
        "Sort",
        "M3.5 6h17",
        "M3.5 12h12",
        "M3.5 18h7"
    )
}
// 返回：左箭头
private val arrowBack by lazy {
    lineIcon(
        "ArrowBack",
        "M20 12H4.5",
        "M10.5 6l-6 6 6 6"
    )
}
// 上移
private val arrowUp by lazy {
    lineIcon(
        "ArrowUp",
        "M12 20V4.5",
        "M6 10.5l6 -6 6 6"
    )
}
// 下移
private val arrowDown by lazy {
    lineIcon(
        "ArrowDown",
        "M12 4v15.5",
        "M6 13.5l6 6 6 -6"
    )
}
// 展开
private val chevronDown by lazy {
    lineIcon(
        "ChevronDown",
        "M5.5 9l6.5 6.5L18.5 9"
    )
}
// 收起
private val chevronUp by lazy {
    lineIcon(
        "ChevronUp",
        "M5.5 15l6.5 -6.5L18.5 15"
    )
}
// 进入
private val chevronRight by lazy {
    lineIcon(
        "ChevronRight",
        "M9 5.5l6.5 6.5L9 18.5"
    )
}
// 向左
private val chevronLeft by lazy {
    lineIcon(
        "ChevronLeft",
        "M15 5.5L8.5 12l6.5 6.5"
    )
}

val MelodiaIcons.Close: ImageVector get() = close
val MelodiaIcons.Check: ImageVector get() = check
val MelodiaIcons.CheckCircle: ImageVector get() = checkCircle
val MelodiaIcons.Unchecked: ImageVector get() = unchecked
val MelodiaIcons.Checklist: ImageVector get() = checklist
val MelodiaIcons.AddCircle: ImageVector get() = addCircle
val MelodiaIcons.Remove: ImageVector get() = remove
val MelodiaIcons.PlaylistAdd: ImageVector get() = playlistAdd
val MelodiaIcons.PlaylistRemove: ImageVector get() = playlistRemove
val MelodiaIcons.PlaylistPlay: ImageVector get() = playlistPlay
val MelodiaIcons.Delete: ImageVector get() = delete
val MelodiaIcons.Share: ImageVector get() = share
val MelodiaIcons.Download: ImageVector get() = download
val MelodiaIcons.Upload: ImageVector get() = upload
val MelodiaIcons.Refresh: ImageVector get() = refresh
val MelodiaIcons.Restore: ImageVector get() = restore
val MelodiaIcons.MoreHoriz: ImageVector get() = moreHoriz
val MelodiaIcons.MoreVert: ImageVector get() = moreVert
val MelodiaIcons.Drag: ImageVector get() = drag
val MelodiaIcons.Link: ImageVector get() = link
val MelodiaIcons.Copy: ImageVector get() = copy
val MelodiaIcons.Edit: ImageVector get() = edit
val MelodiaIcons.PushPin: ImageVector get() = pushPin
val MelodiaIcons.ToTop: ImageVector get() = toTop
val MelodiaIcons.Sort: ImageVector get() = sort
val MelodiaIcons.ArrowBack: ImageVector get() = arrowBack
val MelodiaIcons.ArrowUp: ImageVector get() = arrowUp
val MelodiaIcons.ArrowDown: ImageVector get() = arrowDown
val MelodiaIcons.ChevronDown: ImageVector get() = chevronDown
val MelodiaIcons.ChevronUp: ImageVector get() = chevronUp
val MelodiaIcons.ChevronRight: ImageVector get() = chevronRight
val MelodiaIcons.ChevronLeft: ImageVector get() = chevronLeft

// 已加入：实心圆镂空对勾
private val checkCircleFilled by lazy {
    fillIcon(
        "CheckCircleFilled",
        "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0 -20zM6.8 12.6l1.6 -1.6 2.4 2.4 5 -5 1.6 1.6 -6.6 6.6z"
    )
}

val MelodiaIcons.CheckCircleFilled: ImageVector get() = checkCircleFilled
