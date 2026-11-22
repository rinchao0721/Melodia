package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.PlatformRiskChallenge
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KugouQrCreateResult(
    val key: String,
    val qrUrl: String,
    val qrImageBase64: String? = null
)

@Serializable
data class KugouQrPollData(
    val status: Int = 0,
    val token: String = "",
    val userid: Long = 0L,
    val nickname: String = "",
    val pic: String = ""
)

@Serializable
data class KugouQrPollResult(
    val state: State,
    val token: String = "",
    val userid: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val rawStatus: Int = 0,
    val message: String = ""
) {
    enum class State {
        EXPIRED,
        WAITING,
        CONFIRMING,
        AUTHORIZED,
        FAILED
    }
}

@Serializable
data class KugouAuthCredentials(
    val userid: String,
    val token: String,
    val t1: String = "",
    val vipType: Int = 0,
    val vipToken: String = "",
    val auth: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val extra: Map<String, String> = emptyMap()
) {
    fun toCredentialMap(): Map<String, String> = buildMap {
        putAll(extra)
        put("userid", userid)
        put("token", token)
        if (t1.isNotBlank()) put("t1", t1)
        put("vip_type", vipType.toString())
        if (vipToken.isNotBlank()) put("vip_token", vipToken)
        if (auth.isNotBlank()) put("auth", auth)
    }
}

@Serializable
data class KugouUserDetailDto(
    val userid: Long = 0L,
    val nickname: String = "",
    val pic: String = "",
    val signature: String = "",
    @SerialName("vip_type") val vipType: Int = 0,
    @SerialName("is_vip") val isVip: Int = 0,
    val gender: Int = 0
)

@Serializable
data class KugouVipDetailDto(
    @SerialName("is_vip") val isVip: Int = 0,
    @SerialName("vip_type") val vipType: Int = 0,
    @SerialName("vip_begin_time") val vipBeginTime: String = "",
    @SerialName("vip_end_time") val vipEndTime: String = "",
    @SerialName("su_vip_begin_time") val suVipBeginTime: String = "",
    @SerialName("su_vip_end_time") val suVipEndTime: String = "",
    val busiVipNames: List<String> = emptyList()
)

@Serializable
data class KugouVerifyInfoDto(
    val eventId: String = "",
    val rawJson: String = ""
)

class KugouRiskException(
    val challenge: PlatformRiskChallenge,
    override val message: String = challenge.message.ifBlank { "触发酷狗风控验证" }
) : RuntimeException(message) {
    companion object {
        fun fromResponse(response: KugouRawResponse): KugouRiskException? {
            val ssa = response.ssaChallenge ?: return null
            return KugouRiskException(
                challenge = PlatformRiskChallenge(
                    platform = MusicPlatform.KUGOU,
                    ssaCode = ssa.ssaCode,
                    message = response.errorMsg.ifBlank { "风控验证(ssa-code=${ssa.ssaCode})" },
                    sid = ssa.sid,
                    edt = ssa.edt
                )
            )
        }
    }
}
