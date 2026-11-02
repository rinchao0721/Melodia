package com.lin0721.linmusic.core.update.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UpdateRepositoryTest {

    private val releasesJson = """
        [
          {"tag_name":"v2.0.0-beta.3","body":"beta3","prerelease":true,"html_url":"https://example.com/beta3"},
          {"tag_name":"v2.1.0","draft":true,"html_url":"https://example.com/draft"},
          {"tag_name":"nightly","html_url":"https://example.com/nightly"},
          {"tag_name":"v1.13.0","body":"stable","html_url":"https://example.com/stable"},
          {"tag_name":"v1.12.1","html_url":""}
        ]
    """.trimIndent()

    private fun withRepository(body: String, block: suspend (UpdateRepository) -> Unit) = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(body))
            val json = Json { ignoreUnknownKeys = true }
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(GithubReleaseApi::class.java)
            block(UpdateRepository(api))
        }
    }

    @Test
    fun `正式版渠道忽略预发布与草稿`() = withRepository(releasesJson) { repo ->
        val info = repo.fetchLatestUpdate(currentVersionCode = 1120199, allowPrerelease = false).getOrThrow()
        assertEquals("v1.13.0", info?.versionName)
        assertEquals("https://example.com/stable", info?.releasePageUrl)
    }

    @Test
    fun `预发布渠道取版本号最大的一条`() = withRepository(releasesJson) { repo ->
        val info = repo.fetchLatestUpdate(currentVersionCode = 1130099, allowPrerelease = true).getOrThrow()
        assertEquals("v2.0.0-beta.3", info?.versionName)
        assertTrue(info?.isPrerelease == true)
    }

    @Test
    fun `当前版本不低于最新版时无更新`() = withRepository(releasesJson) { repo ->
        assertNull(repo.fetchLatestUpdate(currentVersionCode = 1130099, allowPrerelease = false).getOrThrow())
    }

    @Test
    fun `html_url为空时拼接tag页地址`() = withRepository("""[{"tag_name":"v1.12.1","html_url":""}]""") { repo ->
        val info = repo.fetchLatestUpdate(currentVersionCode = 1, allowPrerelease = false).getOrThrow()
        assertEquals("${UpdateRepository.RELEASES_URL}/tag/v1.12.1", info?.releasePageUrl)
    }

    @Test
    fun `响应异常时返回失败结果`() = withRepository("not json") { repo ->
        assertTrue(repo.fetchLatestUpdate(currentVersionCode = 1, allowPrerelease = true).isFailure)
    }
}
