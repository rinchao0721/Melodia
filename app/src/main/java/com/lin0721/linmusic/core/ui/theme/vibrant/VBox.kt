package com.lin0721.linmusic.core.ui.theme.vibrant

// 颜色立方体（Volume Box），对应 vibrant-quantizer-mmcq/src/vbox.ts。
// count/avg/split 的实现细节（尤其 split 的中位切割）逐行照抄上游逻辑，
// 只是把 JS 的 falsy 判断换成显式的边界检查
internal class VBox(
    var r1: Int, var r2: Int,
    var g1: Int, var g2: Int,
    var b1: Int, var b2: Int,
    val histogram: VibrantHistogram,
) {
    private var cachedVolume = -1
    private var cachedCount = -1
    private var cachedAvg: IntArray? = null

    fun volume(): Int {
        if (cachedVolume < 0) {
            cachedVolume = (r2 - r1 + 1) * (g2 - g1 + 1) * (b2 - b1 + 1)
        }
        return cachedVolume
    }

    fun count(): Int {
        if (cachedCount < 0) {
            val hist = histogram.hist
            var c = 0
            for (r in r1..r2) for (g in g1..g2) for (b in b1..b2) {
                c += hist[histogram.colorIndex(r, g, b)]
            }
            cachedCount = c
        }
        return cachedCount
    }

    fun clone(): VBox = VBox(r1, r2, g1, g2, b1, b2, histogram)

    fun avg(): IntArray {
        cachedAvg?.let { return it }
        val hist = histogram.hist
        val mult = 1 shl VibrantHistogram.RSHIFT
        var nTotal = 0L
        var rSum = 0.0
        var gSum = 0.0
        var bSum = 0.0
        for (r in r1..r2) for (g in g1..g2) for (b in b1..b2) {
            val h = hist[histogram.colorIndex(r, g, b)]
            if (h == 0) continue
            nTotal += h
            rSum += h * (r + 0.5) * mult
            gSum += h * (g + 0.5) * mult
            bSum += h * (b + 0.5) * mult
        }
        val result = if (nTotal > 0) {
            intArrayOf((rSum / nTotal).toInt(), (gSum / nTotal).toInt(), (bSum / nTotal).toInt())
        } else {
            intArrayOf(
                mult * (r1 + r2 + 1) / 2,
                mult * (g1 + g2 + 1) / 2,
                mult * (b1 + b2 + 1) / 2,
            )
        }
        cachedAvg = result
        return result
    }

    // 沿最长的一条边做中位切割：先找出让像素数量对半的分割点，
    // 再按左右哪边更窄做微调（贴向更窄的一侧），避免切出全空的子盒子
    fun split(): List<VBox> {
        val cnt = count()
        if (cnt == 0) return emptyList()
        if (cnt == 1) return listOf(clone())

        val rw = r2 - r1 + 1
        val gw = g2 - g1 + 1
        val bw = b2 - b1 + 1
        val maxw = maxOf(rw, gw, bw)
        val hist = histogram.hist

        val dim: Char
        val d1: Int
        val d2Orig: Int
        val accSum: IntArray
        var total = 0

        when (maxw) {
            rw -> {
                dim = 'r'; d1 = r1; d2Orig = r2
                accSum = IntArray(r2 + 1)
                for (r in r1..r2) {
                    var sum = 0
                    for (g in g1..g2) for (b in b1..b2) sum += hist[histogram.colorIndex(r, g, b)]
                    total += sum
                    accSum[r] = total
                }
            }
            gw -> {
                dim = 'g'; d1 = g1; d2Orig = g2
                accSum = IntArray(g2 + 1)
                for (g in g1..g2) {
                    var sum = 0
                    for (r in r1..r2) for (b in b1..b2) sum += hist[histogram.colorIndex(r, g, b)]
                    total += sum
                    accSum[g] = total
                }
            }
            else -> {
                dim = 'b'; d1 = b1; d2Orig = b2
                accSum = IntArray(b2 + 1)
                for (b in b1..b2) {
                    var sum = 0
                    for (r in r1..r2) for (g in g1..g2) sum += hist[histogram.colorIndex(r, g, b)]
                    total += sum
                    accSum[b] = total
                }
            }
        }

        var splitPoint = -1
        val reverseSum = IntArray(accSum.size)
        for (i in accSum.indices) {
            val d = accSum[i]
            if (d == 0) continue
            if (splitPoint < 0 && d > total / 2) splitPoint = i
            reverseSum[i] = total - d
        }
        if (splitPoint < 0) splitPoint = d2Orig

        val left = splitPoint - d1
        val right = d2Orig - splitPoint
        var cut = if (left <= right) {
            maxOf(0, minOf(d2Orig - 1, splitPoint + right / 2))
        } else {
            minOf(d2Orig, maxOf(d1, splitPoint - 1 - left / 2))
        }

        while (accSum[cut] == 0) cut++
        var tailCount = reverseSum[cut]
        while (tailCount == 0 && cut > 0 && accSum[cut - 1] != 0) {
            cut--
            tailCount = reverseSum[cut]
        }

        val vbox1 = clone()
        val vbox2 = clone()
        when (dim) {
            'r' -> { vbox1.r2 = cut; vbox2.r1 = cut + 1 }
            'g' -> { vbox1.g2 = cut; vbox2.g1 = cut + 1 }
            else -> { vbox1.b2 = cut; vbox2.b1 = cut + 1 }
        }
        return listOf(vbox1, vbox2)
    }
}
