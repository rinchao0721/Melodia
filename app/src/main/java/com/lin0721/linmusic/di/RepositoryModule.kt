package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.AuthRepository
import com.lin0721.linmusic.core.auth.AuthRepositoryImpl
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.feature.create.data.CreateRepository
import com.lin0721.linmusic.feature.create.data.CreateRepositoryImpl
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.data.PodcastRepositoryImpl
import com.lin0721.linmusic.feature.music.data.MusicRepository
import com.lin0721.linmusic.feature.music.data.MusicRepositoryImpl
import com.lin0721.linmusic.feature.home.data.HomeRepository
import com.lin0721.linmusic.feature.home.data.HomeRepositoryImpl
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.feature.artist.data.ArtistRepositoryImpl
import com.lin0721.linmusic.feature.profile.data.ProfileRepository
import com.lin0721.linmusic.feature.profile.data.ProfileRepositoryImpl
import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.core.comment.data.CommentRepositoryImpl
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.PlaybackRepositoryImpl
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.songlike.SongLikeRepositoryImpl
import com.lin0721.linmusic.core.userartist.UserArtistRepository
import com.lin0721.linmusic.core.userartist.UserArtistRepositoryImpl
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepositoryImpl
import com.lin0721.linmusic.feature.library.data.LibraryRepository
import com.lin0721.linmusic.feature.library.data.LibraryRepositoryImpl
import com.lin0721.linmusic.feature.listendata.data.ListenDataRepository
import com.lin0721.linmusic.feature.listendata.data.ListenDataRepositoryImpl
import com.lin0721.linmusic.feature.newworks.data.NewWorksRepository
import com.lin0721.linmusic.feature.newworks.data.NewWorksRepositoryImpl
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import com.lin0721.linmusic.feature.player.data.PlayerRepositoryImpl
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.CreatePlaylistAndAddSongUseCase
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.playlist.domain.UpdatePlaylistCoverUseCase
import com.lin0721.linmusic.feature.settings.data.SettingsRepository
import com.lin0721.linmusic.feature.settings.data.SettingsRepositoryImpl
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepositoryImpl
import com.lin0721.linmusic.feature.recent.data.RecentRepository
import com.lin0721.linmusic.feature.recent.data.RecentRepositoryImpl
import com.lin0721.linmusic.feature.cloud.data.CloudRepository
import com.lin0721.linmusic.feature.cloud.data.CloudRepositoryImpl
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.data.SearchRepositoryImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin 数据仓库层依赖注入模块
 */
val repositoryModule = module {

    // 登录态与账号信息（core/auth，跨业务域共享）
    singleOf(::AuthRepositoryImpl) { bind<AuthRepository>() }

    // 首页数据仓储（feature/home）
    singleOf(::HomeRepositoryImpl) { bind<HomeRepository>() }
    singleOf(::MusicRepositoryImpl) { bind<MusicRepository>() }
    singleOf(::PodcastRepositoryImpl) { bind<PodcastRepository>() }

    // 搜索数据仓储（feature/search）
    singleOf(::SearchRepositoryImpl) { bind<SearchRepository>() }

    // 音乐库数据仓储（feature/library）
    singleOf(::LibraryRepositoryImpl) { bind<LibraryRepository>() }

    // 最近播放数据仓储（feature/recent，首页区块与侧边栏二级页共用）
    singleOf(::RecentRepositoryImpl) { bind<RecentRepository>() }

    // 我的云盘数据仓储（feature/cloud）
    singleOf(::CloudRepositoryImpl) { bind<CloudRepository>() }

    // 听歌数据仓储（feature/listendata）
    singleOf(::ListenDataRepositoryImpl) { bind<ListenDataRepository>() }

    // 关注歌手新作数据仓储（feature/newworks，首页音乐 tab「最新」二级药丸消费）
    singleOf(::NewWorksRepositoryImpl) { bind<NewWorksRepository>() }

    // 歌手数据仓储（feature/artist）
    singleOf(::ArtistRepositoryImpl) { bind<ArtistRepository>() }

    // 评论数据仓储（core/comment 共享能力）
    singleOf(::CommentRepositoryImpl) { bind<CommentRepository>() }

    // 歌曲红心数据仓储（core/songlike 共享能力）
    singleOf(::SongLikeRepositoryImpl) { bind<SongLikeRepository>() }

    // 当前用户歌单列表仓储（core/userplaylist 共享能力）
    singleOf(::UserPlaylistRepositoryImpl) { bind<UserPlaylistRepository>() }

    // 关注歌手列表仓储（core/userartist 共享能力）
    singleOf(::UserArtistRepositoryImpl) { bind<UserArtistRepository>() }

    // 歌单/专辑数据仓储（feature/playlist）
    singleOf(::PlaylistRepositoryImpl) { bind<PlaylistRepository>() }

    // 播放引擎数据仓储（core/player 共享能力：播放链接/歌词/相似与智能推荐）
    singleOf(::PlaybackRepositoryImpl) { bind<PlaybackRepository>() }

    // 播放器详情页数据仓储（feature/player：歌曲详情/百科）
    singleOf(::PlayerRepositoryImpl) { bind<PlayerRepository>() }

    // 个人信息/等级/签到数据仓储（feature/settings）
    singleOf(::SettingsRepositoryImpl) { bind<SettingsRepository>() }

    // 新建歌单数据仓储（feature/create）
    singleOf(::CreateRepositoryImpl) { bind<CreateRepository>() }

    // 新建歌单并加入当前歌曲（跨 artist/library/playlist 域共用）
    singleOf(::CreatePlaylistAndAddSongUseCase)

    // 更换歌单封面（复用 feature/cloud 的 NOS 直传基础设施）
    singleOf(::UpdatePlaylistCoverUseCase)

    // “收藏到歌单”弹窗状态与操作，artist/playlist 各持有独立实例
    factoryOf(::SongCollectDelegate)

    // 登录成功后同步账号资料（跨 home/library/artist/playlist 域共用）
    singleOf(::SyncProfileAfterLoginUseCase)

    // 拉取已红心歌曲 ID（跨 artist/player/playlist 域共用）
    singleOf(::LoadLikedSongIdsUseCase)

    // 跨模块歌单变更事件总线
    singleOf(::PlaylistMutationBus)

    // 个人主页数据仓储
    singleOf(::ProfileRepositoryImpl) { bind<ProfileRepository>() }
}
