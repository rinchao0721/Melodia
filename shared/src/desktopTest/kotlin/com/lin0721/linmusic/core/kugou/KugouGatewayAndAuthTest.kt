package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.PlatformQrPollResult
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.util.Base64
import javax.crypto.Cipher

// 酷狗独立网关、风控与全量鉴权接口单元测试（基于 MockWebServer + classpath fixtures + 测试私钥端到端加解密校验）
class KugouGatewayAndAuthTest {

    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryPreferencesStore
    private lateinit var userPreferences: UserPreferences
    private lateinit var deviceStore: KugouDeviceStore
    private lateinit var testPrivateKey: RSAPrivateKey
    private lateinit var testPublicKeyPem: String
    private lateinit var config: KugouConfig
    private lateinit var gatewayClient: KugouGatewayClient
    private lateinit var authService: KugouAuthService
    private lateinit var authProvider: KugouAuthProvider
    private var clockMs = 1_700_000_000_123L
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        testPrivateKey = keyPair.private as RSAPrivateKey
        testPublicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getEncoder().encodeToString(keyPair.public.encoded) +
            "\n-----END PUBLIC KEY-----"

        val baseUrl = server.url("/").toString().trimEnd('/')
        config = KugouConfig(
            initialMode = KugouClientMode.LITE,
            gatewayBaseUrl = baseUrl,
            baseUrlRewriter = { baseUrl },
            overridePublicKeyPem = testPublicKeyPem
        )
        store = InMemoryPreferencesStore()
        userPreferences = UserPreferences(store, nowMs = { clockMs })
        deviceStore = KugouDeviceStore(store)
        gatewayClient = KugouGatewayClient(
            config = config,
            deviceStore = deviceStore,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
        authService = KugouAuthService(
            config = config,
            deviceStore = deviceStore,
            gatewayClient = gatewayClient,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
        authProvider = KugouAuthProvider(
            authService = authService,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `registerDevice 正确 RSA+AES 加密请求并以同一 aesKey 解密响应且持久化 dfid`() = runTest {
        val registerJson = loadFixture("kugou/register_dev.json")
        server.shutdown()

        server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                assertEquals("/risk/v3/r_register_dev", url.encodedPath)
                assertEquals("1", url.queryParameter("part"))
                assertEquals("1", url.queryParameter("platid"))
                assertNotNull(url.queryParameter("signature"))

                val pHex = url.queryParameter("p")!!
                val pJsonStr = decryptPkcs1Hex(pHex)
                val pObj = json.parseToJsonElement(pJsonStr).jsonObject
                val aesKey = pObj["aes"]!!.jsonPrimitive.content

                val bodyCipherBase64 = request.body.readUtf8()
                val decryptedReqBody = KugouCrypto.playlistAesDecrypt(bodyCipherBase64, aesKey)
                val reqObj = json.parseToJsonElement(decryptedReqBody).jsonObject
                assertEquals("Redmi", reqObj["brand"]!!.jsonPrimitive.content)
                assertEquals("marble", reqObj["device"]!!.jsonPrimitive.content)

                val encryptedResp = KugouCrypto.playlistAesEncrypt(registerJson, fixedKey = aesKey)
                val respBytes = Base64.getDecoder().decode(encryptedResp.str)
                return MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(respBytes))
            }
        }
        server.start()
        rebindClientToServer()

        val result = authService.registerDevice()
        assertTrue(result.isSuccess)
        assertEquals("MOCK_DFID_TEST_0000000001", result.getOrThrow())
        assertEquals("MOCK_DFID_TEST_0000000001", deviceStore.getOrCreateDeviceIdentity().effectiveDfid)
    }

