package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.feature.cloud.data.CloudUploadApi
import com.lin0721.linmusic.feature.cloud.data.NosTokenRequest
import com.lin0721.linmusic.feature.cloud.upload.NosUploadClient
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.first
import java.io.ByteArrayInputStream

private const val COVER_BUCKET = "yyimgs"

// 更新歌单封面：复用 core 的 NOS 直传链路（云盘上传歌曲同一套基础设施），
// 裁剪后的图片字节先直传拿 docId，再登记到歌单。
// 图片场景的请求字段严格对齐参考实现：不带 md5、不发 Content-MD5，二者都是音频查重专用，
// 带上会被服务端判为非法请求
class UpdatePlaylistCoverUseCase(
    private val cloudUploadApi: CloudUploadApi,
    private val nosUploadClient: NosUploadClient,
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, imageBytes: ByteArray): Result<String> = runCatching {
        val tokenResponse = cloudUploadApi.allocNosToken(
            NosTokenRequest(
                bucket = COVER_BUCKET,
                ext = "jpg",
                filename = "cover.jpg",
                nosProduct = 0,
                type = "other",
                returnBody = "{\"code\":200,\"size\":\"\$(ObjectSize)\"}"
            )
        )
        val tokenResult = tokenResponse.result
        if (!tokenResponse.isSuccess || tokenResult == null) {
            error("获取封面上传凭证失败")
        }

        val uploadHost = nosUploadClient.fetchUploadHosts(COVER_BUCKET).firstOrNull()
            ?: error("未获取到可用的封面上传地址")

        nosUploadClient.uploadBytes(
            host = uploadHost,
            bucket = COVER_BUCKET,
            objectKey = tokenResult.objectKey,
            token = tokenResult.token,
            md5 = null,
            mimeType = "image/jpeg",
            contentLength = imageBytes.size.toLong(),
            openStream = { ByteArrayInputStream(imageBytes) },
            onProgress = {}
        )

        // 图片场景的图片 ID 只认 docId；resourceId 是音频资源的 ID，语义不同，
        // 拿它去登记会绑错资源，取不到宁可直接失败
        val coverImgId = tokenResult.docId
        if (coverImgId <= 0) {
            error("封面上传成功但未获取到图片 ID")
        }

        // 登记接口会下发带扩展名的最终地址，直接用它，不要拿 objectKey 自行拼
        playlistRepository.updatePlaylistCover(playlistId, coverImgId).first().getOrThrow()
    }
}
