package com.lin0721.linmusic.core.update.domain

// tag_name → versionCode 的推导，算法必须与 .github/workflows/release.yml 的 shell 逻辑保持一致，
// 否则客户端判断的"有无更新"会跟 CI 实际打出来的包版本号对不上
object VersionCodeResolver {

    private const val BETA_MAX = 49
    private const val RC_MAX = 48

    fun resolve(tagName: String): Int? {
        val name = tagName.removePrefix("v")
        val dashIndex = name.indexOf('-')
        val base = if (dashIndex >= 0) name.substring(0, dashIndex) else name

        val parts = base.split(".")
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null

        val subCode = if (dashIndex < 0) {
            99
        } else {
            val suffix = name.substring(dashIndex + 1)
            val dotIndex = suffix.indexOf('.')
            if (dotIndex < 0) return null
            val kind = suffix.substring(0, dotIndex)
            val num = suffix.substring(dotIndex + 1).toIntOrNull() ?: return null
            when (kind) {
                "beta" -> if (num in 0..BETA_MAX) num else return null
                "rc" -> if (num in 0..RC_MAX) 50 + num else return null
                else -> return null
            }
        }

        return (major * 10000 + minor * 100 + patch) * 100 + subCode
    }
}
