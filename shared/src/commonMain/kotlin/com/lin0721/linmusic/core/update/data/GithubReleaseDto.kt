package com.lin0721.linmusic.core.update.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// GitHub Releases API 响应体，字段与 GitHub 官方文档保持一致
@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GithubReleaseAssetDto> = emptyList()
)

@Serializable
data class GithubReleaseAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)
