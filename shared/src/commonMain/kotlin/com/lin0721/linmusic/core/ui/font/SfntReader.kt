package com.lin0721.linmusic.core.ui.font

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

// 最小 SFNT 元数据，仅用于字体库归组与展示
data class SfntMetadata(
    val familyName: String,
    val subfamilyName: String,
    val weight: Int,
    val isItalic: Boolean,
    val hasChinese: Boolean
)

sealed interface SfntParseResult {
    data class Success(val metadata: SfntMetadata) : SfntParseResult
    data class Failure(val reason: String) : SfntParseResult
}

/**
 * 纯 Kotlin 最小 SFNT 表读取器：校验文件头，解析 name 与 OS/2 表。
 * 所有偏移与长度均做越界检查，畸形输入返回 Failure 而不抛异常。
 */
object SfntReader {

    private const val HEADER_SIZE = 12
    private const val TABLE_RECORD_SIZE = 16

    private const val TAG_TTF_1 = 0x00010000
    private const val TAG_OTTO = 0x4F54544F
    private const val TAG_TRUE = 0x74727565
    private const val TAG_TTCF = 0x74746366
    private const val TAG_WOFF = 0x774F4646
    private const val TAG_WOF2 = 0x774F4632

    private const val TABLE_NAME = 0x6E616D65 // "name"
    private const val TABLE_OS2 = 0x4F532F32  // "OS/2"

    private const val NAME_ID_FAMILY = 1
    private const val NAME_ID_SUBFAMILY = 2
    private const val NAME_ID_TYPOGRAPHIC_FAMILY = 16
    private const val NAME_ID_TYPOGRAPHIC_SUBFAMILY = 17

    private const val PLATFORM_UNICODE = 0
    private const val PLATFORM_MACINTOSH = 1
    private const val PLATFORM_WINDOWS = 3

    private const val OS2_MIN_WEIGHT_LENGTH = 6
    private const val OS2_MIN_FS_SELECTION_LENGTH = 64
    private const val OS2_MIN_CODEPAGE_LENGTH = 82

    private const val FS_SELECTION_ITALIC = 1 shl 0
    private const val FS_SELECTION_OBLIQUE = 1 shl 9
    private const val CP_SIMPLIFIED_CHINESE = 1L shl 17
    private const val CP_TRADITIONAL_CHINESE = 1L shl 20

    fun read(file: File): SfntParseResult {
        val bytes = try {
            if (!file.isFile) return SfntParseResult.Failure("字体文件不存在")
            file.readBytes()
        } catch (e: IOException) {
            return SfntParseResult.Failure("读取字体文件失败：${e.message}")
        } catch (e: SecurityException) {
            return SfntParseResult.Failure("无权读取字体文件")
        }
        return read(bytes, fallbackName = file.nameWithoutExtension)
    }

    fun read(bytes: ByteArray, fallbackName: String = "Unknown"): SfntParseResult {
        if (bytes.size < HEADER_SIZE) {
            return SfntParseResult.Failure("文件过小，不是有效的 SFNT 字体")
        }
        val sfntVersion = readInt32BE(bytes, 0)
        when (sfntVersion) {
            TAG_TTF_1, TAG_OTTO, TAG_TRUE -> Unit
            TAG_TTCF -> return SfntParseResult.Failure("暂不支持 TTC 字体合集")
            TAG_WOFF, TAG_WOF2 -> return SfntParseResult.Failure("暂不支持 WOFF/WOFF2 网页字体")
            else -> return SfntParseResult.Failure("不支持的字体文件头")
        }

        val numTables = readUInt16BE(bytes, 4)
        if (numTables <= 0) {
            return SfntParseResult.Failure("字体表数量无效")
        }
        val directoryEnd = HEADER_SIZE.toLong() + numTables.toLong() * TABLE_RECORD_SIZE
        if (directoryEnd > bytes.size.toLong()) {
            return SfntParseResult.Failure("字体表目录超出文件长度")
        }

        var nameOffset = -1
        var nameLength = 0
        var os2Offset = -1
        var os2Length = 0

        for (i in 0 until numTables) {
            val recordOffset = HEADER_SIZE + i * TABLE_RECORD_SIZE
            val tag = readInt32BE(bytes, recordOffset)
            val offset = readUInt32BE(bytes, recordOffset + 8)
            val length = readUInt32BE(bytes, recordOffset + 12)
            if (offset < 0 || length < 0 || offset + length > bytes.size.toLong()) {
                return SfntParseResult.Failure("字体表偏移或长度越界")
            }
            when (tag) {
                TABLE_NAME -> {
                    nameOffset = offset.toInt()
                    nameLength = length.toInt()
                }
                TABLE_OS2 -> {
                    os2Offset = offset.toInt()
                    os2Length = length.toInt()
                }
            }
        }

        val (family, subfamily) = if (nameOffset >= 0) {
            parseNameTable(bytes, nameOffset, nameLength)
        } else {
            null to null
        }

        val (weight, isItalic, hasChinese) = if (os2Offset >= 0) {
            parseOs2Table(bytes, os2Offset, os2Length)
        } else {
            Triple(400, false, false)
        }

        val resolvedFamily = family?.takeIf { it.isNotBlank() } ?: fallbackName.ifBlank { "Unknown" }
        val resolvedSubfamily = subfamily?.takeIf { it.isNotBlank() } ?: if (isItalic) "Italic" else "Regular"

        return SfntParseResult.Success(
            SfntMetadata(
                familyName = resolvedFamily,
                subfamilyName = resolvedSubfamily,
                weight = weight,
                isItalic = isItalic,
                hasChinese = hasChinese
            )
        )
    }

