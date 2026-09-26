package com.lin0721.linmusic.feature.artist.data

import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.artist.domain.ArtistAlbumPage
import com.lin0721.linmusic.feature.artist.domain.ArtistMvPage
import com.lin0721.linmusic.feature.artist.domain.ArtistSongsPage
import com.lin0721.linmusic.feature.artist.domain.MvDetail
import kotlinx.coroutines.flow.Flow

// 歌手数据仓储（artist 业务域）
interface ArtistRepository {

    fun getArtistDetail(artistId: Long): Flow<Result<ArtistDetailInfo>>

    fun getArtistAlbums(artistId: Long, limit: Int = 10, offset: Int = 0): Flow<Result<ArtistAlbumPage>>

    // 获取艺人粉丝数（每月听众数）
    fun getArtistFansCount(artistId: Long): Flow<Result<Long>>

    // 获取艺人热门歌曲（50首）
    fun getArtistTopSongs(artistId: Long): Flow<Result<List<Track>>>

    // 收藏/关注歌手
    fun subscribeArtist(artistId: Long, subscribe: Boolean): Flow<Result<Unit>>

    // 检查是否已关注歌手
    fun checkArtistFollowed(artistId: Long): Flow<Result<Boolean>>

    fun getSimilarArtists(artistId: Long): Flow<Result<List<ArtistInfo>>>

    // 获取歌手 MV 列表（分页）
    fun getArtistMvs(artistId: Long, limit: Int = 20, offset: Int = 0): Flow<Result<ArtistMvPage>>

    // 获取 MV 播放地址
    fun getMvUrl(mvId: Long, resolution: Int = 1080): Flow<Result<String>>

    // 获取歌手全部歌曲（分页；接口前缀未经真机验证，内部按 eapi 优先/失败回退 weapi）
    fun getArtistAllSongs(artistId: Long, offset: Int = 0, limit: Int = 100, order: String = "hot"): Flow<Result<ArtistSongsPage>>

    // 获取 MV 详情（观看页信息面板用：歌手/播放量/收藏数/评论数/点赞收藏态）
    fun getMvDetail(mvId: Long): Flow<Result<MvDetail>>

    // 收藏/取消收藏 MV
    fun subscribeMv(mvId: Long, subscribe: Boolean): Flow<Result<Unit>>

    // 点赞/取消点赞 MV
    fun likeMv(mvId: Long, like: Boolean): Flow<Result<Unit>>
}
