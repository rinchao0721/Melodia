package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Base64

// 酷狗设备注册、登录、续期与风控服务：严格对照 KuGouMusicApi/module/*.js
class KugouAuthService(
    private val config: KugouConfig,
    private val deviceStore: KugouDeviceStore,
    private val gatewayClient: KugouGatewayClient,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // 对照 module/register_dev.js：加密上报设备信息并持久化服务端分配的 dfid
    suspend fun registerDevice(
        userid: String? = null,
        token: String? = null
    ): Result<String> = runCatchingCancellable {
        config.ensureModeSynced()
        val device = deviceStore.getOrCreateDeviceIdentity()
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        val effectiveUserId = (userid ?: activeAccount?.effectivePlatformUid ?: "0").toLongOrNull() ?: 0L
        val effectiveToken = token ?: activeAccount?.credentials?.get("token").orEmpty()

        val dataMap = buildJsonObject {
            put("availableRamSize", 4983533568L)
            put("availableRomSize", 48114719L)
            put("availableSDSize", 48114717L)
            put("basebandVer", "")
            put("batteryLevel", 100)
            put("batteryStatus", 3)
            put("brand", "Redmi")
            put("buildSerial", "unknown")
            put("device", "marble")
            put("imei", device.guid)
            put("imsi", "")
            put("manufacturer", "Xiaomi")
            put("uuid", device.guid)
            put("accelerometer", false)
            put("accelerometerValue", "")
            put("gravity", false)
            put("gravityValue", "")
            put("gyroscope", false)
            put("gyroscopeValue", "")
            put("light", false)
            put("lightValue", "")
            put("magnetic", false)
            put("magneticValue", "")
            put("orientation", false)
            put("orientationValue", "")
            put("pressure", false)
            put("pressureValue", "")
            put("step_counter", false)
            put("step_counterValue", "")
            put("temperature", false)
            put("temperatureValue", "")
        }.toString()

        val aesEncrypt = KugouCrypto.playlistAesEncrypt(dataMap)
        val pPayload = buildJsonObject {
            put("aes", aesEncrypt.key)
            put("uid", effectiveUserId)
            put("token", effectiveToken)
        }.toString()
        val p = KugouCrypto.rsaEncrypt2(pPayload, config.effectivePublicKeyPem())

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                baseUrl = "https://userservice.kugou.com",
                url = "/risk/v3/r_register_dev",
                params = mapOf("part" to 1, "platid" to 1, "p" to p),
                data = aesEncrypt.str,
                encryptType = KugouEncryptType.ANDROID,
                binaryResponse = true
            )
        )
        KugouRiskException.fromResponse(response)?.let { throw it }

        val responseBase64 = Base64.getEncoder().encodeToString(response.bodyBytes)
        val decryptedText = KugouCrypto.playlistAesDecrypt(responseBase64, aesEncrypt.key)
        val body = json.parseToJsonElement(decryptedText).jsonObject
        val status = body["status"]?.jsonPrimitive?.intOrNull ?: 0
        val errorCode = body["error_code"]?.jsonPrimitive?.intOrNull ?: 0
        if (status != 1 || errorCode != 0) {
            throw AppError.BizError(
                errorCode.takeIf { it != 0 } ?: status,
                body["error_msg"]?.jsonPrimitive?.contentOrNull ?: "酷狗设备注册失败"
            )
        }
        val dfid = body["data"]?.jsonObject?.get("dfid")?.jsonPrimitive?.contentOrNull.orEmpty()
        require(dfid.isNotBlank()) { "酷狗设备注册未返回有效 dfid" }
        deviceStore.updateDfid(dfid)
        dfid
    }

    // 对照 module/login_qr_key.js + module/login_qr_create.js
    suspend fun createQrLogin(isWeb: Boolean = false): Result<KugouQrCreateResult> = runCatchingCancellable {
        config.ensureModeSynced()
        val mode = config.mode
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                baseUrl = "https://login-user.kugou.com",
                url = "/v2/qrcode",
                params = mapOf(
                    "appid" to if (isWeb) 1014 else 1001,
                    "type" to 1,
                    "plat" to 4,
                    "qrcode_txt" to "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=${mode.appid}&",
                    "srcappid" to KugouConfig.SRC_APPID
                ),
                encryptType = KugouEncryptType.WEB,
                skipActiveAccountCookies = true
            )
        )
        ensureBizSuccess(response, "获取酷狗登录二维码失败")
        val dataObj = response.bodyJson?.get("data")?.jsonObject
            ?: throw AppError.NetworkError
        val key = dataObj["qrcode"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(key.isNotBlank()) { "酷狗二维码 key 为空" }
        val qrImg = dataObj["qrcode_img"]?.jsonPrimitive?.contentOrNull
        val qrUrl = "https://h5.kugou.com/apps/loginQRCode/html/index.html?qrcode=$key"
        KugouQrCreateResult(
            key = key,
            qrUrl = qrUrl,
            qrImageBase64 = qrImg
        )
    }

    // 对照 module/login_qr_check.js：0=过期，1=等待扫码，2=待确认，4=授权登录成功
    suspend fun pollQrLogin(key: String): Result<KugouQrPollResult> = runCatchingCancellable {
        config.ensureModeSynced()
        val device = deviceStore.getOrCreateDeviceIdentity()
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                baseUrl = "https://login-user.kugou.com",
                url = "/v2/get_userinfo_qrcode",
                params = mapOf(
                    "plat" to 4,
                    "appid" to config.mode.appid,
                    "srcappid" to KugouConfig.SRC_APPID,
                    "qrcode" to key,
                    "dev" to device.dev
                ),
                encryptType = KugouEncryptType.WEB,
                skipActiveAccountCookies = true
            )
        )
        ensureBizSuccess(response, "查询酷狗二维码状态失败")
        val dataObj = response.bodyJson?.get("data")?.jsonObject
            ?: throw AppError.NetworkError
        val qrStatus = dataObj["status"]?.jsonPrimitive?.intOrNull ?: -1
        val token = dataObj["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val userid = dataObj["userid"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val nickname = dataObj["nickname"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val pic = dataObj["pic"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val mappedState = when (qrStatus) {
            0 -> KugouQrPollResult.State.EXPIRED
            1 -> KugouQrPollResult.State.WAITING
            2 -> KugouQrPollResult.State.CONFIRMING
            4 -> KugouQrPollResult.State.AUTHORIZED
            else -> KugouQrPollResult.State.FAILED
        }
        KugouQrPollResult(
            state = mappedState,
            token = token,
            userid = userid,
            nickname = nickname,
            avatarUrl = pic,
            rawStatus = qrStatus,
            message = response.errorMsg
        )
    }

    // 对照 module/captcha_sent.js
    suspend fun sendPhoneCaptcha(mobile: String): Result<Unit> = runCatchingCancellable {
        val device = deviceStore.getOrCreateDeviceIdentity()
        val body = buildJsonObject {
            put("businessid", 5)
            put("mobile", mobile)
            put("plat", 3)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                baseUrl = "http://login.user.kugou.com",
                url = "/v7/send_mobile_code",
                data = body,
                encryptType = KugouEncryptType.ANDROID,
                cookieOverrides = mapOf("mid" to device.mid),
                skipActiveAccountCookies = true
            )
        )
        ensureBizSuccess(response, "发送酷狗短信验证码失败")
    }

    // 对照 module/login_cellphone.js：含 params/pk/t1/t2 加密及响应 secu_params 解密
    suspend fun loginByPhone(
        mobile: String,
        code: String,
        userid: String? = null
    ): Result<KugouAuthCredentials> = runCatchingCancellable {
        config.ensureModeSynced()
        val mode = config.mode
        val device = deviceStore.getOrCreateDeviceIdentity()
        val dateTime = nowMs()

        val plainPayload = buildJsonObject {
            put("mobile", mobile)
            put("code", code)
        }.toString()
        val encrypt = KugouCrypto.cryptoAesEncrypt(plainPayload)

        val maskedMobile = if (mobile.length >= 11) {
            "${mobile.substring(0, 2)}*****${mobile.substring(10, 11)}"
        } else {
            mobile
        }
        val effectiveDfid = device.dfid.takeIf { it.isNotBlank() && it != "-" }
            ?: KugouCrypto.randomString(24)

        val t2 = KugouCrypto.cryptoAesEncryptWithKeyIv(
            data = "${device.guid}|0f607264fc6318a92b9e13c65db7cd3c|${device.mac}|${device.dev}|$dateTime",
            key = KugouConfig.LITE_T2_KEY,
            iv = KugouConfig.LITE_T2_IV
        )
        val t1 = KugouCrypto.cryptoAesEncryptWithKeyIv(
            data = "|$dateTime",
            key = KugouConfig.LITE_T1_KEY,
            iv = KugouConfig.LITE_T1_IV
        )
        val pkPayload = buildJsonObject {
            put("clienttime_ms", dateTime)
            put("key", encrypt.key)
        }.toString()
        val pk = KugouCrypto.cryptoRSAEncrypt(pkPayload, config.effectivePublicKeyPem()).uppercase()
        val signKeyParam = KugouSigner.signParamsKey(dateTime.toString(), mode = mode)

        val dataObj = buildJsonObject {
            put("plat", 1)
            put("support_multi", 1)
            if (mode.isLite) put("t1", t1) else put("t1", 0)
            if (mode.isLite) put("t2", t2) else put("t2", 0)
            put("clienttime_ms", dateTime)
            put("mobile", maskedMobile)
            put("key", signKeyParam)
            put("pk", pk)
            put("params", encrypt.str)
            if (!userid.isNullOrBlank()) {
                put("userid", userid)
            }
            if (mode.isLite) {
                put("dfid", effectiveDfid)
                put("dev", device.dev)
                put("gitversion", "5f0b7c4")
            } else {
                put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=")
            }
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                baseUrl = "https://loginserviceretry.kugou.com",
                url = "/v7/login_by_verifycode",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf(
                    "support-calm" to "1",
                    "User-Agent" to "Android16-1070-11440-130-0-LOGIN-wifi"
                ),
                cookieOverrides = mapOf("dfid" to effectiveDfid),
                skipActiveAccountCookies = true
            )
        )
        ensureBizSuccess(response, "酷狗手机验证码登录失败")
        val respData = response.bodyJson?.get("data")?.jsonObject
            ?: throw AppError.NetworkError
        extractCredentialsFromSecuParams(respData, encrypt.key)
    }

    // 对照 module/login_token.js：刷新 token 并自动回写 UserPreferences 凭据
    suspend fun refreshToken(
        token: String? = null,
        userid: String? = null,
        t1Cookie: String? = null
    ): Result<KugouAuthCredentials> = runCatchingCancellable {
        config.ensureModeSynced()
        val mode = config.mode
        val device = deviceStore.getOrCreateDeviceIdentity()
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()

        val effectiveToken = token ?: activeAccount?.credentials?.get("token").orEmpty()
        val effectiveUserId = userid
            ?: activeAccount?.credentials?.get("userid")
            ?: activeAccount?.effectivePlatformUid
            ?: "0"
        val effectiveT1 = t1Cookie ?: activeAccount?.credentials?.get("t1").orEmpty()

        val dateNow = nowMs()
        val p3Plain = buildJsonObject {
            put("clienttime", dateNow / 1000L)
            put("token", effectiveToken)
        }.toString()
        val p3 = KugouCrypto.cryptoAesEncryptWithKeyIv(
            data = p3Plain,
            key = mode.loginTokenKey,
            iv = mode.loginTokenIv
        )
        val encryptParams = KugouCrypto.cryptoAesEncrypt("{}")
        val pkPayload = buildJsonObject {
            put("clienttime_ms", dateNow)
            put("key", encryptParams.key)
        }.toString()
        val pk = KugouCrypto.cryptoRSAEncrypt(pkPayload, config.effectivePublicKeyPem())
        val t2 = KugouCrypto.cryptoAesEncryptWithKeyIv(
            data = "${device.guid}|0f607264fc6318a92b9e13c65db7cd3c|${device.mac}|${device.dev}|$dateNow",
            key = KugouConfig.LITE_T2_KEY,
            iv = KugouConfig.LITE_T2_IV
        )
        val t1 = KugouCrypto.cryptoAesEncryptWithKeyIv(
            data = if (effectiveT1.isNotBlank()) "$effectiveT1|$dateNow" else "|$dateNow",
            key = KugouConfig.LITE_T1_KEY,
            iv = KugouConfig.LITE_T1_IV
        )

        val dataObj = buildJsonObject {
            put("dfid", device.effectiveDfid)
            put("p3", p3)
            put("plat", 1)
            if (mode.isLite) put("t1", t1) else put("t1", 0)
            if (mode.isLite) put("t2", t2) else put("t2", 0)
            put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=")
            put("pk", pk)
            put("params", encryptParams.str)
            put("userid", effectiveUserId)
            put("clienttime_ms", dateNow)
            if (mode.isLite) {
                put("dev", device.dev)
            }
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                baseUrl = "http://login.user.kugou.com",
                url = "/v5/login_by_token",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                cookieOverrides = mapOf(
                    "token" to effectiveToken,
                    "userid" to effectiveUserId
                )
            )
        )
        ensureBizSuccess(response, "刷新酷狗登录态失败")
        val respData = response.bodyJson?.get("data")?.jsonObject
            ?: throw AppError.NetworkError
        val credentials = extractCredentialsFromSecuParams(respData, encryptParams.key)

        val targetId = credentials.userid.takeIf { it.isNotBlank() && it != "0" } ?: effectiveUserId
        if (targetId.isNotBlank() && targetId != "0") {
            userPreferences.updatePlatformCredentials(
                accountId = AccountId.kugou(targetId),
                credentials = credentials.toCredentialMap(),
                lastRefreshAt = dateNow
            )
        }
        credentials
    }

    // 对照 module/user_detail.js
    suspend fun fetchUserDetail(
        token: String? = null,
        userid: String? = null
    ): Result<KugouUserDetailDto> = runCatchingCancellable {
        config.ensureModeSynced()
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        val effectiveToken = token ?: activeAccount?.credentials?.get("token").orEmpty()
        val effectiveUserId = userid
            ?: activeAccount?.credentials?.get("userid")
            ?: activeAccount?.effectivePlatformUid
            ?: "0"
        val uidLong = effectiveUserId.toLongOrNull() ?: 0L
        val clientTimeSec = nowMs() / 1000L

        val pkPayload = buildJsonObject {
            put("token", effectiveToken)
            put("clienttime", clientTimeSec)
        }.toString()
        val pk = KugouCrypto.cryptoRSAEncrypt(pkPayload, config.effectivePublicKeyPem()).uppercase()

        val dataObj = buildJsonObject {
            put("visit_time", clientTimeSec)
            put("usertype", 1)
            put("p", pk)
            put("userid", uidLong)
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v3/get_my_info",
                params = mapOf("plat" to 1),
                data = dataObj,
                headers = mapOf("x-router" to "usercenter.kugou.com"),
                encryptType = KugouEncryptType.ANDROID,
                cookieOverrides = buildMap {
                    if (effectiveToken.isNotBlank()) put("token", effectiveToken)
                    if (effectiveUserId.isNotBlank()) put("userid", effectiveUserId)
                }
            )
        )
        ensureBizSuccess(response, "获取酷狗用户信息失败", notifyUnauthorized = true)
        val dataObjResp = response.bodyJson?.get("data")?.jsonObject
            ?: throw AppError.NetworkError
        json.decodeFromJsonElement(KugouUserDetailDto.serializer(), dataObjResp)
    }

    // 对照 module/user_vip_detail.js
    suspend fun fetchVipDetail(
        cookieOverrides: Map<String, String> = emptyMap()
    ): Result<KugouVipDetailDto> = runCatchingCancellable {
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                baseUrl = "https://kugouvip.kugou.com",
                url = "/v1/get_union_vip",
                params = mapOf("busi_type" to "concept"),
                encryptType = KugouEncryptType.ANDROID,
                cookieOverrides = cookieOverrides
            )
        )
        ensureBizSuccess(response, "获取酷狗 VIP 信息失败", notifyUnauthorized = true)
        val dataObj = response.bodyJson?.get("data")?.jsonObject
            ?: throw AppError.NetworkError
        val busiNames = dataObj["busi_vip"]?.let { elem ->
            runCatchingCancellable {
                elem.jsonArray.mapNotNull { item ->
                    item.jsonObject["product_name"]?.jsonPrimitive?.contentOrNull
                }
            }.getOrDefault(emptyList())
        }.orEmpty()
        val baseDto = json.decodeFromJsonElement(KugouVipDetailDto.serializer(), dataObj)
        baseDto.copy(busiVipNames = busiNames)
    }

    // 对照 module/user_verify.js：获取播放鉴权所需 auth 凭据
    suspend fun verifyPlaybackAuth(
        cookieOverrides: Map<String, String> = emptyMap()
    ): Result<String> = runCatchingCancellable {
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                baseUrl = "http://gateway.kugou.com",
                url = "/v1/user_verify",
                params = mapOf("module_id" to 51),
                headers = mapOf("x-router" to "trackercdngz.kugou.com"),
                encryptType = KugouEncryptType.ANDROID,
                cookieOverrides = cookieOverrides
            )
        )
        ensureBizSuccess(response, "酷狗播放权限校验失败", notifyUnauthorized = true)
        val auth = response.bodyJson?.get("data")?.jsonObject?.get("auth")?.jsonPrimitive?.contentOrNull.orEmpty()
        require(auth.isNotBlank()) { "酷狗 user_verify 未返回 auth 凭据" }
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        val uid = cookieOverrides["userid"] ?: activeAccount?.effectivePlatformUid
        if (!uid.isNullOrBlank() && uid != "0") {
            userPreferences.updatePlatformCredentials(
                accountId = AccountId.kugou(uid),
                credentials = mapOf("auth" to auth)
            )
        }
        auth
    }

    // 对照 module/get_verify_info.js
    suspend fun getVerifyInfo(
        eventId: String,
        platId: Int = 2,
        userid: String? = null
    ): Result<KugouVerifyInfoDto> = runCatchingCancellable {
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        val uidLong = (userid ?: activeAccount?.effectivePlatformUid ?: "0").toLongOrNull() ?: 0L
        val dataObj = buildJsonObject {
            put("eventid", eventId)
            put("userid", uidLong)
            put("platid", platId)
            put("rtype", 1)
            put("wasm", 1)
            put("i", "")
            put("sid", "")
            put("edt", "")
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/verifyservice/v3/get_verify_info",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureBizSuccess(response, "获取酷狗验证信息失败")
        KugouVerifyInfoDto(
            eventId = eventId,
            rawJson = response.bodyText
        )
    }

    private fun extractCredentialsFromSecuParams(respData: JsonObject, aesTempKey: String): KugouAuthCredentials {
        val extraMap = LinkedHashMap<String, String>()
        val mergedData = LinkedHashMap<String, String>()
        respData.forEach { (k, v) ->
            if (v is JsonPrimitive) {
                mergedData[k] = v.content
            }
        }

        val secuParams = respData["secu_params"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (secuParams.isNotBlank()) {
            val decrypted = KugouCrypto.cryptoAesDecrypt(secuParams, aesTempKey)
            val parsedObj = runCatchingCancellable { json.parseToJsonElement(decrypted).jsonObject }.getOrNull()
            if (parsedObj != null) {
                parsedObj.forEach { (k, v) ->
                    if (v is JsonPrimitive) {
                        mergedData[k] = v.content
                        extraMap[k] = v.content
                    }
                }
            } else if (decrypted.isNotBlank()) {
                mergedData["token"] = decrypted
                extraMap["token"] = decrypted
            }
        }

        val resolvedToken = mergedData["token"].orEmpty()
        val resolvedUserId = mergedData["userid"].orEmpty().ifBlank { "0" }
        val resolvedT1 = mergedData["t1"].orEmpty()
        val resolvedVipType = mergedData["vip_type"]?.toIntOrNull() ?: 0
        val resolvedVipToken = mergedData["vip_token"].orEmpty()
        val nickname = mergedData["nickname"].orEmpty()
        val avatar = mergedData["pic"].orEmpty()

        return KugouAuthCredentials(
            userid = resolvedUserId,
            token = resolvedToken,
            t1 = resolvedT1,
            vipType = resolvedVipType,
            vipToken = resolvedVipToken,
            nickname = nickname,
            avatarUrl = avatar,
            extra = extraMap
        )
    }

    private fun ensureBizSuccess(
        response: KugouRawResponse,
        fallbackMessage: String,
        notifyUnauthorized: Boolean = false
    ) {
        ensureKugouBizSuccess(response, fallbackMessage, notifyUnauthorized)
    }
}
