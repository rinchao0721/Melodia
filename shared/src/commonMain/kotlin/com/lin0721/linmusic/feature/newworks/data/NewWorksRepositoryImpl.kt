package com.lin0721.linmusic.feature.newworks.data

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.newworks.domain.NewWorksMv
import com.lin0721.linmusic.feature.newworks.domain.NewWorksReleasePage
import com.lin0721.linmusic.feature.newworks.domain.toDomain
import com.lin0721.linmusic.feature.newworks.domain.toReleaseDomain
import kotlinx.coroutines.flow.Flow

class NewWorksRepositoryImpl(
    private val apiService: NewWorksApi
) : NewWorksRepository {

    override fun getMvs(): Flow<Result<List<NewWorksMv>>> = apiFlow(
        request = { apiService.getNewMvs() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { response -> response.data!!.newWorks.map { it.toDomain() } }
    )

    override fun getReleases(before: Long, firstRequest: Boolean): Flow<Result<NewWorksReleasePage>> = apiFlow(
        request = {
            apiService.getNewReleases(
                NewWorksReleaseRequest(startTimestamp = before, firstRequest = firstRequest)
            )
        },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { response ->
            val data = response.data!!
            NewWorksReleasePage(
                items = data.newWorks.mapNotNull { it.toReleaseDomain() },
                hasMore = data.hasMore,
                nextCursor = data.newWorks.minOfOrNull { it.publishTime } ?: before
            )
        }
    )
}
