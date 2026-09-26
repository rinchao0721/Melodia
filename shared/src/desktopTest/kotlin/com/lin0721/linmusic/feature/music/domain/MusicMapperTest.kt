package com.lin0721.linmusic.feature.music.domain

import com.lin0721.linmusic.feature.music.data.StyleArtistDto
import com.lin0721.linmusic.feature.music.data.StyleHeadDto
import com.lin0721.linmusic.feature.music.data.StylePlaylistDto
import com.lin0721.linmusic.feature.music.data.StylePortraitValueDto
import com.lin0721.linmusic.feature.music.data.StylePreferenceData
import com.lin0721.linmusic.feature.music.data.StylePreferenceItemDto
import com.lin0721.linmusic.feature.music.data.StyleTagDto
import com.lin0721.linmusic.feature.music.data.StyleTagPortraitDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicMapperTest {

    private fun value(text: String) = StylePortraitValueDto(text)

    @Test
    fun `曲风列表保留两级结构`() {
        val styles = listOf(
            StyleTagDto(
                tagId = 1000, tagName = "流行", enName = "Pop", level = 1, colorDeep = "23303B",
                childrenTags = listOf(
                    StyleTagDto(tagId = 1020, tagName = "华语流行", level = 2, colorDeep = "")
                )
            )
        ).toMusicStyles()

        val pop = styles.single()
        assertEquals("流行", pop.name)
        assertEquals("23303B", pop.colorHex)
        assertEquals("华语流行", pop.children.single().name)
        // 二级曲风服务端下发空串，归一为 null 交由 UI 兜底
        assertNull(pop.children.single().colorHex)
    }

    @Test
    fun `无名或无id的曲风被丢弃`() {
        val styles = listOf(
            StyleTagDto(tagId = 0, tagName = "没有id"),
            StyleTagDto(tagId = 1, tagName = "  "),
            StyleTagDto(tagId = 2, tagName = "正常")
        ).toMusicStyles()

        assertEquals(listOf("正常"), styles.map { it.name })
    }

    @Test
    fun `非法配色一律归零`() {
        val styles = listOf(
            StyleTagDto(tagId = 1, tagName = "空串", colorDeep = ""),
            StyleTagDto(tagId = 2, tagName = "带井号", colorDeep = "#23303B"),
            StyleTagDto(tagId = 3, tagName = "位数不足", colorDeep = "23303"),
            StyleTagDto(tagId = 4, tagName = "合法", colorDeep = "EA647B")
        ).toMusicStyles()

        assertEquals(listOf(null, null, null, "EA647B"), styles.map { it.colorHex })
    }

    @Test
    fun `偏好占比从字符串解析并回填配色`() {
        val prefs = StylePreferenceData(
            tagPreferenceVos = listOf(
                StylePreferenceItemDto(tagId = 1252, tagName = "二次元", ratio = "48"),
                StylePreferenceItemDto(tagId = 9999, tagName = "无配色", ratio = "7")
            ),
            tags = listOf(StyleTagDto(tagId = 1252, tagName = "二次元", colorDeep = "EA647B"))
        ).toStylePreferences()

        assertEquals(48, prefs[0].ratio)
        assertEquals("EA647B", prefs[0].colorHex)
        assertEquals(7, prefs[1].ratio)
        assertNull(prefs[1].colorHex)
    }

    @Test
    fun `占比脏值当零处理而不是抛异常`() {
        val prefs = StylePreferenceData(
            tagPreferenceVos = listOf(
                StylePreferenceItemDto(tagId = 1, tagName = "空", ratio = ""),
                StylePreferenceItemDto(tagId = 2, tagName = "带百分号", ratio = "48%"),
                StylePreferenceItemDto(tagId = 3, tagName = "带空格", ratio = " 12 ")
            )
        ).toStylePreferences()

        assertEquals(listOf(0, 0, 12), prefs.map { it.ratio })
    }

    @Test
    fun `画像模板按变量表替换`() {
        val head = StyleHeadDto(
            tagId = 1000,
            name = "流行",
            tagPortrait = StyleTagPortraitDto(
                templateContent = "你已涉猎\${tagNum}个主流曲风\n其中你对\${tagName}最为钟爱，偏好高达\${tagPercent}",
                pattern = mapOf(
                    "tagNum" to value("21"),
                    "tagName" to value("二次元"),
                    "tagPercent" to value("61.5%")
                ),
                dataTip = "根据你近30天的听歌记录生成"
            )
        ).toStyleHead()

        assertEquals(
            "你已涉猎21个主流曲风\n其中你对二次元最为钟爱，偏好高达61.5%",
            head.portrait?.content
        )
        assertEquals("根据你近30天的听歌记录生成", head.portrait?.dataTip)
    }

    @Test
    fun `画像缺变量时不把占位符露到界面`() {
        val head = StyleHeadDto(
            tagId = 1,
            name = "流行",
            tagPortrait = StyleTagPortraitDto(
                // 服务端模板固定预留 23 个小众曲风位，实际给不满
                templateContent = "还挖掘到了\${a}、\${b}、\${c}这些小众曲风",
                pattern = mapOf("a" to value("融合爵士"))
            )
        ).toStyleHead()

        val content = head.portrait?.content.orEmpty()
        assertTrue("不应残留占位符: $content", !content.contains("\${"))
        assertEquals("还挖掘到了融合爵士这些小众曲风", content)
    }

    @Test
    fun `空槽夹在两个有值词之间时保留一个顿号`() {
        val head = StyleHeadDto(
            tagId = 1,
            name = "流行",
            tagPortrait = StyleTagPortraitDto(
                templateContent = "挖掘到了\${a}、\${b}、\${c}这些",
                pattern = mapOf("a" to value("融合爵士"), "c" to value("新浪潮"))
            )
        ).toStyleHead()

        // 吞掉两侧分隔符会压成「融合爵士新浪潮」，两个词之间必须留一个顿号
        assertEquals("挖掘到了融合爵士、新浪潮这些", head.portrait?.content)
    }

    @Test
    fun `画像首位为空槽时不留前导顿号`() {
        val head = StyleHeadDto(
            tagId = 1,
            name = "流行",
            tagPortrait = StyleTagPortraitDto(
                templateContent = "挖掘到了\${a}、\${b}这些",
                pattern = mapOf("b" to value("新浪潮"))
            )
        ).toStyleHead()

        assertEquals("挖掘到了新浪潮这些", head.portrait?.content)
    }

    @Test
    fun `画像模板为空时不产出画像`() {
        assertNull(
            StyleHeadDto(tagId = 1, name = "流行", tagPortrait = StyleTagPortraitDto(templateContent = ""))
                .toStyleHead().portrait
        )
        assertNull(StyleHeadDto(tagId = 1, name = "流行").toStyleHead().portrait)
    }

    @Test
    fun `数量字段保持字符串原样`() {
        val head = StyleHeadDto(
            tagId = 1, name = "流行", songNum = "999999+", artistNum = "1000+",
            cover = listOf("http://p5.music.126.net/x/1")
        ).toStyleHead()

        assertEquals("999999+", head.songNum)
        assertEquals("1000+", head.artistNum)
        assertEquals("http://p5.music.126.net/x/1", head.coverUrl)
    }

    @Test
    fun `简介压平换行与全角空格`() {
        val head = StyleHeadDto(
            tagId = 1,
            name = "流行",
            // 服务端文案里混有全角空格与换行，原样展示会在卡片上断行
            desc = "解锁你的最佳流行推荐聚集地！\n流　行　音乐，是指那些结构短小的歌曲。 "
        ).toStyleHead()

        assertEquals("解锁你的最佳流行推荐聚集地！ 流 行 音乐，是指那些结构短小的歌曲。", head.desc)
    }

    @Test
    fun `无简介时为空串而非null`() {
        assertEquals("", StyleHeadDto(tagId = 1, name = "流行").toStyleHead().desc)
    }

    @Test
    fun `缺封面的歌单被丢弃`() {
        val items = listOf(
            StylePlaylistDto(id = 1, name = "有封面", cover = "http://c/1.jpg", playCount = 100),
            StylePlaylistDto(id = 2, name = "无封面", cover = null),
            StylePlaylistDto(id = 0, name = "无id", cover = "http://c/2.jpg")
        ).toStylePlaylistItems()

        assertEquals(listOf("有封面"), items.map { it.name })
    }

    @Test
    fun `歌手优先取1比1头像`() {
        val items = listOf(
            StyleArtistDto(id = 1, name = "两种都有", picUrl = "http://p/sq.jpg", img1v1Url = "http://p/1v1.jpg"),
            StyleArtistDto(id = 2, name = "只有方图", picUrl = "http://p/sq2.jpg"),
            StyleArtistDto(id = 3, name = "都没有")
        ).toStyleArtistItems()

        assertEquals("http://p/1v1.jpg", items[0].picUrl)
        assertEquals("http://p/sq2.jpg", items[1].picUrl)
        assertEquals(2, items.size)
    }
}
