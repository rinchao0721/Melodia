package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.LoudnessPolicy
import com.lin0721.linmusic.core.player.TrackLoudness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField
import java.io.File
import java.net.URI
import java.util.logging.Level
import java.util.logging.Logger

private const val TAG = "DesktopReplayGainReader"
private const val TRACK_GAIN_KEY = "REPLAYGAIN_TRACK_GAIN"
private const val TRACK_PEAK_KEY = "REPLAYGAIN_TRACK_PEAK"
private const val R128_TRACK_GAIN_KEY = "R128_TRACK_GAIN"

// 读取本地音频的 ReplayGain / R128 曲目标签，在线地址或读取失败返回 null
internal object DesktopReplayGainReader {

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    suspend fun trackLoudness(uri: String): TrackLoudness? {
        val file = toLocalFile(uri) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val tag = AudioFileIO.read(file)?.tag ?: return@runCatching null
                readFromTag(tag)
            }.onFailure {
                AppLogger.d(TAG, "读取响度标签失败 uri=$uri", it)
            }.getOrNull()
        }
    }

    fun isLocalUri(uri: String?): Boolean = toLocalFile(uri) != null

    internal fun toLocalFile(uri: String?): File? {
        val raw = uri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val file = if (raw.startsWith("file:", ignoreCase = true)) {
            runCatching { File(URI(raw)) }.getOrNull() ?: return null
        } else {
            File(raw)
        }
        return file.takeIf { it.isFile }
    }

    internal fun readFromTag(tag: Tag): TrackLoudness? {
        val rgGain = findTagValue(tag, TRACK_GAIN_KEY)
        val rgPeak = findTagValue(tag, TRACK_PEAK_KEY)
        return LoudnessPolicy.fromReplayGain(rgGain, rgPeak)
            ?: LoudnessPolicy.fromR128Tag(findTagValue(tag, R128_TRACK_GAIN_KEY))
    }

    private fun findTagValue(tag: Tag, key: String): String? {
        val iterator = runCatching { tag.fields }.getOrNull()
        if (iterator != null) {
            while (iterator.hasNext()) {
                val field = iterator.next()
                val value = when (field) {
                    is AbstractID3v2Frame -> {
                        val body = field.body as? FrameBodyTXXX
                        if (body != null && body.description?.trim().equals(key, ignoreCase = true)) {
                            body.firstTextValue
                        } else null
                    }
                    is Mp4TagReverseDnsField -> {
                        if (field.descriptor?.trim().equals(key, ignoreCase = true)) field.content else null
                    }
                    is TagTextField -> {
                        if (field.id.trim().equals(key, ignoreCase = true)) field.content else null
                    }
                    else -> null
                }
                if (!value.isNullOrBlank()) return value
            }
        }
        return runCatching { tag.getFirst(key) }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