    private fun parseNameTable(bytes: ByteArray, tableOffset: Int, tableLength: Int): Pair<String?, String?> {
        if (tableLength < 6) return null to null
        val count = readUInt16BE(bytes, tableOffset + 2)
        val stringOffset = readUInt16BE(bytes, tableOffset + 4)
        val recordsEnd = 6L + count.toLong() * 12L
        if (recordsEnd > tableLength.toLong() || stringOffset > tableLength) {
            return null to null
        }

        val storageStart = tableOffset + stringOffset
        var typographicFamily: ScoredName? = null
        var typographicSubfamily: ScoredName? = null
        var legacyFamily: ScoredName? = null
        var legacySubfamily: ScoredName? = null

        for (i in 0 until count) {
            val rec = tableOffset + 6 + i * 12
            val platformId = readUInt16BE(bytes, rec)
            val encodingId = readUInt16BE(bytes, rec + 2)
            val languageId = readUInt16BE(bytes, rec + 4)
            val nameId = readUInt16BE(bytes, rec + 6)
            val length = readUInt16BE(bytes, rec + 8)
            val offset = readUInt16BE(bytes, rec + 10)

            if (nameId != NAME_ID_FAMILY &&
                nameId != NAME_ID_SUBFAMILY &&
                nameId != NAME_ID_TYPOGRAPHIC_FAMILY &&
                nameId != NAME_ID_TYPOGRAPHIC_SUBFAMILY
            ) {
                continue
            }

            val strStart = storageStart.toLong() + offset.toLong()
            val strEnd = strStart + length.toLong()
            if (strStart < storageStart || strEnd > (tableOffset + tableLength).toLong() || strEnd > bytes.size.toLong()) {
                continue
            }

            val decoded = decodeNameString(bytes, strStart.toInt(), length, platformId, encodingId) ?: continue
            val trimmed = decoded.trim().trim('\u0000')
            if (trimmed.isEmpty()) continue

            val score = scorePlatform(platformId, encodingId, languageId)
            val candidate = ScoredName(trimmed, score)
            when (nameId) {
                NAME_ID_TYPOGRAPHIC_FAMILY -> if (typographicFamily == null || candidate.score > typographicFamily.score) typographicFamily = candidate
                NAME_ID_TYPOGRAPHIC_SUBFAMILY -> if (typographicSubfamily == null || candidate.score > typographicSubfamily.score) typographicSubfamily = candidate
                NAME_ID_FAMILY -> if (legacyFamily == null || candidate.score > legacyFamily.score) legacyFamily = candidate
                NAME_ID_SUBFAMILY -> if (legacySubfamily == null || candidate.score > legacySubfamily.score) legacySubfamily = candidate
            }
        }

        val family = typographicFamily?.value ?: legacyFamily?.value
        val subfamily = typographicSubfamily?.value ?: legacySubfamily?.value
        return family to subfamily
    }

