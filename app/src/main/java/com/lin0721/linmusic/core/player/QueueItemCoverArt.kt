package com.lin0721.linmusic.core.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.localmusic.LocalCoverArtCache
import org.koin.compose.koinInject

// 解析队列项封面，本地音频优先提取内嵌封面
@Composable
fun rememberQueueItemCoverUrl(coverUrl: String, songId: Long, localUri: String?): String {
    if (coverUrl.isNotBlank()) return coverUrl
    val coverCache: LocalCoverArtCache = koinInject()
    val downloadPreferences: DownloadPreferences = koinInject()
    val resolved by produceState(initialValue = "", songId, localUri) {
        val sourceUri = localUri?.let { Uri.parse(it) }
            ?: downloadPreferences.findVerifiedRecord(songId)?.mediaStoreUri?.let { Uri.parse(it) }
        value = sourceUri?.let { coverCache.coverUriFor(it) }?.toString() ?: ""
    }
    return resolved
}
