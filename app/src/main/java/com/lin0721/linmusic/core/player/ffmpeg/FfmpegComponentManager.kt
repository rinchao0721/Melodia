package com.lin0721.linmusic.core.player.ffmpeg

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.plugin.DEFAULT_PLUGIN_CATALOG_URL
import com.lin0721.linmusic.core.plugin.PluginHostInfo
import com.lin0721.linmusic.core.plugin.PluginPackage
import com.lin0721.linmusic.core.plugin.comparePluginVersions
import com.lin0721.linmusic.core.source.SourceHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "FfmpegComponentManager"
private const val LIB_FILE_NAME = "libffmpegJNI.so"
private const val TEMP_FILE_NAME = "libffmpegJNI.so.tmp"
private const val VERSION_FILE_NAME = "version.txt"
private const val TEMP_VERSION_FILE_NAME = "version.txt.tmp"
private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024

sealed interface FfmpegComponentState {
    data object NotInstalled : FfmpegComponentState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : FfmpegComponentState {
        val progress: Float?
            get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }
    data class Installed(val version: String, val sizeBytes: Long) : FfmpegComponentState
    data class Failed(val reason: String) : FfmpegComponentState
}

data class FfmpegCatalogInfo(
    val remoteVersion: String? = null,
    val remoteSizeBytes: Long? = null,
    val checking: Boolean = false,
    val error: String? = null
)

