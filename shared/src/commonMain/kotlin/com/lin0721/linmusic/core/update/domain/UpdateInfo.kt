package com.lin0721.linmusic.core.update.domain

// 一次可用更新的领域模型
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val isPrerelease: Boolean,
    val changelog: String,
    val releasePageUrl: String
)
