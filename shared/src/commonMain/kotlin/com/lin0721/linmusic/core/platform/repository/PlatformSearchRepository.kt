package com.lin0721.linmusic.core.platform.repository

import com.lin0721.linmusic.core.kugou.KugouSearchService
import com.lin0721.linmusic.core.kugou.KugouSearchType
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedPlaylist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.toUnified
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

enum class UnifiedSearchType {
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST,
    RADIO,
    PROGRAM,
    COMPLEX
}

sealed interface UnifiedSearchResultItem {
    val platform: MusicPlatform

    data class SongItem(val track: UnifiedTrack) : UnifiedSearchResultItem {
        override val platform: MusicPlatform get() = track.platform
    }

    data class AlbumItem(
        val album: UnifiedAlbum,
        override val platform: MusicPlatform
    ) : UnifiedSearchResultItem

    data class ArtistItem(
        val artist: UnifiedArtist,
        override val platform: MusicPlatform,
        val albumCount: Int = 0,
        val songCount: Int = 0
    ) : UnifiedSearchResultItem

    data class PlaylistItem(val playlist: UnifiedPlaylist) : UnifiedSearchResultItem {
        override val platform: MusicPlatform get() = playlist.platform
    }

    data class RadioItem(
        val radio: PodcastRadio,
        override val platform: MusicPlatform = MusicPlatform.NETEASE
    ) : UnifiedSearchResultItem

    data class ProgramItem(
        val program: PodcastProgram,
        override val platform: MusicPlatform = MusicPlatform.NETEASE
    ) : UnifiedSearchResultItem
}

data class UnifiedSearchPageResult(
    val platform: MusicPlatform,
    val items: List<UnifiedSearchResultItem>,
    val totalCount: Int,
    val hasMore: Boolean,
    val rawFetchedCount: Int
)

data class UnifiedHotSearch(
    val platform: MusicPlatform,
    val keyword: String,
    val score: Int = 0,
    val description: String = "",
    val iconUrl: String? = null
)

sealed interface UnifiedSearchSuggestion {
    val platform: MusicPlatform
    val text: String

    data class Keyword(
        override val platform: MusicPlatform,
        override val text: String
    ) : UnifiedSearchSuggestion

    data class ArtistMatch(
        override val platform: MusicPlatform,
        val id: ArtistId,
        override val text: String,
        val avatarUrl: String
    ) : UnifiedSearchSuggestion

    data class AlbumMatch(
        override val platform: MusicPlatform,
        val id: AlbumId,
        override val text: String,
        val artistName: String
    ) : UnifiedSearchSuggestion
}

interface PlatformSearchRepository {
    fun search(
        platform: MusicPlatform,
        keyword: String,
        type: UnifiedSearchType,
        offset: Int = 0,
        limit: Int = 30
    ): Flow<Result<UnifiedSearchPageResult>>

    fun getHotSearches(platform: MusicPlatform): Flow<Result<List<UnifiedHotSearch>>>

    fun getSuggestions(platform: MusicPlatform, keyword: String): Flow<Result<List<UnifiedSearchSuggestion>>>

    fun getDefaultSearchKeyword(platform: MusicPlatform): Flow<Result<String>>
}

