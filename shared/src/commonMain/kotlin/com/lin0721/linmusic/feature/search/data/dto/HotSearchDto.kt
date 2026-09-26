package com.lin0721.linmusic.feature.search.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HotSearchDetailResponse(
    val code: Int = 0,
    val data: List<HotSearchItem> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class HotSearchItem(
    val searchWord: String = "",
    val score: Int = 0,
    val content: String = "",
    val iconUrl: String? = null
)
