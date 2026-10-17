package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.localmusic.domain.BatchOnlineMatchDecision
import com.lin0721.linmusic.feature.localmusic.domain.BatchOnlineMatchPolicy
import com.lin0721.linmusic.feature.localmusic.domain.BatchOnlineMatchStatus
import com.lin0721.linmusic.feature.localmusic.domain.LocalAudioCandidate

internal data class BatchSearchPage(val tracks: List<Track>, val complete: Boolean)
internal sealed interface BatchMatchEvidence {
    data class Confirmed(val decision: BatchOnlineMatchDecision) : BatchMatchEvidence
    data class Search(val page: BatchSearchPage) : BatchMatchEvidence
}

/** Strict cached evidence reports an existing source instead of spending another network request. */
internal suspend fun batchMatchEvidence(
    local: LocalAudioCandidate,
    confirmed: List<Track>,
    loadSearch: suspend () -> BatchSearchPage
): BatchMatchEvidence {
    val known = BatchOnlineMatchPolicy.evaluate(local, confirmed)
    return if (known.status == BatchOnlineMatchStatus.MATCHED || known.status == BatchOnlineMatchStatus.NEEDS_CONFIRMATION)
        BatchMatchEvidence.Confirmed(known) else BatchMatchEvidence.Search(loadSearch())
}
