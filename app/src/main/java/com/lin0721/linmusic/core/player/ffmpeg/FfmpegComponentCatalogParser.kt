package com.lin0721.linmusic.core.player.ffmpeg

import com.lin0721.linmusic.core.plugin.PluginHostInfo
import com.lin0721.linmusic.core.plugin.comparePluginVersions
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URISyntaxException

const val FFMPEG_COMPONENT_ID = "ffmpeg-decoder"
const val FFMPEG_EXPECTED_JNI_ABI = 1
const val FFMPEG_MAX_COMPONENT_BYTES = 16L * 1024 * 1024
const val FFMPEG_MAX_CATALOG_BYTES = 4L * 1024 * 1024

private const val EXPECTED_CATALOG_FORMAT = 1
private const val MAX_VERSION_LENGTH = 32
private const val MAX_URL_LENGTH = 2000
private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")
private val catalogJson = Json { ignoreUnknownKeys = true }

data class ComponentFileSpec(
    val abi: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long
)

data class FfmpegComponentManifest(
    val id: String,
    val version: String,
    val jniAbi: Int,
    val minAppVersion: String?,
    val file: ComponentFileSpec
)

sealed interface ComponentResolveResult {
    data class Success(val manifest: FfmpegComponentManifest) : ComponentResolveResult
    data class Failure(val reason: String) : ComponentResolveResult
}

// 从目录 JSON 解析顶层 components 数组，校验 ffmpeg-decoder 条目并按设备 ABI 顺序选出文件
fun parseFfmpegComponentCatalog(
    jsonText: String,
    supportedAbis: List<String>,
    appVersion: String? = PluginHostInfo.appVersion
): ComponentResolveResult {
    val root = try {
        catalogJson.parseToJsonElement(jsonText) as? JsonObject
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } ?: return ComponentResolveResult.Failure("目录不是有效的 JSON")

    if (root.containsKey("format")) {
        val format = (root["format"] as? JsonPrimitive)?.intOrNull
        if (format != EXPECTED_CATALOG_FORMAT) {
            return ComponentResolveResult.Failure("目录格式版本不支持")
        }
    }

    val components = root["components"] as? JsonArray
        ?: return ComponentResolveResult.Failure("目录中未提供解码组件信息")

    val targetObj = components
        .mapNotNull { it as? JsonObject }
        .firstOrNull { it.stringOrNull("id") == FFMPEG_COMPONENT_ID }
        ?: return ComponentResolveResult.Failure("目录中未找到 ffmpeg 解码组件")

    return resolveComponentEntry(targetObj, supportedAbis, appVersion)
}

internal fun resolveComponentEntry(
    entry: JsonObject,
    supportedAbis: List<String>,
    appVersion: String?
): ComponentResolveResult {
    val version = entry.stringOrNull("version")?.trim()
    if (version.isNullOrEmpty() || version.length > MAX_VERSION_LENGTH) {
        return ComponentResolveResult.Failure("组件版本号缺失或无效")
    }

    val jniAbi = (entry["jniAbi"] as? JsonPrimitive)?.intOrNull
    if (jniAbi != FFMPEG_EXPECTED_JNI_ABI) {
        return ComponentResolveResult.Failure("组件 JNI 接口版本不兼容")
    }

    val rawMinApp = entry["minAppVersion"]
    val minAppVersion = if (rawMinApp != null && rawMinApp !is JsonNull) {
        (rawMinApp as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return ComponentResolveResult.Failure("组件最低应用版本格式无效")
    } else {
        null
    }
    if (minAppVersion != null && appVersion != null && comparePluginVersions(appVersion, minAppVersion) < 0) {
        return ComponentResolveResult.Failure("组件需要 Melodia $minAppVersion 或更新版本")
    }

    val filesObj = entry["files"] as? JsonObject
        ?: return ComponentResolveResult.Failure("组件缺少文件列表")
    if (filesObj.isEmpty()) {
        return ComponentResolveResult.Failure("组件文件列表为空")
    }

    val (selectedAbi, fileObj) = selectAbiFileObject(filesObj, supportedAbis)
        ?: return ComponentResolveResult.Failure("不支持当前设备架构")

    val url = fileObj.stringOrNull("url")?.trim()
        ?: return ComponentResolveResult.Failure("组件缺少下载地址")
    if (!isValidHttpsUrl(url)) {
        return ComponentResolveResult.Failure("组件下载地址必须使用 https")
    }

    val size = (fileObj["size"] as? JsonPrimitive)?.longOrNull
        ?: return ComponentResolveResult.Failure("组件缺少文件大小信息")
    if (size <= 0L || size > FFMPEG_MAX_COMPONENT_BYTES) {
        return ComponentResolveResult.Failure("组件文件大小超出允许范围")
    }

    val sha256 = fileObj.stringOrNull("sha256")?.trim()?.lowercase()
        ?: return ComponentResolveResult.Failure("组件缺少 SHA-256 校验值")
    if (!SHA256_HEX_REGEX.matches(sha256)) {
        return ComponentResolveResult.Failure("组件 SHA-256 校验值无效")
    }

    return ComponentResolveResult.Success(
        FfmpegComponentManifest(
            id = FFMPEG_COMPONENT_ID,
            version = version,
            jniAbi = jniAbi,
            minAppVersion = minAppVersion,
            file = ComponentFileSpec(
                abi = selectedAbi,
                url = url,
                sha256 = sha256,
                sizeBytes = size
            )
        )
    )
}

// 按 supportedAbis 优先级选取第一个存在且为 JSON 对象的文件定义
fun selectAbiFileObject(
    files: JsonObject,
    supportedAbis: List<String>
): Pair<String, JsonObject>? {
    for (abi in supportedAbis) {
        val candidate = files[abi] as? JsonObject
        if (candidate != null) return abi to candidate
    }
    return null
}

fun isValidHttpsUrl(url: String): Boolean {
    if (url.length > MAX_URL_LENGTH) return false
    return try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
    } catch (_: URISyntaxException) {
        false
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
