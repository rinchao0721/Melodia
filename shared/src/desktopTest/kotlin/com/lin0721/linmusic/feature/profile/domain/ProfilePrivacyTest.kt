package com.lin0721.linmusic.feature.profile.domain

import com.lin0721.linmusic.core.network.AppError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePrivacyTest {

    @Test
    fun `mapped privacy failure is recognized`() {
        assertTrue(ProfilePrivacyRestricted.isProfilePrivacyRestricted())
    }

    @Test
    fun `unrelated failures are not treated as hidden`() {
        assertFalse(AppError.BizError(400, "用户隐私无权限查看").isProfilePrivacyRestricted())
        assertFalse(AppError.BizError(500, "服务繁忙").isProfilePrivacyRestricted())
        assertFalse(AppError.NetworkError.isProfilePrivacyRestricted())
        assertFalse(AppError.RiskControl.isProfilePrivacyRestricted())
    }

    @Test
    fun `listening rank privacy response requires exact verified code and message`() {
        assertTrue(AppError.BizError(-2, "无权限访问").isListeningRankPrivacyResponse())
        assertFalse(AppError.BizError(400, "无权限访问").isListeningRankPrivacyResponse())
        assertFalse(AppError.BizError(-2, null).isListeningRankPrivacyResponse())
    }

    @Test
    fun `follow list privacy response requires exact verified code and message`() {
        assertTrue(AppError.BizError(400, "用户隐私无权限查看").isFollowListPrivacyResponse())
        assertFalse(AppError.BizError(403, "用户隐私无权限查看").isFollowListPrivacyResponse())
        assertFalse(AppError.BizError(400, null).isFollowListPrivacyResponse())
    }
}
