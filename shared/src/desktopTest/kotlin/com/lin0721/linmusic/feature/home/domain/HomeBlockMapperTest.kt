package com.lin0721.linmusic.feature.home.domain

import com.lin0721.linmusic.feature.home.data.HomeBlockDto
import com.lin0721.linmusic.feature.home.data.HomeBlockPageData
import com.lin0721.linmusic.feature.home.data.HomeCreativeDto
import com.lin0721.linmusic.feature.home.data.HomeDjMainSongDto
import com.lin0721.linmusic.feature.home.data.HomeDjProgramDto
import com.lin0721.linmusic.feature.home.data.HomeDjRadioDto
import com.lin0721.linmusic.feature.home.data.HomeResourceExtInfoDto
import com.lin0721.linmusic.feature.home.data.HomeImageDto
import com.lin0721.linmusic.feature.home.data.HomeLabelDto
import com.lin0721.linmusic.feature.home.data.HomeResourceDto
import com.lin0721.linmusic.feature.home.data.HomeTitleDto
import com.lin0721.linmusic.feature.home.data.HomeUiElementDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBlockMapperTest {

    private fun ui(
        main: String? = null,
        sub: String? = null,
        desc: String? = null,
        image: String? = "http://p1.music.126.net/cover.jpg",
        labels: List<String> = emptyList(),
        label: String? = null
    ) = HomeUiElementDto(
        mainTitle = main?.let { HomeTitleDto(it) },
        subTitle = sub?.let { HomeTitleDto(it) },
        description = desc,
        image = image?.let { HomeImageDto(it) },
        labelTexts = labels,
        labelText = label?.let { HomeLabelDto(it) }
    )

    private fun block(
        code: String,
        blockUi: HomeUiElementDto? = null,
        resources: List<HomeResourceDto> = emptyList()
    ) = HomeBlockDto(
        blockCode = code,
        uiElement = blockUi,
        creatives = if (resources.isEmpty()) emptyList() else listOf(HomeCreativeDto(resources = resources))
    )

    private val playlistRes = HomeResourceDto(
        resourceType = "list",
        resourceId = "7270961168",
        uiElement = ui(main = "音乐是唯一的解药", labels = listOf("驾车", "华语", "安静")),
        action = "orpheus://playlist/7270961168"
    )

    @Test
    fun `歌单区块映射为货架`() {
        val page = HomeBlockPageData(
            blocks = listOf(
                block("HOMEPAGE_BLOCK_PLAYLIST_RCMD", ui(sub = "推荐歌单"), listOf(playlistRes))
            )
        ).toHomeBlockPage()

        assertEquals(1, page.shelves.size)
        val shelf = page.shelves.first()
        assertEquals("推荐歌单", shelf.title)
        assertFalse(shelf.showRank)

        val card = shelf.cards.single() as HomeCard.Playlist
        assertEquals(7270961168L, card.id)
        assertEquals("音乐是唯一的解药", card.title)
        assertEquals("驾车 · 华语 · 安静", card.caption)
    }

    @Test
    fun `排行榜区块标记名次`() {
        val page = HomeBlockPageData(
            blocks = listOf(
                block(
                    "HOMEPAGE_BLOCK_TOPLIST",
                    ui(sub = "排行榜"),
                    listOf(HomeResourceDto("song", "1", ui(main = "某歌", label = "热门"), null))
                )
            )
        ).toHomeBlockPage()

        assertTrue(page.shelves.single().showRank)
    }

    @Test
    fun `标题在mainTitle时同样能取到`() {
        val page = HomeBlockPageData(
            blocks = listOf(
                block("HOMEPAGE_BLOCK_OLD_SUBSCRIBE_ARTIST_NEW", ui(main = "关注艺人的新歌"), listOf(playlistRes))
            )
        ).toHomeBlockPage()

        assertEquals("关注艺人的新歌", page.shelves.single().title)
    }

    @Test
    fun `无标题区块被丢弃`() {
        val page = HomeBlockPageData(
            blocks = listOf(block("HOMEPAGE_BLOCK_STAR_SONG_GUIDE", null, listOf(playlistRes)))
        ).toHomeBlockPage()

        assertTrue(page.shelves.isEmpty())
    }

    @Test
    fun `无可渲染卡片的区块被丢弃`() {
        val page = HomeBlockPageData(
            blocks = listOf(block("HOMEPAGE_VOICELIST_RCMD", ui(sub = "热门播客"), emptyList()))
        ).toHomeBlockPage()

        assertTrue(page.shelves.isEmpty())
    }

    @Test
    fun `播客与活动类资源被过滤`() {
        val page = HomeBlockPageData(
            blocks = listOf(
                block(
                    "HOMEPAGE_VOICELIST_RCMD",
                    ui(sub = "热门播客"),
                    listOf(
                        HomeResourceDto("voice", "3723544210", ui(main = "某节目"), "orpheus://program/3723544210"),
                        HomeResourceDto("orpheus", "1", ui(main = "某活动"), "orpheus://activity")
                    )
                )
            )
        ).toHomeBlockPage()

        assertTrue(page.shelves.isEmpty())
    }

    @Test
    fun `播客单集取mainSong作为播放id`() {
        val page = HomeBlockPageData(
            blocks = listOf(
                block(
                    "HOMEPAGE_VOICELIST_RCMD",
                    ui(sub = "热门播客"),
                    listOf(
                        HomeResourceDto(
                            resourceType = "voice",
                            resourceId = "3725385398",
                            uiElement = ui(main = "13.永镇西方第一名", sub = "黑神话悟空游戏音乐总谱精选集"),
                            action = "orpheus://program/3725385398",
                            resourceExtInfo = HomeResourceExtInfoDto(
                                djProgram = HomeDjProgramDto(
                                    id = 3725385398,
                                    name = "13.永镇西方第一名",
                                    mainSong = HomeDjMainSongDto(3411649805),
                                    radio = HomeDjRadioDto(1495966005, "黑神话悟空游戏音乐总谱精选集")
                                )
                            )
                        )
                    )
                )
            )
        ).toHomeBlockPage()

        val card = page.shelves.single().cards.single() as HomeCard.Voice
        // 节目 id 仅用于 key，播放必须走 mainSong
        assertEquals(3725385398L, card.id)
        assertEquals(3411649805L, card.songId)
    }

    @Test
    fun `缺mainSong的播客单集被丢弃`() {
        fun probe(ext: HomeResourceExtInfoDto?) = HomeBlockPageData(
            blocks = listOf(
                block(
                    "HOMEPAGE_VOICELIST_RCMD",
                    ui(sub = "热门播客"),
                    listOf(
                        HomeResourceDto(
                            resourceType = "voice",
                            resourceId = "1",
                            uiElement = ui(main = "某节目"),
                            resourceExtInfo = ext
                        )
                    )
                )
            )
        ).toHomeBlockPage().shelves

        assertTrue("无 extInfo 应丢弃", probe(null).isEmpty())
        assertTrue("无 djProgram 应丢弃", probe(HomeResourceExtInfoDto()).isEmpty())
        assertTrue(
            "mainSong 为 0 应丢弃",
            probe(HomeResourceExtInfoDto(HomeDjProgramDto(id = 1, mainSong = HomeDjMainSongDto(0)))).isEmpty()
        )
    }

    @Test
    fun `音乐日历的ALBUM资源因跳转不是专辑页被过滤`() {
        val page = HomeBlockPageData(
            blocks = listOf(
                block(
                    "HOMEPAGE_MUSIC_CALENDAR",
                    ui(sub = "音乐日历"),
                    listOf(
                        HomeResourceDto(
                            resourceType = "ALBUM",
                            resourceId = "123",
                            uiElement = ui(main = "某专辑"),
                            action = "orpheus://nm/musicCalendar/detail?id=123"
                        )
                    )
                )
            )
        ).toHomeBlockPage()

        assertTrue(page.shelves.isEmpty())
    }

    @Test
    fun `正规专辑资源保留`() {
        val page = HomeBlockPageData(
            blocks = listOf(
                block(
                    "HOMEPAGE_BLOCK_NEW_ALBUM_NEW_SONG",
                    ui(sub = "新歌新碟"),
                    listOf(
                        HomeResourceDto(
                            resourceType = "album",
                            resourceId = "391143929",
                            uiElement = ui(main = "某新碟"),
                            action = "orpheus://album/391143929"
                        )
                    )
                )
            )
        ).toHomeBlockPage()

        val card = page.shelves.single().cards.single()
        assertTrue(card is HomeCard.Album)
        assertEquals(391143929L, card.id)
    }

    @Test
    fun `resourceId为null时不崩且该卡被丢弃`() {
        val page = HomeBlockPageData(
            blocks = listOf(
                block(
                    "HOMEPAGE_BLOCK_STYLE_RCMD",
                    ui(sub = "二次元理想乡的呼唤"),
                    listOf(HomeResourceDto("song", null, ui(main = "某歌"), null))
                )
            )
        ).toHomeBlockPage()

        assertTrue(page.shelves.isEmpty())
    }

    @Test
    fun `缺封面的卡片被丢弃`() {
        val page = HomeBlockPageData(
            blocks = listOf(
                block(
                    "HOMEPAGE_BLOCK_PLAYLIST_RCMD",
                    ui(sub = "推荐歌单"),
                    listOf(playlistRes.copy(uiElement = ui(main = "无封面歌单", image = null)))
                )
            )
        ).toHomeBlockPage()

        assertTrue(page.shelves.isEmpty())
    }

    @Test
    fun `描述优先级为labelTexts大于labelText大于subTitle大于description`() {
        fun captionOf(element: HomeUiElementDto): String {
            val page = HomeBlockPageData(
                blocks = listOf(
                    block(
                        "HOMEPAGE_BLOCK_TOPLIST",
                        ui(sub = "排行榜"),
                        listOf(HomeResourceDto("song", "1", element, null))
                    )
                )
            ).toHomeBlockPage()
            return page.shelves.single().cards.single().caption
        }

        assertEquals("A · B", captionOf(ui(main = "歌", labels = listOf("A", "B"), label = "热门", sub = "副标题", desc = "描述")))
        assertEquals("热门", captionOf(ui(main = "歌", label = "热门", sub = "副标题", desc = "描述")))
        assertEquals("副标题", captionOf(ui(main = "歌", sub = "副标题", desc = "描述")))
        assertEquals("描述", captionOf(ui(main = "歌", desc = "描述")))
        assertEquals("", captionOf(ui(main = "歌")))
    }

    @Test
    fun `轮播区块不参与货架渲染`() {
        val page = HomeBlockPageData(
            blocks = listOf(block("HOMEPAGE_BANNER", ui(sub = "轮播"), listOf(playlistRes)))
        ).toHomeBlockPage()

        assertTrue(page.shelves.isEmpty())
    }

    @Test
    fun `龙珠入口区块不参与货架渲染`() {
        val page = HomeBlockPageData(
            blocks = listOf(
                block(
                    "HOMEPAGE_BLOCK_OLD_DRAGON_BALL",
                    resources = listOf(
                        HomeResourceDto("dragon_ball", "1", ui(main = "每日推荐", image = "http://icon.png"), "orpheus://songrcmd")
                    )
                )
            )
        ).toHomeBlockPage()

        assertTrue(page.shelves.isEmpty())
    }

    @Test
    fun `cursor为null字符串视为已到底`() {
        assertNull(HomeBlockPageData(cursor = "null").toHomeBlockPage().nextCursor)
        assertNull(HomeBlockPageData(cursor = "").toHomeBlockPage().nextCursor)
        assertEquals(
            """{"offset":10}""",
            HomeBlockPageData(cursor = """{"offset":10}""").toHomeBlockPage().nextCursor
        )
    }
}
