package com.lin0721.linmusic.desktop.ui.cloud

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformCloudRouter
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.desktop.data.cloud.CloudLibrary
import com.lin0721.linmusic.desktop.data.cloud.uploadSourcesOf
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.feature.cloud.domain.CloudSong
import com.lin0721.linmusic.feature.cloud.ui.PlatformCloudStateHolder
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadManager
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadRunner
import kotlinx.coroutines.CoroutineScope
import java.io.File

// 云盘的播放与上传入口，侧栏、云盘页、本地歌单页共用
class CloudMusicActions(
    val library: CloudLibrary,
    val uploadManager: CloudUploadManager,
    val uploadRunner: CloudUploadRunner,
    private val localLibrary: LocalLibraryRepository,
    private val controller: PlaybackController,
    private val platformCloudRouter: PlatformCloudRouter? = null,
    private val settingsPreferences: SettingsPreferences? = null,
    private val resourceProvider: ResourceProvider? = null,
    private val scope: CoroutineScope? = null
) {

    val kugouCloudHolder: PlatformCloudStateHolder? by lazy {
        val router = platformCloudRouter
        val prefs = settingsPreferences
        val res = resourceProvider
        val s = scope
        if (router != null && prefs != null && res != null && s != null) {
            PlatformCloudStateHolder(
                cloudRouter = router,
                settingsPreferences = prefs,
                resourceProvider = res,
                scope = s
            ).also { it.selectPlatform(MusicPlatform.KUGOU) }
        } else {
            null
        }
    }

    fun play(songs: List<CloudSong>, startIndex: Int) {
        if (songs.isEmpty()) return
        controller.playQueue(songs.map(::toQueueItem), startIndex.coerceIn(0, songs.lastIndex), PLAY_CONTEXT)
    }

    fun addToPlayNext(songs: List<CloudSong>) {
        if (songs.isNotEmpty()) controller.addToPlayNext(songs.map(::toQueueItem))
    }

    fun playUnifiedTracks(tracks: List<UnifiedTrack>, startIndex: Int, context: String = PLAY_CONTEXT_KUGOU) {
        if (tracks.isEmpty()) return
        controller.playQueue(tracks.map(QueueItem::fromUnified), startIndex.coerceIn(0, tracks.lastIndex), context)
    }

    fun addUnifiedToPlayNext(tracks: List<UnifiedTrack>) {
        if (tracks.isNotEmpty()) controller.addToPlayNext(tracks.map(QueueItem::fromUnified))
    }

    // 返回实际加入队列的数量
    fun uploadFiles(files: Collection<File>): Int = uploadRunner.enqueue(uploadSourcesOf(files))

    suspend fun audioFilesIn(folder: File): List<File> = localLibrary.audioFilesIn(folder)

    suspend fun searchCandidates(keyword: String): Result<List<Track>> = localLibrary.searchCandidates(keyword)

    companion object {
        const val PLAY_CONTEXT = "cloud"
        const val PLAY_CONTEXT_KUGOU = "kugou_cloud"

        fun toQueueItem(song: CloudSong): QueueItem =
            QueueItem(song.playSongId, song.name, song.artist, song.coverUrl.orEmpty())
    }
}
