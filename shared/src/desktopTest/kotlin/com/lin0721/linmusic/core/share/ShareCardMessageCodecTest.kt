package com.lin0721.linmusic.core.share

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.lin0721.linmusic.core.share.ui.ShareCardTypography
import com.lin0721.linmusic.feature.message.domain.parsePrivateConversation
import com.lin0721.linmusic.feature.message.domain.parsePrivateMessage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardMessageCodecTest {

    private val content = ShareCardContent(
        title = "夜曲",
        artist = "周杰伦",
        lines = listOf(ShareLyricLine("为你弹奏萧邦的夜曲", "Nocturne for you"), ShareLyricLine("纪念我死去的爱情")),
        lineBefore = "前一句",
        lineAfter = "后一句",
        positionMs = 61_000L,
        durationMs = 226_000L
    )

    private val typography = ShareCardTypography(
        family = FontFamily.Serif,
        scale = 1.1f,
        weight = FontWeight.Bold,
        align = TextAlign.Center
    )

    @Test
    fun `编码后可完整解码还原样式与歌词，并剥离参数码`() {
        val text = ShareCardMessageCodec.encode(
            ShareCardStyle.POSTER,
            ShareCardBackground.Palette(2),
            ShareCardRatio.STORY,
            typography,
            content
        )
        assertTrue(text.startsWith("「为你弹奏萧邦的夜曲」"))

        val decoded = ShareCardMessageCodec.decode(text)
        val message = assertNotNull(decoded.message).let { decoded.message!! }
        assertEquals(ShareCardStyle.POSTER, message.style)
        assertEquals(ShareCardBackground.Palette(2), message.background)
        assertEquals(ShareCardRatio.STORY, message.ratio)
        assertEquals(typography, message.typography())
        assertEquals(content, message.content("夜曲", "周杰伦"))
        assertEquals("「为你弹奏萧邦的夜曲」", decoded.displayText)
    }

    @Test
    fun `预设纯色与模糊封面背景可往返`() {
        listOf(ShareCardBackground.presets[3], ShareCardBackground.BlurCover, ShareCardBackground.Auto).forEach { bg ->
            val text = ShareCardMessageCodec.encode(ShareCardStyle.COVER, bg, ShareCardRatio.SQUARE, ShareCardTypography(), content)
            assertEquals(bg, ShareCardMessageCodec.decode(text).message?.background)
        }
    }

    @Test
    fun `参数码被篡改或截断时退回普通文本`() {
        val text = ShareCardMessageCodec.encode(ShareCardStyle.CLASSIC, ShareCardBackground.Auto, ShareCardRatio.PORTRAIT, ShareCardTypography(), content)
        val tampered = text.replaceFirst("⟦mc1:e", "⟦mc1:f")
        val truncated = text.dropLast(10)

        listOf(tampered, truncated, "普通附言", "", "⟦mc1:abc.zzzzzzzz⟧").forEach { raw ->
            val decoded = ShareCardMessageCodec.decode(raw)
            assertNull(decoded.message)
            assertEquals(raw, decoded.displayText)
        }
    }

    @Test
    fun `歌词过长时按序缩减到长度上限内且仍可解码`() {
        val longContent = content.copy(
            lines = List(8) { ShareLyricLine("很长的歌词".repeat(24), "very long translation ".repeat(5)) }
        )
        val text = ShareCardMessageCodec.encode(ShareCardStyle.CLASSIC, ShareCardBackground.Auto, ShareCardRatio.PORTRAIT, ShareCardTypography(), longContent)
        val marker = text.substring(text.indexOf("⟦"))
        assertTrue(marker.length <= 900)
        val message = ShareCardMessageCodec.decode(text).message
        assertNotNull(message)
        assertTrue(message!!.lines.isNotEmpty())
        assertTrue(message.lines.all { it.translation == null })
    }

    @Test
    fun `私信解析识别歌曲卡片附带的分享卡片参数，会话摘要显示分享卡片`() {
        val note = ShareCardMessageCodec.encode(ShareCardStyle.AURORA, ShareCardBackground.Auto, ShareCardRatio.PORTRAIT, ShareCardTypography(), content)
        val inner = buildJsonObject {
            put("type", 1)
            put("msg", note)
            putJsonObject("song") {
                put("id", 101)
                put("name", "夜曲")
            }
        }.toString()
        val message = parsePrivateMessage(buildJsonObject {
            put("id", 1)
            put("time", 5)
            put("msg", inner)
        })
        assertEquals(ShareCardStyle.AURORA, message?.shareCard?.style)
        assertEquals("「为你弹奏萧邦的夜曲」", message?.text)
        assertEquals(101L, message?.card?.resourceId)

        val conversation = parsePrivateConversation(buildJsonObject {
            putJsonObject("fromUser") { put("userId", 200); put("nickname", "好友"); put("avatarUrl", "") }
            putJsonObject("toUser") { put("userId", 100); put("nickname", "我"); put("avatarUrl", "") }
            putJsonObject("user") { put("id", 9); put("fromUserId", 200); put("toUserId", 100) }
            put("lastMsg", inner)
        }, selfUid = 100L)
        assertEquals("分享卡片：夜曲", conversation?.lastMessage)
    }
}
