package com.lin0721.linmusic.core.ui.theme.vibrant

import kotlin.math.abs

// node-vibrant 生成的 6 分类色板，字段语义与 node-vibrant 的 Palette 接口一一对应。
// 某个分类在候选里找不到满足饱和度/明度区间的 swatch 时就是 null——不借用其他分类的色相
// 合成假色，降级到哪个真实分类交给调用方（pickBaseColor）的优先级链
data class VibrantPalette(
    val vibrant: VibrantSwatch?,
    val lightVibrant: VibrantSwatch?,
    val darkVibrant: VibrantSwatch?,
    val muted: VibrantSwatch?,
    val lightMuted: VibrantSwatch?,
    val darkMuted: VibrantSwatch?,
)

// 默认参数照抄 vibrant-generator-default/src/index.ts 的 DefaultOpts
private const val TARGET_DARK_LUMA = 0.26f
private const val MAX_DARK_LUMA = 0.45f
private const val MIN_LIGHT_LUMA = 0.55f
private const val TARGET_LIGHT_LUMA = 0.74f
private const val MIN_NORMAL_LUMA = 0.3f
private const val TARGET_NORMAL_LUMA = 0.5f
private const val MAX_NORMAL_LUMA = 0.7f
private const val TARGET_MUTED_SATURATION = 0.3f
private const val MAX_MUTED_SATURATION = 0.4f
private const val TARGET_VIBRANT_SATURATION = 1.0f
private const val MIN_VIBRANT_SATURATION = 0.35f
private const val WEIGHT_SATURATION = 3f
private const val WEIGHT_LUMA = 6.5f
private const val WEIGHT_POPULATION = 0.5f

// 6 个分类各自在饱和度/明度区间内找一个综合得分最高的候选，同一个 swatch 不会被两个分类复用。
// 不做 node-vibrant 原版的空色板合成兜底——降级到哪个真实分类交给 pickBaseColor 的优先级链
internal fun generateVibrantPalette(swatches: List<VibrantSwatch>): VibrantPalette {
    val maxPopulation = swatches.maxOfOrNull { it.population } ?: 0

    val vibrant = findColorVariation(
        swatches, emptyList(), maxPopulation,
        TARGET_NORMAL_LUMA, MIN_NORMAL_LUMA, MAX_NORMAL_LUMA,
        TARGET_VIBRANT_SATURATION, MIN_VIBRANT_SATURATION, 1f,
    )
    val lightVibrant = findColorVariation(
        swatches, listOfNotNull(vibrant), maxPopulation,
        TARGET_LIGHT_LUMA, MIN_LIGHT_LUMA, 1f,
        TARGET_VIBRANT_SATURATION, MIN_VIBRANT_SATURATION, 1f,
    )
    val darkVibrant = findColorVariation(
        swatches, listOfNotNull(vibrant, lightVibrant), maxPopulation,
        TARGET_DARK_LUMA, 0f, MAX_DARK_LUMA,
        TARGET_VIBRANT_SATURATION, MIN_VIBRANT_SATURATION, 1f,
    )
    val muted = findColorVariation(
        swatches, listOfNotNull(vibrant, lightVibrant, darkVibrant), maxPopulation,
        TARGET_NORMAL_LUMA, MIN_NORMAL_LUMA, MAX_NORMAL_LUMA,
        TARGET_MUTED_SATURATION, 0f, MAX_MUTED_SATURATION,
    )
    val lightMuted = findColorVariation(
        swatches, listOfNotNull(vibrant, lightVibrant, darkVibrant, muted), maxPopulation,
        TARGET_LIGHT_LUMA, MIN_LIGHT_LUMA, 1f,
        TARGET_MUTED_SATURATION, 0f, MAX_MUTED_SATURATION,
    )
    val darkMuted = findColorVariation(
        swatches, listOfNotNull(vibrant, lightVibrant, darkVibrant, muted, lightMuted), maxPopulation,
        TARGET_DARK_LUMA, 0f, MAX_DARK_LUMA,
        TARGET_MUTED_SATURATION, 0f, MAX_MUTED_SATURATION,
    )

    return VibrantPalette(vibrant, lightVibrant, darkVibrant, muted, lightMuted, darkMuted)
}

private fun findColorVariation(
    swatches: List<VibrantSwatch>,
    exclude: List<VibrantSwatch>,
    maxPopulation: Int,
    targetLuma: Float,
    minLuma: Float,
    maxLuma: Float,
    targetSaturation: Float,
    minSaturation: Float,
    maxSaturation: Float,
): VibrantSwatch? {
    var best: VibrantSwatch? = null
    var bestValue = 0f
    for (swatch in swatches) {
        if (exclude.any { it === swatch }) continue
        val s = swatch.hsl[1]
        val l = swatch.hsl[2]
        if (s in minSaturation..maxSaturation && l in minLuma..maxLuma) {
            val value = comparisonValue(s, targetSaturation, l, targetLuma, swatch.population, maxPopulation)
            // value 可能是 NaN（见 comparisonValue 说明），NaN 的比较恒为 false，
            // 这里刻意不做特殊处理，跟上游 JS 的 `value > maxValue` 行为保持一致
            if (best == null || value > bestValue) {
                best = swatch
                bestValue = value
            }
        }
    }
    return best
}

// 三项加权平均：饱和度贴近目标程度、明度贴近目标程度、population 占比。
// 若某一项恰好算出 0（即该项数值本身为 0，而非贡献为 0），
// 会连同权重一起被跳过，不计入分母——三项全部被跳过时结果是 NaN
private fun comparisonValue(
    saturation: Float,
    targetSaturation: Float,
    luma: Float,
    targetLuma: Float,
    population: Int,
    maxPopulation: Int,
): Float {
    val saturationScore = 1f - abs(saturation - targetSaturation)
    val lumaScore = 1f - abs(luma - targetLuma)
    val populationRatio = if (maxPopulation == 0) 0f else population.toFloat() / maxPopulation

    var sum = 0f
    var weightSum = 0f
    if (saturationScore != 0f) {
        sum += saturationScore * WEIGHT_SATURATION
        weightSum += WEIGHT_SATURATION
    }
    if (lumaScore != 0f) {
        sum += lumaScore * WEIGHT_LUMA
        weightSum += WEIGHT_LUMA
    }
    if (populationRatio != 0f) {
        sum += populationRatio * WEIGHT_POPULATION
        weightSum += WEIGHT_POPULATION
    }
    return sum / weightSum
}
