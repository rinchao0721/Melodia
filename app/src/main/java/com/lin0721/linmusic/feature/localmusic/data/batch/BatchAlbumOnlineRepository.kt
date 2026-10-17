package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.EmptyBody
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.mapToAppError
import com.lin0721.linmusic.feature.localmusic.domain.BatchAlbumDetail
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/** Created with the application's regular Retrofit instance and its existing WEAPI interceptors. */
interface BatchAlbumDetailApi {
    @POST("/weapi/v1/album/{id}")
    suspend fun detail(
        @Path("id") id: Long,
        @Body body: EmptyBody = EmptyBody()
    ): BatchAlbumDetailResponse
}

@Serializable
data class BatchAlbumDetailResponse(
    val code: Int = 0,
    val album: BatchAlbumInfo = BatchAlbumInfo(),
    val songs: List<Track> = emptyList()
)

/** Private-to-this-feature DTO retains the advertised size without changing the shared album API. */
@Serializable
data class BatchAlbumInfo(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val artists: List<Artist> = emptyList(),
    val publishTime: Long = 0,
    val size: Int = 0
)

class BatchAlbumOnlineRepository(
    private val search: SearchRepository,
    private val api: BatchAlbumDetailApi,
    private val contentFilter: ContentFilter
) {
    private val requests = Mutex()
    private var lastRequestNanos = 0L
    suspend fun search(query: String, offset: Int = 0): SearchPageResult = boundedRequest {
        if (query.isBlank() || offset < 0) throw AppError.BizError(400, null)
        search.search(query.trim(), SearchType.ALBUM, offset = offset, limit = 50).first().getOrThrow()
    }

    suspend fun detail(album: Album): BatchAlbumDetail = boundedRequest {
        if (album.id <= 0L) throw AppError.BizError(400, null)
        val response = api.detail(album.id)
        validateBatchAlbumResponse(response, album.id)
        val info = response.album
        val raw = response.songs
        val byId = raw.groupBy { it.id }
        val resolvedAlbum = Album(
            id = info.id,
            name = info.name,
            picUrl = info.picUrl.ifBlank { album.picUrl },
            artists = info.artists.ifEmpty { album.artists },
            publishTime = info.publishTime.takeIf { it > 0L } ?: album.publishTime
        )
        val tracks = raw.map { track ->
            track.copy(al = track.al.copy(
                id = track.al.id.takeIf { it > 0L } ?: resolvedAlbum.id,
                name = track.al.name.ifBlank { resolvedAlbum.name },
                picUrl = track.al.picUrl.ifBlank { resolvedAlbum.picUrl },
                artists = track.al.artists.ifEmpty { resolvedAlbum.artists },
                publishTime = track.al.publishTime.takeIf { it > 0L } ?: resolvedAlbum.publishTime
            ))
        }
        val permitted = contentFilter.filterBlockedArtists(tracks) { it.ar.map(Artist::id) }
        val complete = (info.size <= 0 || raw.size >= info.size) &&
            permitted.size == raw.size && byId.size == raw.size
        // Keep the original full set for ambiguity checks. Incomplete results are review-only;
        // the manual album preview uses PlaylistRepository and its normal content filtering.
        BatchAlbumDetail(resolvedAlbum, tracks, complete = complete)
    }

    private suspend fun <T> boundedRequest(request: suspend () -> T): T = requests.withLock { try {
        val sinceLastMs = (System.nanoTime() - lastRequestNanos) / 1_000_000L
        if (lastRequestNanos != 0L && sinceLastMs < 1_200L) delay(1_200L - sinceLastMs)
        lastRequestNanos = System.nanoTime()
        withTimeout(25_000L) { request() }
    } catch (timeout: TimeoutCancellationException) {
        // A parent cancellation must remain cancellation rather than becoming a failed album row.
        currentCoroutineContext().ensureActive()
        throw AppError.NetworkError
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw mapToAppError(error)
    } }
}

/** Reject foreign album tracks before missing album metadata is filled or automatic writes begin. */
internal fun validateBatchAlbumResponse(response: BatchAlbumDetailResponse, expectedAlbumId: Long) {
    if (response.code != 200) {
        if (response.code == 301) throw AppError.Unauthorized
        throw AppError.BizError(response.code, null)
    }
    val info = response.album
    val raw = response.songs
    if (info.id != expectedAlbumId || info.name.isBlank() || raw.isEmpty() ||
        raw.any { it.id <= 0L || (it.al.id != 0L && it.al.id != info.id) }) {
        throw AppError.ParseError
    }
    // Preserve identical repeats for completeness/ambiguity checks instead of silently deduplicating.
    if (raw.groupBy { it.id }.values.any { repeats -> repeats.drop(1).any { it != repeats.first() } }) {
        throw AppError.ParseError
    }
}
