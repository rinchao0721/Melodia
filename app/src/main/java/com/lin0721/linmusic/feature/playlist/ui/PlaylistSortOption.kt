package com.lin0721.linmusic.feature.playlist.ui

import com.lin0721.linmusic.core.model.Track
import java.text.Collator
import java.util.Locale

// 中文排序用 Collator 而非裸字符串比较，Android 上 Locale.CHINA 的默认强度即按拼音排序
private val zhCollator: Collator = Collator.getInstance(Locale.CHINA)

// 歌单详情页的本地排序维度：Track 模型没有"加入歌单时间"字段，排不了最近添加
enum class PlaylistSortOption(val label: String) {
    DEFAULT("排序"),
    TITLE("歌曲名"),
    ARTIST("歌手名"),
    DURATION("时长");

    fun sort(tracks: List<Track>): List<Track> = when (this) {
        DEFAULT -> tracks
        TITLE -> tracks.sortedWith(compareBy(zhCollator) { it.name })
        ARTIST -> tracks.sortedWith(compareBy(zhCollator) { it.ar.firstOrNull()?.name.orEmpty() })
        DURATION -> tracks.sortedBy { it.dt }
    }
}
