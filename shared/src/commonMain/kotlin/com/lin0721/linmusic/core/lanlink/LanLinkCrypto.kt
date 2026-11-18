package com.lin0721.linmusic.core.lanlink

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object LanLinkCrypto {
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    const val AES_KEY_BYTES = 32
    const val GCM_IV_BYTES = 12
    const val NONCE_BYTES = 16
    const val SALT_BYTES = 16

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }

    fun toBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun fromBase64(text: String): ByteArray = Base64.getDecoder().decode(text)

    fun derivePairingKey(code: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    // RFC 5869 HKDF-SHA256，用于从长期设备密钥和双方 nonce 派生每次连接的会话密钥
    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int = AES_KEY_BYTES
    ): ByteArray {
        val prk = hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val okm = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val input = ByteArray(t.size + info.size + 1)
            System.arraycopy(t, 0, input, 0, t.size)
            System.arraycopy(info, 0, input, t.size, info.size)
            input[input.size - 1] = counter.toByte()
            t = hmacSha256(prk, input)
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, okm, offset, copyLen)
            offset += copyLen
            counter++
        }
        return okm
    }

    fun deriveSessionKey(
        deviceKey: ByteArray,
        clientNonce: ByteArray,
        serverNonce: ByteArray
    ): ByteArray {
        val salt = ByteArray(clientNonce.size + serverNonce.size)
        System.arraycopy(clientNonce, 0, salt, 0, clientNonce.size)
        System.arraycopy(serverNonce, 0, salt, clientNonce.size, serverNonce.size)
        return hkdfSha256(
            ikm = deviceKey,
            salt = salt,
            info = "melodia-lanlink-v1-session".toByteArray(Charsets.UTF_8),
            length = AES_KEY_BYTES
        )
    }

    fun pairingClientProof(
        pairingKey: ByteArray,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        clientDeviceId: String,
        serverDeviceId: String
    ): ByteArray = hmacSha256(
        pairingKey,
        transcript("pair-client-proof", clientNonce, serverNonce, clientDeviceId, serverDeviceId)
    )

    fun pairingServerProof(
        pairingKey: ByteArray,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        clientDeviceId: String,
        serverDeviceId: String
    ): ByteArray = hmacSha256(
        pairingKey,
        transcript("pair-server-proof", clientNonce, serverNonce, clientDeviceId, serverDeviceId)
    )

    fun authServerProof(
        deviceKey: ByteArray,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        clientDeviceId: String,
        serverDeviceId: String
    ): ByteArray = hmacSha256(
        deviceKey,
        transcript("auth-server-proof", clientNonce, serverNonce, clientDeviceId, serverDeviceId)
    )

    fun authClientProof(
        deviceKey: ByteArray,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        clientDeviceId: String,
        serverDeviceId: String
    ): ByteArray = hmacSha256(
        deviceKey,
        transcript("auth-client-proof", clientNonce, serverNonce, clientDeviceId, serverDeviceId)
    )

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun encryptAesGcm(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): ByteArray {
        require(iv.size == GCM_IV_BYTES) { "IV 必须为 $GCM_IV_BYTES 字节" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun decryptAesGcm(
        key: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): ByteArray {
        if (iv.size != GCM_IV_BYTES) throw GeneralSecurityException("无效 IV 长度: ${iv.size}")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    // 12 字节 IV 前置到密文前，供单次加密传输（如配对分发 deviceKey）使用
    fun sealWithRandomIv(key: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        val iv = randomBytes(GCM_IV_BYTES)
        val ciphertext = encryptAesGcm(key, iv, plaintext, aad)
        return ByteArray(iv.size + ciphertext.size).apply {
            System.arraycopy(iv, 0, this, 0, iv.size)
            System.arraycopy(ciphertext, 0, this, iv.size, ciphertext.size)
        }
    }

    fun openWithPrefixedIv(key: ByteArray, sealed: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        if (sealed.size <= GCM_IV_BYTES) throw GeneralSecurityException("密文长度不足")
        val iv = sealed.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = sealed.copyOfRange(GCM_IV_BYTES, sealed.size)
        return decryptAesGcm(key, iv, ciphertext, aad)
    }

    private fun transcript(
        label: String,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        clientDeviceId: String,
        serverDeviceId: String
    ): ByteArray {
        val labelBytes = label.toByteArray(Charsets.UTF_8)
        val clientIdBytes = clientDeviceId.toByteArray(Charsets.UTF_8)
        val serverIdBytes = serverDeviceId.toByteArray(Charsets.UTF_8)
        val total = 4 + labelBytes.size +
            4 + clientNonce.size +
            4 + serverNonce.size +
            4 + clientIdBytes.size +
            4 + serverIdBytes.size
        return ByteBuffer.allocate(total).apply {
            putInt(labelBytes.size); put(labelBytes)
            putInt(clientNonce.size); put(clientNonce)
            putInt(serverNonce.size); put(serverNonce)
            putInt(clientIdBytes.size); put(clientIdBytes)
            putInt(serverIdBytes.size); put(serverIdBytes)
        }.array()
    }
}

// 会话帧加密器：两个方向各用独立子密钥，IV 为 4 字节零前缀 + 8 字节递增序号；
// 共用一把密钥时双方 IV 可能撞车，且对端发来的帧可被原样反射回去解密成功
class LanLinkSessionCipher(sessionKey: ByteArray, isHost: Boolean) {
    private val sendKey = directionKey(sessionKey, if (isHost) HOST_TO_CLIENT else CLIENT_TO_HOST)
    private val receiveKey = directionKey(sessionKey, if (isHost) CLIENT_TO_HOST else HOST_TO_CLIENT)
    private val sendCounter = AtomicLong(0L)
    private var lastReceivedSeq = 0L

    fun encryptFrame(plaintext: ByteArray): ByteArray {
        val iv = ByteBuffer.allocate(LanLinkCrypto.GCM_IV_BYTES)
            .putInt(0)
            .putLong(sendCounter.incrementAndGet())
            .array()
        val ciphertext = LanLinkCrypto.encryptAesGcm(sendKey, iv, plaintext)
        return ByteArray(iv.size + ciphertext.size).apply {
            System.arraycopy(iv, 0, this, 0, iv.size)
            System.arraycopy(ciphertext, 0, this, iv.size, ciphertext.size)
        }
    }

    // 只由读循环单线程调用；序号必须严格递增，拒绝重放
    fun decryptFrame(framePayload: ByteArray): ByteArray {
        if (framePayload.size <= LanLinkCrypto.GCM_IV_BYTES) throw GeneralSecurityException("密文长度不足")
        val ivBuffer = ByteBuffer.wrap(framePayload, 0, LanLinkCrypto.GCM_IV_BYTES)
        if (ivBuffer.getInt() != 0) throw GeneralSecurityException("非法 IV 前缀")
        val seq = ivBuffer.getLong()
        if (seq <= lastReceivedSeq) throw GeneralSecurityException("帧序号未递增: $seq")
        val plaintext = LanLinkCrypto.openWithPrefixedIv(receiveKey, framePayload)
        lastReceivedSeq = seq
        return plaintext
    }

    private companion object {
        const val HOST_TO_CLIENT = "melodia-lanlink-v1-host-to-client"
        const val CLIENT_TO_HOST = "melodia-lanlink-v1-client-to-host"

        fun directionKey(sessionKey: ByteArray, label: String): ByteArray = LanLinkCrypto.hkdfSha256(
            ikm = sessionKey,
            salt = ByteArray(0),
            info = label.toByteArray(Charsets.UTF_8)
        )
    }
}
