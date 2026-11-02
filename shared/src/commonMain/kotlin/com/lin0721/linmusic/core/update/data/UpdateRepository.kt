package com.lin0721.linmusic.core.update.data

import com.lin0721.linmusic.core.update.domain.UpdateInfo
import com.lin0721.linmusic.core.update.domain.VersionCodeResolver
import kotlin.coroutines.cancellation.CancellationException

class UpdateRepository(private val api: GithubReleaseApi) {

    // 按渠道过滤后取 versionCode 最大的一条，仅当高于当前安装版本才视为"有更新"
    suspend fun fetchLatestUpdate(currentVersionCode: Int, allowPrerelease: Boolean): Result<UpdateInfo?> {
        return try {
            val releases = api.listReleases(GITHUB_OWNER, GITHUB_REPO)
            val candidate = releases
                .asSequence()
                .filterNot { it.draft }
                .filter { allowPrerelease || !it.prerelease }
                .mapNotNull { dto ->
                    val versionCode = VersionCodeResolver.resolve(dto.tagName) ?: return@mapNotNull null
                    UpdateInfo(
                        versionName = dto.tagName,
                        versionCode = versionCode,
                        isPrerelease = dto.prerelease,
                        changelog = dto.body.orEmpty(),
                        releasePageUrl = dto.htmlUrl.ifBlank { "$RELEASES_URL/tag/${dto.tagName}" }
                    )
                }
                .maxByOrNull { it.versionCode }

            val hasUpdate = candidate != null && candidate.versionCode > currentVersionCode
            Result.success(if (hasUpdate) candidate else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val GITHUB_OWNER = "rinchao0721"
        private const val GITHUB_REPO = "Melodia"
        const val REPOSITORY_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
        const val RELEASES_URL = "$REPOSITORY_URL/releases"
    }
}
