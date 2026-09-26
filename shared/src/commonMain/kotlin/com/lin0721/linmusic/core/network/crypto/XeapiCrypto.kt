package com.lin0721.linmusic.core.network.crypto

import java.net.URLEncoder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// xeapi 公钥状态：version 用于服务端判断密钥是否需要刷新，sk 是注册后返回的长期设备密钥
data class XeapiPublicKeyState(
    val version: String,
    val publicKey: String,
    val sk: String?
)

// 网易云 xeapi 加密：X25519 密钥协商 + AES-GCM/ECB + HMAC 签名。
object XeapiCrypto {

    // AES-256-ECB 静态密钥，用于加密请求体内层 + 加密 R 段 + 解密设备注册响应
    internal val XEAPI_STATIC_KEY: ByteArray = hexToBytes(
        "ab1d5a430f6bb04a3f01e81ddd72bd916d5ce591248ac128714806d7f8fb1b84"
    )

    // HMAC 签名密钥；HMAC 的 key 参数直接使用这个字面量的 UTF-8 字节，不能 base64 解码
    private const val XEAPI_SIGN_KEY =
        "mUHCwVNWJbunMqAHf5MImuirT6plvs6VSFW62MGHstFQxhBGdEoIhLItH3djc4+FB/OKty3+lL2rGeoFBpVe5g=="

    // AES-128-ECB，与现有 NeteaseCrypto 的 EAPI_KEY 同值，用于解密 xeapi 响应体
    private const val EAPI_KEY = "e82ckenh8dichen8"

    // RFC8410 X25519 公钥 DER SPKI 前缀（12 字节）：Android 只暴露 32 字节原始公钥，
    // 导入 BouncyCastle 的 JCA KeyFactory 需要补全这段固定头部
    private val X25519_SPKI_PREFIX = hexToBytes("302a300506032b656e032100")

    // RFC8410 X25519 私钥 DER PKCS8 前缀（16 字节），仅测试里导入固定私钥复现向量时使用
    private val X25519_PKCS8_PREFIX = hexToBytes("302e020100300506032b656e04220420")

    private val bcProvider: Provider = BouncyCastleProvider()

    fun generateEphemeralKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("X25519", bcProvider).generateKeyPair()

    fun rawPublicKeyBytes(publicKey: PublicKey): ByteArray {
        val encoded = publicKey.encoded
        return encoded.copyOfRange(encoded.size - 32, encoded.size)
    }

    fun importPeerPublicKey(rawBytes: ByteArray): PublicKey {
        val spki = X25519_SPKI_PREFIX + rawBytes
        return KeyFactory.getInstance("X25519", bcProvider).generatePublic(X509EncodedKeySpec(spki))
    }

    // 仅供单测复现固定密钥对；生产路径的临时私钥始终来自 generateEphemeralKeyPair()
    internal fun importPrivateKeyForTest(rawBytes: ByteArray): PrivateKey {
        val pkcs8 = X25519_PKCS8_PREFIX + rawBytes
        return KeyFactory.getInstance("X25519", bcProvider).generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }

