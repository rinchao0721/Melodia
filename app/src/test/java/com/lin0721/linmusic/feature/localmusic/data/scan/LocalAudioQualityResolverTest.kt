package com.lin0721.linmusic.feature.localmusic.data.scan

import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import org.junit.Assert.assertEquals
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
