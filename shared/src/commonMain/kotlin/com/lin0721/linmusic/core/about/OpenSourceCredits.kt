package com.lin0721.linmusic.core.about

data class OpenSourceCredit(
    val name: String,
    val url: String,
    val description: String
)

// 设置页「特别感谢」两端共用，改动时同步 README「非常感谢」
object OpenSourceCredits {
    const val INTRO = "本项目的开发离不开以下项目的参考与支持："

    val all: List<OpenSourceCredit> = listOf(
        OpenSourceCredit("NeteaseCloudMusicApi", "https://github.com/binaryify/NeteaseCloudMusicApi", "网易云音乐 API 分析与实现参考"),
        OpenSourceCredit("api-enhanced", "https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced", "增强接口与加密逻辑参考"),
        OpenSourceCredit("KuGouMusicApi", "https://github.com/MakcRe/KuGouMusicApi", "酷狗音乐接口、签名与加密参考"),
        OpenSourceCredit("qq-music-api", "https://github.com/Rain120/qq-music-api", "QQ 音乐接口参考"),
        OpenSourceCredit("Mei", "https://github.com/ljyh223/Mei", "QQ 音乐歌词解析参考"),
        OpenSourceCredit("AMLL 歌词库", "https://github.com/amll-dev/amll-ttml-db", "TTML 逐字歌词、对唱与背景和声歌词来源"),
        OpenSourceCredit("SPlayer", "https://github.com/SPlayer-Dev/SPlayer", "现代流媒体架构设计启发"),
        OpenSourceCredit("Spotify", "https://spotify.com", "移动端流媒体交互范式与 UI/UX 体验灵感"),
        OpenSourceCredit("node-vibrant", "https://github.com/Vibrant-Colors/node-vibrant", "取色算法参考"),
        OpenSourceCredit("SuperLyric", "https://github.com/HChenX/SuperLyric", "系统级实时歌词协议与 AIDL 规范"),
        OpenSourceCredit("LyricInfo", "https://github.com/limczhh/LyricInfo", "蓝牙/系统歌词协议规范"),
        OpenSourceCredit("Lyricon", "https://github.com/proify/Lyricon", "词幕协议广播规范"),
        OpenSourceCredit("Compose Multiplatform", "https://github.com/JetBrains/compose-multiplatform", "跨平台共享层与桌面端界面框架"),
        OpenSourceCredit("Media3", "https://github.com/androidx/media", "Android 端播放内核"),
        OpenSourceCredit("mpv", "https://github.com/mpv-player/mpv", "桌面端音频播放内核 libmpv"),
        OpenSourceCredit("media-kit", "https://github.com/media-kit/media-kit", "精简的纯音频 libmpv 构建"),
        OpenSourceCredit("JNA", "https://github.com/java-native-access/jna", "桌面端调用 libmpv 与 Win32 接口"),
        OpenSourceCredit("libusb", "https://github.com/libusb/libusb", "USB 音频独占输出"),
        OpenSourceCredit("TagLib", "https://github.com/Kyant0/taglib", "本地音乐标签读写"),
        OpenSourceCredit("Retrofit", "https://github.com/square/retrofit", "网络接口定义"),
        OpenSourceCredit("OkHttp", "https://github.com/square/okhttp", "网络请求"),
        OpenSourceCredit("Koin", "https://github.com/InsertKoinIO/koin", "依赖注入"),
        OpenSourceCredit("Coil", "https://github.com/coil-kt/coil", "图片加载"),
        OpenSourceCredit("Haze", "https://github.com/chrisbanes/haze", "毛玻璃模糊效果")
    )
}
