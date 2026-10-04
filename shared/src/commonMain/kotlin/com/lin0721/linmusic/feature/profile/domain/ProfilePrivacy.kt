package com.lin0721.linmusic.feature.profile.domain

import com.lin0721.linmusic.core.network.AppError

// 关注列表和听歌排行的隐私响应通常是 400/403；部分接口还会附带明确的中文提示。
// 只在这些可识别情形下转成“已隐藏”，避免吞掉网络、鉴权、解析或其他业务错误。
fun Throwable.isProfilePrivacyRestricted(): Boolean {
    val error = this as? AppError.BizError ?: return false
    val message = error.rawMsg.orEmpty()
    return error.code == 400 ||
        error.code == 403 ||
        listOf("隐私", "隐藏", "不能查看", "无法查看", "无权查看", "不开放").any(message::contains)
}
