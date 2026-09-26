package com.lin0721.linmusic.feature.listendata.domain

// 周/月收听报告，各块缺失时对应集合为空、文本为空串，由 UI 决定是否隐藏
data class ListenReport(
    // 分钟，不含播客
    val playMinutes: Int,
    // 清洗后的百分比文案，服务端原文不可用时为空
    val beyondPercentText: String,
    val achievementTitle: String,
    val achievementSubtitle: String,
    val listenDays: Int,
    // 本周期听过的歌曲封面，用作概览区背景墙
    val wallpaperUrls: List<String>,
    val songCount: Int,
    val highlights: List<Highlight>,
    val dailyDurations: List<DayDuration>,
    val timePeriods: List<TimePeriod>,
    val topArtists: List<TopArtist>,
    val genreName: String,
    val ageLabel: String,
    val languageName: String,
    val vipItems: List<VipItem>,
    val podcastMinutes: Int,
    val podcastEpisodes: Int,
    val friendsListening: List<FriendListening>,
    val friendKeywords: List<String>
)

// 服务端编排好的亮点，label 与 valueText 均为其现成文案（「最多收听」「12次」）
data class Highlight(
    val songId: Long,
    val songName: String,
    val coverUrl: String,
    val label: String,
    val valueText: String
)

data class DayDuration(
    // 横轴刻度文案。月视图柱子密集，只在间隔位置给值，其余为空串
    val label: String,
    // 选中该柱时展示的完整日期，横轴留空的日子也有
    val fullLabel: String,
    val minutes: Int
)

data class TimePeriod(
    // 服务端原值（morning / deep_night 等），UI 据此选图标
    val key: String,
    val label: String,
    val minutes: Int
)

data class TopArtist(
    val id: Long,
    val name: String,
    val coverUrl: String,
    // 形如「26次」，服务端已组装好
    val countText: String
)

data class VipItem(
    val field: String,
    val mainText: String,
    val subText: String
)

data class FriendListening(
    val userId: Long,
    val username: String,
    val avatarUrl: String,
    val songName: String,
    val playCount: Int
)

// 周/月播放排行
data class SongRank(
    val totalCount: Int,
    val songs: List<RankSong>
)

data class RankSong(
    val id: Long,
    val name: String,
    val artistName: String,
    val coverUrl: String,
    val playCount: Int
)

// 今日播放
data class TodaySong(
    val id: Long,
    val name: String,
    val artistName: String,
    val coverUrl: String
)

// 历年收听
data class YearStat(
    val year: Int,
    val playCount: Int,
    val durationSeconds: Long
)
