package com.lin0721.linmusic.core.player.data

import java.util.concurrent.TimeUnit
import java.nio.file.Files
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class AmllLyricsClientTest {
    @Test fun primaryExceedingSixSecondsIsDiscarded() = runBlocking {
        MockWebServer().use { server ->
            val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s"><span begin="1s" end="2s">slow TLS or body</span></p></div></body></tt>"""
            server.enqueue(MockResponse().setBody(xml).setBodyDelay(6200, TimeUnit.MILLISECONDS))
            assertNull(AmllLyricsClient().fetchFrom(listOf(server.url("/primary").toString())))
        }
    }

    @Test fun rawHitSkipsNetworkAndAll404CreatesNegativeCache() = runBlocking {
        val root = Files.createTempDirectory("amll-client-cache").toFile()
        try {
            val cache = LyricsCache(root)
            MockWebServer().use { server ->
                val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">cached</p></div></body></tt>"""
                server.enqueue(MockResponse().setBody(xml))
                val client = AmllLyricsClient(cache)
                val url = server.url("/lyrics").toString()
                assertEquals(xml, client.fetchFor(7, listOf(url)))
                assertEquals(xml, client.fetchFor(7, listOf(url)))
                assertEquals(1, server.requestCount)

                server.enqueue(MockResponse().setResponseCode(404))
                assertNull(client.fetchFor(8, listOf(url)))
                server.enqueue(MockResponse().setBody(xml))
                assertNull(client.fetchFor(8, listOf(url)))
                assertEquals(2, server.requestCount)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun stalledPrimaryDoesNotBlockBackup() = runBlocking {
        MockWebServer().use { slow ->
            MockWebServer().use { fast ->
                val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">hello</p></div></body></tt>"""
                slow.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                fast.enqueue(MockResponse().setBody(xml))
                val actual = withTimeout(1800) {
                    AmllLyricsClient().fetchFrom(listOf(slow.url("/1").toString(), fast.url("/2").toString()))
                }
                assertEquals(xml, actual)
                assertEquals(1, slow.requestCount)
                assertEquals(1, fast.requestCount)
            }
        }
    }

    @Test fun triesBackupAfterMissingOrMalformedMirror() = runBlocking {
        MockWebServer().use { server ->
            val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">hello</p></div></body></tt>"""
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("<html>error</html>"))
            server.enqueue(MockResponse().setBody(xml))
            assertEquals(xml, AmllLyricsClient().fetchFrom((1..3).map { server.url("/$it").toString() }))
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun isolatedClientNeverStoresOrSendsCookies() = runBlocking {
        MockWebServer().use { server ->
            val client = AmllLyricsClient()
            assertTrue(client.client.interceptors.isEmpty())
            assertTrue(client.client.networkInterceptors.isEmpty())
            repeat(2) {
                server.enqueue(MockResponse().setBody("ttml").addHeader("Set-Cookie", "MUSIC_U=secret; Path=/"))
                assertEquals("ttml", client.download(server.url("/lyrics").toString()))
                val request = server.takeRequest()
                assertNull(request.getHeader("Cookie"))
                assertNull(request.getHeader("Authorization"))
            }
        }
    }

    @Test fun errorsEmptyBodiesAndOversizedResponses() = runBlocking {
        MockWebServer().use { server ->
            val client = AmllLyricsClient()
            for (response in listOf(MockResponse().setResponseCode(404), MockResponse().setResponseCode(500),
                MockResponse().setBody("  "), MockResponse().setBody("x".repeat(2 * 1024 * 1024 + 1)))) {
                server.enqueue(response)
                assertNull(client.download(server.url("/lyrics").toString()))
            }
        }
    }

    @Test fun cancellationCancelsUnderlyingCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = AmllLyricsClient()
            val task = launch(Dispatchers.IO) { client.download(server.url("/lyrics").toString()) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            task.cancelAndJoin()
            assertTrue(task.isCancelled)
        }
    }

    @Test fun requestHasBoundedTimeout() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try {
                AmllLyricsClient().download(server.url("/lyrics").toString())
                fail("Expected network timeout")
            } catch (_: java.io.IOException) { }
        }
    }
}
