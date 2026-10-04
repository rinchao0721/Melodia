package com.lin0721.linmusic.feature.profile.domain

import com.lin0721.linmusic.core.network.AppError

// Repository 将已经真机验证的网易云隐私响应映射为该类型，UI 不再猜测业务码或文案。
data object ProfilePrivacyRestricted : Exception()

fun Throwable.isProfilePrivacyRestricted(): Boolean = this === ProfilePrivacyRestricted

internal fun AppError.BizError.isListeningRankPrivacyResponse(): Boolean =
    code == -2 && rawMsg?.trim() == "无权限访问"

internal fun AppError.BizError.isFollowListPrivacyResponse(): Boolean =
    code == 400 && rawMsg?.trim() == "用户隐私无权限查看"
