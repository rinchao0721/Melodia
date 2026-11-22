package com.lin0721.linmusic.core.platform.repository

import com.lin0721.linmusic.core.kugou.KugouPlaylistAddTrackInput
import com.lin0721.linmusic.core.kugou.KugouPlaylistService
import com.lin0721.linmusic.core.kugou.KugouPlaylistTrackSortInput
import com.lin0721.linmusic.core.kugou.KugouPlaylistTracksPage
import com.lin0721.linmusic.core.kugou.KugouUserPlaylistDto
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedCreator
import com.lin0721.linmusic.core.platform.UnifiedPlaylist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.toLegacyNeteasePlaylistOrNull
import com.lin0721.linmusic.core.platform.toUnified
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistexternal.mergeUnifiedPlaylistTracks
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.create.data.CreateRepository
import com.lin0721.linmusic.feature.create.data.playlistPrivacyOf
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface PlatformPlaylistRepository {
    fun getPlaylistDetail(playlistId: PlaylistId): Flow<Result<UnifiedPlaylist>>
    fun loadMoreTracks(trackIds: List<TrackId>): Flow<Result<List<UnifiedTrack>>>
    fun loadAllTracks(detail: UnifiedPlaylist): Flow<Result<List<UnifiedTrack>>>
    fun createPlaylist(platform: MusicPlatform, name: String, isPrivate: Boolean = false): Flow<Result<UnifiedPlaylist>>
    fun subscribePlaylist(playlistId: PlaylistId, subscribe: Boolean): Flow<Result<Unit>>
    fun addTracksToPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>>
    fun removeTracksFromPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>>
    fun renamePlaylist(playlistId: PlaylistId, name: String): Flow<Result<Unit>>
    fun updateDescription(playlistId: PlaylistId, description: String): Flow<Result<Unit>>
    fun deletePlaylist(playlistId: PlaylistId): Flow<Result<Unit>>
    fun updatePlaylistCover(playlistId: PlaylistId, imageBytes: ByteArray): Flow<Result<String>>
    fun sortPlaylistTracks(playlistId: PlaylistId, orderedTracks: List<UnifiedTrack>, listVer: Long = 0L): Flow<Result<Unit>>
}

class NeteasePlatformPlaylistRepository(
    private val playlistRepository: PlaylistRepository,
    private val createRepository: CreateRepository,
    private val playerRepository: PlayerRepository,
    private val coverUploader: (suspend (playlistId: Long, imageBytes: ByteArray) -> Result<String>)? = null
) : PlatformPlaylistRepository {

    override fun getPlaylistDetail(playlistId: PlaylistId): Flow<Result<UnifiedPlaylist>> {
        val id = playlistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌单 ID: ${playlistId.canonical}")))
        return playlistRepository.getPlaylistDetail(id).map { result ->
            result.map { it.toUnified(MusicPlatform.NETEASE) }
        }
    }

    override fun loadMoreTracks(trackIds: List<TrackId>): Flow<Result<List<UnifiedTrack>>> {
        val ids = trackIds.mapNotNull { it.neteaseIdOrNull() }
        if (ids.isEmpty()) return flowOf(Result.success(emptyList()))
        return playlistRepository.loadMoreTracks(ids).map { result ->
            result.map { list -> list.map { it.toUnified() } }
        }
    }

    override fun loadAllTracks(detail: UnifiedPlaylist): Flow<Result<List<UnifiedTrack>>> {
        val legacy = detail.toLegacyNeteasePlaylistOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌单详情: ${detail.id.canonical}")))
        return playlistRepository.loadAllTracks(legacy).map { result ->
            result.map { list -> list.map { it.toUnified() } }
        }
    }

    override fun createPlaylist(
        platform: MusicPlatform,
        name: String,
        isPrivate: Boolean
    ): Flow<Result<UnifiedPlaylist>> =
        createRepository.createPlaylist(name = name, privacy = playlistPrivacyOf(isPrivate)).map { result ->
            result.map { it.toUnified(MusicPlatform.NETEASE) }
        }

    override fun subscribePlaylist(playlistId: PlaylistId, subscribe: Boolean): Flow<Result<Unit>> {
        val id = playlistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌单 ID: ${playlistId.canonical}")))
        return playlistRepository.subscribePlaylist(id, subscribe)
    }

    override fun addTracksToPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>> {
        val id = playlistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌单 ID: ${playlistId.canonical}")))
        val songIds = tracks.mapNotNull { it.id.neteaseIdOrNull() }
        if (songIds.isEmpty()) return flowOf(Result.success(Unit))
        return playlistRepository.manipulatePlaylistTracks("add", id, songIds)
    }

    override fun removeTracksFromPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>> {
        val id = playlistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌单 ID: ${playlistId.canonical}")))
        val songIds = tracks.mapNotNull { it.id.neteaseIdOrNull() }
        if (songIds.isEmpty()) return flowOf(Result.success(Unit))
        return playlistRepository.manipulatePlaylistTracks("del", id, songIds)
    }

    override fun renamePlaylist(playlistId: PlaylistId, name: String): Flow<Result<Unit>> {
        val id = playlistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌单 ID: ${playlistId.canonical}")))
        return playlistRepository.renamePlaylist(id, name)
    }

    override fun updateDescription(playlistId: PlaylistId, description: String): Flow<Result<Unit>> {
        val id = playlistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌单 ID: ${playlistId.canonical}")))
        return playlistRepository.updateDescription(id, description)
    }

    override fun deletePlaylist(playlistId: PlaylistId): Flow<Result<Unit>> {
        val id = playlistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌单 ID: ${playlistId.canonical}")))
        return playlistRepository.deletePlaylist(id)
    }

    override fun updatePlaylistCover(playlistId: PlaylistId, imageBytes: ByteArray): Flow<Result<String>> = flow {
        val id = playlistId.neteaseIdOrNull()
        if (id == null) {
            emit(Result.failure(IllegalArgumentException("无效网易云歌单 ID: ${playlistId.canonical}")))
            return@flow
        }
        val uploader = coverUploader
        if (uploader == null) {
            emit(Result.failure(UnsupportedOperationException("未注入网易云封面上传器")))
            return@flow
        }
        emit(uploader(id, imageBytes))
    }

    override fun sortPlaylistTracks(
        playlistId: PlaylistId,
        orderedTracks: List<UnifiedTrack>,
        listVer: Long
    ): Flow<Result<Unit>> =
        flowOf(Result.failure(UnsupportedOperationException("网易云歌单曲目排序未在共享仓接入")))
}

