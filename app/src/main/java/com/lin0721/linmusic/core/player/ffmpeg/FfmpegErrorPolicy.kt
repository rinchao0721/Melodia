package com.lin0721.linmusic.core.player.ffmpeg

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException

// 与 Media3 1.11.1 FfmpegLibrary.getCodecName 的音频映射保持一致
private val FFMPEG_SUPPORTED_AUDIO_CODEC_LABELS: Map<String, String> = mapOf(
    MimeTypes.AUDIO_AAC.lowercase() to "AAC",
    MimeTypes.AUDIO_MPEG.lowercase() to "MP3",
    MimeTypes.AUDIO_MPEG_L1.lowercase() to "MP1",
    MimeTypes.AUDIO_MPEG_L2.lowercase() to "MP2",
    MimeTypes.AUDIO_AC3.lowercase() to "AC-3",
    MimeTypes.AUDIO_E_AC3.lowercase() to "E-AC-3",
    MimeTypes.AUDIO_E_AC3_JOC.lowercase() to "E-AC-3 JOC",
    MimeTypes.AUDIO_TRUEHD.lowercase() to "TrueHD",
    MimeTypes.AUDIO_DTS.lowercase() to "DTS",
    MimeTypes.AUDIO_DTS_EXPRESS.lowercase() to "DTS Express",
    MimeTypes.AUDIO_DTS_HD.lowercase() to "DTS-HD",
    MimeTypes.AUDIO_VORBIS.lowercase() to "Vorbis",
    MimeTypes.AUDIO_OPUS.lowercase() to "Opus",
    MimeTypes.AUDIO_AMR_NB.lowercase() to "AMR-NB",
    MimeTypes.AUDIO_AMR_WB.lowercase() to "AMR-WB",
    MimeTypes.AUDIO_FLAC.lowercase() to "FLAC",
    MimeTypes.AUDIO_ALAC.lowercase() to "ALAC",
    MimeTypes.AUDIO_MLAW.lowercase() to "PCM μ-law",
    MimeTypes.AUDIO_ALAW.lowercase() to "PCM A-law"
)

private val FFMPEG_PROMPT_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
)

sealed interface PlaybackErrorAction {
    data class PromptFfmpegInstall(val mimeType: String, val codecLabel: String) : PlaybackErrorAction
    data object Skip : PlaybackErrorAction
}

fun isFfmpegSupportedAudioMime(mimeType: String?): Boolean {
    val normalized = mimeType?.trim()?.lowercase() ?: return false
    return normalized in FFMPEG_SUPPORTED_AUDIO_CODEC_LABELS
}

fun ffmpegCodecDisplayName(mimeType: String): String {
    val normalized = mimeType.trim().lowercase()
    return FFMPEG_SUPPORTED_AUDIO_CODEC_LABELS[normalized]
        ?: normalized.substringAfter('/').uppercase()
}

// 解码类错误且格式在 ffmpeg 可解音频清单内、组件尚未安装时，暂停并弹窗引导下载；其余沿用跳过逻辑
fun classifyPlaybackErrorForFfmpeg(
    errorCode: Int,
    failedMimeType: String?,
    isComponentInstalled: Boolean
): PlaybackErrorAction {
    if (isComponentInstalled) return PlaybackErrorAction.Skip
    if (errorCode !in FFMPEG_PROMPT_ERROR_CODES) return PlaybackErrorAction.Skip
    val normalizedMime = failedMimeType?.trim()?.lowercase() ?: return PlaybackErrorAction.Skip
    val codecLabel = FFMPEG_SUPPORTED_AUDIO_CODEC_LABELS[normalizedMime] ?: return PlaybackErrorAction.Skip
    return PlaybackErrorAction.PromptFfmpegInstall(
        mimeType = normalizedMime,
        codecLabel = codecLabel
    )
}

// 播放器自身报错提示：云盘/本地曲目与 FORMAT_UNSUPPORTED 不再误导用户"调低播放音质"
fun formatPlayerErrorMessage(
    errorCode: Int,
    isCloudOrLocalTrack: Boolean
): String = when {
    errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
        "设备不支持该音频格式"
    errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED ->
        if (isCloudOrLocalTrack) "设备不支持该音频格式" else "设备无法解码当前音质，可尝试调低播放音质"
    else -> "连续多首歌曲无法播放"
}
