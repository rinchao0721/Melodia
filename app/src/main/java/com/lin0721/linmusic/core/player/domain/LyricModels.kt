package com.lin0721.linmusic.core.player.domain

// 单个字符/单词的耗时元数据
data class WordInfo(
    val text: String,
    val startOffsetMs: Long,  // 相对于整行歌词起始时间的偏移毫秒数
    val durationMs: Long      // 该字/词的持续发音毫秒数
)

// 歌词行领域模型
data class LyricLine(
    val timeMs: Long,
    val durationMs: Long = 0, // 新增：整行歌词的持续发音时间
    val text: String,
    val translation: String? = null,
    val words: List<WordInfo> = emptyList() // 如果是普通LRC则此列表为空；YRC则填入单字列表
)

// 歌词行在 LazyColumn 里展示用的稳定 key：不能只用 timeMs，逐字歌词里作词/作曲等信息行经常共享同一个
// 时间戳（常见于都标在 0ms），必须叠加下标兜底，否则同名时间戳会撞出 Compose 的 key 重复崩溃
fun lyricLineKey(index: Int, line: LyricLine): String = "${line.timeMs}_$index"
