package com.lin0721.linmusic.feature.podcast.data

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.podcast.domain.PodcastCategory
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadioDetail
import com.lin0721.linmusic.feature.podcast.domain.toPodcastCategories
import com.lin0721.linmusic.feature.podcast.domain.toPodcastPrograms
import com.lin0721.linmusic.feature.podcast.domain.toPodcastRadioDetail
import com.lin0721.linmusic.feature.podcast.domain.toPodcastRadios
import kotlinx.coroutines.flow.Flow

class PodcastRepositoryImpl(
    private val apiService: PodcastApi
) : PodcastRepository {

    override fun getCategories(): Flow<Result<List<PodcastCategory>>> = apiFlow(
        request = { apiService.getCategories() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.categories.toPodcastCategories() }
    )

    override fun getRecommendPrograms(cateId: Long?): Flow<Result<List<PodcastProgram>>> = apiFlow(
        request = { apiService.getRecommendPrograms(PodcastProgramRecommendRequest(cateId = cateId)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.programs.toPodcastPrograms() }
    )

    override fun getPersonalizedRadios(): Flow<Result<List<PodcastRadio>>> = apiFlow(
        request = { apiService.getPersonalizedRadios() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.data.toPodcastRadios() }
    )

    override fun getRecommendRadios(): Flow<Result<List<PodcastRadio>>> = apiFlow(
        request = { apiService.getRecommendRadios() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.djRadios.toPodcastRadios() }
    )

    override fun getToplistRadios(): Flow<Result<List<PodcastRadio>>> = apiFlow(
        request = { apiService.getToplistRadios() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.toplist.toPodcastRadios() }
    )

    override fun getRadioDetail(radioId: Long): Flow<Result<PodcastRadioDetail>> = apiFlow(
        request = { apiService.getRadioDetail(PodcastRadioDetailRequest(radioId)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.toPodcastRadioDetail() }
    )

    override fun setRadioSubscribed(radioId: Long, subscribe: Boolean): Flow<Result<Unit>> = apiFlow(
        request = {
            val body = PodcastSubscribeRequest(radioId)
            if (subscribe) apiService.subscribeRadio(body) else apiService.unsubscribeRadio(body)
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { }
    )

    override fun getRadioPrograms(radioId: Long, offset: Int): Flow<Result<List<PodcastProgram>>> = apiFlow(
        request = { apiService.getRadioPrograms(PodcastProgramListRequest(radioId = radioId, offset = offset)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.programs.toPodcastPrograms() }
    )
}
