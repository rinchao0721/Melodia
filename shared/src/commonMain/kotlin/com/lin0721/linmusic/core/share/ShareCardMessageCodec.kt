package com.lin0721.linmusic.core.share

import com.lin0721.linmusic.core.share.ui.ShareCardTypography
import com.lin0721.linmusic.core.share.ui.ShareCardTypographyOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.zip.CRC32

// 随私信歌曲卡片发送的分享卡片参数；对方 Melodia 据此在本地重新渲染，官方客户端只看到附言
data class ShareCardMessage(
    val style: ShareCardStyle,
    val background: ShareCardBackground,
    val ratio: ShareCardRatio,
    val fontIndex: Int,
    val scale: Float,
    val weightIndex: Int,
    val alignIndex: Int,
    val lines: List<ShareLyricLine>,
    val lineBefore: String?,
    val lineAfter: String?,
    val positionMs: Long,
    val durationMs: Long
) {
    fun typography(): ShareCardTypography = ShareCardTypography(
        family = ShareCardTypographyOptions.presetFonts.getOrNull(fontIndex)?.family,
        scale = scale,
        weight = ShareCardTypographyOptions.weights.getOrNull(weightIndex)?.second,
        align = ShareCardTypographyOptions.aligns.getOrNull(alignIndex)?.second
    )

    fun content(title: String, artist: String): ShareCardContent = ShareCardContent(
        title = title,
        artist = artist,
        lines = lines,
        lineBefore = lineBefore,
        lineAfter = lineAfter,
        positionMs = positionMs,
        durationMs = durationMs
    )
}

data class DecodedShareCardText(
    val message: ShareCardMessage?,
    // 去掉参数码后的可读附言
    val displayText: String
)

object ShareCardMessageCodec {
    private const val VERSION = 1
    private const val MARK_OPEN = "⟦mc1:"
    private const val MARK_CLOSE = "⟧"
    private const val HUMAN_LABEL = "来自 Melodia 的分享卡片"
    private const val MAX_LINES = 8
    private const val MAX_LINE_CHARS = 120

    // 附言长度上限未经服务端证实，超过时依次丢翻译、丢前后句、减行数
    private const val MAX_PAYLOAD_CHARS = 900

    private val MARK_REGEX = Regex("⟦mc1:([A-Za-z0-9_-]+)\\.([0-9a-f]{8})⟧")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun encode(
        style: ShareCardStyle,
        background: ShareCardBackground,
        ratio: ShareCardRatio,
        typography: ShareCardTypography,
        content: ShareCardContent
    ): String {
        val base = Wire(
            v = VERSION,
            s = style.name,
            b = backgroundCode(background),
            r = ratio.name,
            f = ShareCardTypographyOptions.presetFonts.indexOfFirst { it.family == typography.family }.coerceAtLeast(0),
            z = typography.scale,
            w = ShareCardTypographyOptions.weights.indexOfFirst { it.second == typography.weight }.coerceAtLeast(0),
            a = ShareCardTypographyOptions.aligns.indexOfFirst { it.second == typography.align }.coerceAtLeast(0),
            l = content.lines.take(MAX_LINES).map { WireLine(it.text.take(MAX_LINE_CHARS), it.translation?.take(MAX_LINE_CHARS)) },
            lb = content.lineBefore?.take(MAX_LINE_CHARS),
            la = content.lineAfter?.take(MAX_LINE_CHARS),
            p = content.positionMs.coerceAtLeast(0),
            d = content.durationMs.coerceAtLeast(0)
        )
        val candidates = sequence {
            yield(base)
            val noTranslation = base.copy(l = base.l.map { it.copy(r = null) })
            yield(noTranslation)
            val noContext = noTranslation.copy(lb = null, la = null)
            yield(noContext)
            for (count in noContext.l.size - 1 downTo 1) yield(noContext.copy(l = noContext.l.take(count)))
        }
        val marker = candidates.map(::marker).firstOrNull { it.length <= MAX_PAYLOAD_CHARS }
            ?: marker(base.copy(l = base.l.take(1).map { it.copy(r = null) }, lb = null, la = null))
        val lead = content.lines.firstOrNull()?.text?.takeIf { it.isNotBlank() }?.let { "「${it.take(40)}」" }.orEmpty()
        return "$lead$HUMAN_LABEL $marker"
    }

