package com.lin0721.linmusic.core.kugou

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KugouTrackParsingTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun parseBoth(text: String): List<KugouTrackItemDto> {
        val json = obj(text)
        val helper = parseKugouTrackItem(json)
        val discover = KugouDiscoverService.parseDiscoverTrackItem(json)
        assertNotNull(helper)
        assertNotNull(discover)
        return listOf(helper!!, discover!!)
    }

    @Test
    fun `歌名为空且 filename 为歌手 - 歌名时拆出歌名与歌手`() {
        parseBoth("""{"hash":"ABC","songname":"","author_name":"","filename":"周杰伦 - 发如雪"}""").forEach {
            assertEquals("发如雪", it.name)
            assertEquals(listOf("周杰伦"), it.artists.map { artist -> artist.name })
        }
    }

    @Test
    fun `已有歌手时 filename 的歌手前缀不进歌名`() {
        parseBoth("""{"hash":"ABC","songname":"","author_name":"周杰伦","filename":"周杰伦 - 发如雪"}""").forEach {
            assertEquals("发如雪", it.name)
            assertEquals(listOf("周杰伦"), it.artists.map { artist -> artist.name })
        }
    }

    @Test
    fun `歌名与 filename 都不含分隔符时直接取 filename`() {
        parseBoth("""{"hash":"ABC","songname":"  ","author_name":"周杰伦","filename":"发如雪"}""").forEach {
            assertEquals("发如雪", it.name)
        }
    }

    @Test
    fun `歌名与 filename 都为空才回退 hash`() {
        parseBoth("""{"hash":"ABC","songname":"","author_name":"","filename":""}""").forEach {
            assertEquals("abc", it.name)
        }
    }

    @Test
    fun `没有 union_cover 时用专辑封面字段`() {
        parseBoth(
            """{"hash":"ABC","songname":"发如雪","author_name":"周杰伦","album_sizable_cover":"http://imge.kugou.com/stdmusic/{size}/a.jpg"}"""
        ).forEach {
            assertEquals("https://imge.kugou.com/stdmusic/400/a.jpg", it.coverUrl)
        }
        parseBoth(
            """{"hash":"ABC","songname":"发如雪","author_name":"周杰伦","album_cover":"http://imge.kugou.com/stdmusic/{size}/b.jpg"}"""
        ).forEach {
            assertEquals("https://imge.kugou.com/stdmusic/400/b.jpg", it.coverUrl)
        }
    }

    @Test
    fun `首页刷歌条目只有 hash_128 时走发现解析并保留封面与歌名`() {
        val item = KugouDiscoverService.parseDiscoverTrackItem(
            obj(
                """{"song_info":{"hash_128":"AB12","songname":"","filename":"周杰伦 - 发如雪",
                    "album_cover":"http://imge.kugou.com/stdmusic/{size}/c.jpg"}}"""
            )
        )
        assertNotNull(item)
        assertEquals("ab12", item!!.hash)
        assertEquals("发如雪", item.name)
        assertEquals("https://imge.kugou.com/stdmusic/400/c.jpg", item.coverUrl)
    }

    @Test
    fun `云盘上传歌曲的歌名去掉音频扩展名`() {
        parseBoth("""{"hash":"ABC","songname":"magnolia.mp3","author_name":"keshi"}""").forEach {
            assertEquals("magnolia", it.name)
        }
        parseBoth("""{"hash":"ABC","songname":"","author_name":"","filename":"keshi - magnolia.FLAC"}""").forEach {
            assertEquals("magnolia", it.name)
            assertEquals(listOf("keshi"), it.artists.map { artist -> artist.name })
        }
    }

    @Test
    fun `歌名中非音频后缀的点号保留`() {
        parseBoth("""{"hash":"ABC","songname":"Vol.2","author_name":"keshi"}""").forEach {
            assertEquals("Vol.2", it.name)
        }
    }
}
