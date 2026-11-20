package com.lin0721.linmusic.feature.music.ui

import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StylePortrait
import com.lin0721.linmusic.feature.music.domain.StylePreference

// 「音乐」tab 曲风浏览页 UI 状态
sealed interface MusicUiState {
    data object Loading : MusicUiState

    data class Success(val data: MusicBrowseData) : MusicUiState

    data class Error(val message: String) : MusicUiState
}

data class MusicBrowseData(
    val styles: List<MusicStyle> = emptyList(),
    // 未登录或无数据时为空，此时不展示「你的偏好」
    val preferences: List<StylePreference> = emptyList(),
    // 曲风封面地址（偏好与全部曲风共用），无封面或拉取失败的曲风不在表里
    val styleCovers: Map<Long, String> = emptyMap(),
    // 占比最高曲风的画像
    val portrait: StylePortrait? = null
) {
    val hasPreference: Boolean get() = preferences.isNotEmpty()
}