    // 解析失败、校验不过或取值越界时返回 message = null，界面退回普通歌曲卡片
    fun decode(text: String?): DecodedShareCardText {
        if (text.isNullOrBlank()) return DecodedShareCardText(null, text.orEmpty())
        val match = MARK_REGEX.find(text) ?: return DecodedShareCardText(null, text)
        val displayText = text.removeRange(match.range).replace(HUMAN_LABEL, "").trim()
        val message = runCatching {
            val bytes = Base64.getUrlDecoder().decode(match.groupValues[1])
            if (crcHex(bytes) != match.groupValues[2]) return@runCatching null
            toMessage(json.decodeFromString<Wire>(bytes.decodeToString()))
        }.getOrNull()
        return DecodedShareCardText(message, if (message != null) displayText else text)
    }

    private fun marker(wire: Wire): String {
        val bytes = json.encodeToString(wire).encodeToByteArray()
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return "$MARK_OPEN$body.${crcHex(bytes)}$MARK_CLOSE"
    }

    private fun crcHex(bytes: ByteArray): String {
        val crc = CRC32().apply { update(bytes) }.value
        return crc.toString(16).padStart(8, '0')
    }

    private fun toMessage(wire: Wire): ShareCardMessage? {
        if (wire.v != VERSION) return null
        val style = ShareCardStyle.entries.firstOrNull { it.name == wire.s } ?: return null
        val ratio = ShareCardRatio.entries.firstOrNull { it.name == wire.r } ?: return null
        val background = parseBackground(wire.b) ?: return null
        val scale = wire.z.takeIf { it in ShareCardTypographyOptions.scales } ?: 1f
        return ShareCardMessage(
            style = style,
            background = background,
            ratio = ratio,
            fontIndex = wire.f.takeIf { it in ShareCardTypographyOptions.presetFonts.indices } ?: 0,
            scale = scale,
            weightIndex = wire.w.takeIf { it in ShareCardTypographyOptions.weights.indices } ?: 0,
            alignIndex = wire.a.takeIf { it in ShareCardTypographyOptions.aligns.indices } ?: 0,
            lines = wire.l.take(MAX_LINES).mapNotNull { line ->
                line.t.take(MAX_LINE_CHARS).takeIf { it.isNotBlank() }
                    ?.let { ShareLyricLine(it, line.r?.take(MAX_LINE_CHARS)?.takeIf(String::isNotBlank)) }
            },
            lineBefore = wire.lb?.take(MAX_LINE_CHARS)?.takeIf(String::isNotBlank),
            lineAfter = wire.la?.take(MAX_LINE_CHARS)?.takeIf(String::isNotBlank),
            positionMs = wire.p.coerceAtLeast(0),
            durationMs = wire.d.coerceAtLeast(0)
        )
    }

    // a=自动，u=模糊封面，p<序号>=封面取色，s<序号>=预设纯色
    private fun backgroundCode(background: ShareCardBackground): String = when (background) {
        ShareCardBackground.Auto -> "a"
        ShareCardBackground.BlurCover -> "u"
        is ShareCardBackground.Palette -> "p${background.index}"
        is ShareCardBackground.Solid -> ShareCardBackground.presets.indexOf(background)
            .takeIf { it >= 0 }?.let { "s$it" } ?: "a"
    }

    private fun parseBackground(code: String): ShareCardBackground? = when {
        code == "a" -> ShareCardBackground.Auto
        code == "u" -> ShareCardBackground.BlurCover
        code.startsWith("p") -> code.drop(1).toIntOrNull()
            ?.takeIf { it in 0 until ShareCardBackground.PALETTE_SIZE }
            ?.let { ShareCardBackground.Palette(it) }
        code.startsWith("s") -> code.drop(1).toIntOrNull()?.let { ShareCardBackground.presets.getOrNull(it) }
        else -> null
    }

    @Serializable
    private data class Wire(
        val v: Int,
        val s: String,
        val b: String,
        val r: String,
        val f: Int = 0,
        val z: Float = 1f,
        val w: Int = 0,
        val a: Int = 0,
        val l: List<WireLine> = emptyList(),
        val lb: String? = null,
        val la: String? = null,
        val p: Long = 0,
        val d: Long = 0
    )

    @Serializable
    private data class WireLine(
        val t: String,
        val r: String? = null
    )
}