internal sealed interface KugouPlaylistTarget {
    data class CloudList(val listId: Long) : KugouPlaylistTarget
    data class PublicSpecial(val specialId: Long) : KugouPlaylistTarget
    data class PublicCollection(val globalCollectionId: String) : KugouPlaylistTarget
    data class BareNumeric(val numericId: Long) : KugouPlaylistTarget
    data class Unknown(val rawId: String) : KugouPlaylistTarget
}

internal fun parseKugouPlaylistTarget(rawId: String): KugouPlaylistTarget {
    val trimmed = rawId.trim()
    if (trimmed.isEmpty()) return KugouPlaylistTarget.Unknown(trimmed)
    if (trimmed.startsWith("list:", ignoreCase = true)) {
        val id = trimmed.substring(5).trim().toLongOrNull()
        return if (id != null && id > 0L) KugouPlaylistTarget.CloudList(id) else KugouPlaylistTarget.Unknown(trimmed)
    }
    if (trimmed.startsWith("special:", ignoreCase = true)) {
        val id = trimmed.substring(8).trim().toLongOrNull()
        return if (id != null && id > 0L) KugouPlaylistTarget.PublicSpecial(id) else KugouPlaylistTarget.Unknown(trimmed)
    }
    if (trimmed.startsWith("collection:", ignoreCase = true)) {
        val gid = trimmed.substring(11).trim()
        return if (gid.isNotEmpty()) KugouPlaylistTarget.PublicCollection(gid) else KugouPlaylistTarget.Unknown(trimmed)
    }
    val numeric = trimmed.toLongOrNull()
    if (numeric != null) {
        return if (numeric > 0L) KugouPlaylistTarget.BareNumeric(numeric) else KugouPlaylistTarget.Unknown(trimmed)
    }
    return KugouPlaylistTarget.PublicCollection(trimmed)
}

