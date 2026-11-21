package com.lin0721.linmusic.core.player.ffmpeg

import com.lin0721.linmusic.core.plugin.PluginCatalogParser
import com.lin0721.linmusic.core.plugin.PluginPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegComponentCatalogParserTest {

    private val validSha = "a".repeat(64)
    private val validShaArm = "b".repeat(64)

    private fun buildCatalogJson(
        componentsJson: String = """
            [
              {
                "id": "ffmpeg-decoder",
                "version": "1.11.1-1",
                "jniAbi": 1,
                "minAppVersion": "2.4.0",
                "files": {
                  "arm64-v8a": {
                    "url": "https://example.com/libffmpegJNI-arm64.so",
                    "sha256": "$validSha",
                    "size": 2048000
                  },
                  "armeabi-v7a": {
                    "url": "https://example.com/libffmpegJNI-armeabi.so",
                    "sha256": "$validShaArm",
                    "size": 1800000
                  }
                }
              }
            ]
        """.trimIndent()
    ): String = """
        {
          "format": 1,
          "updatedAt": "2026-04-01",
          "plugins": [
            {
              "id": "com.example.demo",
              "name": "Demo",
              "version": "1.0.0",
              "kind": "sandbox",
              "platforms": ["android"],
              "download": {
                "url": "https://example.com/demo.zip",
                "sha256": "${"c".repeat(64)}",
                "size": 1024
              }
            }
          ],
          "components": $componentsJson
        }
    """.trimIndent()

    @Test
    fun `parses valid ffmpeg component and preserves plugin catalog parsing`() {
        val json = buildCatalogJson()
        val result = parseFfmpegComponentCatalog(
            jsonText = json,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            appVersion = "2.4.0"
        )
        assertTrue(result is ComponentResolveResult.Success)
        val manifest = (result as ComponentResolveResult.Success).manifest
        assertEquals("ffmpeg-decoder", manifest.id)
        assertEquals("1.11.1-1", manifest.version)
        assertEquals(1, manifest.jniAbi)
        assertEquals("arm64-v8a", manifest.file.abi)
        assertEquals("https://example.com/libffmpegJNI-arm64.so", manifest.file.url)
        assertEquals(validSha, manifest.file.sha256)
        assertEquals(2048000L, manifest.file.sizeBytes)

        val pluginCatalog = PluginCatalogParser.parse(json, PluginPlatform.ANDROID)
        assertNotNull(pluginCatalog)
        assertEquals(1, pluginCatalog!!.entries.size)
        assertEquals("com.example.demo", pluginCatalog.entries.first().id)
    }

    @Test
    fun `selects first matching abi according to device supportedAbis order`() {
        val result = parseFfmpegComponentCatalog(
            jsonText = buildCatalogJson(),
            supportedAbis = listOf("x86_64", "armeabi-v7a", "arm64-v8a"),
            appVersion = "2.5.0"
        )
        assertTrue(result is ComponentResolveResult.Success)
        val file = (result as ComponentResolveResult.Success).manifest.file
        assertEquals("armeabi-v7a", file.abi)
        assertEquals(validShaArm, file.sha256)
    }

    @Test
    fun `rejects when device abi is not present`() {
        val result = parseFfmpegComponentCatalog(
            jsonText = buildCatalogJson(),
            supportedAbis = listOf("x86"),
            appVersion = "2.4.0"
        )
        assertTrue(result is ComponentResolveResult.Failure)
    }

    @Test
    fun `rejects when minAppVersion is newer than current app version`() {
        val result = parseFfmpegComponentCatalog(
            jsonText = buildCatalogJson(),
            supportedAbis = listOf("arm64-v8a"),
            appVersion = "2.3.9"
        )
        assertTrue(result is ComponentResolveResult.Failure)
        assertTrue((result as ComponentResolveResult.Failure).reason.contains("2.4.0"))
    }

    @Test
    fun `rejects incompatible jniAbi`() {
        val json = buildCatalogJson(
            componentsJson = """
                [
                  {
                    "id": "ffmpeg-decoder",
                    "version": "1.11.1-1",
                    "jniAbi": 2,
                    "files": {
                      "arm64-v8a": {
                        "url": "https://example.com/a.so",
                        "sha256": "$validSha",
                        "size": 100
                      }
                    }
                  }
                ]
            """.trimIndent()
        )
        val result = parseFfmpegComponentCatalog(json, listOf("arm64-v8a"), "2.4.0")
        assertTrue(result is ComponentResolveResult.Failure)
    }

    @Test
    fun `rejects non-https url, out-of-range size, and invalid sha256`() {
        val httpJson = buildCatalogJson(
            componentsJson = """
                [{"id":"ffmpeg-decoder","version":"1.11.1-1","jniAbi":1,"files":{"arm64-v8a":{"url":"http://example.com/a.so","sha256":"$validSha","size":100}}}]
            """.trimIndent()
        )
        assertTrue(parseFfmpegComponentCatalog(httpJson, listOf("arm64-v8a"), "2.4.0") is ComponentResolveResult.Failure)

        val oversizedJson = buildCatalogJson(
            componentsJson = """
                [{"id":"ffmpeg-decoder","version":"1.11.1-1","jniAbi":1,"files":{"arm64-v8a":{"url":"https://example.com/a.so","sha256":"$validSha","size":${FFMPEG_MAX_COMPONENT_BYTES + 1}}}}]
            """.trimIndent()
        )
        assertTrue(parseFfmpegComponentCatalog(oversizedJson, listOf("arm64-v8a"), "2.4.0") is ComponentResolveResult.Failure)

        val zeroSizeJson = buildCatalogJson(
            componentsJson = """
                [{"id":"ffmpeg-decoder","version":"1.11.1-1","jniAbi":1,"files":{"arm64-v8a":{"url":"https://example.com/a.so","sha256":"$validSha","size":0}}}]
            """.trimIndent()
        )
        assertTrue(parseFfmpegComponentCatalog(zeroSizeJson, listOf("arm64-v8a"), "2.4.0") is ComponentResolveResult.Failure)

        val badShaJson = buildCatalogJson(
            componentsJson = """
                [{"id":"ffmpeg-decoder","version":"1.11.1-1","jniAbi":1,"files":{"arm64-v8a":{"url":"https://example.com/a.so","sha256":"xyz","size":100}}}]
            """.trimIndent()
        )
        assertTrue(parseFfmpegComponentCatalog(badShaJson, listOf("arm64-v8a"), "2.4.0") is ComponentResolveResult.Failure)
    }

    @Test
    fun `rejects invalid json or missing components array`() {
        assertTrue(parseFfmpegComponentCatalog("{not-json", listOf("arm64-v8a")) is ComponentResolveResult.Failure)
        assertTrue(parseFfmpegComponentCatalog("""{"format":1,"plugins":[]}""", listOf("arm64-v8a")) is ComponentResolveResult.Failure)
        assertTrue(parseFfmpegComponentCatalog("""{"format":2,"components":[]}""", listOf("arm64-v8a")) is ComponentResolveResult.Failure)
    }
}
