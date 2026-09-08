package com.lin0721.linmusic.feature.player.data

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.network.mapToAppError
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
            var background = ""
            var awards = ""

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
                        }
                    }
                } else if (block.code == "SONG_PLAY_ABOUT_WIKI") {
                    // 解析歌曲百科模块，提取歌曲背景描述以及所获奖项/荣誉
                    block.creatives.forEach { creative ->
                        val creativeType = creative.creativeType
                        val creativeTitle = creative.uiElement?.mainTitle?.title ?: ""

                        val descriptions = mutableListOf<String>()
                        creative.uiElement?.descriptions?.forEach { desc ->
                            if (desc.description.isNotEmpty()) {
                                descriptions.add(desc.description)
                            }
                        }
                        creative.resources.forEach { res ->
                            res.uiElement?.descriptions?.forEach { desc ->
                                if (desc.description.isNotEmpty()) {
                                    descriptions.add(desc.description)
                                }
                            }
                        }
                        val contentText = descriptions.joinToString("\n")

                        if (creativeType == "background" || creativeTitle.contains("背景") || creativeTitle.contains("故事")) {
                            if (contentText.isNotEmpty()) {
                                background = contentText
                            }
                        } else if (creativeType == "awards" || creativeTitle.contains("奖项") || creativeTitle.contains("获奖") || creativeTitle.contains("荣誉") || creativeTitle.contains("排行")) {
                            if (contentText.isNotEmpty()) {
                                awards = contentText
                            }
                        }
                    }
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
                    background = background,
                    awards = awards
                )
            ))
        }
    }.catch { e ->
        AppLogger.e(TAG, "getSongWiki 请求异常 songId=$songId", e)
        emit(Result.failure(mapToAppError(e)))
    }
}
