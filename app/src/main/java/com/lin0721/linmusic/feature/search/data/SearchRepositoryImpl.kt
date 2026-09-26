package com.lin0721.linmusic.feature.search.data

import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.network.mapToAppError
import com.lin0721.linmusic.feature.search.data.dto.CloudSearchRequest
import com.lin0721.linmusic.feature.search.data.dto.HighQualityPlaylistRequest
import com.lin0721.linmusic.feature.search.data.dto.SearchSuggestRequest
import com.lin0721.linmusic.feature.search.domain.HotSearch
import com.lin0721.linmusic.feature.search.domain.PlaylistCategoryPage
import com.lin0721.linmusic.feature.search.domain.PlaylistTag
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

private const val TAG = "SearchRepositoryImpl"
private const val MAX_ARTIST_SUGGESTIONS = 2
private const val MAX_ALBUM_SUGGESTIONS = 2

class SearchRepositoryImpl(
    private val apiService: SearchApi,
    private val contentFilter: ContentFilter
) : SearchRepository {

    override fun getDefaultSearchKeyword(): Flow<Result<String>> = apiFlow(
        request = { apiService.getSearchDefaultKeyword() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.showKeyword }
    )

    override fun search(keyword: String, type: SearchType, offset: Int, limit: Int): Flow<Result<SearchPageResult>> = apiFlow(
        request = {
            apiService.cloudSearch(CloudSearchRequest(s = keyword, type = type.apiValue, offset = offset, limit = limit))
        },
        isSuccess = { it.isSuccess && it.result != null },
        code = { it.code },
        transform = { response ->
            val result = response.result!!
            when (type) {
                SearchType.SONG -> {
                    val songs = result.songs ?: emptyList()
                    val filtered = contentFilter.filterBlockedArtists(songs) { it.ar.map { a -> a.id } }
                    // 屏蔽过滤后本页为空时强制 hasMore=false，避免翻页死循环
                    val hasMore = if (filtered.isEmpty()) false else (offset + songs.size < result.songCount)
                    SearchPageResult(
                        filtered.map { SearchResultItem.SongItem(it) },
                        result.songCount,
                        hasMore,
                        rawFetchedCount = songs.size
                    )
                }
                SearchType.ALBUM -> {
                    val albums = result.albums ?: emptyList()
                    SearchPageResult(
                        albums.map { SearchResultItem.AlbumItem(it) },
                        result.albumCount,
                        offset + albums.size < result.albumCount,
                        rawFetchedCount = albums.size
                    )
                }
                SearchType.ARTIST -> {
                    val artists = result.artists ?: emptyList()
                    SearchPageResult(
                        artists.map { SearchResultItem.ArtistItem(it) },
                        result.artistCount,
                        offset + artists.size < result.artistCount,
                        rawFetchedCount = artists.size
                    )
                }
                SearchType.PLAYLIST -> {
                    val playlists = result.playlists ?: emptyList()
                    SearchPageResult(
                        playlists.map { SearchResultItem.PlaylistItem(it) },
                        result.playlistCount,
                        offset + playlists.size < result.playlistCount,
                        rawFetchedCount = playlists.size
                    )
                }
            }
        }
    )

    // 两路联想并发，任一成功即出结果
    override fun getSuggestions(keyword: String): Flow<Result<List<SearchSuggestion>>> = flow {
        val request = SearchSuggestRequest(s = keyword)
        val (entityResult, keywordResult) = coroutineScope {
            val entity = async { suggestCatching { apiService.getSearchSuggestWeb(request) } }
            val keywords = async { suggestCatching { apiService.getSearchSuggest(request) } }
            entity.await() to keywords.await()
        }

        val entityData = entityResult.getOrNull()?.takeIf { it.isSuccess }?.result
        val keywordData = keywordResult.getOrNull()?.takeIf { it.isSuccess }
        if (entityData == null && keywordData == null) {
            val error = keywordResult.exceptionOrNull() ?: entityResult.exceptionOrNull()
            AppLogger.w(TAG, "getSuggestions 两路联想均失败", error)
            emit(Result.failure(error?.let(::mapToAppError) ?: AppError.BizError(keywordResult.getOrNull()?.code ?: 0, null)))
            return@flow
        }

        val artists = contentFilter.filterBlockedArtists(entityData?.artists.orEmpty()) { listOf(it.id) }
            .filter { it.name.isNotBlank() }
            .take(MAX_ARTIST_SUGGESTIONS)
            .map { artist ->
                SearchSuggestion.ArtistMatch(
                    id = artist.id,
                    text = artist.name,
                    avatarUrl = artist.picUrl?.takeIf { it.isNotBlank() } ?: artist.img1v1Url.orEmpty()
                )
            }
        val albums = contentFilter.filterBlockedArtists(entityData?.albums.orEmpty()) { listOfNotNull(it.artist?.id) }
            .filter { it.name.isNotBlank() }
            .take(MAX_ALBUM_SUGGESTIONS)
            .map { album ->
                SearchSuggestion.AlbumMatch(id = album.id, text = album.name, artistName = album.artist?.name.orEmpty())
            }
        val keywords = keywordData?.result?.allMatch.orEmpty()
            .map { it.keyword }
            .filter { it.isNotBlank() }
            .distinct()
            .map { SearchSuggestion.Keyword(it) }

        emit(Result.success(artists + albums + keywords))
    }.catch { e ->
        AppLogger.e(TAG, "getSuggestions 请求异常", e)
        emit(Result.failure(mapToAppError(e)))
    }

    // 联想随输入频繁取消，不能像 runCatching 那样吞掉取消异常
    private suspend fun <T> suggestCatching(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun getHotSearches(): Flow<Result<List<HotSearch>>> = apiFlow(
        request = { apiService.getHotSearchDetail() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            // 热搜榜偶尔混入 searchWord 为空的运营占位条目，过滤掉避免渲染出空白格子
            response.data
                .filter { it.searchWord.isNotBlank() }
                .map { item ->
                    HotSearch(
                        keyword = item.searchWord,
                        score = item.score,
                        description = item.content,
                        iconUrl = item.iconUrl
                    )
                }
        }
    )

    override fun getPlaylistTags(): Flow<Result<List<PlaylistTag>>> = flow {
        val (tags, playlists) = coroutineScope {
            val tagsDeferred = async { apiService.getHighQualityTags() }
            val playlistsDeferred = async {
                apiService.getHighQualityPlaylists(HighQualityPlaylistRequest(limit = 50))
            }
            tagsDeferred.await() to playlistsDeferred.await()
        }

        if (!tags.isSuccess) {
            AppLogger.e(TAG, "getPlaylistTags 标签接口业务失败 code=${tags.code}")
            emit(Result.failure(AppError.BizError(tags.code, null)))
            return@flow
        }

        val coverMap = mutableMapOf<String, String>()
        if (playlists.isSuccess) {
            for (playlist in playlists.playlists) {
                for (tag in playlist.tags) {
                    if (tag !in coverMap && playlist.coverImgUrl.isNotBlank()) {
                        coverMap[tag] = "${playlist.coverImgUrl}?param=400y400"
                    }
                }
            }
        } else {
            AppLogger.w(TAG, "getPlaylistTags 精品歌单接口失败 code=${playlists.code}，封面图将缺失")
        }

        val result = tags.tags
            .filter { it.name != "全部" }
            .map { tag ->
                PlaylistTag(
                    name = tag.name,
                    coverUrl = coverMap[tag.name] ?: ""
                )
            }

        emit(Result.success(result))
    }.catch { e ->
        AppLogger.e(TAG, "getPlaylistTags 请求异常", e)
        emit(Result.failure(mapToAppError(e)))
    }

    override fun getPlaylistsByCategory(category: String, cursor: Long, limit: Int): Flow<Result<PlaylistCategoryPage>> = apiFlow(
        request = {
            apiService.getHighQualityPlaylists(
                HighQualityPlaylistRequest(cat = category, lasttime = cursor, limit = limit)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            PlaylistCategoryPage(
                playlists = response.playlists.map { item ->
                    PlaylistDetail(
                        id = item.id,
                        name = item.name,
                        coverImgUrl = item.coverImgUrl,
                        playCount = item.playCount
                    )
                },
                hasMore = response.more,
                nextCursor = response.lasttime,
                total = response.total
            )
        }
    )
}
