package com.lin0721.linmusic.core.userartist

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.network.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "UserArtistRepositoryImpl"

class UserArtistRepositoryImpl(
    private val apiService: UserArtistApi
) : UserArtistRepository {

    override fun getFavoriteArtists(): Flow<Result<List<ArtistInfo>>> = flow {
        var artists = emptyList<ArtistInfo>()

        // 尝试获取已关注歌手（实际返回: {"data":[...], "code":200}）
        try {
            val response = apiService.getArtistSublist()
            if (response.code == 200 && response.data.isNotEmpty()) {
                artists = response.data.map { dto ->
                    ArtistInfo(
                        id = dto.id,
                        name = dto.name,
                        avatarUrl = dto.img1v1Url.takeIf { it.isNotBlank() } ?: dto.picUrl
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "已关注歌手接口失败，进入热门歌手备用流程", e)
        }


        if (artists.isNotEmpty()) {
            emit(Result.success(artists))
            return@flow
        }

        // 备用：热门歌手榜单
        // 注意：emit 必须放在 try/catch 之外——.first() 等短路收集算子会在拿到首个值后
        // 向上抛内部取消信号，若 emit 处在 try 块内会被这里的 catch(Exception) 误捕获，
        // 导致再次 emit 时触发 "Flow exception transparency violated" 崩溃
        val fallbackResult = try {
            val response = apiService.getTopArtists()
            if (response.isSuccess && response.artists.isNotEmpty()) {
                artists = response.artists.map { dto ->
                    ArtistInfo(
                        id = dto.id,
                        name = dto.name,
                        avatarUrl = dto.img1v1Url.takeIf { it.isNotBlank() } ?: dto.picUrl
                    )
                }
                Result.success(artists)
            } else {
                Result.failure(AppError.BizError(response.code, null))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "热门歌手兜底也失败", e)
            Result.failure(e)
        }
        emit(fallbackResult)
    }
}
