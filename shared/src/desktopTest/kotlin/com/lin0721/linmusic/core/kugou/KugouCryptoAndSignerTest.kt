package com.lin0721.linmusic.core.kugou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.Cipher

// 酷狗加解密与签名单元测试：使用 KuGouMusicApi (util/crypto.js, util/helper.js, util/util.js) 生成的标准参考向量逐一比对
class KugouCryptoAndSignerTest {

    @Test
    fun `md5Hex sha1Hex 与 calculateMid 对照 Node 标准向量一致`() {
        assertEquals(
            "af0a96cdc22f36687257026eb379cd5d",
            KugouCrypto.md5Hex("melodia-kugou-test-中文")
        )
        assertEquals(
            "510be9d2b762c30b9b6739ce194a762dfcaf3a74",
            KugouCrypto.sha1Hex("melodia-kugou-test-中文")
        )
        assertEquals(
            "308741372901437977228425563242293240765",
            KugouCrypto.calculateMid("550e8400-e29b-41d4-a716-446655440000")
        )
    }

    @Test
    fun `全量签名算法对照 Node 参考向量完全一致`() {
        val params = linkedMapOf<String, Any?>(
            "dfid" to "dfid_abc",
            "mid" to "123456",
            "a" to "1",
            "ab" to "2",
            "clienttime" to 1700000000
        )
        val bodyStr = """{"mobile":"13800138000","code":"1234"}"""
        val bodyBuf = byteArrayOf(0x01, 0x02, 0x7f, 0x80.toByte(), 0xff.toByte(), 0x41, 0x42)

        assertEquals(
            "458ed184ad1d70ce0511c459cee31281",
            KugouSigner.signatureAndroidParams(params, bodyStr, KugouClientMode.LITE)
        )
        assertEquals(
            "ad09693e6819ab4dc3eafa8864324b3d",
            KugouSigner.signatureAndroidParams(params, bodyBuf, KugouClientMode.LITE)
        )
        assertEquals(
            "a5e21ef20ab92ab847eab51dc4214c67",
            KugouSigner.signatureAndroidParams(params, bodyStr, KugouClientMode.STANDARD)
        )
        assertEquals(
            "f332966a34f3e0cb1bec4e3f2dd6148b",
            KugouSigner.signatureAndroidParams(params, bodyBuf, KugouClientMode.STANDARD)
        )
        assertEquals(
            "9c0f4b81114cc74cceea89b19f20155b",
            KugouSigner.signatureWebParams(params, bodyStr)
        )
        assertEquals(
            "d7706d75ca92d8f3786b73376ad7bd8c",
            KugouSigner.signatureRegisterParams(params)
        )
        assertEquals(
            "f60c16ee8955e39e4d0add009a1f5a36",
            KugouSigner.signParams(params, bodyStr)
        )
        assertEquals(
            "1f123c6e21cd3780509be698e620d507",
            KugouSigner.signKey("aabbccdd", "123456", 9988, 3116, KugouClientMode.LITE)
        )
        assertEquals(
            "376b48381152fd4b48030cb8f5e700d6",
            KugouSigner.signKey("aabbccdd", "123456", 9988, 1005, KugouClientMode.STANDARD)
        )
        assertEquals(
            "1e147fdee80c20bcf6e0a1d8681c84b9",
            KugouSigner.signParamsKey("1700000000123", 3116, 11440, KugouClientMode.LITE)
        )
        assertEquals(
            "e3e86865c059fdec1dd020b6d640b8a5",
            KugouSigner.signParamsKey("1700000000123", 1005, 20489, KugouClientMode.STANDARD)
        )
        assertEquals(
            "50d349890a5e8b36e87080d57b5ea4aa",
            KugouSigner.signCloudKey("hash123", "pid456")
        )
    }

