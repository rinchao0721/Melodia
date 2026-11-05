package com.lin0721.linmusic.core.player

import android.content.Context
import android.net.Uri
import com.kyant.taglib.TagLib
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ReplayGainReader"
private const val TRACK_GAIN_KEY = "REPLAYGAIN_TRACK_GAIN"

// 读取本地音频的 ReplayGain 曲目增益标签，在线地址或读取失败返回 null
class ReplayGainReader(private val context: Context) {

    suspend fun trackGainDb(sourceUri: String): Double? = withContext(Dispatchers.IO) {
        if (!sourceUri.startsWith("content:") && !sourceUri.startsWith("file:")) return@withContext null
        runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")?.use { pfd ->
                val tags = TagLib.getMetadata(pfd.dup().detachFd(), readPictures = false)?.propertyMap
                val raw = tags?.entries?.firstOrNull { it.key.equals(TRACK_GAIN_KEY, ignoreCase = true) }?.value?.firstOrNull()
                LoudnessPolicy.replayGainDb(raw)
            }
        }.onFailure { AppLogger.d(TAG, "ReplayGain 标签读取失败 uri=$sourceUri", it) }.getOrNull()
    }
}
