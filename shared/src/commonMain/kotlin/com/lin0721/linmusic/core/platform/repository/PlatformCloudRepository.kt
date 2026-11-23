package com.lin0721.linmusic.core.platform.repository

import com.lin0721.linmusic.core.kugou.KugouCloudDeleteItem
import com.lin0721.linmusic.core.kugou.KugouCloudService
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.cloud.data.CloudRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class UnifiedCloudQuota(
    val usedBytes: Long = 0L,
    val maxBytes: Long = 0L,
    val totalCount: Int = 0
) {
    val usedRatio: Float
        get() = if (maxBytes <= 0L) 0f else (usedBytes.toFloat() / maxBytes).coerceIn(0f, 1f)
}

data class UnifiedCloudTrack(
    val track: UnifiedTrack,
    val fileSizeBytes: Long = 0L,
    val bitrate: Int = 0,
    val extName: String = "",
    val addedAtMs: Long = 0L,
    val isUnmatched: Boolean = false,
    val deleteKey: String = track.id.canonical
)

data class UnifiedCloudPage(
    val platform: MusicPlatform,
    val tracks: List<UnifiedCloudTrack> = emptyList(),
    val quota: UnifiedCloudQuota = UnifiedCloudQuota(),
    val page: Int = 1,
    val hasMore: Boolean = false
)

// 跨平台音乐云盘契约
interface PlatformCloudRepository {
    fun getCloudTracks(
        platform: MusicPlatform,
        page: Int = 1,
        pageSize: Int = 30
    ): Flow<Result<UnifiedCloudPage>>

    fun deleteCloudTracks(
        platform: MusicPlatform,
        tracks: List<UnifiedCloudTrack>
    ): Flow<Result<Unit>>
}

class NeteasePlatformCloudRepository(
    private val cloudRepository: CloudRepository
) : PlatformCloudRepository {

    override fun getCloudTracks(
        platform: MusicPlatform,
        page: Int,
        pageSize: Int
    ): Flow<Result<UnifiedCloudPage>> {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceAtLeast(1)
        val offset = (safePage - 1) * safeSize
        return cloudRepository.getCloudSongs(limit = safeSize, offset = offset).map { res ->
            res.map { data ->
                UnifiedCloudPage(
                    platform = MusicPlatform.NETEASE,
                    tracks = data.songs.map { song ->
                        val artistList = song.artist.takeIf { it.isNotBlank() }
                            ?.let { listOf(UnifiedArtist(name = it)) }
                            .orEmpty()
                        val albumObj = song.album.takeIf { it.isNotBlank() }
                            ?.let { UnifiedAlbum(name = it, coverUrl = song.coverUrl.orEmpty()) }
                        UnifiedCloudTrack(
                            track = UnifiedTrack(
                                id = TrackId.netease(song.playSongId),
                                name = song.name,
                                artists = artistList,
                                artistNames = song.artist,
                                album = albumObj,
                                durationMs = song.durationMs,
                                coverUrl = song.coverUrl.orEmpty(),
                                publishTimeMs = song.addedAtMs
                            ),
                            fileSizeBytes = song.fileSizeBytes,
                            bitrate = song.bitrate,
                            addedAtMs = song.addedAtMs,
                            isUnmatched = song.isUnmatched,
                            deleteKey = song.songId.toString()
                        )
                    },
                    quota = UnifiedCloudQuota(
                        usedBytes = data.quota.usedBytes,
                        maxBytes = data.quota.maxBytes,
                        totalCount = data.quota.totalCount
                    ),
                    page = safePage,
                    hasMore = data.hasMore
                )
            }
        }
    }

    override fun deleteCloudTracks(
        platform: MusicPlatform,
        tracks: List<UnifiedCloudTrack>
    ): Flow<Result<Unit>> = flow {
        if (tracks.isEmpty()) {
            emit(Result.success(Unit))
            return@flow
        }
        for (item in tracks) {
            val cloudSongId = item.deleteKey.toLongOrNull()
                ?: item.track.id.neteaseIdOrNull()
                ?: continue
            val res = cloudRepository.deleteCloudSong(cloudSongId).first()
            if (res.isFailure) {
                emit(res)
                return@flow
            }
        }
        emit(Result.success(Unit))
    }
}