    @Test
    fun `AES 加解密算法对照 Node 参考向量与往返一致`() {
        val loginPlain = """{"mobile":"13800138000","code":"654321"}"""
        val aesRes = KugouCrypto.cryptoAesEncrypt(loginPlain, tempKey = "0123456789abcdef")
        assertEquals(
            "b79546a82d451f5199a800b32023d64817d9512b670986b68f51b4fd08b6ffcc09d9ccc0fddd21d7aa87e3427c3c0b43",
            aesRes.str
        )
        assertEquals("0123456789abcdef", aesRes.key)
        assertEquals(loginPlain, KugouCrypto.cryptoAesDecrypt(aesRes.str, aesRes.key))

        val t1Plain = "12345|1700000000123"
        val t1Cipher = KugouCrypto.cryptoAesEncryptWithKeyIv(
            data = t1Plain,
            key = KugouConfig.LITE_T1_KEY,
            iv = KugouConfig.LITE_T1_IV
        )
        assertEquals(
            "95f710ffddc9881f6ffdc33cec86501b078019e0a139629072d3221c6f21355f",
            t1Cipher
        )
        assertEquals(
            t1Plain,
            KugouCrypto.cryptoAesDecrypt(t1Cipher, KugouConfig.LITE_T1_KEY, KugouConfig.LITE_T1_IV)
        )

        val devPlain = """{"brand":"Redmi","device":"marble"}"""
        val playlistRes = KugouCrypto.playlistAesEncrypt(devPlain, fixedKey = "a1b2c3")
        assertEquals(
            "XCZNjqhkg9rslvNxWELE6CTcv2XFpupEm7pFvwnSkpPpr4oOCtgclXwQtVV7kuyA",
            playlistRes.str
        )
        assertEquals(devPlain, KugouCrypto.playlistAesDecrypt(playlistRes.str, playlistRes.key))

        // 无指定密钥的随机生成往返验证
        val randomAes = KugouCrypto.cryptoAesEncrypt("""{"hello":"kugou"}""")
        assertEquals(16, randomAes.key.length)
        assertEquals("""{"hello":"kugou"}""", KugouCrypto.cryptoAesDecrypt(randomAes.str, randomAes.key))

        val randomPlaylist = KugouCrypto.playlistAesEncrypt("""{"hello":"playlist"}""")
        assertEquals(6, randomPlaylist.key.length)
        assertEquals("""{"hello":"playlist"}""", KugouCrypto.playlistAesDecrypt(randomPlaylist.str, randomPlaylist.key))
    }

    @Test
    fun `RSA 原始加密对照 Node 向量与 PKCS1 往返解密一致`() {
        val pkPlain = """{"clienttime_ms":1700000000123,"key":"0123456789abcdef"}"""
        assertEquals(
            "939f4d2e845d823cde0d4ba386d8605e33f4026f4c45f7fc60a210b174a697ac756984dd35cbe6b930108294d0537b2385b5ff67940f854d5b4f31d6c6053a7b69344e318386c8f897599d67069421aaab426cb592ad5ed5f55fc3a1b7ca3bd06a20240ca58688dab03d7f74e7d2b7583be608b94bf25b3cec22721cf04141df",
            KugouCrypto.cryptoRSAEncrypt(pkPlain, KugouConfig.PUBLIC_LITE_RSA_KEY)
        )
        assertEquals(
            "31933f306ecd7b98cb30dec17c5d9dd5f266a1cb9be56aaa5f917dcb2dcc523f252f070e2b72a0141bf6187def70ca9b6b672f15b404c7ac6ab10940cbe8a2933d7c903abf0420efb8d637a23b040d359cdd4efa37a030b37d54ca567b41a11c0caefd4e95aae4d256bf785b98a12f9153839051dd2ff5a42de1101f04a12896",
            KugouCrypto.cryptoRSAEncrypt(pkPlain, KugouConfig.PUBLIC_STANDARD_RSA_KEY)
        )

        // 生成 1024 位 RSA 密钥对校验 rsaEncrypt2 (PKCS1Padding)
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        val pubPem = "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getEncoder().encodeToString(keyPair.public.encoded) +
            "\n-----END PUBLIC KEY-----"
        val secretPayload = """{"aes":"x9y8z7","uid":10086,"token":"tok_abc"}"""
        val hexCipher = KugouCrypto.rsaEncrypt2(secretPayload, pubPem)
        assertEquals(256, hexCipher.length)
        assertTrue(hexCipher.all { it in '0'..'9' || it in 'a'..'f' })

        val decryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        decryptCipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        val decrypted = decryptCipher.doFinal(KugouCrypto.hexToBytes(hexCipher)).toString(Charsets.UTF_8)
        assertEquals(secretPayload, decrypted)
    }

    @Test
    fun `KRC 歌词 XOR 与 zlib 解压对照 Node 向量一致`() {
        val sampleKrcBase64 = "a3JjMTjb6kGOA0B3Yb6EHB7jW1mQdtGEFv+9jra8NyQkys+h/Cu8ieJJjoyora7BUhJradxncx4="
        assertEquals(
            "[0,1000]<0,500,0>Hello\n[1000,2000]<0,500,0>酷狗音乐\n",
            KugouCrypto.decodeKrcLyrics(sampleKrcBase64)
        )
        assertEquals("", KugouCrypto.decodeKrcLyrics(byteArrayOf(1, 2, 3)))
    }
}
