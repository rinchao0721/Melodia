package com.lin0721.linmusic.core.player.usb

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioOutputProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WithoutDirectFloatTest {

    private fun support(level: Int, offload: Boolean = false) = AudioOutputProvider.FormatSupport.Builder()
        .setFormatSupportLevel(level)
        .setIsFormatSupportedForOffload(offload)
        .build()

    @Test
    fun directFloatIsDowngradedToTranscoding() {
        val result = withoutDirectFloat(
            Util.getPcmFormat(C.ENCODING_PCM_FLOAT, 2, 44_100),
            support(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY, offload = true)
        )

        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_WITH_TRANSCODING, result.supportLevel)
        assertTrue("卸载相关能力原样保留", result.isFormatSupportedForOffload)
    }

    @Test
    fun sixteenBitPcmIsUntouched() {
        val original = support(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)

        assertSame(original, withoutDirectFloat(Util.getPcmFormat(C.ENCODING_PCM_16BIT, 2, 44_100), original))
    }

    @Test
    fun unsupportedFloatStaysUnsupported() {
        val original = support(AudioOutputProvider.FORMAT_UNSUPPORTED)

        assertSame(original, withoutDirectFloat(Util.getPcmFormat(C.ENCODING_PCM_FLOAT, 2, 48_000), original))
    }

    @Test
    fun compressedFormatIsUntouched() {
        val flac = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_FLAC).build()
        val original = support(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)

        assertSame(original, withoutDirectFloat(flac, original))
    }
}