    @Test
    fun `createQrLogin 与 pollQrLogin 四状态映射并通过 KugouAuthProvider 自动落盘酷狗账号`() = runTest {
        val qrCreateFixture = loadFixture("kugou/login_qr_key.json")
        server.enqueue(MockResponse().setBody(qrCreateFixture))
        server.enqueue(MockResponse().setBody("""{"status":1,"error_code":0,"data":{"status":1}}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"status":1,"error_code":0,"data":{"status":2,"nickname":"待确认酷狗号","pic":"https://p.kg/1.jpg"}}"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"status":1,"error_code":0,"data":{"status":4,"token":"kg_qr_tok_999","userid":778899,"nickname":"酷狗测试号","pic":"https://p.kg/avatar.jpg"}}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"status":1,"error_code":0,"data":{"status":0}}"""))

        val qrKey = authProvider.createQrLogin().getOrThrow()
        assertEquals(MusicPlatform.KUGOU, qrKey.platform)
        assertEquals("mock_qrcode_key_00000000000000001001", qrKey.key)
        assertTrue(qrKey.qrUrl.contains("mock_qrcode_key_00000000000000001001"))

        val createReq = server.takeRequest()
        assertEquals("/v2/qrcode", createReq.requestUrl!!.encodedPath)
        assertEquals("1001", createReq.requestUrl!!.queryParameter("appid"))
        assertEquals(KugouConfig.SRC_APPID.toString(), createReq.requestUrl!!.queryParameter("srcappid"))
        assertNotNull(createReq.requestUrl!!.queryParameter("signature"))

        assertEquals(PlatformQrPollResult.Waiting, authProvider.pollQrLogin(qrKey.key).getOrThrow())
        assertEquals(
            PlatformQrPollResult.Confirming("待确认酷狗号", "https://p.kg/1.jpg"),
            authProvider.pollQrLogin(qrKey.key).getOrThrow()
        )
        val authorized = authProvider.pollQrLogin(qrKey.key).getOrThrow() as PlatformQrPollResult.Authorized
        assertEquals(AccountId.kugou("778899"), authorized.auth.accountId)
        assertEquals("kg_qr_tok_999", authorized.auth.credentials["token"])

        val activeKugou = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        assertNotNull(activeKugou)
        assertEquals("778899", activeKugou?.effectivePlatformUid)
        assertEquals("酷狗测试号", activeKugou?.nickname)
        assertEquals("kg_qr_tok_999", activeKugou?.credentials?.get("token"))

        assertEquals(PlatformQrPollResult.Expired, authProvider.pollQrLogin(qrKey.key).getOrThrow())
    }

