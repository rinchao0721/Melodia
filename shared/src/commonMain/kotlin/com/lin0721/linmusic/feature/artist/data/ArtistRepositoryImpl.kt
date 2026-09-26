package com.lin0721.linmusic.feature.artist.data

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.artist.domain.ArtistAlbumPage
import com.lin0721.linmusic.feature.artist.domain.ArtistMvPage
import com.lin0721.linmusic.feature.artist.domain.ArtistSongsPage
import com.lin0721.linmusic.feature.artist.domain.MvDetail
import kotlinx.coroutines.flow.Flow

private const val TAG = "ArtistRepositoryImpl"

class ArtistRepositoryImpl(
    private val apiService: ArtistApi
) : ArtistRepository {

    override fun getArtistDetail(artistId: Long): Flow<Result<ArtistDetailInfo>> = apiFlow(
        request = { apiService.getArtistDetail(ArtistDetailRequest(id = artistId)) },
        isSuccess = { it.isSuccess && it.data?.artist != null },
        code = { it.code },
        transform = { it.data!!.artist!! }
    )

    override fun getArtistAlbums(artistId: Long, limit: Int, offset: Int): Flow<Result<ArtistAlbumPage>> = apiFlow(
        request = { apiService.getArtistAlbums(id = artistId, body = ArtistAlbumRequest(limit = limit, offset = offset)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { ArtistAlbumPage(albums = it.hotAlbums, hasMore = it.more) }
    )

    // 获取歌手粉丝数量
    override fun getArtistFansCount(artistId: Long): Flow<Result<Long>> = apiFlow(
        request = { apiService.getArtistFollowCount(ArtistFollowCountRequest(id = artistId)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { val data = it.data; data?.fansCnt ?: data?.fansCount ?: data?.fans ?: 0L }
    )

    override fun getArtistTopSongs(artistId: Long): Flow<Result<List<Track>>> = apiFlow(
        request = { apiService.getArtistTopSongs(ArtistTopSongsRequest(id = artistId)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.songs }
    )

    override fun subscribeArtist(artistId: Long, subscribe: Boolean): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.subscribeArtist(
                op = if (subscribe) "sub" else "unsub",
                body = ArtistSubscriptionRequest(
                    artistId = artistId,
                    artistIds = "[$artistId]"
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { Unit }
    )

    // 真机抓包确认：关注状态在 follow/count/get 接口的 data.isFollow 里返回，与 getArtistFansCount 复用同一接口
    override fun checkArtistFollowed(artistId: Long): Flow<Result<Boolean>> = apiFlow(
        request = { apiService.getArtistFollowCount(ArtistFollowCountRequest(id = artistId)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.data?.isFollow ?: false }
    )

    override fun getSimilarArtists(artistId: Long): Flow<Result<List<ArtistInfo>>> = apiFlow(
        request = { apiService.getSimiArtists(SimiArtistRequest(artistid = artistId)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            response.artists.map { artist ->
                ArtistInfo(
                    id = artist.id,
                    name = artist.name,
                    avatarUrl = artist.img1v1Url.ifEmpty { artist.picUrl }
                )
            }
        }
    )

    override fun getArtistMvs(artistId: Long, limit: Int, offset: Int): Flow<Result<ArtistMvPage>> = apiFlow(
        request = { apiService.getArtistMvs(ArtistMvsRequest(artistId = artistId, limit = limit, offset = offset)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { ArtistMvPage(mvs = it.mvs, hasMore = it.hasMore) }
    )

    override fun getMvUrl(mvId: Long, resolution: Int): Flow<Result<String>> = apiFlow(
        request = { apiService.getMvUrl(MvUrlRequest(id = mvId, r = resolution)) },
        isSuccess = { !it.data?.url.isNullOrBlank() },
        code = { it.code },
        transform = { it.data!!.url!! }
    )

    // 全部歌曲接口是裸 api，前缀未经真机验证：优先请求 eapi，非 200 或异常时回退 weapi，日志标注实际生效的前缀
    override fun getArtistAllSongs(artistId: Long, offset: Int, limit: Int, order: String): Flow<Result<ArtistSongsPage>> = apiFlow(
        request = {
            val body = ArtistAllSongsRequest(id = artistId, order = order, offset = offset, limit = limit)
            val eapiResult = runCatching { apiService.getArtistAllSongsEapi(body) }
            val eapiResponse = eapiResult.getOrNull()
            if (eapiResponse != null && eapiResponse.isSuccess) {
                AppLogger.d(TAG, "getArtistAllSongs [eapi] 前缀生效 artistId=$artistId offset=$offset")
                eapiResponse
            } else {
                AppLogger.w(
                    TAG,
                    "getArtistAllSongs [eapi] 前缀失败(异常=${eapiResult.exceptionOrNull()?.message}, code=${eapiResponse?.code})，回退 [weapi] artistId=$artistId"
                )
                val weapiResponse = apiService.getArtistAllSongsWeapi(body)
                AppLogger.d(TAG, "getArtistAllSongs [weapi] 前缀 code=${weapiResponse.code} artistId=$artistId")
                weapiResponse
            }
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { ArtistSongsPage(songs = it.songs, hasMore = it.more) }
    )

    override fun getMvDetail(mvId: Long): Flow<Result<MvDetail>> = apiFlow(
        request = { apiService.getMvDetail(MvDetailRequest(id = mvId)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = {
            val d = it.data!!
            MvDetail(
                id = d.id,
                name = d.name,
                artistId = d.artistId,
                artistName = d.artistName,
                cover = d.cover,
                duration = d.duration,
                playCount = d.playCount,
                subCount = d.subCount,
                commentCount = d.commentCount,
                likedCount = d.likedCount,
                isSubscribed = d.subed,
                isLiked = d.liked,
                publishTime = d.publishTime,
                briefDesc = d.briefDesc.orEmpty()
            )
        }
    )

    override fun subscribeMv(mvId: Long, subscribe: Boolean): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.subscribeMv(
                op = if (subscribe) "sub" else "unsub",
                body = MvSubscriptionRequest(mvId = mvId, mvIds = "[\"$mvId\"]")
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { Unit }
    )

    override fun likeMv(mvId: Long, like: Boolean): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.likeResource(
                op = if (like) "like" else "unlike",
                body = ResourceLikeRequest(threadId = "R_MV_5_$mvId")
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { Unit }
    )
}
