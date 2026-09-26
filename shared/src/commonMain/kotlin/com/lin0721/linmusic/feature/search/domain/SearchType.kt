package com.lin0721.linmusic.feature.search.domain

// 云搜索结果分类，apiValue 对应 cloudsearch 接口的 type 参数（真机核实）
enum class SearchType(val apiValue: Int, val label: String) {
    SONG(1, "单曲"),
    ALBUM(10, "专辑"),
    ARTIST(100, "歌手"),
    PLAYLIST(1000, "歌单")
}
