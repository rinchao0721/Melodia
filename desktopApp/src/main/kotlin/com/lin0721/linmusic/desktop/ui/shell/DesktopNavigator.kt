package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.share.ShareCardKind
import com.lin0721.linmusic.desktop.ui.comments.CommentsHost
import com.lin0721.linmusic.feature.profile.ui.FollowListMode

// 跨页面跳转与全局提示，避免逐层透传回调
@Immutable
class DesktopNavigator(
    val isLoggedIn: Boolean,
    val openArtist: (id: Long, name: String) -> Unit,
    val openPlaylist: (id: Long, name: String) -> Unit,
    val openAlbum: (id: Long, name: String) -> Unit,
    val openRadio: (id: Long) -> Unit,
    val openProfile: (uid: Long) -> Unit,
    val openFollowList: (uid: Long, mode: FollowListMode) -> Unit,
    val openPodcastSubscribed: () -> Unit,
    val openPodcastToplist: () -> Unit,
    val openPodcastCategory: (id: Long, name: String) -> Unit,
    val openLogin: () -> Unit,
    // 下载音质跟随播放音质设置
    val downloadLevel: String,
    // 新建歌单对话框里隐私开关的初始值
    val defaultPlaylistPrivate: Boolean,
    val downloadTrack: (Track) -> Unit,
    // 指定下载音质，供需要先选音质的入口使用
    val downloadTrackAt: (Track, level: String) -> Unit,
    val showMessage: (String) -> Unit,
    val goBack: () -> Unit,
    // 在右侧栏打开（或关闭已打开的）歌单评论
    val toggleCommentsPanel: (CommentsHost) -> Unit,
    val isCommentsPanelOpen: (CommentsHost) -> Boolean,
    // 为当前播放的歌曲打开分享卡片
    val openShareCard: (ShareCardKind) -> Unit
)

val LocalDesktopNavigator = compositionLocalOf<DesktopNavigator> {
    error("DesktopNavigator 未提供")
}
