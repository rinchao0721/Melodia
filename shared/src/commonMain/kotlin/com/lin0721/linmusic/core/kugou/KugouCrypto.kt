package com.lin0721.linmusic.core.kugou

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

// 酷狗加解密结果（str 为密文，key 为随机生成或指定的临时密钥）
data class KugouAesResult(
    val str: String,
    val key: String
)

// 酷狗独立加解密工具集：纯 JDK 实现，逐行对照 KuGouMusicApi/util/crypto.js 与 util/util.js
object KugouCrypto {

    private const val RANDOM_ALPHANUMERIC = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val MAX_KRC_DECOMPRESSED_BYTES = 4 * 1024 * 1024
    private val KRC_XOR_KEY = byteArrayOf(
        64, 71, 97, 119, 94, 50, 116, 71,
        81, 54, 49, 45, 206.toByte(), 210.toByte(), 110, 105
    )

    private val secureRandom = SecureRandom()
    private val rsaKeyCache = ConcurrentHashMap<String, RSAPublicKey>()

    fun md5Hex(input: String): String = md5Hex(input.toByteArray(Charsets.UTF_8))

    fun md5Hex(bytes: ByteArray): String = digestHex("MD5", bytes)

    fun sha1Hex(input: String): String = sha1Hex(input.toByteArray(Charsets.UTF_8))

    fun sha1Hex(bytes: ByteArray): String = digestHex("SHA-1", bytes)

    private fun digestHex(algorithm: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return bytesToHex(digest)
    }

