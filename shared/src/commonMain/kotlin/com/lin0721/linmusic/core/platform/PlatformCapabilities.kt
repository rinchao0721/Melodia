package com.lin0721.linmusic.core.platform

import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.Serializable

// 声明式平台能力矩阵，用于 UI 与业务层按平台能力动态裁剪功能入口
@Serializable
data class PlatformCapabilities(
    val platform: MusicPlatform,
    val supportsSearch: Boolean = false,
    val supportsPlayback: Boolean = false,
    val supportsLogin: Boolean = false,
    val supportsQrLogin: Boolean = false,
    val supportsPhoneLogin: Boolean = false,
    val supportsPersonalFm: Boolean = false,
    val supportsDailyRecommend: Boolean = false,
    val supportsCloudDisk: Boolean = false,
    val supportsCommentRead: Boolean = false,
    val supportsCommentSend: Boolean = false,
    val supportsCommentDelete: Boolean = false,
    val supportsPrivateMessage: Boolean = false,
    val supportsPodcast: Boolean = false,
    val supportsListenTogether: Boolean = false,
    val supportsPlaylistCreateEdit: Boolean = false,
    val supportsPlaylistSort: Boolean = false,
    val supportsLikeSync: Boolean = false,
    val supportsDownload: Boolean = false,
    val supportsLyrics: Boolean = false
) {
    companion object {
        private val NETEASE = PlatformCapabilities(
            platform = MusicPlatform.NETEASE,
            supportsSearch = true,
            supportsPlayback = true,
            supportsLogin = true,
            supportsQrLogin = true,
            supportsPhoneLogin = true,
            supportsPersonalFm = true,
            supportsDailyRecommend = true,
            supportsCloudDisk = true,
            supportsCommentRead = true,
            supportsCommentSend = true,
            supportsCommentDelete = true,
            supportsPrivateMessage = true,
            supportsPodcast = true,
            supportsListenTogether = true,
            supportsPlaylistCreateEdit = true,
            supportsPlaylistSort = true,
            supportsLikeSync = true,
            supportsDownload = true,
            supportsLyrics = true
        )

        private val KUGOU = PlatformCapabilities(
            platform = MusicPlatform.KUGOU,
            supportsSearch = true,
            supportsPlayback = true,
            supportsLogin = true,
            supportsQrLogin = true,
            supportsPhoneLogin = true,
            supportsPersonalFm = true,
            supportsDailyRecommend = true,
            supportsCloudDisk = true,
            supportsCommentRead = true,
            supportsCommentSend = true,
            supportsCommentDelete = true,
            supportsPrivateMessage = false,
            supportsPodcast = false,
            supportsListenTogether = false,
            supportsPlaylistCreateEdit = true,
            supportsPlaylistSort = true,
            supportsLikeSync = true,
            supportsDownload = true,
            supportsLyrics = true
        )

        private val QQ = PlatformCapabilities(
            platform = MusicPlatform.QQ,
            supportsSearch = true,
            supportsPlayback = true,
            supportsLyrics = true,
            supportsCommentRead = true
        )

        private val KUWO = PlatformCapabilities(
            platform = MusicPlatform.KUWO,
            supportsSearch = true,
            supportsPlayback = true
        )

        private val MIGU = PlatformCapabilities(
            platform = MusicPlatform.MIGU,
            supportsPlayback = true
        )

        private val LX = PlatformCapabilities(
            platform = MusicPlatform.LX,
            supportsSearch = true,
            supportsPlayback = true,
            supportsDownload = true,
            supportsLyrics = true
        )

        private val LOCAL = PlatformCapabilities(
            platform = MusicPlatform.LOCAL,
            supportsPlayback = true,
            supportsLyrics = true
        )

        fun of(platform: MusicPlatform): PlatformCapabilities = when (platform) {
            MusicPlatform.NETEASE -> NETEASE
            MusicPlatform.KUGOU -> KUGOU
            MusicPlatform.QQ -> QQ
            MusicPlatform.KUWO -> KUWO
            MusicPlatform.MIGU -> MIGU
            MusicPlatform.LX -> LX
            MusicPlatform.LOCAL -> LOCAL
        }
    }
}