    private fun parseOs2Table(bytes: ByteArray, tableOffset: Int, tableLength: Int): Triple<Int, Boolean, Boolean> {
        val rawWeight = if (tableLength >= OS2_MIN_WEIGHT_LENGTH) {
            readUInt16BE(bytes, tableOffset + 4)
        } else {
            400
        }
        val weight = rawWeight.coerceIn(1, 1000).takeIf { rawWeight in 1..1000 } ?: 400

        val isItalic = if (tableLength >= OS2_MIN_FS_SELECTION_LENGTH) {
            val fsSelection = readUInt16BE(bytes, tableOffset + 62)
            (fsSelection and (FS_SELECTION_ITALIC or FS_SELECTION_OBLIQUE)) != 0
        } else {
            false
        }

        val hasChinese = if (tableLength >= OS2_MIN_CODEPAGE_LENGTH) {
            val cp1 = readUInt32BE(bytes, tableOffset + 78)
            (cp1 and (CP_SIMPLIFIED_CHINESE or CP_TRADITIONAL_CHINESE)) != 0L
        } else {
            false
        }

        return Triple(weight, isItalic, hasChinese)
    }

    private fun decodeNameString(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        platformId: Int,
        encodingId: Int
    ): String? {
        if (length == 0) return null
        return when (platformId) {
            PLATFORM_WINDOWS, PLATFORM_UNICODE -> {
                if (length % 2 != 0) return null
                String(bytes, offset, length, StandardCharsets.UTF_16BE)
            }
            PLATFORM_MACINTOSH -> {
                if (encodingId == 0) decodeMacRoman(bytes, offset, length) else null
            }
            else -> null
        }
    }

    private fun decodeMacRoman(bytes: ByteArray, offset: Int, length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            val b = bytes[offset + i].toInt() and 0xFF
            if (b < 0x80) {
                sb.append(b.toChar())
            } else {
                sb.append(MAC_ROMAN_HIGH[b - 0x80])
            }
        }
        return sb.toString()
    }

    private fun scorePlatform(platformId: Int, encodingId: Int, languageId: Int): Int {
        var score = when (platformId) {
            PLATFORM_WINDOWS -> if (encodingId == 1 || encodingId == 10) 30 else 20
            PLATFORM_UNICODE -> 25
            PLATFORM_MACINTOSH -> 10
            else -> 0
        }
        // 优先英文 (0x0409) 或中文 (0x0804 / 0x0404)
        if (platformId == PLATFORM_WINDOWS && (languageId == 0x0409 || languageId == 0x0804 || languageId == 0x0404)) {
            score += 5
        }
        return score
    }

    private fun readUInt16BE(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun readInt32BE(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun readUInt32BE(bytes: ByteArray, offset: Int): Long =
        readInt32BE(bytes, offset).toLong() and 0xFFFFFFFFL

    private data class ScoredName(val value: String, val score: Int)

    private val MAC_ROMAN_HIGH = charArrayOf(
        'Ä', 'Å', 'Ç', 'É', 'Ñ', 'Ö', 'Ü', 'á', 'à', 'â', 'ä', 'ã', 'å', 'ç', 'é', 'è',
        'ê', 'ë', 'í', 'ì', 'î', 'ï', 'ñ', 'ó', 'ò', 'ô', 'ö', 'õ', 'ú', 'ù', 'û', 'ü',
        '†', '°', '¢', '£', '§', '•', '¶', 'ß', '®', '©', '™', '´', '¨', '≠', 'Æ', 'Ø',
        '∞', '±', '≤', '≥', '¥', 'µ', '∂', '∑', '∏', 'π', '∫', 'ª', 'º', 'Ω', 'æ', 'ø',
        '¿', '¡', '¬', '√', 'ƒ', '≈', '∆', '«', '»', '…', '\u00A0', 'À', 'Ã', 'Õ', 'Œ', 'œ',
        '–', '—', '“', '”', '‘', '’', '÷', '◊', 'ÿ', 'Ÿ', '⁄', '€', '‹', '›', 'ﬁ', 'ﬂ',
        '‡', '·', '‚', '„', '‰', 'Â', 'Ê', 'Á', 'Ë', 'È', 'Í', 'Î', 'Ï', 'Ì', 'Ó', 'Ô',
        '\uF8FF', 'Ò', 'Ú', 'Û', 'Ù', 'ı', 'ˆ', '˜', '¯', '˘', '˙', '˚', '¸', '˝', '˛', 'ˇ'
    )
}
