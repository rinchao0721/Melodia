package com.lin0721.linmusic.feature.library.data

import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.flow.Flow

class LibraryRepositoryImpl(
    private val apiService: LibraryApi,
    private val contentFilter: ContentFilter
) : LibraryRepository {

    override fun getUserRecord(uid: Long, type: Int): Flow<Result<List<UserRecordTrack>>> = apiFlow(
        request = { apiService.getUserRecord(UserRecordRequest(uid = uid, type = type)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val list = if (type == 1) {
                response.weekData ?: emptyList()
            } else {
                response.allData ?: emptyList()
            }
            val records = list.map { UserRecordTrack(track = it.song, playCount = it.playCount) }
            contentFilter.filterBlockedArtists(records) { it.track.ar.map { a -> a.id } }
        }
    )

    override fun getCollectedAlbums(limit: Int): Flow<Result<List<AlbumSubItem>>> = apiFlow(
        request = { apiService.getAlbumSublist(AlbumSublistRequest(limit = limit)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.data }
    )

    override fun getUserSubcount(): Flow<Result<UserSubcountResponse>> = apiFlow(
        request = { apiService.getUserSubcount() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it }
    )
}
