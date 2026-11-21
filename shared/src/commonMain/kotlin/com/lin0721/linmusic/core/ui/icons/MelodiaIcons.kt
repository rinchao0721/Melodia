package com.lin0721.linmusic.core.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

// 主界面自绘线性图标集：24 格内统一占 y=3~21，圆头圆角描边
object MelodiaIcons {
    // 主页：圆角屋顶与底边门洞
    val Home: ImageVector by lazy {
        lineIcon(
            "Home",
            "M10.6 3.8a2.5 2.5 0 0 1 2.8 0l6.2 4.4a2 2 0 0 1 .9 1.6V19a2 2 0 0 1 -2 2h-3.5v-5.5a1 1 0 0 0 -1 -1h-3a1 1 0 0 0 -1 1V21H5.5a2 2 0 0 1 -2 -2V9.8a2 2 0 0 1 .9 -1.6z"
        )
    }

    // 主页激活态：实心屋身，门洞放宽
    val HomeFilled: ImageVector by lazy {
        solidIcon(
            "HomeFilled",
            listOf("M10.6 3.8a2.5 2.5 0 0 1 2.8 0l6.2 4.4a2 2 0 0 1 .9 1.6V19a2 2 0 0 1 -2 2h-3v-7a1 1 0 0 0 -1 -1h-5a1 1 0 0 0 -1 1v7H5.5a2 2 0 0 1 -2 -2V9.8a2 2 0 0 1 .9 -1.6z")
        )
    }

    // 搜索：大镜片短斜柄
    val Search: ImageVector by lazy {
        lineIcon(
            "Search",
            "M10.8 3a7.8 7.8 0 1 0 0 15.6 7.8 7.8 0 0 0 0 -15.6z",
            "M16.6 16.6L21 21"
        )
    }

    // 听歌识曲：五道对称竖向声波
    val Recognition: ImageVector by lazy {
        lineIcon(
            "Recognition",
            "M4 9.5v5",
            "M8 6v12",
            "M12 3v18",
            "M16 6v12",
            "M20 9.5v5"
        )
    }

    // 听歌识曲激活态：声波加粗
    val RecognitionFilled: ImageVector by lazy {
        lineIcon(
            "RecognitionFilled",
            3.2f,
            "M4 9.5v5",
            "M8 6v12",
            "M12 3v18",
            "M16 6v12",
            "M20 9.5v5"
        )
    }

    // 浏览：提手托盘与带椭圆标签的篮筐
    val Browse: ImageVector by lazy {
        lineIcon(
            "Browse",
            "M5.5 6.5V4.5a1 1 0 0 1 1 -1h11a1 1 0 0 1 1 1v2",
            "M3.2 9.5h17.6l-1.9 10.1a1.5 1.5 0 0 1 -1.5 1.2H6.6a1.5 1.5 0 0 1 -1.5 -1.2z",
            "M12 13.6c-1.5 0 -2.6 .8 -2.6 1.8s1.1 1.8 2.6 1.8 2.6 -.8 2.6 -1.8 -1.1 -1.8 -2.6 -1.8z"
        )
    }

    // 浏览激活态：实心篮筐，椭圆镂空放大
    val BrowseFilled: ImageVector by lazy {
        solidIcon(
            "BrowseFilled",
            listOf(
                "M5.5 6.5V4.5a1 1 0 0 1 1 -1h11a1 1 0 0 1 1 1v2z",
                "M3.2 9.5h17.6l-1.9 10.1a1.5 1.5 0 0 1 -1.5 1.2H6.6a1.5 1.5 0 0 1 -1.5 -1.2zM12 12.8c-2 0 -3.6 1.15 -3.6 2.6s1.6 2.6 3.6 2.6 3.6 -1.15 3.6 -2.6 -1.6 -2.6 -3.6 -2.6z"
            )
        )
    }

    // 插件：上右双凸耳拼图块
    val Plugin: ImageVector by lazy {
        lineIcon(
            "Plugin",
            "M5 7.5h3.5a2.5 2.5 0 0 1 5 0H17a1 1 0 0 1 1 1V12a2.5 2.5 0 0 1 0 5v3a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1V8.5a1 1 0 0 1 1 -1z"
        )
    }

    // 消息：铃铛与底部铃舌
    val Messages: ImageVector by lazy {
        lineIcon(
            "Messages",
            "M12 3a6 6 0 0 0 -6 6v4.5l-2 3.5h16l-2 -3.5V9a6 6 0 0 0 -6 -6z",
            "M9.5 20.5h5"
        )
    }

    // 添加帐号：半圆肩人像与右侧加号
    val PersonAdd: ImageVector by lazy {
        lineIcon(
            "PersonAdd",
            "M9 3.5a4 4 0 1 0 0 8 4 4 0 0 0 0 -8z",
            "M2.5 21a6.5 6.5 0 0 1 13 0",
            "M18 8v6",
            "M15 11h6"
        )
    }