class NeteasePlatformSearchRepository(
    private val searchRepository: SearchRepository
) : PlatformSearchRepository {

    override fun search(
        platform: MusicPlatform,
        keyword: String,
        type: UnifiedSearchType,
        offset: Int,
        limit: Int
    ): Flow<Result<UnifiedSearchPageResult>> {
        val neteaseType = when (type) {
            UnifiedSearchType.SONG, UnifiedSearchType.COMPLEX -> SearchType.SONG
            UnifiedSearchType.ALBUM -> SearchType.ALBUM
            UnifiedSearchType.ARTIST -> SearchType.ARTIST
            UnifiedSearchType.PLAYLIST -> SearchType.PLAYLIST
            UnifiedSearchType.RADIO -> SearchType.RADIO
            UnifiedSearchType.PROGRAM -> SearchType.PROGRAM
        }
        return searchRepository.search(keyword, neteaseType, offset, limit).map { result ->
            result.map { page ->
                UnifiedSearchPageResult(
                    platform = MusicPlatform.NETEASE,
                    items = page.items.map { item ->
                        when (item) {
                            is SearchResultItem.SongItem -> UnifiedSearchResultItem.SongItem(item.track.toUnified())
                            is SearchResultItem.AlbumItem -> UnifiedSearchResultItem.AlbumItem(
                                album = item.album.toUnified(MusicPlatform.NETEASE),
                                platform = MusicPlatform.NETEASE
                            )
                            is SearchResultItem.ArtistItem -> UnifiedSearchResultItem.ArtistItem(
                                artist = item.artist.toUnified(MusicPlatform.NETEASE),
                                platform = MusicPlatform.NETEASE
                            )
                            is SearchResultItem.PlaylistItem -> UnifiedSearchResultItem.PlaylistItem(
                                item.playlist.toUnified(MusicPlatform.NETEASE)
                            )
                            is SearchResultItem.RadioItem -> UnifiedSearchResultItem.RadioItem(item.radio)
                            is SearchResultItem.ProgramItem -> UnifiedSearchResultItem.ProgramItem(item.program)
                        }
                    },
                    totalCount = page.totalCount,
                    hasMore = page.hasMore,
                    rawFetchedCount = page.rawFetchedCount
                )
            }
        }
    }

    override fun getHotSearches(platform: MusicPlatform): Flow<Result<List<UnifiedHotSearch>>> =
        searchRepository.getHotSearches().map { result ->
            result.map { list ->
                list.map { item ->
                    UnifiedHotSearch(
                        platform = MusicPlatform.NETEASE,
                        keyword = item.keyword,
                        score = item.score,
                        description = item.description,
                        iconUrl = item.iconUrl
                    )
                }
            }
        }

    override fun getSuggestions(platform: MusicPlatform, keyword: String): Flow<Result<List<UnifiedSearchSuggestion>>> =
        searchRepository.getSuggestions(keyword).map { result ->
            result.map { list ->
                list.map { item ->
                    when (item) {
                        is SearchSuggestion.Keyword -> UnifiedSearchSuggestion.Keyword(
                            platform = MusicPlatform.NETEASE,
                            text = item.text
                        )
                        is SearchSuggestion.ArtistMatch -> UnifiedSearchSuggestion.ArtistMatch(
                            platform = MusicPlatform.NETEASE,
                            id = ArtistId.netease(item.id),
                            text = item.text,
                            avatarUrl = item.avatarUrl
                        )
                        is SearchSuggestion.AlbumMatch -> UnifiedSearchSuggestion.AlbumMatch(
                            platform = MusicPlatform.NETEASE,
                            id = AlbumId.netease(item.id),
                            text = item.text,
                            artistName = item.artistName
                        )
                    }
                }
            }
        }

    override fun getDefaultSearchKeyword(platform: MusicPlatform): Flow<Result<String>> =
        searchRepository.getDefaultSearchKeyword()
}

