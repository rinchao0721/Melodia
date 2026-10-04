package com.lin0721.linmusic.feature.profile.domain

import com.lin0721.linmusic.core.network.AppError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePrivacyTest {

    @Test
    fun `privacy business responses are recognized`() {
        assertTrue(AppError.BizError(400, null).isProfilePrivacyRestricted())
        assertTrue(AppError.BizError(500, "由于隐私设置无法查看").isProfilePrivacyRestricted())
    }

    @Test
    fun `unrelated failures are not treated as hidden`() {
        assertFalse(AppError.BizError(500, "服务繁忙").isProfilePrivacyRestricted())
        assertFalse(AppError.NetworkError.isProfilePrivacyRestricted())
        assertFalse(AppError.RiskControl.isProfilePrivacyRestricted())
    }
}
