package com.lin0721.linmusic.core.ui.theme.vibrant

import androidx.compose.ui.graphics.Color

// MMCQ（Modified Median Cut Quantization），对应 vibrant-quantizer-mmcq/src/index.ts。
// 先按 population 排序分裂到 0.75 倍目标色数，再按 population*volume 重排序分裂到目标色数
private const val FRACT_BY_POPULATION = 0.75

internal fun medianCutQuantize(
    pixels: IntArray,
    colorCount: Int,
    filter: (r: Int, g: Int, b: Int, a: Int) -> Boolean,
): List<VibrantSwatch> {
    if (pixels.isEmpty() || colorCount < 2) return emptyList()

    val histogram = VibrantHistogram(pixels, filter)
    if (histogram.isEmpty()) return emptyList()

    val rootBox = VBox(
        histogram.rMin, histogram.rMax,
        histogram.gMin, histogram.gMax,
        histogram.bMin, histogram.bMax,
        histogram,
    )

    val byPopulation = VibrantPQueue<VBox>(compareBy { it.count() })
    byPopulation.push(rootBox)
    splitBoxes(byPopulation, (FRACT_BY_POPULATION * colorCount).toInt())

    val byPopulationVolume = VibrantPQueue<VBox>(compareBy { it.count().toLong() * it.volume() })
    byPopulation.toList().forEach { byPopulationVolume.push(it) }
    splitBoxes(byPopulationVolume, colorCount - byPopulationVolume.size())

    return generateSwatches(byPopulationVolume)
}

private fun splitBoxes(pq: VibrantPQueue<VBox>, target: Int) {
    var lastSize = pq.size()
    while (pq.size() < target) {
        val vbox = pq.pop() ?: break
        if (vbox.count() <= 0) break

        val children = vbox.split()
        val vbox1 = children.getOrNull(0) ?: break
        pq.push(vbox1)
        val vbox2 = children.getOrNull(1)
        if (vbox2 != null && vbox2.count() > 0) pq.push(vbox2)

        // 分裂不出新盒子了，说明已经收敛，不再继续
        if (pq.size() == lastSize) break
        lastSize = pq.size()
    }
}

private fun generateSwatches(pq: VibrantPQueue<VBox>): List<VibrantSwatch> {
    val swatches = mutableListOf<VibrantSwatch>()
    while (pq.size() > 0) {
        val vbox = pq.pop() ?: break
        val avg = vbox.avg()
        swatches += VibrantSwatch(
            rgb = Color(avg[0] / 255f, avg[1] / 255f, avg[2] / 255f),
            population = vbox.count(),
        )
    }
    return swatches
}
