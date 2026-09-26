package com.lin0721.linmusic.feature.search.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchDefaultResponse(
    val code: Int = 0,
    val data: SearchDefaultData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class SearchDefaultData(
    val showKeyword: String = "",
    val realkeyword: String = "",
    val searchType: Int = 0,
    val action: Int = 0
)
