package com.lin0721.linmusic.core.network

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderInterceptorTest {

    private class CapturingClient(
        userPreferences: UserPreferences,
        settingsPreferences: SettingsPreferences
    ) {
        var capturedRequest: Request? = null

        private val client = OkHttpClient.Builder()
            .addInterceptor(HeaderInterceptor(userPreferences, settingsPreferences, RealIpProvider()))
            .addInterceptor { chain ->
                val req = chain.request()
                capturedRequest = req
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody(null))
                    .build()
            }
            .build()

        fun execute(request: Request) {
            client.newCall(request).execute().close()
        }
    }

    @Test
    fun noSessionCookieHeaderSuppressesStoredCookieAndStripsInternalHeader() = runTest {
        val userPrefs = UserPreferences(InMemoryPreferencesStore())
        userPrefs.upsertAccount(StoredAccount(uid = 1L, nickname = "A", cookies = "MUSIC_U=active_secret"))
        val settings = SettingsPreferences(InMemoryPreferencesStore(), userPreferences = userPrefs)
        val harness = CapturingClient(userPrefs, settings)

        // Web 请求
        harness.execute(
            Request.Builder()
                .url("https://music.163.com/weapi/login/qrcode/client/login")
                .header(HeaderInterceptor.HEADER_NO_SESSION_COOKIE, "true")
                .build()
        )
        val webReq = harness.capturedRequest!!
        assertNull(webReq.header(HeaderInterceptor.HEADER_NO_SESSION_COOKIE))
        assertFalse(webReq.header("Cookie").orEmpty().contains("active_secret"))

        // EAPI 请求
        harness.execute(
            Request.Builder()
                .url("https://interface.music.163.com/eapi/login/qrcode/unikey")
                .header(HeaderInterceptor.HEADER_NO_SESSION_COOKIE, "true")
                .build()
        )
        val eapiReq = harness.capturedRequest!!
        assertNull(eapiReq.header(HeaderInterceptor.HEADER_NO_SESSION_COOKIE))
        assertFalse(eapiReq.header("Cookie").orEmpty().contains("active_secret"))

        // XEAPI 请求
        harness.execute(
            Request.Builder()
                .url("https://interface3.music.163.com/xeapi/some/endpoint")
                .header(HeaderInterceptor.HEADER_NO_SESSION_COOKIE, "true")
                .build()
        )
        val xeapiReq = harness.capturedRequest!!
        assertNull(xeapiReq.header(HeaderInterceptor.HEADER_NO_SESSION_COOKIE))
        assertNull(xeapiReq.header("x-music-u"))
        assertFalse(xeapiReq.header("Cookie").orEmpty().contains("active_secret"))
    }

    @Test
    fun overrideSessionCookieUsesProvidedCookieWithoutMutatingUserPreferences() = runTest {
        val userPrefs = UserPreferences(InMemoryPreferencesStore())
        userPrefs.upsertAccount(StoredAccount(uid = 1L, nickname = "A", cookies = "MUSIC_U=active_cookie"))
        val settings = SettingsPreferences(InMemoryPreferencesStore(), userPreferences = userPrefs)
        val harness = CapturingClient(userPrefs, settings)

        // EAPI 请求使用 override
        harness.execute(
            Request.Builder()
                .url("https://interface.music.163.com/eapi/nuser/account/get")
                .header(HeaderInterceptor.HEADER_OVERRIDE_SESSION_COOKIE, "MUSIC_U=candidate_cookie")
                .build()
        )
        val eapiReq = harness.capturedRequest!!
        assertNull(eapiReq.header(HeaderInterceptor.HEADER_OVERRIDE_SESSION_COOKIE))
        assertTrue(eapiReq.header("Cookie").orEmpty().contains("MUSIC_U=candidate_cookie"))
        assertFalse(eapiReq.header("Cookie").orEmpty().contains("active_cookie"))

        // XEAPI 请求同时注入对应 x-music-u
        harness.execute(
            Request.Builder()
                .url("https://interface3.music.163.com/xeapi/nuser/account/get")
                .header(HeaderInterceptor.HEADER_OVERRIDE_SESSION_COOKIE, "MUSIC_U=candidate_xeapi")
                .build()
        )
        val xeapiReq = harness.capturedRequest!!
        assertNull(xeapiReq.header(HeaderInterceptor.HEADER_OVERRIDE_SESSION_COOKIE))
        assertEquals("candidate_xeapi", xeapiReq.header("x-music-u"))
        assertTrue(xeapiReq.header("Cookie").orEmpty().contains("MUSIC_U=candidate_xeapi"))

        // 原 UserPreferences 中的活跃账户凭据保持不变
        assertEquals("MUSIC_U=active_cookie", userPrefs.cookies.first())
    }

    @Test
    fun realIpSettingInjectsCustomIpOnNeteaseRequests() = runTest {
        val userPrefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore(), userPreferences = userPrefs)
        settings.saveUseRealIp(true)
        settings.saveRealIpValue("116.25.1.1")
        val harness = CapturingClient(userPrefs, settings)

        harness.execute(Request.Builder().url("https://music.163.com/weapi/song/detail").build())

        val req = harness.capturedRequest!!
        assertEquals("116.25.1.1", req.header("X-Real-IP"))
        assertEquals("116.25.1.1", req.header("X-Forwarded-For"))
    }

    @Test
    fun realIpSettingDisabledSkipsIpHeaders() = runTest {
        val userPrefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore(), userPreferences = userPrefs)
        settings.saveRealIpValue("116.25.1.1")
        val harness = CapturingClient(userPrefs, settings)

        harness.execute(Request.Builder().url("https://music.163.com/weapi/song/detail").build())

        assertNull(harness.capturedRequest!!.header("X-Real-IP"))
    }

    @Test
    fun realIpValidationAcceptsOnlyIpv4() {
        assertTrue(RealIpProvider.isValidIpv4(" 116.25.1.1 "))
        assertFalse(RealIpProvider.isValidIpv4("256.1.1.1"))
        assertFalse(RealIpProvider.isValidIpv4("example.com"))
    }
}
