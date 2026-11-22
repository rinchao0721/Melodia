package com.lin0721.linmusic.core.kugou

import java.security.SecureRandom
import java.util.Base64
import kotlin.math.max
import kotlin.math.roundToInt

// 酷狗 SSA 风控挑战上下文（当响应头带 ssa-code 时附带模拟指纹 sid/edt）
data class KugouSsaChallenge(
    val ssaCode: String,
    val sid: String,
    val edt: String
)

// 轻量化行为指纹模拟器：对照 KuGouMusicApi/util/generate_simulate.js
object KugouRiskSimulator {

    private const val SIMULATE_IV = "kugousecurity123"
    private const val SIMULATE_PUBLIC_KEY_PEM =
        "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoW2+Ylo8ALePSQTP0xBF\n" +
            "lFmEOHvBD9tS+s7DBlfKEu3RzzvZTaX1JtYbX4+AVUqj6ARz8IM+CKByqGFvbHN/\n" +
            "W64XxNI+q7z36ajCL3VTJ2W5G9MCJitc6oGbire4NQfhaEq0nC+hxBWQvCbIFflA\n" +
            "2ItrLUbSU7z1bHA/a+jlQm4OWvY+IKnTryOJTPuT1yNOVjbJ8wBLKy2DgQr9pPqW\n" +
            "PmEQtGpR5IM9V8Kao6PaSdKYOWGbX3i2+RzIKhvZUxxtJwdVbqPlDPlW9h4/xIBc\n" +
            "56Lgvr4aIl8nFtwbj4UJVUTFuGrs0tY9H/tXvZ22dUCKuGxW/gW7ZF+gXz6vHtYa\n" +
            "rQIDAQAB\n" +
            "-----END PUBLIC KEY-----"

    private val random = SecureRandom()

    fun generateSimulate(
        mid: String,
        userid: String,
        dfid: String,
        webglHash: String,
        ssaCode: String = "",
        timestampMs: Long = System.currentTimeMillis()
    ): KugouSsaChallenge {
        val sentinel = 0xFFFFFFFFL - random.nextInt(20)
        val aesKey = KugouCrypto.md5Hex(KugouCrypto.randomString(16)).substring(0, 16)
        val data = generateEdtData(sentinel)
        val safeMid = mid.ifBlank { "0" }
        val safeUserId = userid.ifBlank { "0" }
        val safeDfid = dfid.ifBlank { "0" }
        val plaintext = "mid=$safeMid;userid=$safeUserId;dfid=$safeDfid;webgl=$webglHash;webdriver=0;ts=$timestampMs;data=$data"

        val edtBytes = KugouCrypto.aesCbcEncryptBytes(
            plainBytes = plaintext.toByteArray(Charsets.UTF_8),
            keyBytes = aesKey.toByteArray(Charsets.UTF_8),
            ivBytes = SIMULATE_IV.toByteArray(Charsets.UTF_8)
        )
        val edt = Base64.getEncoder().encodeToString(edtBytes)
        val sid = KugouCrypto.rsaOaepSha256EncryptBase64(
            data = aesKey.toByteArray(Charsets.UTF_8),
            publicKeyPem = SIMULATE_PUBLIC_KEY_PEM
        )
        return KugouSsaChallenge(ssaCode = ssaCode, sid = sid, edt = edt)
    }

    private fun generateEdtData(sentinel: Long): String {
        val points = nextInt(30, 60)
        val startX = nextInt(200, 600).toDouble()
        val startY = nextInt(200, 500).toDouble()
        val endX = nextInt(500, 700).toDouble()
        val endY = nextInt(80, 150).toDouble()

        val entries = mutableListOf<String>()
        var ts = 0
        var ei = 0

        entries.add("5,0,0")
        entries.add("5,$sentinel,0")
        entries.add("5,0,0")
        entries.add("5,$sentinel,0")

        ts += nextInt(5, 20)
        entries.add("6,$ts,$ei,750,500")
        entries.add("6,$sentinel,$ei,750,500")
        ei++

        repeat(3) {
            ts += nextInt(80, 600)
            entries.add("5,$ts,$ei")
            entries.add("5,$sentinel,$ei")
            ei++
        }

        val path = bezierPath(startX, startY, endX, endY, points)
        var si = 0
        for (i in path.indices) {
            val (px, py) = path[i]
            ts += nextInt(8, 50)
            val rx = px.roundToInt()
            val ry = py.roundToInt()
            entries.add("3,$ts,$si,$rx,$ry")
            entries.add("3,$sentinel,$si,$rx,$ry")
            if (i > 0 && i % 12 == 0) {
                ts += nextInt(20, 60)
                entries.add("5,$ts,$ei")
                entries.add("5,$sentinel,$ei")
                ei++
            }
            si = (si + 1) % 2
        }

        ts += nextInt(5, 30)
        entries.add("3,$ts,1,${(endX + nextInt(-5, 5)).roundToInt()},${(endY + nextInt(-5, 5)).roundToInt()}")
        entries.add("3,$sentinel,1,${endX.roundToInt()},${endY.roundToInt()}")
        return entries.joinToString(":")
    }

    private fun bezierPath(sx: Double, sy: Double, ex: Double, ey: Double, n: Int): List<Pair<Double, Double>> {
        val c1x = sx + (ex - sx) * 0.3 + nextInt(-80, 80)
        val c1y = sy + (ey - sy) * 0.2 + nextInt(-60, 60)
        val c2x = sx + (ex - sx) * 0.7 + nextInt(-60, 60)
        val c2y = sy + (ey - sy) * 0.8 + nextInt(-40, 40)

        val pts = ArrayList<Pair<Double, Double>>(n + 1)
        for (i in 0..n) {
            val t = i.toDouble() / n.toDouble()
            val u = 1.0 - t
            val x = u * u * u * sx + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * ex
            val y = u * u * u * sy + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * ey
            val jitter = max(0.5, 3.0 - t * 2.5)
            pts.add(
                (x + (random.nextDouble() - 0.5) * jitter) to
                    (y + (random.nextDouble() - 0.5) * jitter)
            )
        }
        return pts
    }

    private fun nextInt(min: Int, max: Int): Int = min + random.nextInt(max - min + 1)
}