    @Test
    fun `loginByPhone 与 refreshToken 完整加解密 secu_params 并更新 UserPreferences`() = runTest {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val reqObj = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                return when (path) {
                    "/v7/login_by_verifycode" -> {
                        val pkHex = reqObj["pk"]!!.jsonPrimitive.content
                        val pkJson = decryptRawRsaHex(pkHex)
                        val pkObj = json.parseToJsonElement(pkJson).jsonObject
                        val tempKey = pkObj["key"]!!.jsonPrimitive.content
                        val decryptedParams = KugouCrypto.cryptoAesDecrypt(
                            reqObj["params"]!!.jsonPrimitive.content,
                            tempKey
                        )
                        val loginReq = json.parseToJsonElement(decryptedParams).jsonObject
                        assertEquals("13800138000", loginReq["mobile"]!!.jsonPrimitive.content)
                        assertEquals("654321", loginReq["code"]!!.jsonPrimitive.content)

                        val secuPlain = """{"token":"phone_tok_111","t1":"t1_val_111","vip_token":"vt_111"}"""
                        val secuEncrypted = KugouCrypto.cryptoAesEncrypt(secuPlain, tempKey = tempKey).str
                        MockResponse().setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {
                                "userid": 665544,
                                "nickname": "手机登录用户",
                                "pic": "https://p.kg/phone.jpg",
                                "vip_type": 6,
                                "secu_params": "$secuEncrypted"
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    "/v5/login_by_token" -> {
                        val pkHex = reqObj["pk"]!!.jsonPrimitive.content
                        val pkJson = decryptRawRsaHex(pkHex)
                        val tempKey = json.parseToJsonElement(pkJson).jsonObject["key"]!!.jsonPrimitive.content
                        val p3Plain = KugouCrypto.cryptoAesDecrypt(
                            reqObj["p3"]!!.jsonPrimitive.content,
                            KugouClientMode.LITE.loginTokenKey,
                            KugouClientMode.LITE.loginTokenIv
                        )
                        val p3Obj = json.parseToJsonElement(p3Plain).jsonObject
                        assertEquals("phone_tok_111", p3Obj["token"]!!.jsonPrimitive.content)

                        val secuPlain = """{"token":"refreshed_tok_222","t1":"t1_val_222","vip_token":"vt_222"}"""
                        val secuEncrypted = KugouCrypto.cryptoAesEncrypt(secuPlain, tempKey = tempKey).str
                        MockResponse().setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {
                                "userid": 665544,
                                "nickname": "手机登录用户",
                                "vip_type": 6,
                                "secu_params": "$secuEncrypted"
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val loginResult = authProvider.loginByPhone("13800138000", "654321").getOrThrow()
        assertEquals(AccountId.kugou("665544"), loginResult.accountId)
        assertEquals("酷狗 VIP", loginResult.vipLabel)
        assertEquals("phone_tok_111", loginResult.credentials["token"])

        clockMs += 60_000L
        val refreshed = authProvider.refreshSession(loginResult.account!!).getOrThrow()
        assertEquals("refreshed_tok_222", refreshed.credentials["token"])
        assertEquals("t1_val_222", refreshed.credentials["t1"])

        val persisted = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        assertEquals("refreshed_tok_222", persisted?.credentials?.get("token"))
        assertEquals(clockMs, persisted?.lastLoginRefreshAt)
    }

    @Test
    fun `fetchUserDetail fetchVipDetail verifyPlaybackAuth 与 ssa-code 风控捕获及云歌单双向加解密`() = runTest {
        userPreferences.upsertPlatformAccount(
            com.lin0721.linmusic.core.auth.StoredAccount(
                uid = 334455L,
                nickname = "旧昵称",
                platform = MusicPlatform.KUGOU,
                platformUid = "334455",
                credentials = mapOf("token" to "tok_334455", "userid" to "334455")
            )
        )

        // 1. user_detail
        server.enqueue(
            MockResponse().setBody(
                """{"status":1,"error_code":0,"data":{"userid":334455,"nickname":"酷狗详情用户","pic":"https://p.kg/d.jpg","vip_type":6,"is_vip":1,"signature":"hello"}}"""
            )
        )
        val detail = authService.fetchUserDetail().getOrThrow()
        assertEquals("酷狗详情用户", detail.nickname)
        assertEquals(1, detail.isVip)
        val detailReq = server.takeRequest()
        assertEquals("usercenter.kugou.com", detailReq.getHeader("x-router"))

        // 2. user_vip_detail
        server.enqueue(
            MockResponse().setBody(
                """{"status":1,"error_code":0,"data":{"is_vip":1,"vip_type":6,"vip_end_time":"2030-01-01","busi_vip":[{"product_name":"概念版VIP"}]}}"""
            )
        )
        val vipDetail = authService.fetchVipDetail().getOrThrow()
        assertEquals(1, vipDetail.isVip)
        assertEquals(listOf("概念版VIP"), vipDetail.busiVipNames)
        val vipReq = server.takeRequest()
        assertEquals("/v1/get_union_vip", vipReq.requestUrl!!.encodedPath)

        // 3. user_verify 并自动写回 auth 凭据
        server.enqueue(
            MockResponse().setBody("""{"status":1,"error_code":0,"data":{"auth":"play_auth_secret_99"}}""")
        )
        val authToken = authService.verifyPlaybackAuth().getOrThrow()
        assertEquals("play_auth_secret_99", authToken)
        assertEquals(
            "play_auth_secret_99",
            userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()?.credentials?.get("auth")
        )
        val verifyReq = server.takeRequest()
        assertEquals("trackercdngz.kugou.com", verifyReq.getHeader("x-router"))

        // 4. ssa-code 风控响应头触发 KugouRiskException 并附带 sid/edt
        server.enqueue(
            MockResponse()
                .addHeader("ssa-code", "9001")
                .setBody("""{"status":0,"error_code":9001,"error_msg":"需要安全验证"}""")
        )
        val riskFailure = authService.fetchVipDetail().exceptionOrNull()
        assertTrue(riskFailure is KugouRiskException)
        val riskChallenge = (riskFailure as KugouRiskException).challenge
        assertEquals("9001", riskChallenge.ssaCode)
        assertTrue(!riskChallenge.sid.isNullOrBlank())
        assertTrue(!riskChallenge.edt.isNullOrBlank())

        // 5. executeCloudRequest 双向 AES-CBC + PKCS1 RSA 通道验证
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                assertEquals("cloudlist.service.kugou.com", request.getHeader("x-router"))
                val pJson = decryptPkcs1Hex(request.requestUrl!!.queryParameter("p")!!)
                val pObj = json.parseToJsonElement(pJson).jsonObject
                val aesSecret = pObj["aes"]!!.jsonPrimitive.content
                val md5 = KugouCrypto.md5Hex(aesSecret)
                val keyBytes = md5.substring(0, 16).toByteArray(Charsets.UTF_8)
                val ivBytes = md5.substring(16, 32).toByteArray(Charsets.UTF_8)

                val plainReq = KugouCrypto.aesCbcDecryptBytes(request.body.readByteArray(), keyBytes, ivBytes)
                    .toString(Charsets.UTF_8)
                assertEquals("""{"listid":123}""", plainReq)

                val respEncrypted = KugouCrypto.aesCbcEncryptBytes(
                    plainBytes = """{"status":1,"error_code":0,"data":{"ok":true}}""".toByteArray(Charsets.UTF_8),
                    keyBytes = keyBytes,
                    ivBytes = ivBytes
                )
                return MockResponse().setBody(okio.Buffer().write(respEncrypted))
            }
        }
        val cloudResp = gatewayClient.executeCloudRequest(
            url = "/v1/cloud_list",
            bodyJson = """{"listid":123}"""
        )
        assertTrue(cloudResp.isBizSuccess)
        assertTrue(cloudResp.bodyJson?.get("data")?.jsonObject?.get("ok")?.jsonPrimitive?.content == "true")
    }

    @Test
    fun `冷启动首请求经 modeSynchronizer 同步为 STANDARD 且 openapicdn 参数正确编码并在 IOException 时保留 cause`() = runTest {
        val syncCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val baseUrl = server.url("/").toString().trimEnd('/')
        val standardConfig = KugouConfig(
            initialMode = KugouClientMode.LITE,
            gatewayBaseUrl = baseUrl,
            baseUrlRewriter = { baseUrl },
            overridePublicKeyPem = testPublicKeyPem,
            modeSynchronizer = {
                syncCalls.incrementAndGet()
                KugouClientMode.STANDARD
            }
        )
        val syncGateway = KugouGatewayClient(
            config = standardConfig,
            deviceStore = deviceStore,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":1,"error_code":0,"data":{"ok":1}}"""))
        val resp1 = syncGateway.executeRequest(
            KugouRequestOptions(
                method = "GET",
                url = "/v1/search",
                baseUrl = "https://openapicdn.kugou.com",
                params = mapOf("keyword" to "a b&c=1", "album_id" to "中文"),
                notSignature = true
            )
        )
        assertTrue(resp1.isBizSuccess)
        assertEquals(KugouClientMode.STANDARD, standardConfig.mode)
        assertEquals(1, syncCalls.get())

        val req1 = server.takeRequest()
        assertEquals(KugouClientMode.STANDARD.appid.toString(), req1.requestUrl!!.queryParameter("appid"))
        assertEquals(KugouClientMode.STANDARD.clientver.toString(), req1.requestUrl!!.queryParameter("clientver"))
        assertEquals("a b&c=1", req1.requestUrl!!.queryParameter("keyword"))
        assertEquals("中文", req1.requestUrl!!.queryParameter("album_id"))

        // 第二次请求不应重复调用 modeSynchronizer
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":1,"error_code":0}"""))
        syncGateway.executeRequest(KugouRequestOptions(method = "GET", url = "/v1/ping", notSignature = true))
        assertEquals(1, syncCalls.get())
        server.takeRequest()

        // 关闭 server 触发 IOException，应包装为 AppError.NetworkError 且保留原始 cause
        server.shutdown()
        val netFailure = runCatching {
            syncGateway.executeRequest(KugouRequestOptions(method = "GET", url = "/v1/ping", notSignature = true))
        }.exceptionOrNull()
        assertTrue(netFailure is com.lin0721.linmusic.core.network.AppError.NetworkError)
        assertTrue(netFailure?.cause is java.io.IOException)
    }