// 管理 ffmpeg 解码组件（libffmpegJNI.so）的目录拉取、校验下载、版本记录与本地清理
@OptIn(UnstableApi::class)
class FfmpegComponentManager(
    context: Context,
    private val componentDir: File = defaultComponentDir(context),
    private val catalogUrl: String = DEFAULT_PLUGIN_CATALOG_URL,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val supportedAbisProvider: () -> List<String> = { Build.SUPPORTED_ABIS.toList() },
    private val appVersionProvider: () -> String? = { PluginHostInfo.appVersion }
) {
    companion object {
        fun defaultComponentDir(context: Context): File =
            File(context.filesDir, "components/ffmpeg")

        fun defaultLibraryFile(context: Context): File =
            File(defaultComponentDir(context), LIB_FILE_NAME)

        private fun defaultHttpClient(): OkHttpClient =
            SourceHttpClient.client.newBuilder()
                .followRedirects(true)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }

    private val soFile = File(componentDir, LIB_FILE_NAME)
    private val tempFile = File(componentDir, TEMP_FILE_NAME)
    private val versionFile = File(componentDir, VERSION_FILE_NAME)
    private val tempVersionFile = File(componentDir, TEMP_VERSION_FILE_NAME)
    private val operationMutex = Mutex()

    private val _state = MutableStateFlow<FfmpegComponentState>(readInstalledState())
    val state: StateFlow<FfmpegComponentState> = _state.asStateFlow()

    private val _catalogInfo = MutableStateFlow(FfmpegCatalogInfo())
    val catalogInfo: StateFlow<FfmpegCatalogInfo> = _catalogInfo.asStateFlow()

    val isInstalled: Boolean
        get() = soFile.isFile && soFile.length() > 0L

    init {
        markReadOnly()
        FfmpegLibrary.setLibraryPath(soFile.absolutePath)
    }

    // 系统对加载可写 so 已告警，后续版本会直接拒绝加载
    private fun markReadOnly() {
        if (soFile.isFile && soFile.canWrite() && !soFile.setReadOnly()) {
            AppLogger.w(TAG, "组件文件设为只读失败: ${soFile.absolutePath}")
        }
    }

    fun hasUpdate(currentState: FfmpegComponentState = _state.value, catalog: FfmpegCatalogInfo = _catalogInfo.value): Boolean {
        val installed = currentState as? FfmpegComponentState.Installed ?: return false
        val remote = catalog.remoteVersion ?: return false
        return comparePluginVersions(remote, installed.version) > 0
    }

    fun restoreLocalStateIfIdle() {
        if (_state.value is FfmpegComponentState.Downloading) return
        _state.value = readInstalledState()
    }

    suspend fun refreshCatalog(): FfmpegCatalogInfo = withContext(Dispatchers.IO) {
        _catalogInfo.value = _catalogInfo.value.copy(checking = true, error = null)
        when (val result = fetchAndResolveManifest()) {
            is ComponentResolveResult.Success -> {
                val info = FfmpegCatalogInfo(
                    remoteVersion = result.manifest.version,
                    remoteSizeBytes = result.manifest.file.sizeBytes,
                    checking = false,
                    error = null
                )
                _catalogInfo.value = info
                info
            }
            is ComponentResolveResult.Failure -> {
                val info = _catalogInfo.value.copy(checking = false, error = result.reason)
                _catalogInfo.value = info
                info
            }
        }
    }

    // 拉取目录、按 ABI 下载并校验 sha256 与大小，通过后原子落盘；失败保留已有组件并写明原因
    suspend fun installOrUpdate(): Boolean = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val knownSize = _catalogInfo.value.remoteSizeBytes ?: 0L
            _state.value = FfmpegComponentState.Downloading(0L, knownSize)

            val manifest = when (val resolved = fetchAndResolveManifest()) {
                is ComponentResolveResult.Success -> resolved.manifest
                is ComponentResolveResult.Failure -> {
                    _state.value = FfmpegComponentState.Failed(resolved.reason)
                    return@withContext false
                }
            }

            _catalogInfo.value = FfmpegCatalogInfo(
                remoteVersion = manifest.version,
                remoteSizeBytes = manifest.file.sizeBytes,
                checking = false,
                error = null
            )

            if (!componentDir.exists() && !componentDir.mkdirs()) {
                _state.value = FfmpegComponentState.Failed("无法创建组件目录")
                return@withContext false
            }

            tempFile.delete()
            tempVersionFile.delete()
            _state.value = FfmpegComponentState.Downloading(0L, manifest.file.sizeBytes)

            try {
                downloadToTemp(manifest.file.url, manifest.file.sizeBytes) { downloaded ->
                    _state.value = FfmpegComponentState.Downloading(downloaded, manifest.file.sizeBytes)
                }
                val actualSize = tempFile.length()
                if (actualSize != manifest.file.sizeBytes) {
                    tempFile.delete()
                    _state.value = FfmpegComponentState.Failed("组件大小校验失败（期望 ${manifest.file.sizeBytes} 字节，实际 $actualSize 字节）")
                    return@withContext false
                }
                val actualSha256 = PluginPackage.sha256(tempFile)
                if (actualSha256 != manifest.file.sha256) {
                    tempFile.delete()
                    _state.value = FfmpegComponentState.Failed("组件 SHA-256 校验失败")
                    return@withContext false
                }

                tempVersionFile.writeText(manifest.version)
                moveAtomically(tempFile, soFile)
                markReadOnly()
                moveAtomically(tempVersionFile, versionFile)
                FfmpegLibrary.setLibraryPath(soFile.absolutePath)

                val installed = FfmpegComponentState.Installed(
                    version = manifest.version,
                    sizeBytes = soFile.length()
                )
                _state.value = installed
                AppLogger.i(TAG, "ffmpeg 组件安装完成 version=${manifest.version} abi=${manifest.file.abi} size=${installed.sizeBytes}")
                true
            } catch (e: CancellationException) {
                tempFile.delete()
                tempVersionFile.delete()
                _state.value = readInstalledState()
                throw e
            } catch (e: IOException) {
                tempFile.delete()
                tempVersionFile.delete()
                val msg = e.message ?: "网络错误"
                AppLogger.w(TAG, "ffmpeg 组件下载失败: $msg", e)
                _state.value = FfmpegComponentState.Failed("下载组件失败：$msg")
                false
            } catch (e: Exception) {
                tempFile.delete()
                tempVersionFile.delete()
                val msg = e.message ?: "写入文件失败"
                AppLogger.e(TAG, "ffmpeg 组件安装异常: $msg", e)
                _state.value = FfmpegComponentState.Failed("安装组件失败：$msg")
                false
            }
        }
    }

    suspend fun deleteComponent() = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            tempFile.delete()
            tempVersionFile.delete()
            soFile.delete()
            versionFile.delete()
            _state.value = FfmpegComponentState.NotInstalled
            AppLogger.i(TAG, "ffmpeg 组件已删除")
        }
    }

    private fun readInstalledState(): FfmpegComponentState {
        if (!soFile.isFile || soFile.length() <= 0L) return FfmpegComponentState.NotInstalled
        val version = runCatching { versionFile.takeIf { it.isFile }?.readText()?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: "已安装"
        return FfmpegComponentState.Installed(version = version, sizeBytes = soFile.length())
    }

    private fun fetchAndResolveManifest(): ComponentResolveResult {
        if (!isValidHttpsUrl(catalogUrl)) {
            return ComponentResolveResult.Failure("组件目录地址必须使用 https")
        }
        val text = try {
            val request = Request.Builder().url(catalogUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return ComponentResolveResult.Failure("拉取组件目录失败：HTTP ${response.code}")
                }
                if (!response.request.url.isHttps) {
                    return ComponentResolveResult.Failure("组件目录被重定向到非 https 地址")
                }
                val body = response.body ?: return ComponentResolveResult.Failure("组件目录响应为空")
                val declared = body.contentLength()
                if (declared > FFMPEG_MAX_CATALOG_BYTES) {
                    return ComponentResolveResult.Failure("组件目录大小超出限制")
                }
                val peeked = response.peekBody(FFMPEG_MAX_CATALOG_BYTES + 1)
                if (peeked.contentLength() > FFMPEG_MAX_CATALOG_BYTES) {
                    return ComponentResolveResult.Failure("组件目录大小超出限制")
                }
                peeked.string()
            }
        } catch (e: IOException) {
            AppLogger.w(TAG, "拉取组件目录失败: $catalogUrl", e)
            return ComponentResolveResult.Failure("拉取组件目录失败：${e.message ?: "网络错误"}")
        }
        return parseFfmpegComponentCatalog(
            jsonText = text,
            supportedAbis = supportedAbisProvider(),
            appVersion = appVersionProvider()
        )
    }

    private suspend fun downloadToTemp(
        url: String,
        expectedBytes: Long,
        onProgress: (Long) -> Unit
    ) {
        val request = try {
            Request.Builder().url(url).get().build()
        } catch (e: IllegalArgumentException) {
            throw IOException("组件下载地址无效", e)
        }
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("服务器返回 HTTP ${response.code}")
            if (!response.request.url.isHttps) throw IOException("下载地址被重定向到非 https")
            val body = response.body ?: throw IOException("响应体为空")
            val declaredLength = body.contentLength()
            if (declaredLength > FFMPEG_MAX_COMPONENT_BYTES || (declaredLength > 0 && declaredLength != expectedBytes)) {
                throw IOException("响应大小与目录声明不一致")
            }
            val input = body.byteStream()
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > expectedBytes || total > FFMPEG_MAX_COMPONENT_BYTES) {
                        throw IOException("下载内容超过目录声明的大小")
                    }
                    output.write(buffer, 0, read)
                    onProgress(total)
                }
            }
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

fun formatComponentSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