    fun randomString(length: Int = 16): String {
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(RANDOM_ALPHANUMERIC[secureRandom.nextInt(RANDOM_ALPHANUMERIC.length)])
        }
        return sb.toString()
    }

    // 对照 crypto.js#cryptoAesEncrypt（不传 iv 时由 tempKey MD5 派生 key/iv，返回 Hex 密文与 tempKey）
    fun cryptoAesEncrypt(data: String, tempKey: String? = null): KugouAesResult {
        val actualTempKey = tempKey ?: randomString(16).lowercase()
        val derivedKey = md5Hex(actualTempKey).substring(0, 32)
        val derivedIv = derivedKey.substring(derivedKey.length - 16)
        val cipherBytes = aesCbcEncryptBytes(
            plainBytes = data.toByteArray(Charsets.UTF_8),
            keyBytes = derivedKey.toByteArray(Charsets.UTF_8),
            ivBytes = derivedIv.toByteArray(Charsets.UTF_8)
        )
        return KugouAesResult(str = bytesToHex(cipherBytes), key = actualTempKey)
    }

    // 对照 crypto.js#cryptoAesEncrypt(data, { key, iv })（直接传入 key/iv，返回 Hex 密文）
    fun cryptoAesEncryptWithKeyIv(data: String, key: String, iv: String): String {
        val cipherBytes = aesCbcEncryptBytes(
            plainBytes = data.toByteArray(Charsets.UTF_8),
            keyBytes = key.toByteArray(Charsets.UTF_8),
            ivBytes = iv.toByteArray(Charsets.UTF_8)
        )
        return bytesToHex(cipherBytes)
    }

    // 对照 crypto.js#cryptoAesDecrypt（iv 为空时由 key MD5 派生，输入 Hex 密文返回 UTF-8 明文）
    fun cryptoAesDecrypt(cipherHex: String, key: String, iv: String? = null): String {
        val resolvedKey = if (iv == null) md5Hex(key).substring(0, 32) else key
        val resolvedIv = iv ?: resolvedKey.substring(resolvedKey.length - 16)
        val plainBytes = aesCbcDecryptBytes(
            cipherBytes = hexToBytes(cipherHex),
            keyBytes = resolvedKey.toByteArray(Charsets.UTF_8),
            ivBytes = resolvedIv.toByteArray(Charsets.UTF_8)
        )
        return plainBytes.toString(Charsets.UTF_8)
    }

    // 对照 crypto.js#playlistAesEncrypt（6 字符随机小写 key，AES-128-CBC，Base64 密文）
    fun playlistAesEncrypt(data: String, fixedKey: String? = null): KugouAesResult {
        val key = fixedKey ?: randomString(6).lowercase()
        val keyHash = md5Hex(key)
        val encryptKey = keyHash.substring(0, 16)
        val iv = keyHash.substring(16, 32)
        val cipherBytes = aesCbcEncryptBytes(
            plainBytes = data.toByteArray(Charsets.UTF_8),
            keyBytes = encryptKey.toByteArray(Charsets.UTF_8),
            ivBytes = iv.toByteArray(Charsets.UTF_8)
        )
        return KugouAesResult(
            str = Base64.getEncoder().encodeToString(cipherBytes),
            key = key
        )
    }

    // 对照 crypto.js#playlistAesDecrypt（Base64 密文 + 6 字符 key 解密为 UTF-8 明文）
    fun playlistAesDecrypt(base64Cipher: String, key: String): String {
        val keyHash = md5Hex(key)
        val encryptKey = keyHash.substring(0, 16)
        val iv = keyHash.substring(16, 32)
        val cipherBytes = Base64.getDecoder().decode(base64Cipher)
        val plainBytes = aesCbcDecryptBytes(
            cipherBytes = cipherBytes,
            keyBytes = encryptKey.toByteArray(Charsets.UTF_8),
            ivBytes = iv.toByteArray(Charsets.UTF_8)
        )
        return plainBytes.toString(Charsets.UTF_8)
    }

    fun aesCbcEncryptBytes(plainBytes: ByteArray, keyBytes: ByteArray, ivBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        return cipher.doFinal(plainBytes)
    }

    fun aesCbcDecryptBytes(cipherBytes: ByteArray, keyBytes: ByteArray, ivBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        return cipher.doFinal(cipherBytes)
    }

    // 对照 crypto.js#cryptoRSAEncrypt（无填充原始 RSA：右侧零填充至密钥字节长度后计算 m^e mod n）
    fun cryptoRSAEncrypt(data: String, publicKeyPem: String): String =
        cryptoRSAEncrypt(data.toByteArray(Charsets.UTF_8), publicKeyPem)

    fun cryptoRSAEncrypt(data: ByteArray, publicKeyPem: String): String {
        val pubKey = parsePublicKey(publicKeyPem)
        val keyLength = (pubKey.modulus.bitLength() + 7) / 8
        require(data.size <= keyLength) { "RSA 明文长度(${data.size})超过密钥字节长度($keyLength)" }
        val padded = ByteArray(keyLength)
        System.arraycopy(data, 0, padded, 0, data.size)
        val message = BigInteger(1, padded)
        val encrypted = message.modPow(pubKey.publicExponent, pubKey.modulus)
        return encrypted.toString(16).padStart(keyLength * 2, '0')
    }

    // 对照 crypto.js#rsaEncrypt2（RSA/ECB/PKCS1Padding，返回小写 Hex）
    fun rsaEncrypt2(data: String, publicKeyPem: String): String =
        rsaEncrypt2(data.toByteArray(Charsets.UTF_8), publicKeyPem)

    fun rsaEncrypt2(data: ByteArray, publicKeyPem: String): String {
        val pubKey = parsePublicKey(publicKeyPem)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pubKey)
        return bytesToHex(cipher.doFinal(data))
    }

    // 供风控行为指纹使用：RSA-OAEP SHA-256（MGF1 同样使用 SHA-256），返回 Base64
    fun rsaOaepSha256EncryptBase64(data: ByteArray, publicKeyPem: String): String {
        val pubKey = parsePublicKey(publicKeyPem)
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        val oaepSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )
        cipher.init(Cipher.ENCRYPT_MODE, pubKey, oaepSpec)
        return Base64.getEncoder().encodeToString(cipher.doFinal(data))
    }

    // 对照 util/util.js#calculateMid（MD5 16 进制摘要转十进制大整数字符串）
    fun calculateMid(guid: String): String {
        val hex = md5Hex(guid)
        return BigInteger(hex, 16).toString(10)
    }

    // 对照 util/util.js#decodeLyrics（跳过前 4 字节头 -> 16 字节固定密钥 XOR -> zlib 解压，限制最大解压体积防异常膨胀）
    fun decodeKrcLyrics(rawBytes: ByteArray): String {
        if (rawBytes.size <= 4) return ""
        val xorBytes = ByteArray(rawBytes.size - 4)
        for (i in xorBytes.indices) {
            xorBytes[i] = (rawBytes[i + 4].toInt() xor KRC_XOR_KEY[i % KRC_XOR_KEY.size].toInt()).toByte()
        }
        val inflater = Inflater()
        return try {
            inflater.setInput(xorBytes)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var totalRead = 0
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    if (totalRead + count > MAX_KRC_DECOMPRESSED_BYTES) {
                        return ""
                    }
                    output.write(buffer, 0, count)
                    totalRead += count
                }
            }
            output.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            ""
        } finally {
            inflater.end()
        }
    }

    fun decodeKrcLyrics(base64: String): String =
        runCatching { decodeKrcLyrics(Base64.getDecoder().decode(base64)) }.getOrDefault("")

    fun parsePublicKey(publicKeyPem: String): RSAPublicKey =
        rsaKeyCache.computeIfAbsent(publicKeyPem) {
            val cleaned = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s+".toRegex(), "")
            val decoded = Base64.getDecoder().decode(cleaned)
            val spec = X509EncodedKeySpec(decoded)
            KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
        }

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = digits[v ushr 4]
            hexChars[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hexChars)
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "十六进制字符串长度必须为偶数: ${clean.length}" }
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val hi = clean[i * 2].digitToInt(16)
            val lo = clean[i * 2 + 1].digitToInt(16)
            result[i] = ((hi shl 4) or lo).toByte()
        }
        return result
    }
}
