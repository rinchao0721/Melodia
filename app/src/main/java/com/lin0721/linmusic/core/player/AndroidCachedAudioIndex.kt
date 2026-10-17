package com.lin0721.linmusic.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.offline.CachedAudioIndex
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// 已下载的歌曲，加上播放缓存里已完整缓存的歌曲
@OptIn(UnstableApi::class)
class AndroidCachedAudioIndex(
    private val context: Context,
    private val downloadPreferences: DownloadPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val localMusicApi: LocalMusicApi
) : CachedAudioIndex {

    override val changes: Flow<Unit> = combine(localMusicApi.availabilityChanges, downloadPreferences.records) { _, _ -> Unit }

    override suspend fun playableIds(ids: Collection<Long>): Set<Long> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptySet()
        val playable = downloadPreferences.findVerifiedRecords(ids).mapTo(HashSet()) { it.songId }
        try { playable += localMusicApi.knownPlayableIds(ids) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* External-file lookup must not block ordinary downloaded/cache playback. */ }
        val remaining = ids.filterTo(HashSet()) { it !in playable }
        if (remaining.isNotEmpty()) {
            val maxSize = settingsPreferences.audioCacheMaxSize.first()
            playable += AudioCacheManager.findCompleteKeys(context, maxSize, remaining).keys
        }
        playable
    }
}