class KugouPlatformCloudRepository(
    private val cloudService: KugouCloudService,
    private val isEnabled: suspend () -> Boolean = { true }
) : PlatformCloudRepository {

    override fun getCloudTracks(
        platform: MusicPlatform,
        page: Int,
        pageSize: Int
    ): Flow<Result<UnifiedCloudPage>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        emit(
            cloudService.getCloudList(page = page, pageSize = pageSize).map { res ->
                UnifiedCloudPage(
                    platform = MusicPlatform.KUGOU,
                    tracks = res.items.map { dto ->
                        UnifiedCloudTrack(
                            track = dto.toUnifiedTrack(),
                            fileSizeBytes = dto.sizeBytes,
                            bitrate = dto.bitrate,
                            extName = dto.extName,
                            addedAtMs = dto.addTimeMs,
                            isUnmatched = false,
                            deleteKey = "${dto.kvId}:${dto.albumAudioId}"
                        )
                    },
                    quota = UnifiedCloudQuota(
                        usedBytes = res.quota.usedBytes,
                        maxBytes = res.quota.maxBytes,
                        totalCount = res.quota.totalCount
                    ),
                    page = res.page,
                    hasMore = res.hasMore
                )
            }
        )
    }

    override fun deleteCloudTracks(
        platform: MusicPlatform,
        tracks: List<UnifiedCloudTrack>
    ): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val items = tracks.mapNotNull { item ->
            val parts = item.deleteKey.split(":")
            val kvId = item.track.kugouMeta?.cloudKvId
                ?: item.track.kugouMeta?.playlistFileId
                ?: parts.firstOrNull()?.toLongOrNull()
                ?: return@mapNotNull null
            if (kvId <= 0L) return@mapNotNull null
            val albumAudioId = item.track.kugouMeta?.albumAudioId
                ?: parts.getOrNull(1)?.toLongOrNull()
                ?: 0L
            KugouCloudDeleteItem(kvId = kvId, albumAudioId = albumAudioId)
        }
        if (items.isEmpty()) {
            emit(Result.failure(IllegalArgumentException("缺少酷狗云盘文件 kvId，无法删除")))
            return@flow
        }
        emit(cloudService.deleteCloudFiles(items))
    }
}

// 跨平台云盘路由器：kugouPlatformEnabled 关闭时查询自动回退网易并跳过酷狗请求
class PlatformCloudRouter(
    private val netease: PlatformCloudRepository,
    private val kugou: PlatformCloudRepository,
    private val isKugouEnabled: suspend () -> Boolean = { true }
) : PlatformCloudRepository {

    suspend fun resolveCloudPlatform(requested: MusicPlatform): MusicPlatform =
        if (requested == MusicPlatform.KUGOU && isKugouEnabled()) MusicPlatform.KUGOU else MusicPlatform.NETEASE

    override fun getCloudTracks(
        platform: MusicPlatform,
        page: Int,
        pageSize: Int
    ): Flow<Result<UnifiedCloudPage>> = flow {
        val target = resolveCloudPlatform(platform)
        val repo = if (target == MusicPlatform.KUGOU) kugou else netease
        emit(repo.getCloudTracks(target, page, pageSize).first())
    }

    override fun deleteCloudTracks(
        platform: MusicPlatform,
        tracks: List<UnifiedCloudTrack>
    ): Flow<Result<Unit>> = when (platform) {
        MusicPlatform.NETEASE -> netease.deleteCloudTracks(MusicPlatform.NETEASE, tracks)
        MusicPlatform.KUGOU -> flow {
            if (!isKugouEnabled()) {
                emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            } else {
                emit(kugou.deleteCloudTracks(MusicPlatform.KUGOU, tracks).first())
            }
        }
        else -> flowOf(Result.failure(UnsupportedOperationException("暂不支持该平台云盘: ${platform.key}")))
    }
}
