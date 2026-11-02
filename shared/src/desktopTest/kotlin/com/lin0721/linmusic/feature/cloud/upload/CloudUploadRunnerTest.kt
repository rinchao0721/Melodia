package com.lin0721.linmusic.feature.cloud.upload

import com.lin0721.linmusic.feature.cloud.data.CloudActionResponse
import com.lin0721.linmusic.feature.cloud.data.CloudInfoRequest
import com.lin0721.linmusic.feature.cloud.data.CloudInfoResponse
import com.lin0721.linmusic.feature.cloud.data.CloudPublishRequest
import com.lin0721.linmusic.feature.cloud.data.CloudUploadApi
import com.lin0721.linmusic.feature.cloud.data.CloudUploadCheckRequest
import com.lin0721.linmusic.feature.cloud.data.CloudUploadCheckResponse
import com.lin0721.linmusic.feature.cloud.data.NosTokenRequest
import com.lin0721.linmusic.feature.cloud.data.NosTokenResponse
import com.lin0721.linmusic.feature.cloud.data.NosTokenResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class CloudUploadRunnerTest {

    private class FakeSource(override val key: String, private val bytes: ByteArray = byteArrayOf(1, 2, 3)) : UploadSource {
        val opens = AtomicInteger()
        override val fileName: String = "$key.mp3"
        override val mimeType: String = "audio/mpeg"
        override fun size(): Long = bytes.size.toLong()
        override fun openStream(): InputStream {
            opens.incrementAndGet()
            return ByteArrayInputStream(bytes)
        }
        override fun readMetadata() = LocalAudioMetadata("标题", null, null)
    }

    private class FakeApi : CloudUploadApi {
        var needUpload = true
        var failPublish = false
        var registered: CloudInfoRequest? = null
        override suspend fun checkUpload(body: CloudUploadCheckRequest) =
            CloudUploadCheckResponse(code = 200, songId = "s1", needUpload = needUpload)
        override suspend fun allocNosToken(body: NosTokenRequest) =
            NosTokenResponse(code = 200, result = NosTokenResult(token = "t", objectKey = "a/b", resourceId = 7))
        override suspend fun registerCloudInfo(body: CloudInfoRequest): CloudInfoResponse {
            registered = body
            return CloudInfoResponse(code = 200, songId = "s2")
        }
        override suspend fun publishCloud(body: CloudPublishRequest) =
            CloudActionResponse(code = if (failPublish) 500 else 200)
    }

    private class FakeNos : NosTransport {
        val uploads = AtomicInteger()
        override suspend fun fetchUploadHosts(bucket: String) = listOf("https://nos")
        override suspend fun uploadBytes(
            host: String, bucket: String, objectKey: String, token: String, md5: String?,
            mimeType: String, contentLength: Long, openStream: () -> InputStream, onProgress: (Float) -> Unit
        ) {
            uploads.incrementAndGet()
            openStream().use { it.readBytes() }
            onProgress(1f)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manager = CloudUploadManager()
    private val api = FakeApi()
    private val nos = FakeNos()
    private val runner = CloudUploadRunner(manager, api, nos, scope)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun runBatch(block: () -> Unit): UploadBatchResult = withTimeout(5_000) {
        // SharedFlow 不重放完成事件；入队前先订阅，避免快速任务先完成而漏收结果。
        val result = async(start = CoroutineStart.UNDISPATCHED) { runner.batchFinished.first() }
        block()
        result.await()
    }

    @Test
    fun uploadsAndPublishes() = runBlocking {
        val result = runBatch { runner.enqueue(listOf(FakeSource("a"))) }
        assertEquals(UploadBatchResult(1, 0), result)
        assertEquals(UploadStatus.SUCCESS, manager.state.value.single().status)
        assertEquals(1, nos.uploads.get())
        assertEquals("标题", api.registered?.song)
        assertEquals("未知专辑", api.registered?.album)
        assertEquals(false, runner.running.value)
    }

    @Test
    fun skipsByteUploadWhenServerHasFile() = runBlocking {
        api.needUpload = false
        runBatch { runner.enqueue(listOf(FakeSource("a"))) }
        assertEquals(0, nos.uploads.get())
    }

    @Test
    fun retryReusesCachedChecksum() = runBlocking {
        api.failPublish = true
        val source = FakeSource("a")
        assertEquals(UploadBatchResult(0, 1), runBatch { runner.enqueue(listOf(source)) })
        val failed = manager.state.value.single()
        assertEquals(UploadStatus.FAILED, failed.status)
        val opensAfterFirst = source.opens.get()

        api.failPublish = false
        assertEquals(UploadBatchResult(1, 0), runBatch { runner.retry(failed.id) })
        // 重试只为字节上传再打开一次，不再重新计算 MD5
        assertEquals(opensAfterFirst + 1, source.opens.get())
        assertNull(manager.state.value.single().errorMessage)
    }

    @Test
    fun skipsDuplicateActiveSources() = runBlocking {
        runBatch {
            assertEquals(2, runner.enqueue(listOf(FakeSource("a"), FakeSource("a"), FakeSource("b"))))
        }
        assertEquals(2, manager.state.value.size)
    }

    @Test
    fun clearDropsQueue() {
        runner.enqueue(listOf(FakeSource("a"), FakeSource("b")))
        runner.clear()
        assertEquals(emptyList<UploadTask>(), manager.state.value)
        assertEquals(false, runner.running.value)
    }
}
