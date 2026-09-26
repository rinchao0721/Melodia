package com.lin0721.linmusic.feature.player.data

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.network.mapToAppError
import com.lin0721.linmusic.feature.player.domain.SongMusicMemory
import com.lin0721.linmusic.feature.player.domain.SongWikiCreatorRole
import com.lin0721.linmusic.feature.player.domain.SongWikiData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

private const val TAG = "PlayerRepositoryImpl"

class PlayerRepositoryImpl(
    private val apiService: PlayerApi
) : PlayerRepository {

    override fun getSongDetail(songId: Long): Flow<Result<Track>> = apiFlow(
        request = {
            val c = """[{"id":$songId}]"""
            apiService.getSongDetail(SongDetailRequest(c = c))
        },
        isSuccess = { it.isSuccess && it.songs.isNotEmpty() },
        code = { it.code },
        transform = { it.songs[0] }
    )

    override fun getSongDetails(songIds: List<Long>): Flow<Result<List<Track>>> = apiFlow(
        request = {
            val c = songIds.joinToString(prefix = "[", postfix = "]") { """{"id":$it}""" }
            apiService.getSongDetail(SongDetailRequest(c = c))
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.songs }
    )

    override fun getChorusStartTime(songId: Long): Flow<Result<Long?>> = apiFlow(
        request = { apiService.getSongChorus(SongChorusRequest(ids = "[$songId]")) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            response.chorus
                .filter { it.id == songId && it.startTime > 0L }
                .minOfOrNull { it.startTime }
        }
    )

    // 获取合并后的歌曲详情与百科信息
    override fun getSongWiki(songId: Long): Flow<Result<SongWikiData>> = flow {
        coroutineScope {
            // 并发请求三个核心接口
            val detailDeferred = async {
                runCatching {
                    val c = """[{"id":$songId}]"""
                    apiService.getSongDetail(SongDetailRequest(c = c))
                }
            }
            val wikiDeferred = async {
                runCatching {
                    apiService.getSongWikiSummary(SongWikiSummaryRequest(songId = songId))
                }
            }
            val creatorsDeferred = async {
                runCatching {
                    apiService.getSongCreators(SongCreatorsRequest(songId = songId))
                }
            }

            val detailOutcome = detailDeferred.await()
            val wikiOutcome = wikiDeferred.await()
            val creatorsOutcome = creatorsDeferred.await()
            detailOutcome.exceptionOrNull()?.let { AppLogger.w(TAG, "getSongWiki 歌曲详情子请求失败 songId=$songId", it) }
            wikiOutcome.exceptionOrNull()?.let { AppLogger.w(TAG, "getSongWiki 百科摘要子请求失败 songId=$songId", it) }
            creatorsOutcome.exceptionOrNull()?.let { AppLogger.w(TAG, "getSongWiki 制作人员子请求失败 songId=$songId", it) }

            val detailResult = detailOutcome.getOrNull()
            val wikiResult = wikiOutcome.getOrNull()
            val creatorsResult = creatorsOutcome.getOrNull()

            // 解析基础数据：专辑名与发行时间
            val albumName = detailResult?.songs?.firstOrNull()?.al?.name ?: ""
            val publishTime = detailResult?.songs?.firstOrNull()?.publishTime ?: 0L
            val publishDateStr = if (publishTime > 0) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                sdf.format(java.util.Date(publishTime))
            } else {
                ""
            }

            var style = ""
            var language = ""
            var bpm = ""
            var entertainment = ""
            var awards = emptyList<String>()
            var awardTotal = 0
            var musicMemory: SongMusicMemory? = null

            // 解析百科简要信息中的 Block 列表
            wikiResult?.data?.blocks?.forEach { block ->
                if (block.code == "SONG_PLAY_ABOUT_SONG_BASIC") {
                    block.creatives.forEach { creative ->
                        when (creative.creativeType) {
                            "songTag" -> {
                                style = creative.resources.mapNotNull { it.uiElement?.mainTitle?.title }
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" / ")
                            }
                            "language" -> {
                                language = creative.uiElement?.textLinks?.firstOrNull()?.text ?: ""
                            }
                            "bpm" -> {
                                bpm = creative.uiElement?.textLinks?.firstOrNull()?.text ?: ""
                            }
                            "entertainment" -> {
                                entertainment = creative.resources.mapNotNull { it.uiElement?.mainTitle?.title }
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" / ")
                            }
                            "songAward" -> {
                                awards = creative.resources.mapNotNull { it.uiElement?.mainTitle?.title }
                                    .filter { it.isNotEmpty() }
                                awardTotal = creative.uiElement?.buttons?.firstOrNull()?.text
                                    ?.filter { it.isDigit() }
                                    ?.toIntOrNull() ?: 0
                            }
                        }
                    }
                } else if (block.code == "SONG_PLAY_ABOUT_MUSIC_MEMORY") {
                    musicMemory = parseMusicMemory(block)
                }
            }

            // 解析制作人员信息：按角色分组，供"制作"详情面板展示；同时拼一份摘要文本用于列表行
            val creatorRoles = (creatorsResult?.data?.songCreatorsRoleVos ?: emptyList())
                .mapNotNull { role ->
                    val artists = role.creatorMetaVOS.map { it.artistName }.filter { it.isNotEmpty() }
                    if (artists.isNotEmpty()) SongWikiCreatorRole(role.roleName, artists) else null
                }
            val creatorsStr = creatorRoles.joinToString(" / ") { "${it.roleName} ${it.artistNames.joinToString(" ")}" }

            emit(Result.success(
                SongWikiData(
                    style = style,
                    album = albumName,
                    language = language,
                    publishTime = publishDateStr,
                    bpm = bpm,
                    creators = creatorsStr,
                    creatorRoles = creatorRoles,
                    entertainment = entertainment,
                    awards = awards,
                    awardTotal = awardTotal,
                    musicMemory = musicMemory
                )
            ))
        }
    }.catch { e ->
        AppLogger.e(TAG, "getSongWiki 请求异常 songId=$songId", e)
        emit(Result.failure(mapToAppError(e)))
    }
}

// 首次收听与累计播放都缺失时返回 null，卡片不展示
internal fun parseMusicMemory(block: SongWikiBlock): SongMusicMemory? {
    val resources = block.creatives.flatMap { it.resources }
    val firstListen = resources.firstOrNull { it.resourceType == "FIRST_LISTEN" }
        ?.resourceExt?.musicFirstListenDto
    val totalPlay = resources.firstOrNull { it.resourceType == "TOTAL_PLAY" }
        ?.resourceExt?.musicTotalPlayDto
    val date = firstListen?.date?.trim().orEmpty()
    val playCount = totalPlay?.playCount?.coerceAtLeast(0) ?: 0
    if (date.isEmpty() && playCount == 0) return null
    return SongMusicMemory(
        firstListenDate = date,
        season = firstListen?.season?.trim().orEmpty(),
        period = firstListen?.period?.trim().orEmpty(),
        playCount = playCount,
        playCountText = totalPlay?.text?.trim().orEmpty()
    )
}