// 酷狗双路径歌单仓储：显式 list:/已校验云歌单 listid 走云歌单 v3（保留 fileid）；collection:/gid 走公开歌单接口；special: 缺失 gid 时明确拦截
class KugouPlatformPlaylistRepository(
    private val playlistService: KugouPlaylistService,
    private val isEnabled: suspend () -> Boolean = { true }
) : PlatformPlaylistRepository {

    override fun getPlaylistDetail(playlistId: PlaylistId): Flow<Result<UnifiedPlaylist>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        when (val target = parseKugouPlaylistTarget(playlistId.rawId)) {
            is KugouPlaylistTarget.PublicSpecial -> {
                emit(
                    Result.failure(
                        IllegalStateException("该酷狗公开歌单缺少 global_collection_id（specialid=${target.specialId}），暂不支持查看详情")
                    )
                )
            }
            is KugouPlaylistTarget.Unknown -> {
                emit(Result.failure(IllegalArgumentException("无效酷狗歌单 ID: ${playlistId.canonical}")))
            }
            is KugouPlaylistTarget.CloudList -> {
                val meta = findUserPlaylistResultByListId(target.listId).getOrNull()
                emit(loadCloudPlaylistDetail(playlistId, target.listId, meta))
            }
            is KugouPlaylistTarget.BareNumeric -> {
                val metaRes = findUserPlaylistResultByListId(target.numericId)
                val meta = metaRes.getOrElse {
                    emit(Result.failure(it))
                    return@flow
                }
                if (meta == null) {
                    emit(
                        Result.failure(
                            IllegalArgumentException("未找到对应的酷狗云歌单（id=${target.numericId}），公开歌单请提供 global_collection_id")
                        )
                    )
                    return@flow
                }
                emit(loadCloudPlaylistDetail(playlistId, target.numericId, meta))
            }
            is KugouPlaylistTarget.PublicCollection -> {
                val gid = target.globalCollectionId
                val detail = playlistService.getPublicPlaylistDetail(gid).getOrElse {
                    emit(Result.failure(it))
                    return@flow
                }
                val tracksPage = playlistService.getPublicPlaylistTracks(gid, page = 1, pageSize = PUBLIC_TRACK_PAGE_SIZE)
                    .getOrElse {
                        emit(Result.failure(it))
                        return@flow
                    }
                val unifiedTracks = tracksPage.tracks.map { it.toUnifiedTrack() }
                val baseDetail = detail.toUnifiedPlaylist(unifiedTracks)
                val resolvedId = if (playlistId.rawId.trim().startsWith("collection:", ignoreCase = true)) {
                    playlistId
                } else {
                    baseDetail.id
                }
                emit(
                    Result.success(
                        baseDetail.copy(
                            id = resolvedId,
                            listVer = maxOf(detail.listVer, tracksPage.listVer)
                        )
                    )
                )
            }
        }
    }

    private suspend fun loadCloudPlaylistDetail(
        requestedId: PlaylistId,
        listId: Long,
        meta: KugouUserPlaylistDto?
    ): Result<UnifiedPlaylist> {
        val tracksRes = playlistService.getCloudPlaylistTracks(
            listId = listId,
            type = meta?.type ?: 0,
            page = 1,
            pageSize = CLOUD_TRACK_PAGE_SIZE
        )
        return tracksRes.map { page ->
            val unifiedTracks = page.tracks.map { it.toUnifiedTrack() }
            UnifiedPlaylist(
                id = requestedId,
                name = meta?.name.orEmpty(),
                coverUrl = meta?.coverUrl.orEmpty(),
                description = meta?.intro?.takeIf { it.isNotBlank() },
                subscribed = meta?.isCollected ?: false,
                creator = meta?.let {
                    if (it.createUserId > 0L || it.createUsername.isNotBlank()) {
                        UnifiedCreator(
                            id = it.createUserId.takeIf { uid -> uid > 0L }?.let { uid -> AccountId.kugou(uid) },
                            nickname = it.createUsername
                        )
                    } else {
                        null
                    }
                },
                tracks = unifiedTracks,
                trackCount = meta?.trackCount?.takeIf { it > 0 } ?: page.totalCount,
                trackIds = unifiedTracks.map { it.id },
                isPublic = !(meta?.isPrivate ?: false),
                tags = meta?.tags?.split(",", "，")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                listVer = maxOf(meta?.listVer ?: 0L, page.listVer)
            )
        }
    }

    override fun loadMoreTracks(trackIds: List<TrackId>): Flow<Result<List<UnifiedTrack>>> =
        flowOf(Result.success(emptyList()))

    override fun loadAllTracks(detail: UnifiedPlaylist): Flow<Result<List<UnifiedTrack>>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val target = parseKugouPlaylistTarget(detail.id.rawId)
        val cloudMeta: KugouUserPlaylistDto?
        val isCloudPath: Boolean
        val cloudListId: Long
        val publicGid: String
        when (target) {
            is KugouPlaylistTarget.PublicSpecial -> {
                emit(
                    Result.failure(
                        IllegalStateException("该酷狗公开歌单缺少 global_collection_id（specialid=${target.specialId}），暂不支持加载全部曲目")
                    )
                )
                return@flow
            }
            is KugouPlaylistTarget.Unknown -> {
                emit(Result.failure(IllegalArgumentException("无效酷狗歌单 ID: ${detail.id.canonical}")))
                return@flow
            }
            is KugouPlaylistTarget.CloudList -> {
                isCloudPath = true
                cloudListId = target.listId
                publicGid = ""
                cloudMeta = findUserPlaylistResultByListId(target.listId).getOrNull()
            }
            is KugouPlaylistTarget.BareNumeric -> {
                val meta = findUserPlaylistResultByListId(target.numericId).getOrElse {
                    emit(Result.failure(it))
                    return@flow
                }
                if (meta == null) {
                    emit(
                        Result.failure(
                            IllegalArgumentException("未找到对应的酷狗云歌单（id=${target.numericId}），公开歌单请提供 global_collection_id")
                        )
                    )
                    return@flow
                }
                isCloudPath = true
                cloudListId = target.numericId
                publicGid = ""
                cloudMeta = meta
            }
            is KugouPlaylistTarget.PublicCollection -> {
                isCloudPath = false
                cloudListId = 0L
                publicGid = target.globalCollectionId
                cloudMeta = null
            }
        }
        val all = ArrayList<UnifiedTrack>()
        var page = 1
        while (page <= MAX_TRACK_PAGES) {
            val res = if (isCloudPath) {
                playlistService.getCloudPlaylistTracks(
                    listId = cloudListId,
                    type = cloudMeta?.type ?: 0,
                    page = page,
                    pageSize = CLOUD_TRACK_PAGE_SIZE
                )
            } else {
                playlistService.getPublicPlaylistTracks(publicGid, page = page, pageSize = PUBLIC_TRACK_PAGE_SIZE)
            }
            val pageData = res.getOrElse {
                if (all.isEmpty()) emit(Result.failure(it)) else emit(Result.success(all))
                return@flow
            }
            all.addAll(pageData.tracks.map { it.toUnifiedTrack() })
            if (!pageData.hasMore || pageData.tracks.isEmpty()) break
            page++
        }
        emit(Result.success(all))
    }

    override fun createPlaylist(
        platform: MusicPlatform,
        name: String,
        isPrivate: Boolean
    ): Flow<Result<UnifiedPlaylist>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        emit(
            playlistService.createPlaylist(name = name, isPrivate = isPrivate).map { created ->
                UnifiedPlaylist(
                    id = created.preferredPlaylistId(),
                    name = created.name,
                    coverUrl = created.coverUrl,
                    isPublic = !created.isPrivate,
                    listVer = created.listVer
                )
            }
        )
    }

    override fun subscribePlaylist(playlistId: PlaylistId, subscribe: Boolean): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val raw = playlistId.rawId.trim()
        if (subscribe) {
            val gid = when (val target = parseKugouPlaylistTarget(raw)) {
                is KugouPlaylistTarget.PublicCollection -> target.globalCollectionId
                is KugouPlaylistTarget.PublicSpecial -> {
                    emit(
                        Result.failure(
                            IllegalStateException("该酷狗公开歌单缺少 global_collection_id（specialid=${target.specialId}），暂不支持收藏")
                        )
                    )
                    return@flow
                }
                is KugouPlaylistTarget.CloudList -> findUserPlaylistResultByListId(target.listId).getOrNull()
                    ?.globalCollectionId?.takeIf { it.isNotBlank() }
                is KugouPlaylistTarget.BareNumeric -> findUserPlaylistResultByListId(target.numericId).getOrNull()
                    ?.globalCollectionId?.takeIf { it.isNotBlank() }
                is KugouPlaylistTarget.Unknown -> null
            }
            if (gid.isNullOrBlank()) {
                emit(Result.failure(IllegalArgumentException("收藏酷狗歌单需要 global_collection_id: $raw")))
                return@flow
            }
            val detail = playlistService.getPublicPlaylistDetail(gid).getOrElse {
                emit(Result.failure(it))
                return@flow
            }
            val res = playlistService.collectPlaylist(
                name = detail.name.ifBlank { gid },
                listCreateUserId = detail.createUserId,
                listCreateListId = detail.listId,
                listCreateGid = detail.globalCollectionId.ifBlank { gid }
            ).map { }
            emit(res)
        } else {
            val listId = resolveListId(raw)
            if (listId == null || listId <= 0L) {
                emit(Result.failure(IllegalArgumentException("未能定位可取消收藏的酷狗歌单 listid: $raw")))
                return@flow
            }
            emit(playlistService.deletePlaylist(listId))
        }
    }

    override fun addTracksToPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val listId = resolveListId(playlistId.rawId)
        if (listId == null || listId <= 0L) {
            emit(Result.failure(IllegalArgumentException("仅支持向酷狗云歌单添加曲目: ${playlistId.rawId}")))
            return@flow
        }
        val inputs = tracks.mapNotNull { track ->
            val hash = track.kugouMeta?.hash ?: track.id.rawId
            if (hash.isBlank()) return@mapNotNull null
            KugouPlaylistAddTrackInput(
                name = track.name,
                hash = hash,
                albumId = track.kugouMeta?.albumId ?: track.album?.id?.rawId,
                mixSongId = track.kugouMeta?.mixSongId
            )
        }
        if (inputs.isEmpty()) {
            emit(Result.success(Unit))
            return@flow
        }
        emit(playlistService.addTracksToPlaylist(listId, inputs).map { })
    }

    override fun removeTracksFromPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val listId = resolveListId(playlistId.rawId)
        if (listId == null || listId <= 0L) {
            emit(Result.failure(IllegalArgumentException("仅支持从酷狗云歌单删除曲目: ${playlistId.rawId}")))
            return@flow
        }
        val directFileIds = tracks.mapNotNull { it.kugouMeta?.playlistFileId?.takeIf { fid -> fid > 0L } }
        val resolvedFileIds = if (directFileIds.size == tracks.size) {
            directFileIds
        } else {
            val missingHashes = tracks.mapNotNull { t ->
                if ((t.kugouMeta?.playlistFileId ?: 0L) <= 0L) (t.kugouMeta?.hash ?: t.id.rawId).lowercase() else null
            }.toSet()
            val meta = findUserPlaylistResultByListId(listId).getOrNull()
            val cloudPages = fetchAllCloudPlaylistPages(listId, type = meta?.type ?: 0).getOrElse {
                emit(Result.failure(it))
                return@flow
            }
            val matched = cloudPages.flatMap { it.tracks }
                .filter { it.hash.lowercase() in missingHashes && it.fileId > 0L }
                .map { it.fileId }
            (directFileIds + matched).distinct()
        }
        if (resolvedFileIds.isEmpty()) {
            emit(Result.success(Unit))
            return@flow
        }
        emit(playlistService.removeTracksFromPlaylist(listId, resolvedFileIds))
    }

    override fun renamePlaylist(playlistId: PlaylistId, name: String): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val listId = resolveListId(playlistId.rawId)
        if (listId == null || listId <= 0L) {
            emit(Result.failure(IllegalArgumentException("无效酷狗云歌单 listid: ${playlistId.rawId}")))
            return@flow
        }
        val meta = findUserPlaylistByListId(listId)
        emit(
            playlistService.updatePlaylist(
                listId = listId,
                totalVer = meta?.totalVer ?: 0L,
                type = meta?.type ?: 0,
                name = name,
                intro = meta?.intro.orEmpty(),
                tags = meta?.tags.orEmpty(),
                sort = meta?.sort ?: 0
            )
        )
    }

    override fun updateDescription(playlistId: PlaylistId, description: String): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val listId = resolveListId(playlistId.rawId)
        if (listId == null || listId <= 0L) {
            emit(Result.failure(IllegalArgumentException("无效酷狗云歌单 listid: ${playlistId.rawId}")))
            return@flow
        }
        val meta = findUserPlaylistByListId(listId)
        emit(
            playlistService.updatePlaylist(
                listId = listId,
                totalVer = meta?.totalVer ?: 0L,
                type = meta?.type ?: 0,
                intro = description,
                tags = meta?.tags.orEmpty(),
                sort = meta?.sort ?: 0
            )
        )
    }

    override fun deletePlaylist(playlistId: PlaylistId): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val listId = resolveListId(playlistId.rawId)
        if (listId == null || listId <= 0L) {
            emit(Result.failure(IllegalArgumentException("无效酷狗云歌单 listid: ${playlistId.rawId}")))
            return@flow
        }
        emit(playlistService.deletePlaylist(listId))
    }

    override fun updatePlaylistCover(playlistId: PlaylistId, imageBytes: ByteArray): Flow<Result<String>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val listId = resolveListId(playlistId.rawId)
        if (listId == null || listId <= 0L) {
            emit(Result.failure(IllegalArgumentException("无效酷狗云歌单 listid: ${playlistId.rawId}")))
            return@flow
        }
        val uploadResult = playlistService.uploadPlaylistCover(imageBytes).getOrElse {
            emit(Result.failure(it))
            return@flow
        }
        val meta = findUserPlaylistByListId(listId)
        val modifyRes = playlistService.updatePlaylist(
            listId = listId,
            totalVer = meta?.totalVer ?: 0L,
            type = meta?.type ?: 0,
            intro = meta?.intro.orEmpty(),
            tags = meta?.tags.orEmpty(),
            sort = meta?.sort ?: 0,
            pic = uploadResult.picValue
        )
        emit(modifyRes.map { uploadResult.picValue })
    }

    override fun sortPlaylistTracks(
        playlistId: PlaylistId,
        orderedTracks: List<UnifiedTrack>,
        listVer: Long
    ): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        if (orderedTracks.isEmpty()) {
            emit(Result.failure(IllegalArgumentException("排序曲目列表不能为空")))
            return@flow
        }
        val listId = resolveListId(playlistId.rawId)
        if (listId == null || listId <= 0L) {
            emit(Result.failure(IllegalArgumentException("无效酷狗云歌单 listid: ${playlistId.rawId}")))
            return@flow
        }
        val meta = findUserPlaylistResultByListId(listId).getOrNull()
        val needsRefresh = orderedTracks.any { (it.kugouMeta?.playlistFileId ?: 0L) <= 0L }
        val hashToFileId = LinkedHashMap<String, Long>()
        var refreshedListVer = 0L
        if (needsRefresh) {
            val pages = fetchAllCloudPlaylistPages(listId, type = meta?.type ?: 0).getOrElse {
                emit(Result.failure(it))
                return@flow
            }
            pages.forEach { page ->
                if (page.listVer > refreshedListVer) refreshedListVer = page.listVer
                page.tracks.forEach { dto ->
                    val h = dto.hash.trim().lowercase()
                    if (h.isNotEmpty() && dto.fileId > 0L) {
                        hashToFileId.putIfAbsent(h, dto.fileId)
                    }
                }
            }
        }
        val sortItems = ArrayList<KugouPlaylistTrackSortInput>(orderedTracks.size)
        for ((index, track) in orderedTracks.withIndex()) {
            val hash = (track.kugouMeta?.hash ?: track.id.rawId).trim().lowercase()
            val fileId = track.kugouMeta?.playlistFileId?.takeIf { it > 0L }
                ?: hashToFileId[hash]?.takeIf { it > 0L }
            if (fileId == null || fileId <= 0L) {
                emit(Result.failure(IllegalArgumentException("酷狗曲目缺少 fileid（${track.name}），无法调整排序")))
                return@flow
            }
            sortItems += KugouPlaylistTrackSortInput(fileId = fileId, sort = index + 1)
        }
        val effectiveListVer = listVer.takeIf { it > 0L }
            ?: refreshedListVer.takeIf { it > 0L }
            ?: meta?.listVer
            ?: 0L
        emit(
            playlistService.sortPlaylistTracks(
                listId = listId,
                listVer = effectiveListVer,
                items = sortItems,
                type = meta?.type ?: 0
            )
        )
    }

    private suspend fun fetchAllCloudPlaylistPages(listId: Long, type: Int): Result<List<KugouPlaylistTracksPage>> {
        val pages = ArrayList<KugouPlaylistTracksPage>()
        var page = 1
        while (page <= MAX_TRACK_PAGES) {
            val pageData = playlistService.getCloudPlaylistTracks(
                listId = listId,
                type = type,
                page = page,
                pageSize = CLOUD_TRACK_PAGE_SIZE
            ).getOrElse { err ->
                return if (pages.isEmpty()) Result.failure(err) else Result.success(pages)
            }
            pages += pageData
            if (!pageData.hasMore || pageData.tracks.isEmpty()) break
            page++
        }
        return Result.success(pages)
    }

    private suspend fun findUserPlaylistByListId(listId: Long): KugouUserPlaylistDto? =
        findUserPlaylistResultByListId(listId).getOrNull()

    private suspend fun findUserPlaylistResultByListId(listId: Long): Result<KugouUserPlaylistDto?> {
        var page = 1
        while (page <= MAX_USER_PLAYLIST_PAGES) {
            val pageData = playlistService.getUserPlaylists(page = page, pageSize = USER_PLAYLIST_PAGE_SIZE)
                .getOrElse { return Result.failure(it) }
            pageData.playlists.firstOrNull { it.listId == listId }?.let { return Result.success(it) }
            if (!pageData.hasMore || pageData.playlists.isEmpty()) break
            page++
        }
        return Result.success(null)
    }

    private suspend fun resolveListId(rawId: String): Long? = when (val target = parseKugouPlaylistTarget(rawId)) {
        is KugouPlaylistTarget.CloudList -> target.listId
        is KugouPlaylistTarget.BareNumeric -> target.numericId
        is KugouPlaylistTarget.PublicCollection -> {
            var matched: Long? = null
            var page = 1
            while (page <= MAX_USER_PLAYLIST_PAGES) {
                val pageData = playlistService.getUserPlaylists(page = page, pageSize = USER_PLAYLIST_PAGE_SIZE)
                    .getOrNull() ?: break
                val hit = pageData.playlists.firstOrNull {
                    it.globalCollectionId.equals(target.globalCollectionId, ignoreCase = true) ||
                        it.createGid.equals(target.globalCollectionId, ignoreCase = true)
                }
                if (hit != null) {
                    matched = hit.listId
                    break
                }
                if (!pageData.hasMore || pageData.playlists.isEmpty()) break
                page++
            }
            matched
        }
        is KugouPlaylistTarget.PublicSpecial,
        is KugouPlaylistTarget.Unknown -> null
    }

    private companion object {
        private const val CLOUD_TRACK_PAGE_SIZE = 300
        private const val PUBLIC_TRACK_PAGE_SIZE = 100
        private const val USER_PLAYLIST_PAGE_SIZE = 100
        private const val MAX_TRACK_PAGES = 30
        private const val MAX_USER_PLAYLIST_PAGES = 20
    }
}

