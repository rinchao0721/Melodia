package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.player.PersonalFmMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalFmParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
    }

    // 构造旧式字段（artists / album / duration）响应，模拟 /eapi/v1/radio/get 结构
    private val legacyRadioGetJson = """
        {
          "code": 200,
          "popAdjust": false,
          "data": [
            {
              "id": 186016,
              "name": "晴天",
              "artists": [
                {"id": 6452, "name": "周杰伦", "picUrl": "http://p1.music.126.net/a.jpg"}
              ],
              "album": {
                "id": 18905,
                "name": "叶惠美",
                "picUrl": "http://p2.music.126.net/cover.jpg",
                "publishTime": 1059580800000
              },
              "duration": 269000,
              "fee": 8,
              "privilege": {
                "id": 186016,
                "plLevel": "exhigh",
                "maxBrLevel": "hires",
                "st": 0
              }
            },
            {
              "id": 25706282,
              "name": "夜曲"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `旧式字段FM响应可正确映射到Track与默认值`() {
        val response = json.decodeFromString<PersonalFmResponse>(legacyRadioGetJson)

        assertTrue(response.isSuccess)
        assertEquals(2, response.data.size)

        val first = response.data[0]
        assertEquals(186016L, first.id)
        assertEquals("晴天", first.name)
        assertEquals(listOf("周杰伦"), first.ar.map { it.name })
        assertEquals(6452L, first.ar.single().id)
        assertEquals("叶惠美", first.al.name)
        assertEquals("http://p2.music.126.net/cover.jpg", first.al.picUrl)
        assertEquals(269000L, first.dt)
        assertEquals(8, first.fee)
        assertEquals("hires", first.privilege?.maxBrLevel)

        // 缺省字段按默认值兜底不报错
        val second = response.data[1]
        assertEquals(25706282L, second.id)
        assertEquals("夜曲", second.name)
        assertTrue(second.ar.isEmpty())
        assertEquals("", second.al.name)
        assertEquals(0L, second.dt)
    }

    @Test
    fun `FM请求体按模式省略或携带subMode`() {
        val defaultJson = json.encodeToString(
            PersonalFmRequest(mode = PersonalFmMode.DEFAULT.mode, subMode = PersonalFmMode.DEFAULT.subMode)
        )
        assertEquals("""{"mode":"DEFAULT","limit":3}""", defaultJson)

        val sceneJson = json.encodeToString(
            PersonalFmRequest(mode = PersonalFmMode.EXERCISE.mode, subMode = PersonalFmMode.EXERCISE.subMode)
        )
        assertEquals("""{"mode":"SCENE_RCMD","subMode":"EXERCISE","limit":3}""", sceneJson)
    }

    @Test
    fun `非200响应与不喜欢响应解析`() {
        val failed = json.decodeFromString<PersonalFmResponse>("""{"code":301}""")
        assertFalse(failed.isSuccess)
        assertTrue(failed.data.isEmpty())

        val trashOk = json.decodeFromString<FmTrashResponse>("""{"code":200}""")
        assertTrue(trashOk.isSuccess)
    }
}