class KugouPlatformSearchRepository(
    private val searchService: KugouSearchService,
    private val isEnabled: suspend () -> Boolean = { true }
) : PlatformSearchRepository {

    override fun search(
        platform: MusicPlatform,
        keyword: String,
        type: UnifiedSearchType,
        offset: Int,
        limit: Int
    ): Flow<Result<UnifiedSearchPageResult>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val pageSize = limit.coerceIn(1, 50)
        val page = (offset.coerceAtLeast(0) / pageSize) + 1
        if (type == UnifiedSearchType.COMPLEX) {
            val res = searchService.complexSearch(keyword = keyword, page = page, pageSize = pageSize)
            emit(
                res.map { complex ->
                    val items = buildList {
                        complex.artists.forEach {
                            add(
                                UnifiedSearchResultItem.ArtistItem(
                                    artist = it.toUnifiedArtist(),
                                    platform = MusicPlatform.KUGOU,
                                    albumCount = it.albumCount,
                                    songCount = it.songCount
                                )
                            )
                        }
                        complex.albums.forEach {
                            add(UnifiedSearchResultItem.AlbumItem(it.toUnifiedAlbum(), MusicPlatform.KUGOU))
                        }
                        complex.playlists.forEach {
                            add(UnifiedSearchResultItem.PlaylistItem(it.toUnifiedPlaylist()))
                        }
                        complex.songs.forEach {
                            add(UnifiedSearchResultItem.SongItem(it.toUnifiedTrack()))
                        }
                    }
                    UnifiedSearchPageResult(
                        platform = MusicPlatform.KUGOU,
                        items = items,
                        totalCount = items.size,
                        hasMore = complex.songs.size >= pageSize,
                        rawFetchedCount = items.size
                    )
                }
            )
            return@flow
        }

        val kugouType = when (type) {
            UnifiedSearchType.SONG -> KugouSearchType.SONG
            UnifiedSearchType.ALBUM -> KugouSearchType.ALBUM
            UnifiedSearchType.ARTIST -> KugouSearchType.AUTHOR
            UnifiedSearchType.PLAYLIST -> KugouSearchType.SPECIAL
            UnifiedSearchType.RADIO,
            UnifiedSearchType.PROGRAM -> {
                emit(Result.failure(UnsupportedOperationException("酷狗不支持播客/电台搜索")))
                return@flow
            }
            UnifiedSearchType.COMPLEX -> KugouSearchType.SONG
        }
        val res = searchService.search(keyword = keyword, type = kugouType, page = page, pageSize = pageSize)
        emit(
            res.map { pageResult ->
                val items = when (kugouType) {
                    KugouSearchType.SONG -> pageResult.songs.map { UnifiedSearchResultItem.SongItem(it.toUnifiedTrack()) }
                    KugouSearchType.ALBUM -> pageResult.albums.map {
                        UnifiedSearchResultItem.AlbumItem(it.toUnifiedAlbum(), MusicPlatform.KUGOU)
                    }
                    KugouSearchType.AUTHOR -> pageResult.artists.map {
                        UnifiedSearchResultItem.ArtistItem(
                            artist = it.toUnifiedArtist(),
                            platform = MusicPlatform.KUGOU,
                            albumCount = it.albumCount,
                            songCount = it.songCount
                        )
                    }
                    KugouSearchType.SPECIAL -> pageResult.playlists.map {
                        UnifiedSearchResultItem.PlaylistItem(it.toUnifiedPlaylist())
                    }
                    KugouSearchType.LYRIC -> pageResult.lyrics.map {
                        UnifiedSearchResultItem.SongItem(it.track.toUnifiedTrack())
                    }
                    KugouSearchType.MV -> emptyList()
                }
                UnifiedSearchPageResult(
                    platform = MusicPlatform.KUGOU,
                    items = items,
                    totalCount = pageResult.totalCount,
                    hasMore = pageResult.hasMore,
                    rawFetchedCount = items.size
                )
            }
        )
    }

    override fun getHotSearches(platform: MusicPlatform): Flow<Result<List<UnifiedHotSearch>>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val res = searchService.getHotSearches().map { list ->
            list.mapIndexed { index, item ->
                UnifiedHotSearch(
                    platform = MusicPlatform.KUGOU,
                    keyword = item.keyword,
                    score = (list.size - index).coerceAtLeast(0),
                    description = item.reason,
                    iconUrl = item.iconUrl
                )
            }
        }
        emit(res)
    }

    override fun getSuggestions(platform: MusicPlatform, keyword: String): Flow<Result<List<UnifiedSearchSuggestion>>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val res = searchService.getSuggestions(keyword).map { suggest ->
            buildList {
                suggest.artists.forEach {
                    add(
                        UnifiedSearchSuggestion.ArtistMatch(
                            platform = MusicPlatform.KUGOU,
                            id = ArtistId.kugou(it.authorId),
                            text = it.name,
                            avatarUrl = it.avatarUrl
                        )
                    )
                }
                suggest.albums.forEach {
                    add(
                        UnifiedSearchSuggestion.AlbumMatch(
                            platform = MusicPlatform.KUGOU,
                            id = AlbumId.kugou(it.albumId),
                            text = it.name,
                            artistName = it.singerName
                        )
                    )
                }
                suggest.keywords.forEach {
                    add(
                        UnifiedSearchSuggestion.Keyword(
                            platform = MusicPlatform.KUGOU,
                            text = it.keyword
                        )
                    )
                }
            }
        }
        emit(res)
    }

    override fun getDefaultSearchKeyword(platform: MusicPlatform): Flow<Result<String>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val hot = searchService.getHotSearches()
        emit(hot.map { list -> list.firstOrNull()?.keyword.orEmpty() })
    }
}

class PlatformSearchRouter(
    private val netease: PlatformSearchRepository,
    private val kugou: PlatformSearchRepository,
    private val isKugouEnabled: suspend () -> Boolean = { true }
) : PlatformSearchRepository {

    override fun search(
        platform: MusicPlatform,
        keyword: String,
        type: UnifiedSearchType,
        offset: Int,
        limit: Int
    ): Flow<Result<UnifiedSearchPageResult>> = route(platform) { it.search(platform, keyword, type, offset, limit) }

    override fun getHotSearches(platform: MusicPlatform): Flow<Result<List<UnifiedHotSearch>>> =
        route(platform) { it.getHotSearches(platform) }

    override fun getSuggestions(platform: MusicPlatform, keyword: String): Flow<Result<List<UnifiedSearchSuggestion>>> =
        route(platform) { it.getSuggestions(platform, keyword) }

    override fun getDefaultSearchKeyword(platform: MusicPlatform): Flow<Result<String>> =
        route(platform) { it.getDefaultSearchKeyword(platform) }

    private fun <T> route(
        platform: MusicPlatform,
        call: (PlatformSearchRepository) -> Flow<Result<T>>
    ): Flow<Result<T>> = when (platform) {
        MusicPlatform.NETEASE -> call(netease)
        MusicPlatform.KUGOU -> flow {
            if (!isKugouEnabled()) {
                emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            } else {
                emit(call(kugou).first())
            }
        }
        else -> flowOf(Result.failure(UnsupportedOperationException("暂不支持该平台搜索: ${platform.key}")))
    }
}