// 跨平台歌单路由：按 PlaylistId.platform 分发云端主链，并对异源混排曲目自动读写 PlaylistExternalTrackStore
class PlatformPlaylistRouter(
    private val netease: PlatformPlaylistRepository,
    private val kugou: PlatformPlaylistRepository,
    private val externalStore: PlaylistExternalTrackStore,
    private val isKugouEnabled: suspend () -> Boolean = { true }
) : PlatformPlaylistRepository {

    override fun getPlaylistDetail(playlistId: PlaylistId): Flow<Result<UnifiedPlaylist>> = flow {
        val baseRes = delegateRead(playlistId.platform) { it.getPlaylistDetail(playlistId) }.first()
        val detail = baseRes.getOrElse {
            emit(Result.failure(it))
            return@flow
        }
        val localEntries = externalStore.entriesOf(playlistId).first()
        if (localEntries.isEmpty()) {
            emit(Result.success(detail))
        } else {
            val fullyLoaded = detail.trackIds.isEmpty() || detail.tracks.size >= detail.trackIds.size
            val merged = mergeUnifiedPlaylistTracks(
                baseTracks = detail.tracks,
                entries = localEntries,
                fullyLoaded = fullyLoaded
            )
            emit(
                Result.success(
                    detail.copy(
                        tracks = merged,
                        trackCount = maxOf(detail.trackCount, merged.size)
                    )
                )
            )
        }
    }

    override fun loadMoreTracks(trackIds: List<TrackId>): Flow<Result<List<UnifiedTrack>>> {
        if (trackIds.isEmpty()) return flowOf(Result.success(emptyList()))
        val firstPlatform = trackIds.first().platform
        return delegateRead(firstPlatform) { it.loadMoreTracks(trackIds) }
    }

    override fun loadAllTracks(detail: UnifiedPlaylist): Flow<Result<List<UnifiedTrack>>> = flow {
        val baseRes = delegateRead(detail.platform) { it.loadAllTracks(detail) }.first()
        val baseTracks = baseRes.getOrElse {
            emit(Result.failure(it))
            return@flow
        }
        val localEntries = externalStore.entriesOf(detail.id).first()
        if (localEntries.isEmpty()) {
            emit(Result.success(baseTracks))
        } else {
            emit(Result.success(mergeUnifiedPlaylistTracks(baseTracks = baseTracks, entries = localEntries, fullyLoaded = true)))
        }
    }

    override fun createPlaylist(
        platform: MusicPlatform,
        name: String,
        isPrivate: Boolean
    ): Flow<Result<UnifiedPlaylist>> = delegateRead(platform) { it.createPlaylist(platform, name, isPrivate) }

    override fun subscribePlaylist(playlistId: PlaylistId, subscribe: Boolean): Flow<Result<Unit>> =
        delegateRead(playlistId.platform) { it.subscribePlaylist(playlistId, subscribe) }

    // 同平台曲目走对应平台云端加歌接口，异源曲目（如酷狗->网易、网易->酷狗、QQ/酷我->任意）持久化至混排存储
    override fun addTracksToPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>> = flow {
        if (tracks.isEmpty()) {
            emit(Result.success(Unit))
            return@flow
        }
        if (playlistId.platform == MusicPlatform.KUGOU && !isKugouEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }

        val targetPlatform = playlistId.platform
        val (samePlatform, crossPlatform) = tracks.partition { it.platform == targetPlatform }

        if (crossPlatform.isNotEmpty()) {
            externalStore.add(playlistId, crossPlatform)
        }
        if (samePlatform.isNotEmpty()) {
            val remoteRes = when (targetPlatform) {
                MusicPlatform.NETEASE -> netease.addTracksToPlaylist(playlistId, samePlatform).first()
                MusicPlatform.KUGOU -> kugou.addTracksToPlaylist(playlistId, samePlatform).first()
                else -> {
                    externalStore.add(playlistId, samePlatform)
                    Result.success(Unit)
                }
            }
            emit(remoteRes)
        } else {
            emit(Result.success(Unit))
        }
    }

    override fun removeTracksFromPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>> = flow {
        if (tracks.isEmpty()) {
            emit(Result.success(Unit))
            return@flow
        }
        if (playlistId.platform == MusicPlatform.KUGOU && !isKugouEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }

        val targetPlatform = playlistId.platform
        val (samePlatform, crossPlatform) = tracks.partition { it.platform == targetPlatform }
        val localStoredTrackIds = HashSet<TrackId>()
        for (track in tracks) {
            if (externalStore.contains(playlistId, track.id)) {
                localStoredTrackIds.add(track.id)
            }
        }
        val toRemoveLocal = (crossPlatform.map { it.id } + localStoredTrackIds).toSet()
        if (toRemoveLocal.isNotEmpty()) {
            externalStore.remove(playlistId, toRemoveLocal)
        }

        val toRemoveRemote = samePlatform.filterNot { it.id in localStoredTrackIds }
        if (toRemoveRemote.isNotEmpty()) {
            val remoteRes = when (targetPlatform) {
                MusicPlatform.NETEASE -> netease.removeTracksFromPlaylist(playlistId, toRemoveRemote).first()
                MusicPlatform.KUGOU -> kugou.removeTracksFromPlaylist(playlistId, toRemoveRemote).first()
                else -> Result.success(Unit)
            }
            emit(remoteRes)
        } else {
            emit(Result.success(Unit))
        }
    }

    override fun renamePlaylist(playlistId: PlaylistId, name: String): Flow<Result<Unit>> =
        delegateRead(playlistId.platform) { it.renamePlaylist(playlistId, name) }

    override fun updateDescription(playlistId: PlaylistId, description: String): Flow<Result<Unit>> =
        delegateRead(playlistId.platform) { it.updateDescription(playlistId, description) }

    override fun deletePlaylist(playlistId: PlaylistId): Flow<Result<Unit>> = flow {
        val remoteRes = delegateRead(playlistId.platform) { it.deletePlaylist(playlistId) }.first()
        if (remoteRes.isSuccess) {
            externalStore.clearPlaylist(playlistId)
        }
        emit(remoteRes)
    }

    override fun updatePlaylistCover(playlistId: PlaylistId, imageBytes: ByteArray): Flow<Result<String>> =
        delegateRead(playlistId.platform) { it.updatePlaylistCover(playlistId, imageBytes) }

    override fun sortPlaylistTracks(
        playlistId: PlaylistId,
        orderedTracks: List<UnifiedTrack>,
        listVer: Long
    ): Flow<Result<Unit>> =
        delegateRead(playlistId.platform) { it.sortPlaylistTracks(playlistId, orderedTracks, listVer) }

    private fun <T> delegateRead(
        platform: MusicPlatform,
        call: (PlatformPlaylistRepository) -> Flow<Result<T>>
    ): Flow<Result<T>> = when (platform) {
        MusicPlatform.NETEASE -> call(netease)
        MusicPlatform.KUGOU -> flow {
            if (!isKugouEnabled()) {
                emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            } else {
                emit(call(kugou).first())
            }
        }
        else -> flowOf(Result.failure(UnsupportedOperationException("暂不支持该平台歌单操作: ${platform.key}")))
    }
}
