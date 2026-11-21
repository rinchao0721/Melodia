package com.lin0721.linmusic.core.player

import android.content.Context
import android.net.Uri
import com.kyant.taglib.TagLib
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ReplayGainReader"
private const val TRACK_GAIN_KEY = "REPLAYGAIN_TRACK_GAIN"
private const val TRACK_PEAK_KEY = "REPLAYGAIN_TRACK_PEAK"
private const val R128_TRACK_GAIN_KEY = "R128_TRACK_GAIN"

// 读取本地音频的 ReplayGain / R128 曲目标签，在线地址或读取失败返回 null
class ReplayGainReader(private val context: Context) {

    suspend fun trackLoudness(sourceUri: String): TrackLoudness? = withContext(Dispatchers.IO) {
        if (!sourceUri.startsWith("content:") && !sourceUri.startsWith("file:")) return@withContext null
        runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")?.use { pfd ->
                val tags = TagLib.getMetadata(pfd.dup().detachFd(), readPictures = false)?.propertyMap
                    ?: return@use null
                val rgGain = tags.firstTag(TRACK_GAIN_KEY)
                val rgPeak = tags.firstTag(TRACK_PEAK_KEY)
                LoudnessPolicy.fromReplayGain(rgGain, rgPeak)
                    ?: LoudnessPolicy.fromR128Tag(tags.firstTag(R128_TRACK_GAIN_KEY))
            }
        }.onFailure { AppLogger.d(TAG, "ReplayGain 标签读取失败 uri=$sourceUri", it) }.getOrNull()
    }

    private fun Map<String, Array<String>>.firstTag(key: String): String? =
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.firstOrNull()
}