    private fun rebindClientToServer() {
        val baseUrl = server.url("/").toString().trimEnd('/')
        config = KugouConfig(
            initialMode = KugouClientMode.LITE,
            gatewayBaseUrl = baseUrl,
            baseUrlRewriter = { baseUrl },
            overridePublicKeyPem = testPublicKeyPem
        )
        gatewayClient = KugouGatewayClient(
            config = config,
            deviceStore = deviceStore,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
        authService = KugouAuthService(
            config = config,
            deviceStore = deviceStore,
            gatewayClient = gatewayClient,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
        authProvider = KugouAuthProvider(
            authService = authService,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
    }

    private fun decryptPkcs1Hex(hexCipher: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, testPrivateKey)
        return cipher.doFinal(KugouCrypto.hexToBytes(hexCipher)).toString(Charsets.UTF_8)
    }

    private fun decryptRawRsaHex(hexCipher: String): String {
        val m = BigInteger(hexCipher, 16).modPow(testPrivateKey.privateExponent, testPrivateKey.modulus)
        val raw = m.toByteArray()
        val start = if (raw.isNotEmpty() && raw[0].toInt() == 0) 1 else 0
        var end = raw.size
        while (end > start && raw[end - 1].toInt() == 0) {
            end--
        }
        return String(raw, start, end - start, Charsets.UTF_8)
    }

    private fun loadFixture(resourcePath: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("找不到测试资源: $resourcePath")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
