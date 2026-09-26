package com.lin0721.linmusic.feature.music.domain

import com.lin0721.linmusic.feature.music.data.StyleArtistDto
import com.lin0721.linmusic.feature.music.data.StyleHeadDto
import com.lin0721.linmusic.feature.music.data.StylePlaylistDto
import com.lin0721.linmusic.feature.music.data.StylePreferenceData
import com.lin0721.linmusic.feature.music.data.StyleTagDto
import com.lin0721.linmusic.feature.music.data.StyleTagPortraitDto

// 模板串里的占位符形如 ${tagName}，值取自同名 pattern 键。
// 右花括号必须转义：桌面 JVM 容忍裸的 }，Android 会抛 PatternSyntaxException
private val PORTRAIT_PLACEHOLDER = Regex("""\$\{(\w+)\}""")

// 六位十六进制，服务端对部分曲风下发空串
private val HEX_COLOR = Regex("""^[0-9a-fA-F]{6}$""")

// 缺值占位符的临时标记，仅存活于替换过程中。取 NUL 是因为它绝不会出现在服务端文案里，
// 换成空格之类的常见字符会误伤正文；用 Char 构造以免源码里混入不可见字节。
// 注意不要把它拼进正则：桌面 JVM 能编译含 NUL 的 pattern，Android 直接抛 PatternSyntaxException。
private val EMPTY_SLOT_CHAR = Char(0)
private val EMPTY_SLOT = EMPTY_SLOT_CHAR.toString()
private const val SEPARATORS = "、，,"

// 曲风列表映射。丢掉无名或无 id 的条目，避免胶囊上出现空标签
fun List<StyleTagDto>.toMusicStyles(): List<MusicStyle> = mapNotNull { it.toMusicStyleOrNull() }

private fun StyleTagDto.toMusicStyleOrNull(): MusicStyle? {
    if (tagId <= 0 || tagName.isBlank()) return null
    return MusicStyle(
        id = tagId,
        name = tagName,
        enName = enName.orEmpty(),
        colorHex = colorDeep.normalizeHex(),
        children = childrenTags.mapNotNull { it.toMusicStyleOrNull() }
    )
}

// 偏好项本身不带配色，用同一响应里的 tags 补全，补不上就留空由 UI 兜底
fun StylePreferenceData.toStylePreferences(): List<StylePreference> {
    val colorById = tags.associate { it.tagId to it.colorDeep.normalizeHex() }
    return tagPreferenceVos.mapNotNull { dto ->
        if (dto.tagId <= 0 || dto.tagName.isBlank()) return@mapNotNull null
        StylePreference(
            id = dto.tagId,
            name = dto.tagName,
            // 服务端以字符串下发，脏值一律当 0 处理而不是让整屏崩掉
            ratio = dto.ratio.trim().toIntOrNull() ?: 0,
            colorHex = colorById[dto.tagId]
        )
    }
}

fun StyleHeadDto.toStyleHead(): StyleHead = StyleHead(
    id = tagId,
    name = name,
    enName = enName.orEmpty(),
    // 服务端文案里混有全角空格与换行，直接展示会在卡片上留下断行
    desc = desc.orEmpty().replace('　', ' ').lines().joinToString(" ") { it.trim() }.trim(),
    coverUrl = cover.firstOrNull()?.takeIf { it.isNotBlank() },
    colorHex = colorDeep.normalizeHex(),
    songNum = songNum.orEmpty(),
    artistNum = artistNum.orEmpty(),
    favouriteSong = favouriteSong?.favouriteSong,
    portrait = tagPortrait?.toPortraitOrNull()
)

// 模板固定预留 23 个小众曲风位，服务端给不满是常态。缺值的占位符先打标记，
// 再连同紧邻的一个顿号一并抹掉，否则界面上会留下「情绪说唱、、、这些小众曲风」。
private fun StyleTagPortraitDto.toPortraitOrNull(): StylePortrait? {
    val template = templateContent?.takeIf { it.isNotBlank() } ?: return null

    val marked = PORTRAIT_PLACEHOLDER.replace(template) { match ->
        pattern[match.groupValues[1]]?.text?.takeIf { it.isNotBlank() } ?: EMPTY_SLOT
    }
    val cleaned = marked.stripEmptySlots()
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")

    return if (cleaned.isBlank()) null else StylePortrait(cleaned, dataTip.orEmpty())
}

// 每个空槽只吞掉一个相邻分隔符：优先回删前面那个，前面没有才吃后面那个。
// 两侧都吃会把「A、空、B」压成「AB」，把本该保留的分隔符也弄丢。
private fun String.stripEmptySlots(): String {
    if (indexOf(EMPTY_SLOT_CHAR) < 0) return this

    val result = StringBuilder(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char != EMPTY_SLOT_CHAR) {
            result.append(char)
            index++
            continue
        }
        if (result.isNotEmpty() && result.last() in SEPARATORS) {
            result.deleteCharAt(result.length - 1)
        } else if (index + 1 < length && this[index + 1] in SEPARATORS) {
            index++
        }
        index++
    }
    return result.toString()
}

fun List<StylePlaylistDto>.toStylePlaylistItems(): List<StylePlaylistItem> = mapNotNull { dto ->
    if (dto.id <= 0 || dto.name.isBlank()) return@mapNotNull null
    val cover = dto.cover?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    StylePlaylistItem(dto.id, dto.name, cover, dto.playCount)
}

// 歌手有方图与 1:1 两种图，优先取 1:1 —— 圆形头像裁方图容易切到脸
fun List<StyleArtistDto>.toStyleArtistItems(): List<StyleArtistItem> = mapNotNull { dto ->
    if (dto.id <= 0 || dto.name.isBlank()) return@mapNotNull null
    val pic = dto.img1v1Url?.takeIf { it.isNotBlank() }
        ?: dto.picUrl?.takeIf { it.isNotBlank() }
        ?: return@mapNotNull null
    StyleArtistItem(dto.id, dto.name, pic, dto.musicSize)
}

private fun String?.normalizeHex(): String? = this?.trim()?.takeIf { HEX_COLOR.matches(it) }