    fun computeSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("X25519", bcProvider)
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)
        return agreement.generateSecret()
    }

    // HKDF 风格派生：零盐 HMAC-Extract 得到 prk，再用 (ephemeralPub||0x01) HMAC-Expand 取前 16 字节做 AES-128 密钥
    fun deriveAesKey(sharedSecret: ByteArray, ephemeralPublicRaw: ByteArray): ByteArray {
        val prk = hmacSha256(ByteArray(32), if (sharedSecret.isEmpty()) ByteArray(32) else sharedSecret)
        val okm = hmacSha256(prk, ephemeralPublicRaw + byteArrayOf(1))
        return okm.copyOfRange(0, 16)
    }

    // AES/GCM/NoPadding：Java 的 doFinal 已把 16 字节认证 tag 拼接在密文末尾，调用方无需再单独处理 tag
    fun gcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)
    }

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    // 设备注册取公钥请求的签名：HMAC-SHA256(XEAPI_SIGN_KEY, timestamp+nonce)
    fun sign(timestamp: String, nonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(XEAPI_SIGN_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal((timestamp + nonce).toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    fun aesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(plaintext)
    }

    fun aesEcbDecrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(ciphertext)
    }

    // 解密设备注册接口返回的 encryptedData，还原出公钥状态
    fun decryptPublicKeyResponse(encryptedDataBase64: String): XeapiPublicKeyState {
        val plaintext = aesEcbDecrypt(XEAPI_STATIC_KEY, Base64.getDecoder().decode(encryptedDataBase64))
        val json = JSONObject(String(plaintext, Charsets.UTF_8))
        return XeapiPublicKeyState(
            version = json.optString("version", ""),
            publicKey = json.optString("publicKey", ""),
            sk = if (json.has("sk") && !json.isNull("sk")) json.getString("sk") else null
        )
    }

    // 解密业务接口响应体：AES-ECB(eapiKey) 解密后按 gzip 魔数判断是否需要解压
    fun decryptResponseBody(body: ByteArray): String {
        val decrypted = aesEcbDecrypt(EAPI_KEY.toByteArray(Charsets.UTF_8), body)
        val isGzip = decrypted.size >= 2 &&
            decrypted[0] == 0x1f.toByte() &&
            decrypted[1] == 0x8b.toByte()
        val plaintext = if (isGzip) {
            GZIPInputStream(ByteArrayInputStream(decrypted)).use { it.readBytes() }
        } else {
            decrypted
        }
        return String(plaintext, Charsets.UTF_8)
    }

    data class XeapiEncryptedForm(val b: String, val s: String, val r: String)

    private val secureRandom = SecureRandom()

    // application/x-www-form-urlencoded 编码，字段顺序沿用调用方传入 Map 的迭代顺序
    private fun encodeForm(data: Map<String, String>): String =
        data.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

    // 构造加密前的明文 JSON：本项目三个 xeapi 写接口都不带查询参数、都是 POST、都用默认 content-type，
    // 因此只需要 body+queryString 两个字段，字段顺序（body 先于 queryString）
    internal fun buildPlaintext(uri: String, data: Map<String, String>): String {
        val bodyBase64 = Base64.getEncoder().encodeToString(encodeForm(data).toByteArray(Charsets.UTF_8))
        return "{\"body\":\"$bodyBase64\",\"queryString\":\"e_r=true\"}"
    }

    // 自定义混淆：用随机掩码 XOR 密文、base64 编码后按掩码首字节循环移位，前置掩码本身
    internal fun midTransform(ciphertext: ByteArray, randomMask: ByteArray): ByteArray {
        val xored = ByteArray(ciphertext.size) { i -> (ciphertext[i].toInt() xor randomMask[i and 0x0f].toInt()).toByte() }
        val b64 = Base64.getEncoder().encodeToString(xored).toByteArray(Charsets.UTF_8)
        val rotate = if (b64.isNotEmpty()) (randomMask[0].toInt() and 0x0f) % b64.size else 0
        return randomMask + b64.copyOfRange(rotate, b64.size) + b64.copyOfRange(0, rotate)
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    // 组装 xeapi 请求体的 B/S/R 三段表单字段。除 uri/data/publicKeyState/os 外的参数只用于单测注入固定值，
    // 生产调用方（CryptoInterceptor）不需要传，全部走默认的真随机实现
    fun assembleRequest(
        uri: String,
        data: Map<String, String>,
        publicKeyState: XeapiPublicKeyState,
        os: String = "android",
        dynamicKey: ByteArray = randomBytes(16),
        midTransformMask: ByteArray = randomBytes(16),
        ephemeralKeyPair: KeyPair = generateEphemeralKeyPair(),
        gcmIv: ByteArray = randomBytes(12)
    ): XeapiEncryptedForm {
        val plaintext = buildPlaintext(uri, data).toByteArray(Charsets.UTF_8)
        val innerCipher = aesEcbEncrypt(XEAPI_STATIC_KEY, plaintext)
        val transformed = midTransform(innerCipher, midTransformMask)
        val b = aesEcbEncrypt(dynamicKey, transformed)

        val ephemeralPubRaw = rawPublicKeyBytes(ephemeralKeyPair.public)
        val peerPublicKey = importPeerPublicKey(Base64.getDecoder().decode(publicKeyState.publicKey))
        val sharedSecret = computeSharedSecret(ephemeralKeyPair.private, peerPublicKey)
        val aesKey = deriveAesKey(sharedSecret, ephemeralPubRaw)
        val dynamicKeyBase64 = Base64.getEncoder().encodeToString(dynamicKey)
        val gcmPlaintext = "$dynamicKeyBase64|$os|${publicKeyState.sk ?: ""}".toByteArray(Charsets.UTF_8)
        val gcmOutput = gcmEncrypt(aesKey, gcmIv, gcmPlaintext)
        val s = ephemeralPubRaw + gcmIv + gcmOutput

        val r = aesEcbEncrypt(XEAPI_STATIC_KEY, "${publicKeyState.version}|".toByteArray(Charsets.UTF_8))

        return XeapiEncryptedForm(
            b = Base64.getEncoder().encodeToString(b),
            s = Base64.getEncoder().encodeToString(s),
            r = Base64.getEncoder().encodeToString(r)
        )
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte() }
}
