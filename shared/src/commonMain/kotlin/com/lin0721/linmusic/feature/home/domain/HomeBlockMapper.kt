package com.lin0721.linmusic.feature.home.domain

import com.lin0721.linmusic.feature.home.data.HomeBlockDto
import com.lin0721.linmusic.feature.home.data.HomeBlockPageData
import com.lin0721.linmusic.feature.home.data.HomeResourceDto
import com.lin0721.linmusic.feature.home.data.HomeUiElementDto

private const val BLOCK_BANNER = "HOMEPAGE_BANNER"
private const val BLOCK_DRAGON_BALL = "HOMEPAGE_BLOCK_OLD_DRAGON_BALL"
private const val BLOCK_TOPLIST = "HOMEPAGE_BLOCK_TOPLIST"

// orpheus://playlist/123 与 orpheus://album/123 两种站内跳转
private val PLAYLIST_ACTION = Regex("""orpheus://playlist/(\d+)""")
private val ALBUM_ACTION = Regex("""orpheus://album/(\d+)""")

// 把服务端下发的区块页拍平成货架列表。
// 不认识的资源类型一律丢弃而非兜底渲染——首页宁可少一张卡，也不能出现点不动的死卡片。
fun HomeBlockPageData.toHomeBlockPage(): HomeBlockPage {
    val shelves = blocks.mapNotNull { it.toShelfOrNull() }

    return HomeBlockPage(
        shelves = shelves,
        nextCursor = cursor?.takeIf { it.isNotBlank() && it != "null" },
        hasMore = hasMore
    )
}

// 标题与卡片缺一不可：无标题的区块（轮播、功能入口、占位块）不走货架渲染
private fun HomeBlockDto.toShelfOrNull(): HomeShelf? {
    if (blockCode == BLOCK_BANNER || blockCode == BLOCK_DRAGON_BALL) return null

    val element = uiElement ?: return null
    val title = element.resolveTitle()
    if (title.isBlank()) return null

    val cards = creatives.flatMap { it.resources }.mapNotNull { it.toCardOrNull() }
    if (cards.isEmpty()) return null

    return HomeShelf(
        blockCode = blockCode,
        title = title,
        showRank = blockCode == BLOCK_TOPLIST,
        cards = cards
    )
}

// 货架标题两处都可能出现：多数区块在 subTitle，关注艺人新歌这类在 mainTitle
private fun HomeUiElementDto.resolveTitle(): String =
    mainTitle?.title?.takeIf { it.isNotBlank() }
        ?: subTitle?.title?.takeIf { it.isNotBlank() }
        ?: ""

private fun HomeResourceDto.toCardOrNull(): HomeCard? {
    val ui = uiElement ?: return null
    val name = ui.mainTitle?.title?.takeIf { it.isNotBlank() } ?: return null
    val cover = ui.image?.imageUrl?.takeIf { it.isNotBlank() } ?: return null
    val caption = ui.resolveCaption()

    return when (resourceType.lowercase()) {
        "list" -> {
            val id = PLAYLIST_ACTION.find(action.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
                ?: resourceId?.toLongOrNull()
                ?: return null
            HomeCard.Playlist(id, name, cover, caption)
        }
        // 音乐日历的 ALBUM 资源 action 指向日历详情而非专辑页，靠这条正则一并挡掉；
        // 数字专辑走 openurl 外链，同样不放行
        "album", "digitalalbum" -> {
            val id = ALBUM_ACTION.find(action.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
                ?: return null
            HomeCard.Album(id, name, cover, caption)
        }
        "song" -> {
            val id = resourceId?.toLongOrNull() ?: return null
            HomeCard.Song(id, name, cover, caption)
        }
        // 播客单集。整个节目对象随 resourceExtInfo 一并下发，可播的 mainSong 就在里面，
        // 不必为每张卡再查一次节目详情；拿不到 mainSong 的照旧丢弃
        "voice" -> {
            val program = resourceExtInfo?.djProgram ?: return null
            val songId = program.mainSong?.id?.takeIf { it > 0 } ?: return null
            val programId = program.id.takeIf { it > 0 } ?: resourceId?.toLongOrNull() ?: return null
            HomeCard.Voice(programId, songId, name, cover, caption)
        }
        else -> null
    }
}

// 四种描述来源按信息量排优先级
private fun HomeUiElementDto.resolveCaption(): String {
    labelTexts.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { return it.joinToString(" · ") }
    labelText?.text?.takeIf { it.isNotBlank() }?.let { return it }
    subTitle?.title?.takeIf { it.isNotBlank() }?.let { return it }
    return description?.takeIf { it.isNotBlank() }.orEmpty()
}
