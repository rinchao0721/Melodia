package com.lin0721.linmusic.core.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes

// 封面取色区分组；皮肤文件 cover 下的 key，改名会让已发布的皮肤失效
enum class CoverGroup(val key: String) {
    HEADER("header"),
    CARDS("cards"),
    PLAYER("player");

    companion object {
        fun fromKey(key: String): CoverGroup? = entries.firstOrNull { it.key == key }
    }
}

enum class CoverSource(val key: String) {
    COVER("cover"),
    COLOR("color"),
    BACKDROP("backdrop");

    companion object {
        fun fromKey(key: String): CoverSource? = entries.firstOrNull { it.key == key }
    }
}

// color 来源时 dark / light 至少有一个；缺失的模式按跟随封面处理
data class CoverRule(val source: CoverSource, val dark: Color? = null, val light: Color? = null) {
    fun color(isDark: Boolean): Color? = if (isDark) dark else light

    companion object {
        val Default = CoverRule(CoverSource.COVER)
    }
}

// 运行时某一组的实际取色方式
sealed interface CoverTint {
    data object FollowCover : CoverTint
    data class Fixed(val color: Color) : CoverTint
    data object Backdrop : CoverTint
}

@Immutable
data class CoverPolicy(
    val header: CoverTint = CoverTint.FollowCover,
    val cards: CoverTint = CoverTint.FollowCover,
    val player: CoverTint = CoverTint.FollowCover
) {
    fun tint(group: CoverGroup): CoverTint = when (group) {
        CoverGroup.HEADER -> header
        CoverGroup.CARDS -> cards
        CoverGroup.PLAYER -> player
    }

    // 透出背景图时返回 null，其余返回参与色调换算的原色
    fun base(group: CoverGroup, extracted: Color): Color? = when (val tint = tint(group)) {
        CoverTint.FollowCover -> extracted
        is CoverTint.Fixed -> tint.color
        CoverTint.Backdrop -> null
    }

    fun isBackdrop(group: CoverGroup): Boolean = tint(group) == CoverTint.Backdrop

    companion object {
        val Default = CoverPolicy()
    }
}

// backdropReady 为背景图已解码可用；不可用时透出背景图的组退回跟随封面
fun Skin?.coverPolicy(isDark: Boolean, backdropReady: Boolean): CoverPolicy {
    val cover = this?.cover ?: return CoverPolicy.Default
    fun resolve(rule: CoverRule): CoverTint = when (rule.source) {
        CoverSource.COVER -> CoverTint.FollowCover
        CoverSource.COLOR -> rule.color(isDark)?.let(CoverTint::Fixed) ?: CoverTint.FollowCover
        CoverSource.BACKDROP -> if (backdropReady) CoverTint.Backdrop else CoverTint.FollowCover
    }
    return CoverPolicy(resolve(cover.rule(CoverGroup.HEADER)), resolve(cover.rule(CoverGroup.CARDS)), resolve(cover.rule(CoverGroup.PLAYER)))
}

data class SkinCover(val rules: Map<CoverGroup, CoverRule> = emptyMap()) {
    fun rule(group: CoverGroup): CoverRule = rules[group] ?: CoverRule.Default
}

val LocalCoverPolicy = staticCompositionLocalOf { CoverPolicy.Default }

@Composable
@ReadOnlyComposable
fun coverBase(group: CoverGroup, extracted: Color): Color? = LocalCoverPolicy.current.base(group, extracted)

@Composable
@ReadOnlyComposable
fun isCoverBackdrop(group: CoverGroup): Boolean = LocalCoverPolicy.current.isBackdrop(group)

// 沉浸区（有色底白字）使用的深色色板：内置深色叠加皮肤的深色配色，主色由调用方按当前主题再设置
fun immersiveBasePalette(skin: Skin?): MelodiaPalette =
    MelodiaPalettes.dark.withOverrides(skin?.darkColors.orEmpty() - PaletteToken.accentDerived - PaletteToken.ACCENT)

val LocalImmersiveBasePalette = staticCompositionLocalOf { MelodiaPalettes.dark }

// 导入预览里描述取色区设置；全部跟随封面时返回 null
fun coverSummary(skin: Skin): String? {
    val labels = mapOf(CoverGroup.HEADER to "页面头部", CoverGroup.CARDS to "封面色卡片", CoverGroup.PLAYER to "播放区")
    val parts = CoverGroup.entries.mapNotNull { group ->
        when (skin.cover.rule(group).source) {
            CoverSource.COVER -> null
            CoverSource.COLOR -> "${labels.getValue(group)}使用指定颜色"
            CoverSource.BACKDROP -> if (skin.background != null) "${labels.getValue(group)}透出背景图" else null
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("、")
}
