package com.lin0721.linmusic.core.kugou

import java.io.ByteArrayOutputStream

// 酷狗请求签名工具：逐行对照 KuGouMusicApi/util/helper.js
object KugouSigner {

    // 对照 helper.js#signatureWebParams：先拼 "k=v" 再按字典序排序后拼接，两端加 Web 盐值
    fun signatureWebParams(params: Map<String, Any?>, data: String? = null): String {
        val salt = KugouConfig.WEB_SIGN_SALT
        val paramsString = params.entries
            .map { (key, value) -> "$key=${value ?: ""}" }
            .sorted()
            .joinToString("")
        return KugouCrypto.md5Hex("$salt$paramsString${data.orEmpty()}$salt")
    }

    // 对照 helper.js#signatureAndroidParams（字符串请求体）：先按 key 排序再拼 "k=v"，两端加模式对应盐值
    fun signatureAndroidParams(
        params: Map<String, Any?>,
        data: String? = null,
        mode: KugouClientMode = KugouClientMode.LITE
    ): String {
        val salt = mode.androidSignSalt
        val paramsString = params.keys
            .sorted()
            .joinToString("") { key -> "$key=${params[key] ?: ""}" }
        return KugouCrypto.md5Hex("$salt$paramsString${data.orEmpty()}$salt")
    }

    // 对照 helper.js#signatureAndroidParams（二进制请求体）
    fun signatureAndroidParams(
        params: Map<String, Any?>,
        data: ByteArray,
        mode: KugouClientMode = KugouClientMode.LITE
    ): String {
        val saltBytes = mode.androidSignSalt.toByteArray(Charsets.UTF_8)
        val paramsString = params.keys
            .sorted()
            .joinToString("") { key -> "$key=${params[key] ?: ""}" }
        val paramsBytes = paramsString.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(saltBytes.size * 2 + paramsBytes.size + data.size)
        out.write(saltBytes)
        out.write(paramsBytes)
        out.write(data)
        out.write(saltBytes)
        return KugouCrypto.md5Hex(out.toByteArray())
    }

    // 对照 helper.js#signatureRegisterParams：只取参数值排序拼接，两端加 "1014"
    fun signatureRegisterParams(params: Map<String, Any?>): String {
        val salt = KugouConfig.REGISTER_SIGN_SALT
        val paramsString = params.values
            .map { it?.toString().orEmpty() }
            .sorted()
            .joinToString("")
        return KugouCrypto.md5Hex("$salt$paramsString$salt")
    }

    // 对照 helper.js#signParams：按 key 排序，无等号拼接 "k+v"，末尾加请求体与盐值
    fun signParams(params: Map<String, Any?>, data: String? = null): String {
        val salt = KugouConfig.SIGN_PARAMS_SALT
        val paramsString = params.keys
            .sorted()
            .joinToString("") { key -> "$key${params[key] ?: ""}" }
        return KugouCrypto.md5Hex("$paramsString${data.orEmpty()}$salt")
    }

    // 对照 helper.js#signKey：MD5(hash + salt + appid + mid + userid)
    fun signKey(
        hash: String,
        mid: String,
        userid: Any? = 0,
        appid: Int? = null,
        mode: KugouClientMode = KugouClientMode.LITE
    ): String {
        val useAppid = appid ?: mode.appid
        val uidStr = userid?.toString()?.takeIf { it.isNotBlank() } ?: "0"
        return KugouCrypto.md5Hex("${hash}${mode.signKeySalt}${useAppid}${mid}${uidStr}")
    }

    // 对照 helper.js#signCloudKey：MD5("musicclound" + hash + pid + salt)
    fun signCloudKey(hash: String, pid: String): String =
        KugouCrypto.md5Hex("musicclound${hash}${pid}${KugouConfig.SIGN_CLOUD_KEY_SALT}")

    // 对照 helper.js#signParamsKey：MD5(appid + salt + clientver + data)
    fun signParamsKey(
        data: String,
        appid: Int? = null,
        clientver: Int? = null,
        mode: KugouClientMode = KugouClientMode.LITE
    ): String {
        val useAppid = appid ?: mode.appid
        val useClientver = clientver ?: mode.clientver
        return KugouCrypto.md5Hex("${useAppid}${mode.androidSignSalt}${useClientver}${data}")
    }
}
