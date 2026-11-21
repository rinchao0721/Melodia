package com.lin0721.linmusic.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(UnstableApi::class)
class PlaybackQualityExtractorTest {

    @Test
    fun `pcmEncodingToBitDepth maps valid constants and rejects invalid`() {
        assertEquals(8, pcmEncodingToBitDepth(C.ENCODING_PCM_8BIT))
        assertEquals(16, pcmEncodingToBitDepth(C.ENCODING_PCM_16BIT))
        assertEquals(16, pcmEncodingToBitDepth(C.ENCODING_PCM_16BIT_BIG_ENDIAN))
        assertEquals(24, pcmEncodingToBitDepth(C.ENCODING_PCM_24BIT))
        assertEquals(24, pcmEncodingToBitDepth(C.ENCODING_PCM_24BIT_BIG_ENDIAN))
        assertEquals(32, pcmEncodingToBitDepth(C.ENCODING_PCM_32BIT))
        assertEquals(32, pcmEncodingToBitDepth(C.ENCODING_PCM_32BIT_BIG_ENDIAN))
        assertEquals(32, pcmEncodingToBitDepth(C.ENCODING_PCM_FLOAT))
        assertEquals(32, pcmEncodingToBitDepth(C.ENCODING_PCM_FLOAT_BIG_ENDIAN))
        assertEquals(64, pcmEncodingToBitDepth(C.ENCODING_PCM_DOUBLE))
        assertEquals(64, pcmEncodingToBitDepth(C.ENCODING_PCM_DOUBLE_BIG_ENDIAN))
        assertNull(pcmEncodingToBitDepth(C.ENCODING_INVALID))
        assertNull(pcmEncodingToBitDepth(Format.NO_VALUE))
    }

    @Test
    fun `parseFlacStreamInfoBitDepth parses standard header and dfLa extended payload`() {
        assertEquals(16, parseFlacStreamInfoBitDepth(listOf(buildFlacCsd(44100, 2, 16))))
        assertEquals(20, parseFlacStreamInfoBitDepth(listOf(buildFlacCsd(48000, 2, 20))))
        assertEquals(24, parseFlacStreamInfoBitDepth(listOf(buildFlacCsd(96000, 2, 24))))
        assertEquals(32, parseFlacStreamInfoBitDepth(listOf(buildFlacCsd(192000, 2, 32, extraBytes = 16))))
    }

    @Test
    fun `parseFlacStreamInfoBitDepth returns null on invalid inputs`() {
        assertNull(parseFlacStreamInfoBitDepth(emptyList()))
        assertNull(parseFlacStreamInfoBitDepth(listOf(ByteArray(41))))

        val badMagic = buildFlacCsd(44100, 2, 16).apply { this[0] = 'X'.code.toByte() }
        assertNull(parseFlacStreamInfoBitDepth(listOf(badMagic)))

        val wrongBlockType = buildFlacCsd(44100, 2, 16).apply { this[4] = 0x04 } // VORBIS_COMMENT
        assertNull(parseFlacStreamInfoBitDepth(listOf(wrongBlockType)))

        val wrongLength = buildFlacCsd(44100, 2, 16).apply { this[7] = 35 }
        assertNull(parseFlacStreamInfoBitDepth(listOf(wrongLength)))

        // bitsPerSample = 2 (字段值 1) 超出有效范围 [4, 32]
        val invalidBitDepth = buildFlacCsd(44100, 2, 2)
        assertNull(parseFlacStreamInfoBitDepth(listOf(invalidBitDepth)))
    }

    @Test
    fun `mapFormatToStreamAudioInfo falls back to FLAC STREAMINFO when pcmEncoding is unset`() {
        val flacFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setSampleRate(96_000)
            .setChannelCount(2)
            .setAverageBitrate(2_850_000)
            .setInitializationData(listOf(buildFlacCsd(96_000, 2, 24)))
            .build()

        val info = mapFormatToStreamAudioInfo(flacFormat)
        assertEquals(MimeTypes.AUDIO_FLAC, info.codec)
        assertEquals(96_000, info.sampleRateHz)
        assertEquals(24, info.bitDepth)
        assertEquals(2_850_000, info.bitrateBps)
        assertEquals(2, info.channels)
    }

