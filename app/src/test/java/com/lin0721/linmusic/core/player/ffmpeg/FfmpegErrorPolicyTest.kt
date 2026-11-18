package com.lin0721.linmusic.core.player.ffmpeg

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class FfmpegErrorPolicyTest {

    @Test
    fun `prompts ffmpeg install when decoding format unsupported and component missing`() {
        val action = classifyPlaybackErrorForFfmpeg(
            errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            failedMimeType = MimeTypes.AUDIO_ALAC,
            isComponentInstalled = false
        )
        assertEquals(
            PlaybackErrorAction.PromptFfmpegInstall(
                mimeType = "audio/alac",
                codecLabel = "ALAC"
            ),
            action
        )
    }

    @Test
    fun `prompts ffmpeg install for decoder init or query failures with supported mixed-case mime`() {
        val initAction = classifyPlaybackErrorForFfmpeg(
            errorCode = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            failedMimeType = MimeTypes.AUDIO_MPEG_L1,
            isComponentInstalled = false
        )
        assertEquals(
            PlaybackErrorAction.PromptFfmpegInstall(
                mimeType = "audio/mpeg-l1",
                codecLabel = "MP1"
            ),
            initAction
        )

        val queryAction = classifyPlaybackErrorForFfmpeg(
            errorCode = PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            failedMimeType = MimeTypes.AUDIO_FLAC,
            isComponentInstalled = false
        )
        assertEquals(
            PlaybackErrorAction.PromptFfmpegInstall(
                mimeType = "audio/flac",
                codecLabel = "FLAC"
            ),
            queryAction
        )
    }

    @Test
    fun `skips instead of prompting when component already installed or error unrelated`() {
        assertEquals(
            PlaybackErrorAction.Skip,
            classifyPlaybackErrorForFfmpeg(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                failedMimeType = MimeTypes.AUDIO_ALAC,
                isComponentInstalled = true
            )
        )
        assertEquals(
            PlaybackErrorAction.Skip,
            classifyPlaybackErrorForFfmpeg(
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                failedMimeType = MimeTypes.AUDIO_ALAC,
                isComponentInstalled = false
            )
        )
        assertEquals(
            PlaybackErrorAction.Skip,
            classifyPlaybackErrorForFfmpeg(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                failedMimeType = MimeTypes.VIDEO_H264,
                isComponentInstalled = false
            )
        )
        assertEquals(
            PlaybackErrorAction.Skip,
            classifyPlaybackErrorForFfmpeg(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                failedMimeType = null,
                isComponentInstalled = false
            )
        )
    }

    @Test
    fun `formatPlayerErrorMessage avoids lower-quality suggestion for unsupported format and cloud or local tracks`() {
        val cloudUnsupported = formatPlayerErrorMessage(
            errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            isCloudOrLocalTrack = true
        )
        assertEquals("设备不支持该音频格式", cloudUnsupported)
        assertFalse(cloudUnsupported.contains("调低播放音质"))

        val onlineUnsupported = formatPlayerErrorMessage(
            errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            isCloudOrLocalTrack = false
        )
        assertEquals("设备不支持该音频格式", onlineUnsupported)

        val cloudInitFailed = formatPlayerErrorMessage(
            errorCode = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            isCloudOrLocalTrack = true
        )
        assertEquals("设备不支持该音频格式", cloudInitFailed)

        val onlineInitFailed = formatPlayerErrorMessage(
            errorCode = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            isCloudOrLocalTrack = false
        )
        assertTrue(onlineInitFailed.contains("调低播放音质"))

        val otherError = formatPlayerErrorMessage(
            errorCode = PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            isCloudOrLocalTrack = true
        )
        assertEquals("连续多首歌曲无法播放", otherError)
    }
}
