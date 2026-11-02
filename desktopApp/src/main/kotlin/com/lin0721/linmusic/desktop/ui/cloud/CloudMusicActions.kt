package com.lin0721.linmusic.desktop.ui.cloud

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.desktop.data.cloud.CloudLibrary
import com.lin0721.linmusic.desktop.data.cloud.uploadSourcesOf
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.feature.cloud.domain.CloudSong
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadManager
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadRunner
import java.io.File

// 云盘的播放与上传入口，侧栏、云盘页、本地歌单页共用
class CloudMusicActions(
    val library: CloudLibrary,
    val uploadManager: CloudUploadManager,
    val uploadRunner: CloudUploadRunner,
    private val localLibrary: LocalLibraryRepository,
    private val controller: PlaybackController
) {

    fun play(songs: List<CloudSong>, startIndex: Int) {
        if (songs.isEmpty()) return
        controller.playQueue(songs.map(::toQueueItem), startIndex.coerceIn(0, songs.lastIndex), PLAY_CONTEXT)
    }

    fun addToPlayNext(songs: List<CloudSong>) {
        if (songs.isNotEmpty()) controller.addToPlayNext(songs.map(::toQueueItem))
    }

    // 返回实际加入队列的数量
    fun uploadFiles(files: Collection<File>): Int = uploadRunner.enqueue(uploadSourcesOf(files))

    suspend fun audioFilesIn(folder: File): List<File> = localLibrary.audioFilesIn(folder)

    suspend fun searchCandidates(keyword: String): Result<List<Track>> = localLibrary.searchCandidates(keyword)

    companion object {
        const val PLAY_CONTEXT = "cloud"

        fun toQueueItem(song: CloudSong): QueueItem =
            QueueItem(song.playSongId, song.name, song.artist, song.coverUrl.orEmpty())
    }
}
