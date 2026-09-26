package com.lin0721.linmusic.feature.music.data

import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StyleArtistItem
import com.lin0721.linmusic.feature.music.domain.StyleHead
import com.lin0721.linmusic.feature.music.domain.StylePlaylistItem
import com.lin0721.linmusic.feature.music.domain.StylePreference
import com.lin0721.linmusic.feature.music.domain.toMusicStyles
import com.lin0721.linmusic.feature.music.domain.toStyleArtistItems
import com.lin0721.linmusic.feature.music.domain.toStyleHead
import com.lin0721.linmusic.feature.music.domain.toStylePlaylistItems
import com.lin0721.linmusic.feature.music.domain.toStylePreferences
import kotlinx.coroutines.flow.Flow

class MusicRepositoryImpl(
    private val apiService: MusicApi,
    private val contentFilter: ContentFilter
) : MusicRepository {

    override fun getStyleList(): Flow<Result<List<MusicStyle>>> = apiFlow(
        request = { apiService.getStyleList() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.data.toMusicStyles() }
    )

    override fun getStylePreferences(): Flow<Result<List<StylePreference>>> = apiFlow(
        request = { apiService.getStylePreference() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.data?.toStylePreferences().orEmpty() }
    )

    override fun getStyleHead(tagId: Long): Flow<Result<StyleHead>> = apiFlow(
        request = { apiService.getStyleHead(StyleHeadRequest(tagId)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.toStyleHead() }
    )

    override fun getStylePlaylists(tagId: Long): Flow<Result<List<StylePlaylistItem>>> = apiFlow(
        request = { apiService.getStylePlaylists(StyleContentRequest(tagId = tagId)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.playlist.toStylePlaylistItems() }
    )

    override fun getStyleSongs(tagId: Long): Flow<Result<List<Track>>> = apiFlow(
        request = { apiService.getStyleSongs(StyleContentRequest(tagId = tagId)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { response ->
            contentFilter.filterBlockedArtists(response.data!!.songs) { song -> song.ar.map { it.id } }
        }
    )

    override fun getStyleArtists(tagId: Long): Flow<Result<List<StyleArtistItem>>> = apiFlow(
        request = { apiService.getStyleArtists(StyleContentRequest(tagId = tagId)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.artists.toStyleArtistItems() }
    )
}
