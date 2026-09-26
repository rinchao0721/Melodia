package com.lin0721.linmusic.feature.podcast.domain

import com.lin0721.linmusic.feature.podcast.data.PodcastCategoryDto
import com.lin0721.linmusic.feature.podcast.data.PodcastProgramDto
import com.lin0721.linmusic.feature.podcast.data.PodcastRadioDetailDto
import com.lin0721.linmusic.feature.podcast.data.PodcastRadioDto

fun List<PodcastCategoryDto>.toPodcastCategories(): List<PodcastCategory> = mapNotNull { dto ->
    if (dto.id <= 0 || dto.name.isBlank()) return@mapNotNull null
    PodcastCategory(dto.id, dto.name)
}

// 缺封面的电台直接丢弃：货架卡片以封面为主体，占位图比少一张更难看
fun List<PodcastRadioDto>.toPodcastRadios(): List<PodcastRadio> = mapNotNull { dto ->
    if (dto.id <= 0 || dto.name.isBlank()) return@mapNotNull null
    val pic = dto.picUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    PodcastRadio(
        id = dto.id,
        name = dto.name,
        picUrl = pic,
        programCount = dto.programCount,
        subCount = dto.subCount,
        djName = dto.dj?.nickname.orEmpty()
    )
}

// 没有 mainSong 的节目点了播不出声，一律不渲染
fun List<PodcastProgramDto>.toPodcastPrograms(): List<PodcastProgram> = mapNotNull { it.toProgramOrNull() }

private fun PodcastProgramDto.toProgramOrNull(): PodcastProgram? {
    if (id <= 0 || name.isBlank()) return null
    val songId = mainSong?.id?.takeIf { it > 0 } ?: return null
    // 节目自身没封面时退回所属电台的封面，电台封面通常就是节目封面
    val cover = coverUrl?.takeIf { it.isNotBlank() }
        ?: radio?.picUrl?.takeIf { it.isNotBlank() }
        ?: return null

    return PodcastProgram(
        id = id,
        songId = songId,
        name = name,
        coverUrl = cover,
        durationMs = duration,
        createTimeMs = createTime,
        listenerCount = listenerCount,
        serialNum = serialNum,
        radioId = radio?.id ?: 0,
        radioName = radio?.name.orEmpty(),
        // 节目层的 dj 常缺省，退回电台层的主播
        djName = dj?.nickname?.takeIf { it.isNotBlank() }
            ?: radio?.dj?.nickname.orEmpty()
    )
}

fun PodcastRadioDetailDto.toPodcastRadioDetail(): PodcastRadioDetail = PodcastRadioDetail(
    id = id,
    name = name,
    picUrl = picUrl.orEmpty(),
    // 简介里的换行原样渲染会撑开卡片，压平交由 UI 控制行数
    desc = desc.orEmpty().lines().joinToString(" ") { it.trim() }.trim(),
    category = category.orEmpty(),
    programCount = programCount,
    subCount = subCount,
    djName = dj?.nickname.orEmpty(),
    djAvatarUrl = dj?.avatarUrl.orEmpty(),
    subscribed = subed
)