    // 最近播放：逆时针回溯箭头表盘
    val History: ImageVector by lazy {
        lineIcon(
            "History",
            "M3.5 12a8.5 8.5 0 1 0 2.5 -6",
            "M6 2.5V6h3.5",
            "M12 8v4l2.8 2"
        )
    }

    // 我的云盘：三段弧云朵
    val Cloud: ImageVector by lazy {
        lineIcon(
            "Cloud",
            "M7 20h10.5a4 4 0 0 0 .6 -7.95A6 6 0 0 0 6.3 11.1 4.5 4.5 0 0 0 7 20z"
        )
    }

    // 设置：圆角花瓣齿轮
    val Settings: ImageVector by lazy {
        lineIcon(
            "Settings",
            "M10.3 3.5h3.4l.6 2.4 1.6 .9 2.4 -.7 1.7 2.9 -1.8 1.7v1.9l1.8 1.7 -1.7 2.9 -2.4 -.7 -1.6 .9 -.6 2.4h-3.4l-.6 -2.4 -1.6 -.9 -2.4 .7 -1.7 -2.9 1.8 -1.7v-1.9L4.1 8.9l1.7 -2.9 2.4 .7 1.6 -.9z",
            "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0 -6z"
        )
    }

    // 退出：左侧门框与向右箭头
    val Logout: ImageVector by lazy {
        lineIcon(
            "Logout",
            "M10 3.5H5.5a2 2 0 0 0 -2 2v13a2 2 0 0 0 2 2H10",
            "M9.5 12h11",
            "M16.5 8l4 4 -4 4"
        )
    }

    // 登录：右侧门框与向内箭头
    val Login: ImageVector by lazy {
        lineIcon(
            "Login",
            "M14 3.5h4.5a2 2 0 0 1 2 2v13a2 2 0 0 1 -2 2H14",
            "M3.5 12h11",
            "M10.5 8l4 4 -4 4"
        )
    }

    // 头像兜底：圆头与半圆肩
    val Person: ImageVector by lazy {
        lineIcon(
            "Person",
            "M12 3.5a4 4 0 1 0 0 8 4 4 0 0 0 0 -8z",
            "M5.5 21a6.5 6.5 0 0 1 13 0"
        )
    }

    // 音乐库：两竖一斜三册唱片架
    val Library: ImageVector by lazy {
        lineIcon(
            "Library",
            "M4 3.5v17",
            "M8.5 3.5v17",
            "M13 4.2l6.4 3.4a1 1 0 0 1 0.6 0.9v11a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1z"
        )
    }

    // 私人 FM：中心信号圈与左右双层电波弧
    val Radio: ImageVector by lazy {
        lineIcon(
            "Radio",
            "M12 9.8a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0 -4.4z",
            "M8.1 8.1a5.5 5.5 0 0 0 0 7.8",
            "M15.9 8.1a5.5 5.5 0 0 1 0 7.8",
            "M7.5 4.2a9 9 0 0 0 0 15.6",
            "M16.5 4.2a9 9 0 0 1 0 15.6"
        )
    }

    // 心动模式：桃心与心跳折线
    val Heart: ImageVector by lazy {
        lineIcon(
            "Heart",
            "M12 20.2C8 17.5 3 13.5 3 8.8A4.8 4.8 0 0 1 12 6.4 4.8 4.8 0 0 1 21 8.8c0 4.7 -5 8.7 -9 11.4z",
            "M6.5 12h3l1.5 -2.5 2 5 1.5 -2.5h3"
        )
    }

    // 相似漫游：开口唱片与外旋箭头
    val Explore: ImageVector by lazy {
        lineIcon(
            "Explore",
            "M10.5 4.5a7.5 7.5 0 1 0 7.2 9.6",
            "M10.5 10a2 2 0 1 0 0 4 2 2 0 0 0 0 -4z",
            "M15 3.5l5 2.5 -2.5 5"
        )
    }

    // 每日推荐：带双挂耳与今日方格的日历
    val Calendar: ImageVector by lazy {
        lineIcon(
            "Calendar",
            "M5.5 5h13a2 2 0 0 1 2 2v11.5a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z",
            "M8 3v3.5",
            "M16 3v3.5",
            "M3.5 9.5h17",
            "M7.5 13.2h3.3v3.3H7.5z"
        )
    }

    // 本地音乐：带标签页的文件夹
    val LocalMusic: ImageVector by lazy {
        lineIcon(
            "LocalMusic",
            "M3.5 7V6a2 2 0 0 1 2 -2h3.6l2 2.5h7.4a2 2 0 0 1 2 2V18a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2z"
        )
    }

    // 添加：等长十字加号
    val Plus: ImageVector by lazy {
        lineIcon(
            "Plus",
            "M12 4v16",
            "M4 12h16"
        )
    }
}
