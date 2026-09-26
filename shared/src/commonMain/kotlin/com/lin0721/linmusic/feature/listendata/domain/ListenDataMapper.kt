package com.lin0721.linmusic.feature.listendata.domain

import com.lin0721.linmusic.feature.listendata.data.ListenReportData
import com.lin0721.linmusic.feature.listendata.data.SongPlayRankData
import com.lin0721.linmusic.feature.listendata.data.TodayRankData
import com.lin0721.linmusic.feature.listendata.data.YearReportData

private const val MONTH_LABEL_STEP = 5

// DTO → 领域模型。isWeek 决定每日横轴标星期几还是日期
fun ListenReportData.toDomain(isWeek: Boolean): ListenReport {
    val timeBlock = listenTimeBlock
    val distribution = listenTimeDistributionBlock

    return ListenReport(
        playMinutes = timeBlock?.playDuration ?: 0,
        beyondPercentText = cleanBeyondPercentText(timeBlock?.playDurationText.orEmpty()),
        achievementTitle = distribution?.achievementTitle?.mainTitle.orEmpty(),
        achievementSubtitle = distribution?.achievementTitle?.subTitle.orEmpty(),
        listenDays = distribution?.listenDays ?: 0,
        wallpaperUrls = wallpaperBlock?.picUrls.orEmpty().filter { it.isNotBlank() },
        songCount = wallpaperBlock?.songCount ?: 0,
        highlights = topSongBlock?.sections.orEmpty()
            .filter { it.songName.isNotBlank() }
            .map {
                Highlight(
                    songId = it.songId,
                    songName = it.songName,
                    coverUrl = it.picUrl,
                    label = it.field,
                    valueText = it.text
                )
            },
        dailyDurations = distribution?.durationDetails.orEmpty().mapIndexed { index, detail ->
            DayDuration(
                // 周视图七天逐个标星期；月视图三十天挤不下，每五天留一个刻度
                label = when {
                    isWeek -> weekdayLabel(detail.period)
                    index % MONTH_LABEL_STEP == 0 -> monthDayLabel(detail.period)
                    else -> ""
                },
                fullLabel = if (isWeek) fullWeekdayLabel(detail.period)
                else fullMonthDayLabel(detail.period),
                minutes = detail.duration
            )
        },
        timePeriods = timeBlock?.circleTimePeriodDurations.orEmpty()
            .sortedBy { periodSortIndex(it.period) }
            .map { TimePeriod(key = it.period, label = periodLabel(it.period), minutes = it.duration) },
        topArtists = topArtistBlock?.sections.orEmpty().map {
            TopArtist(
                id = it.artistId,
                name = it.artistName,
                coverUrl = it.picUrl,
                countText = it.text
            )
        },
        genreName = topStyleBlock?.genreName.orEmpty(),
        ageLabel = ageLabel(topAgeBlock?.sections?.firstOrNull()?.age.orEmpty()),
        languageName = topLanguageBlock?.sections?.firstOrNull()?.language.orEmpty(),
        vipItems = vipBlock?.sections.orEmpty().map {
            VipItem(field = it.field, mainText = it.mainText, subText = it.subText)
        },
        podcastMinutes = djListenDataBlock?.podcastPlayDuration ?: 0,
        podcastEpisodes = djListenDataBlock?.podcastEpNum ?: 0,
        friendsListening = friendsListenBlock?.items.orEmpty().map {
            FriendListening(
                userId = it.userId,
                username = it.username,
                avatarUrl = it.userAvatar,
                songName = it.songName,
                playCount = it.playCount
            )
        },
        friendKeywords = friendsKeywordBlock?.items.orEmpty()
            .map { it.title }
            .filter { it.isNotBlank() }
    )
}

fun SongPlayRankData.toDomain(): SongRank = SongRank(
    totalCount = songCount,
    songs = songItems.map {
        RankSong(
            id = it.songId,
            name = it.songName,
            artistName = it.artists.joinToString(" / ") { artist -> artist.artistName },
            coverUrl = it.picUrl,
            playCount = it.playCount
        )
    }
)

fun TodayRankData.toDomain(): List<TodaySong> = songDTOs.map {
    TodaySong(
        id = it.songId,
        name = it.songName,
        artistName = it.artists.joinToString(" / ") { artist -> artist.artistName },
        coverUrl = it.picUrl
    )
}

fun YearReportData.toDomain(): List<YearStat> = yearItems.map {
    YearStat(
        year = it.year,
        playCount = it.playNum,
        durationSeconds = it.playDuration
    )
}
