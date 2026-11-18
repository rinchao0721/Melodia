package com.lin0721.linmusic.feature.localmusic.data.scan

import com.lin0721.linmusic.core.download.ContainerAudioMetrics
import com.lin0721.linmusic.core.download.parseAlacCsdBitDepth
import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAudioQualityResolverTest {

    @Test
    fun resolveAudioExtensionHandlesPathsUrisAndMimeTypes() {
        assertEquals("flac", resolveAudioExtension("/storage/emulated/0/Music/song.FLAC"))
        assertEquals("wav", resolveAudioExtension(null, null, "primary:Music/track%201.wav"))
        assertEquals("m4a", resolveAudioExtension(null, "audio/mp4; codecs=mp4a.40.2", null))
        assertEquals("mp3", resolveAudioExtension(null, "audio/mpeg", "unknown_no_ext"))
        assertEquals("", resolveAudioExtension("noext", "application/octet-stream", null))
    }

    @Test
    fun inferLocalQualityLevelHandlesLosslessHiresAndFallbacks() {
        // 无损格式：采样率或位深超 CD 判为 hires，仅扩展名无属性时兜底为 lossless
        assertEquals("hires", inferLocalQualityLevel("flac", 1411, 96_000, 16, 0, 0))
        assertEquals("hires", inferLocalQualityLevel("wav", 1411, 48_000, 24, 0, 0))
        assertEquals("lossless", inferLocalQualityLevel("flac", 900, 44_100, 16, 0, 0))
        assertEquals("lossless", inferLocalQualityLevel("ape", 0, 0, 0, 0, 0))
    }

    @Test
    fun inferLocalQualityLevelHandlesLossyBitrateAndSizeDurationEstimate() {
        assertEquals("exhigh", inferLocalQualityLevel("mp3", 320, 44_100, 16, 0, 0))
        assertEquals("higher", inferLocalQualityLevel("m4a", 192, 44_100, 16, 0, 0))
        assertEquals("standard", inferLocalQualityLevel("mp3", 128, 44_100, 16, 0, 0))
        // 码率缺失时按文件大小与时长估算（4_000_000 * 8 / 100_000 = 320 kbps）
        assertEquals("exhigh", inferLocalQualityLevel("mp3", 0, 0, 0, 4_000_000L, 100_000L))
        // 有损格式既无码率又无大小/时长时不臆断
        assertNull(inferLocalQualityLevel("mp3", 0, 0, 0, 0L, 0L))
    }

    @Test
    fun resolveLocalQualityLevelClassifiesAlacM4aAsLosslessOrHires() {
        // ALAC 24bit / 44.1kHz / 1577kbps .m4a（audio/mp4）：按既有规则 24bit 判为 hires
        val alac24FromExtractor = resolveLocalQualityLevel(
            extension = "m4a",
            mimeType = "audio/mp4",
            sizeBytes = 40_000_000L,
            durationMs = 200_000L,
            initialBitrateKbps = 1577,
            probeExtractor = { ContainerAudioMetrics(codec = "audio/alac", sampleRateHz = 44_100, bitDepth = 24) }
        )
        assertEquals("hires", alac24FromExtractor)

        // 若 Extractor 未读出位深，44.1kHz 无损仍触发 Retriever 补查 24bit
        val alac24FromRetriever = resolveLocalQualityLevel(
            extension = "m4a",
            mimeType = "audio/mp4",
            sizeBytes = 40_000_000L,
            durationMs = 200_000L,
            initialBitrateKbps = 1577,
            supportsRetrieverBitDepth = true,
            probeExtractor = { ContainerAudioMetrics(codec = "audio/alac", sampleRateHz = 44_100, bitDepth = 0) },
            probeRetriever = { LocalAudioMetrics(bitrateKbps = 1577, sampleRateHz = 44_100, bitDepth = 24) }
        )
        assertEquals("hires", alac24FromRetriever)

        // ALAC 16bit/44.1kHz 或 API<31 读不到位深时判为 lossless
        val alac16 = resolveLocalQualityLevel(
            extension = "m4a",
            mimeType = "audio/mp4",
            sizeBytes = 22_500_000L,
            durationMs = 200_000L,
            initialBitrateKbps = 900,
            supportsRetrieverBitDepth = false,
            probeExtractor = { ContainerAudioMetrics(codec = "audio/alac", sampleRateHz = 44_100, bitDepth = 0) }
        )
        assertEquals("lossless", alac16)
    }

    @Test
    fun resolveLocalQualityLevelClassifiesAacM4aAsLossyWithoutRetriever() {
        var retrieverCalled = false
        val aacHigher = resolveLocalQualityLevel(
            extension = "m4a",
            mimeType = "audio/mp4",
            sizeBytes = 4_800_000L,
            durationMs = 200_000L,
            initialBitrateKbps = 192,
            supportsRetrieverBitDepth = true,
            probeExtractor = { ContainerAudioMetrics(codec = "audio/mp4a-latm", bitrateKbps = 192, sampleRateHz = 44_100) },
            probeRetriever = {
                retrieverCalled = true
                LocalAudioMetrics()
            }
        )
        assertEquals("higher", aacHigher)
        assertFalse(retrieverCalled)

        val aacExhigh = resolveLocalQualityLevel(
            extension = "mp4",
            mimeType = "audio/mp4",
            sizeBytes = 6_400_000L,
            durationMs = 200_000L,
            initialBitrateKbps = 256,
            probeExtractor = { ContainerAudioMetrics(codec = "audio/mp4a-latm", bitrateKbps = 256, sampleRateHz = 44_100) }
        )
        assertEquals("exhigh", aacExhigh)
    }

    @Test
    fun resolveLocalQualityLevelFallsBackSafelyWhenProbesFail() {
        // 探测全部失败但有大小/时长估算时安全回退
        val estimated = resolveLocalQualityLevel(
            extension = "m4a",
            mimeType = "audio/mp4",
            sizeBytes = 4_000_000L,
            durationMs = 100_000L,
            initialBitrateKbps = 0,
            probeExtractor = { ContainerAudioMetrics() },
            probeTagLib = { null },
            probeRetriever = { LocalAudioMetrics() }
        )
        assertEquals("exhigh", estimated)

        // 完全无有效信息时安全返回 null
        val unknown = resolveLocalQualityLevel(
            extension = "m4a",
            mimeType = "audio/mp4",
            sizeBytes = 0L,
            durationMs = 0L,
            initialBitrateKbps = 0,
            probeExtractor = { ContainerAudioMetrics() },
            probeTagLib = { null },
            probeRetriever = { LocalAudioMetrics() }
        )
        assertNull(unknown)

        // 非容器格式不触发容器探针
        var extractorCalled = false
        val mp3 = resolveLocalQualityLevel(
            extension = "mp3",
            mimeType = "audio/mpeg",
            sizeBytes = 0L,
            durationMs = 0L,
            initialBitrateKbps = 320,
            probeExtractor = {
                extractorCalled = true
                ContainerAudioMetrics()
            }
        )
        assertEquals("exhigh", mp3)
        assertFalse(extractorCalled)
    }

    @Test
    fun parseAlacCsdBitDepthReadsValid24And28ByteConfigs() {
        val raw24 = ByteArray(24).apply {
            this[4] = 0
            this[5] = 24
            // 44100 = 0x0000AC44
            this[22] = 0xAC.toByte()
            this[23] = 0x44.toByte()
        }
        assertEquals(24, parseAlacCsdBitDepth(raw24, 44_100))
        assertEquals(0, parseAlacCsdBitDepth(raw24, 48_000))

        val wrapped28 = ByteArray(28).apply {
            this[8] = 0
            this[9] = 16
            this[26] = 0xAC.toByte()
            this[27] = 0x44.toByte()
        }
        assertEquals(16, parseAlacCsdBitDepth(wrapped28, 44_100))
        assertEquals(0, parseAlacCsdBitDepth(byteArrayOf(1, 2, 3), 44_100))
    }

    @Test
    fun fallbackLocalQualityLevelUsesPathOrUriWhenLegacyFieldMissing() {
        assertEquals(
            "lossless",
            fallbackLocalQualityLevel(
                pathOrName = "/music/demo.flac",
                uriString = "content://media/external/audio/media/1",
                sizeBytes = 0L,
                durationMs = 0L
            )
        )
        assertEquals(
            "exhigh",
            fallbackLocalQualityLevel(
                pathOrName = null,
                uriString = "content://com.android.externalstorage.documents/document/primary%3AMusic%2Ftest.mp3",
                sizeBytes = 4_000_000L,
                durationMs = 100_000L
            )
        )
        assertNull(
            fallbackLocalQualityLevel(
                pathOrName = null,
                uriString = "content://media/external/audio/media/2",
                sizeBytes = 0L,
                durationMs = 0L
            )
        )
    }

    @Test
    fun entityWithNullQualityLevelComputesFallbackSafelyWithoutUriParseCrash() {
        val entity = LocalTrackEntity(
            uri = "/music/sample.wav",
            mediaStoreId = 1L,
            songId = null,
            title = "Sample",
            artist = "Artist",
            album = null,
            durationMs = 180_000L,
            sizeBytes = 30_000_000L,
            path = "/music/sample.wav",
            dateAddedMs = 0L,
            dateModifiedMs = 0L,
            source = LocalTrackSource.EXTERNAL.name,
            qualityLevel = null
        )
        assertEquals("lossless", fallbackLocalQualityLevel(entity.path, entity.uri, entity.sizeBytes, entity.durationMs))
    }
}
