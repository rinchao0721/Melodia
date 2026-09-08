package com.lin0721.linmusic.feature.playlist.data

import com.lin0721.linmusic.core.model.PlaylistDetail
import kotlinx.coroutines.flow.Flow

// 歌单/专辑数据仓储（playlist 业务域）
interface PlaylistRepository {

    // 获取歌单详情
    fun getPlaylistDetail(id: Long): Flow<Result<PlaylistDetail>>

    // 获取专辑详情，映射至统一领域模型 PlaylistDetail
    fun getAlbumDetail(id: Long): Flow<Result<PlaylistDetail>>

    // 收藏/取消收藏歌单
    fun subscribePlaylist(playlistId: Long, subscribe: Boolean): Flow<Result<Unit>>

    // 收藏/取消收藏专辑
    fun subscribeAlbum(albumId: Long, subscribe: Boolean): Flow<Result<Unit>>

    // 歌单歌曲添加/删除操作，支持批量传入多首歌曲 ID
    fun manipulatePlaylistTracks(op: String, playlistId: Long, trackIds: List<Long>): Flow<Result<Unit>>

    // 重命名歌单
    fun renamePlaylist(playlistId: Long, name: String): Flow<Result<Unit>>

    // 更新歌单描述
    fun updateDescription(playlistId: Long, desc: String): Flow<Result<Unit>>

    // 删除歌单
    fun deletePlaylist(playlistId: Long): Flow<Result<Unit>>

    // 登记歌单封面：coverImgId 是图片字节经 NOS 直传后拿到的资源 ID，返回服务端下发的封面地址
    fun updatePlaylistCover(playlistId: Long, coverImgId: Long): Flow<Result<String>>
}