    @Test
    fun `mapFormatToStreamAudioInfo prefers pcmEncoding and skips FLAC parser for non-FLAC`() {
        val flacWithPcm = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setInitializationData(listOf(buildFlacCsd(96_000, 2, 24)))
            .build()
        assertEquals(16, mapFormatToStreamAudioInfo(flacWithPcm).bitDepth)

        val mp3WithFlacLikeBytes = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_MPEG)
            .setSampleRate(44_100)
            .setChannelCount(2)
            .setPeakBitrate(320_000)
            .setInitializationData(listOf(buildFlacCsd(44_100, 2, 16)))
            .build()
        val mp3Info = mapFormatToStreamAudioInfo(mp3WithFlacLikeBytes)
        assertNull(mp3Info.bitDepth)
        assertEquals(320_000, mp3Info.bitrateBps)
    }

    @Test
    fun `extractStreamAudioInfo reads only selected audio track`() {
        val unselectedAudio = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_MPEG)
            .setSampleRate(44_100)
            .setAverageBitrate(128_000)
            .build()
        val selectedAudio = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setSampleRate(48_000)
            .setChannelCount(2)
            .setPcmEncoding(C.ENCODING_PCM_24BIT)
            .build()
        val videoTrack = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .build()

        val tracks = Tracks(
            listOf(
                Tracks.Group(
                    TrackGroup(videoTrack),
                    false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(true)
                ),
                Tracks.Group(
                    TrackGroup(unselectedAudio, selectedAudio),
                    false,
                    intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED),
                    booleanArrayOf(false, true)
                )
            )
        )

        val stream = extractStreamAudioInfo(tracks)
        assertEquals(MimeTypes.AUDIO_FLAC, stream?.codec)
        assertEquals(48_000, stream?.sampleRateHz)
        assertEquals(24, stream?.bitDepth)
        assertEquals(2, stream?.channels)
        assertNull(extractStreamAudioInfo(Tracks.EMPTY))
    }

    @Test
    fun `extractAudioMimeForError falls back to unselected audio track on decoder error`() {
        val unselectedAlac = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .build()
        val tracks = Tracks(
            listOf(
                Tracks.Group(
                    TrackGroup(unselectedAlac),
                    false,
                    intArrayOf(C.FORMAT_UNSUPPORTED_SUBTYPE),
                    booleanArrayOf(false)
                )
            )
        )
        assertEquals(MimeTypes.AUDIO_ALAC, extractAudioMimeForError(tracks))
        assertNull(extractAudioMimeForError(Tracks.EMPTY))
    }

    @Test
    fun `findUnplayableAudioMime only reports when every audio track is unsupported and unselected`() {
        val alac = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_ALAC).build()
        fun tracksOf(support: Int, selected: Boolean) = Tracks(
            listOf(Tracks.Group(TrackGroup(alac), false, intArrayOf(support), booleanArrayOf(selected)))
        )
        assertEquals(MimeTypes.AUDIO_ALAC, findUnplayableAudioMime(tracksOf(C.FORMAT_UNSUPPORTED_SUBTYPE, false)))
        assertNull(findUnplayableAudioMime(tracksOf(C.FORMAT_HANDLED, true)))
        assertNull(findUnplayableAudioMime(tracksOf(C.FORMAT_EXCEEDS_CAPABILITIES, true)))
        assertNull(findUnplayableAudioMime(tracksOf(C.FORMAT_HANDLED, false)))
        assertNull(findUnplayableAudioMime(Tracks.EMPTY))
    }

    private fun buildFlacCsd(
        sampleRateHz: Int,
        channels: Int,
        bitsPerSample: Int,
        extraBytes: Int = 0
    ): ByteArray {
        val data = ByteArray(42 + extraBytes)
        // "fLaC"
        data[0] = 0x66
        data[1] = 0x4C
        data[2] = 0x61
        data[3] = 0x43
        // last-metadata-block=1, type=0 (STREAMINFO), length=34
        data[4] = 0x80.toByte()
        data[5] = 0x00
        data[6] = 0x00
        data[7] = 0x22
        // STREAMINFO[10..13]: sampleRate(20) | (channels-1)(3) | (bitsPerSample-1)(5) | totalSamplesHi(4)
        val packed = ((sampleRateHz and 0xFFFFF) shl 12) or
            (((channels - 1) and 0x07) shl 9) or
            (((bitsPerSample - 1) and 0x1F) shl 4)
        data[18] = ((packed ushr 24) and 0xFF).toByte()
        data[19] = ((packed ushr 16) and 0xFF).toByte()
        data[20] = ((packed ushr 8) and 0xFF).toByte()
        data[21] = (packed and 0xFF).toByte()
        return data
    }
}
