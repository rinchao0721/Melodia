package com.lin0721.linmusic.core.network.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class XeapiCryptoTest {

    // 【向量1】xeapiSign：timestamp+nonce 的 HMAC-SHA256(xeapiSignKey) base64
    @Test
    fun `sign与已知向量一致`() {
        val result = XeapiCrypto.sign("1700000000000", "1234567890123456")
        assertEquals("AfwKXk83sQ/wAKzoswSsn7/DgRvQ6zfI4O5eOSKnkIA=", result)
    }

    // 【向量2】AES-ECB(xeapiStaticKey) 往返
    @Test
    fun `decryptPublicKeyResponse还原出version_publicKey_sk`() {
        val result = XeapiCrypto.decryptPublicKeyResponse(
            "jg9tIwv6SBtCBuDTVIYiMMnd0Qt6QYXVSoLGo9sEFZvuICBGVbrD1tfWVZPDo9YbuLa37GfarCqFjSE41mw5Dw=="
        )
        assertEquals("1", result.version)
        assertEquals("AAAA", result.publicKey)
        assertEquals("deadbeef", result.sk)
    }

    @Test
    fun `decryptPublicKeyResponse在sk字段缺失时返回null`() {
        // {"version":"2","publicKey":"BBBB"} 用 xeapiStaticKey 手工加密得到的密文
        val plaintext = "{\"version\":\"2\",\"publicKey\":\"BBBB\"}".toByteArray(Charsets.UTF_8)
        val cipher = XeapiCrypto.aesEcbEncrypt(XeapiCrypto.XEAPI_STATIC_KEY, plaintext)
        val result = XeapiCrypto.decryptPublicKeyResponse(Base64.getEncoder().encodeToString(cipher))
        assertEquals("2", result.version)
        assertNull(result.sk)
    }

    // 【向量7】无 gzip 响应解密
    @Test
    fun `decryptResponseBody处理无gzip压缩的响应`() {
        val cipher = Base64.getDecoder().decode("VLwDVCg2o8SlTOwRfYqG8ZH5Fu0CNxJEdHtbOZ97uK8=")
        val json = XeapiCrypto.decryptResponseBody(cipher)
        assertEquals("{\"code\":200,\"comments\":[]}", json)
    }

    // 【向量8】带 gzip 压缩的响应解密
    @Test
    fun `decryptResponseBody处理gzip压缩的响应`() {
        val cipher = Base64.getDecoder().decode(
            "UtqDYh+i5LhGQT4EOiuvVda7MJ2EJAzxJ0oN09ZbVBE6XvJMNzZRrbdLGOZKN3C5jdOh/xHL9/rlqDGl82ooq0PMXqjKIAZOC0dd3zoigqM="
        )
        val json = XeapiCrypto.decryptResponseBody(cipher)
        assertEquals("{\"code\":200,\"comments\":[{\"id\":1,\"content\":\"hello\"}]}", json)
    }

    @Test
    fun `aesEcb加解密对任意明文往返一致`() {
        val plaintext = "hello xeapi 测试".toByteArray(Charsets.UTF_8)
        val cipher = XeapiCrypto.aesEcbEncrypt(XeapiCrypto.XEAPI_STATIC_KEY, plaintext)
        val decrypted = XeapiCrypto.aesEcbDecrypt(XeapiCrypto.XEAPI_STATIC_KEY, cipher)
        assertEquals("hello xeapi 测试", String(decrypted, Charsets.UTF_8))
    }

    // 【向量4】deriveAesKey：固定 sharedSecret/ephemeralPublic 得到确定输出
    @Test
    fun `deriveAesKey与已知向量一致`() {
        val sharedSecret = ByteArray(32) { 0x00 }
        val ephemeralPub = ByteArray(32) { 0x01 }
        val result = XeapiCrypto.deriveAesKey(sharedSecret, ephemeralPub)
        assertEquals("vZ+r8QDJdtDmBoCDOsX7KA==", Base64.getEncoder().encodeToString(result))
        assertEquals(16, result.size)
    }

    // 【向量6】X25519 共享密钥派生 + AES-128-GCM 加密，端到端比对已知向量
    @Test
    fun `X25519共享密钥与GCM加密匹配已知向量`() {
        val serverPubRaw = Base64.getDecoder().decode("EB6F4FI8SfVyIq1jmO6NNDHY1FJwZb2DswpwT+8WMHc=")
        val ephemeralPrivRaw = Base64.getDecoder().decode("AJ3FSuFJ4W+4D5GFXt271zi40IQxQX9pjcj+rqWSb1E=")
        val ephemeralPubRaw = Base64.getDecoder().decode("nfYdqkZtsl/W5CFeUN/NO3jX6oXtmtejdmyTQ3m6ByQ=")

        val ephemeralPrivateKey = XeapiCrypto.importPrivateKeyForTest(ephemeralPrivRaw)
        val serverPublicKey = XeapiCrypto.importPeerPublicKey(serverPubRaw)

        val sharedSecret = XeapiCrypto.computeSharedSecret(ephemeralPrivateKey, serverPublicKey)
        val aesKey = XeapiCrypto.deriveAesKey(sharedSecret, ephemeralPubRaw)
        assertEquals("YXZohS866PwLh3k1q8PW3g==", Base64.getEncoder().encodeToString(aesKey))

        val iv = Base64.getDecoder().decode("ICEiIyQlJicoKSor")
        val gcmPlaintext = "MDEyMzQ1Njc4OTo7PD0+Pw==|android|test-sk-value".toByteArray(Charsets.UTF_8)
        val encrypted = XeapiCrypto.gcmEncrypt(aesKey, iv, gcmPlaintext)

        val expectedCiphertext = Base64.getDecoder().decode("16l2ybuJZRbGOfmhlU2aondZUW8UBUaqAXy2hAGhBkwNta7s4Gra2wrilsqTAw==")
        val expectedAuthTag = Base64.getDecoder().decode("7qb/dG3W4/m4pHa021boEg==")
        assertArrayEquals(expectedCiphertext + expectedAuthTag, encrypted)
    }

    @Test
    fun `rawPublicKeyBytes与importPeerPublicKey互为逆操作`() {
        val keyPair = XeapiCrypto.generateEphemeralKeyPair()
        val raw = XeapiCrypto.rawPublicKeyBytes(keyPair.public)
        assertEquals(32, raw.size)
        val reimported = XeapiCrypto.importPeerPublicKey(raw)
        assertEquals(keyPair.public, reimported)
    }

    // 【向量3】xeapiMidTransform
    @Test
    fun `midTransform与已知向量一致`() {
        val ciphertext = hexDecode("0011223344556677889900aabbccddeeff")
        val mask = hexDecode("000102030405060708090a0b0c0d0e0f")
        val result = XeapiCrypto.midTransform(ciphertext, mask)
        assertEquals("AAECAwQFBgcICQoLDA0OD0FCQWdNRUJRWUhDQWtBcWh0OEhUNGY4PQ==", Base64.getEncoder().encodeToString(result))
        assertEquals(40, result.size)
    }

    // 【向量5】buildPlaintext：字段顺序固定为 body 在前、queryString 在后
    @Test
    fun `buildPlaintext与已知向量一致`() {
        val data = linkedMapOf(
            "threadId" to "R_SO_4_123456",
            "content" to "测试评论",
            "resourceType" to "0",
            "expressionPicId" to "-1",
            "bubbleId" to "-1"
        )
        val plaintext = XeapiCrypto.buildPlaintext("/api/resource/comments/add", data)
        val expected = "{\"body\":\"dGhyZWFkSWQ9Ul9TT180XzEyMzQ1NiZjb250ZW50PSVFNiVCNSU4QiVFOCVBRiU5NSVFOCVBRiU4NCVFOCVBRSVCQSZyZXNvdXJjZVR5cGU9MCZleHByZXNzaW9uUGljSWQ9LTEmYnViYmxlSWQ9LTE=\",\"queryString\":\"e_r=true\"}"
        assertEquals(expected, plaintext)
    }

    // assembleRequest 没有可比对的外部整体向量（B/S/R 每次都带真随机的椭圆曲线临时密钥），
    // 这里用结构性自洽校验：R 段能用同一把静态密钥解密还原出 "version|"；S 段长度符合
    // 32(临时公钥)+12(iv)+明文长度+16(GCM tag) 的定长公式；B/S/R 均为合法 base64。
    @Test
    fun `assembleRequest组装的B_S_R结构正确`() {
        val serverKeyPair = XeapiCrypto.generateEphemeralKeyPair()
        val serverPublicKeyState = XeapiPublicKeyState(
            version = "3",
            publicKey = Base64.getEncoder().encodeToString(XeapiCrypto.rawPublicKeyBytes(serverKeyPair.public)),
            sk = "sk-value"
        )
        val data = linkedMapOf("threadId" to "R_SO_4_1", "content" to "hi")

        val encrypted = XeapiCrypto.assembleRequest("/api/resource/comments/add", data, serverPublicKeyState)

        // R 段：AES-ECB(静态密钥) 解密后应还原出 "3|"
        val rPlaintext = XeapiCrypto.aesEcbDecrypt(XeapiCrypto.XEAPI_STATIC_KEY, Base64.getDecoder().decode(encrypted.r))
        assertEquals("3|", String(rPlaintext, Charsets.UTF_8))

        // S 段：定长公式校验（不依赖具体随机值）
        val expectedGcmPlaintextLen = Base64.getEncoder().encodeToString(ByteArray(16)).length + "|android|sk-value".length
        val sBytes = Base64.getDecoder().decode(encrypted.s)
        assertEquals(32 + 12 + expectedGcmPlaintextLen + 16, sBytes.size)

        assert(encrypted.b.isNotEmpty())
    }

    private fun hexDecode(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte() }
}
