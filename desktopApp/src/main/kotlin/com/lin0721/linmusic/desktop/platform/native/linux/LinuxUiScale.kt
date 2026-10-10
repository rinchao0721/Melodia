package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl
import com.lin0721.linmusic.desktop.platform.native.UiScale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val TAG = "LinuxUiScale"

// kwinoutputconfig.json 解析器（跨进程只构建一次）
private val KWIN_JSON = Json { ignoreUnknownKeys = true }

// Linux 上 AWT 只支持整数缩放（sun.java2d.uiScale 的分数值会被静默忽略，实测 1.5 无效、
// 2 生效），而桌面常见 125%/150% 分数缩放；此时 AWT 的 defaultTransform 恒为 1.0，
// Compose 的 LocalDensity 随之=1.0，界面整体偏小。这里按以下顺序探测桌面缩放：
//
//   1. MELODIA_UI_SCALE 环境变量（显式覆盖，1.0 表示强制不补偿）
//   2. AWT 已上报缩放（如 -Dsun.java2d.uiScale）时不重复干预
//   3. wayland-info：物理模式宽 / xdg-output 逻辑宽，Wayland 通用（需 wayland-utils）
//   4. KDE：kwinoutputconfig.json 各输出的 scale
//   5. GNOME：monitors.xml 各逻辑显示器的 scale
//   6. X11/XWayland 兼容：xrdb 的 Xft.dpi
//   7. 工具包环境变量：GDK_SCALE / QT_SCALE_FACTOR
@PlatformImpl(DesktopPlatform.LINUX)
class LinuxUiScale : UiScale {

    override val detected: Float? by lazy { resolve() }

    private fun resolve(): Float? {
        // 1) 显式覆盖最高优先；显式 1.0 会直接终止探测
        System.getenv("MELODIA_UI_SCALE")?.let { raw ->
            val value = raw.toFloatOrNull()?.takeIf { it in 0.5f..4f }
            if (value != null) {
                AppLogger.i(TAG, "桌面缩放=$value（MELODIA_UI_SCALE 显式设置）")
                return value.takeIf { it > 1.01f }
            }
            AppLogger.w(TAG, "忽略非法的 MELODIA_UI_SCALE=$raw")
        }

        // 2) AWT 已能给出缩放
        val awtScale = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
        }.getOrDefault(1.0).toFloat()
        if (awtScale > 1.01f) {
            AppLogger.i(TAG, "AWT 已上报缩放 $awtScale，跳过桌面缩放探测")
            return null
        }

        val candidates = listOf(
            "wayland-info" to waylandScale(),
            "KDE 输出配置" to kdeScale(),
            "GNOME monitors.xml" to gnomeScale(),
            "Xft.dpi" to xftDpiScale(),
            "工具包环境变量" to toolkitEnvScale(),
        )
        val chosen = candidates.firstOrNull { it.second != null }
        if (chosen == null) {
            AppLogger.i(TAG, "未探测到桌面缩放，保持 AWT 默认密度")
            return null
        }
        AppLogger.i(TAG, "桌面缩放=${chosen.second}（来源：${chosen.first}）")
        return chosen.second
    }

    // ── Wayland 通用途径 ─────────────────────────────────────────────────────

    private fun waylandScale(): Float? {
        val output = readWaylandInfo() ?: return null
        val screenWidth = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.displayMode.width
        return waylandScaleFrom(parseWaylandInfo(output), screenWidth)
    }

    // ── KDE ─────────────────────────────────────────────────────────────────

    // KDE Plasma 把每个输出的缩放写在 kwinoutputconfig.json；
    // 取当前配置集里真实输出（排除 Unknown-*/None 占位）的最大缩放
    private fun kdeScale(): Float? = runCatching {
        val file = File(kdeConfigDir(), "kwinoutputconfig.json")
        if (!file.isFile) return null
        val sets = KWIN_JSON.decodeFromString<List<KwinConfigSet>>(file.readText())
        sets.asSequence()
            .map { set -> set.data.filter { it.isRealOutput }.mapNotNull { it.scale } }
            .firstOrNull { it.isNotEmpty() }
            ?.maxOrNull()
            ?.takeIf { it > 1.01 }
            ?.toFloat()
    }.onFailure { AppLogger.w(TAG, "读取 KDE 缩放配置失败", it) }.getOrNull()

    private fun kdeConfigDir(): File =
        File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "${System.getProperty("user.home")}/.config")

    private val KwinOutput.isRealOutput: Boolean
        get() = !connectorName.isNullOrBlank() &&
            connectorName !in NON_OUTPUT_CONNECTORS &&
            scale != null &&
            scale > 1.0

    // ── GNOME ───────────────────────────────────────────────────────────────

    private fun gnomeScale(): Float? = runCatching {
        val file = File(kdeConfigDir(), "monitors.xml")
        if (!file.isFile) return null
        scaleFromGnomeMonitors(file.readText())
    }.getOrNull()

    // ── X11 / 工具包兼容层 ───────────────────────────────────────────────────

    private fun xftDpiScale(): Float? = runCatching {
        val process = ProcessBuilder("xrdb", "-query").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
        scaleFromXftDpi(output)
    }.getOrNull()

    private fun toolkitEnvScale(): Float? =
        scaleFromToolkitEnv(System.getenv("GDK_SCALE"), System.getenv("QT_SCALE_FACTOR"))

    private companion object {
        val NON_OUTPUT_CONNECTORS = setOf("Unknown-1", "None")
    }
}

// ── 纯解析函数（internal 供单测直接调用） ─────────────────────────────────────

// 运行 wayland-info 并返回原始输出（无 Wayland 会话或未安装 wayland-utils 时为 null）；
// 缩放探测与托盘坐标换算共用
internal fun readWaylandInfo(): String? = runCatching {
    if (System.getenv("WAYLAND_DISPLAY").isNullOrBlank()) return null
    val process = ProcessBuilder("wayland-info").redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
    output
}.getOrNull()

// 单个输出的物理模式尺寸与合成器逻辑矩形
// （逻辑坐标来自 xdg_output：分数缩放下不仅尺寸不同，原点也可能带小数取整误差）
internal data class WaylandOutputInfo(
    val physicalWidth: Int,
    val physicalHeight: Int,
    val logicalX: Int,
    val logicalY: Int,
    val logicalWidth: Int,
    val logicalHeight: Int,
)

// 解析 wayland-info 输出：wl_output 的当前模式宽高 + xdg_output 的逻辑位置/宽高（按对象 id 配对）
internal fun parseWaylandInfo(text: String): List<WaylandOutputInfo> {
    val physicalById = WL_OUTPUT_BLOCK.findAll(text).mapNotNull { match ->
        val body = match.groupValues[2]
        val size = CURRENT_MODE_SIZE.find(body) ?: ANY_MODE_SIZE.find(body)
        val width = size?.groupValues?.get(1)?.toIntOrNull()
        val height = size?.groupValues?.get(2)?.toIntOrNull()
        if (width == null || height == null) null else match.groupValues[1] to PhysicalSize(width, height)
    }.toMap()
    val logicalById = XDG_OUTPUT_BLOCK.findAll(text).mapNotNull { match ->
        val body = match.groupValues[2]
        // 原点缺省视为 0：老版本 wayland-info 不一定输出 logical_x/y
        val x = LOGICAL_X.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val y = LOGICAL_Y.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val width = LOGICAL_WIDTH.find(body)?.groupValues?.get(1)?.toIntOrNull()
        val height = LOGICAL_HEIGHT.find(body)?.groupValues?.get(1)?.toIntOrNull()
        if (width == null || height == null) {
            null
        } else {
            match.groupValues[1] to LogicalRect(x, y, width, height)
        }
    }.toMap()

    return physicalById.mapNotNull { (id, physical) ->
        val logical = logicalById[id] ?: return@mapNotNull null
        WaylandOutputInfo(
            physicalWidth = physical.width,
            physicalHeight = physical.height,
            logicalX = logical.x,
            logicalY = logical.y,
            logicalWidth = logical.width,
            logicalHeight = logical.height,
        )
    }
}

private data class PhysicalSize(val width: Int, val height: Int)

private data class LogicalRect(val x: Int, val y: Int, val width: Int, val height: Int)

// 依据 AWT 屏幕宽判定：
//  - 屏幕宽 ≈ 物理模式宽 → 合成器未缩放 X11，按 物理/逻辑 求出真实缩放
//  - 屏幕宽 ≈ 逻辑宽     → 合成器已缩放 X11（如 GNOME 分数缩放），返回 1.0 表示不再补偿
//  - 都不匹配            → 取最大比值（尽力而为）
internal fun waylandScaleFrom(outputs: List<WaylandOutputInfo>, awtScreenWidth: Int): Float? {
    if (outputs.isEmpty()) return null
    fun closeTo(a: Int, b: Int) = abs(a - b) <= 2
    val matchesPhysical = outputs.any { closeTo(it.physicalWidth, awtScreenWidth) }
    val matchesLogical = outputs.any { closeTo(it.logicalWidth, awtScreenWidth) }
    if (awtScreenWidth > 0 && matchesLogical && !matchesPhysical) return 1f

    return outputs.mapNotNull { output ->
        if (output.logicalWidth <= 0) {
            null
        } else {
            (output.physicalWidth.toFloat() / output.logicalWidth).takeIf { it > 1.01f && it <= 4f }
        }
    }.maxOrNull()
}

// Xft.dpi:N → N/96 的缩放（如 144 → 1.5）
internal fun scaleFromXftDpi(xrdbQueryOutput: String): Float? {
    val dpi = XFT_DPI.find(xrdbQueryOutput)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
    return (dpi / 96f).takeIf { it > 1.01f && it <= 4f }
}

// GNOME monitors.xml：取所有逻辑显示器 <scale> 的最大值
internal fun scaleFromGnomeMonitors(xml: String): Float? = GNOME_SCALE.findAll(xml)
    .mapNotNull { it.groupValues[1].toFloatOrNull() }
    .maxOrNull()
    ?.takeIf { it > 1.01f && it <= 4f }

// 工具包环境变量：GDK_SCALE（整数）/ QT_SCALE_FACTOR（浮点），取较大者
internal fun scaleFromToolkitEnv(gdkScale: String?, qtScaleFactor: String?): Float? =
    listOfNotNull(qtScaleFactor?.toFloatOrNull(), gdkScale?.toFloatOrNull())
        .maxOrNull()
        ?.takeIf { it > 1.01f && it <= 4f }

private val WL_OUTPUT_BLOCK = Regex("interface: 'wl_output'[^\\n]*name:\\s*(\\d+)([\\s\\S]*?)(?=interface:|\\z)")
private val XDG_OUTPUT_BLOCK = Regex("output:\\s*(\\d+)([\\s\\S]*?)(?=xdg_output_v1|interface:|\\z)")
private val CURRENT_MODE_SIZE = Regex("width:\\s*(\\d+)\\s*px,\\s*height:\\s*(\\d+)\\s*px[^\\n]*\\n\\s*flags:\\s*current")
private val ANY_MODE_SIZE = Regex("^\\s*width:\\s*(\\d+)\\s*px,\\s*height:\\s*(\\d+)\\s*px", RegexOption.MULTILINE)
private val LOGICAL_X = Regex("logical_x:\\s*(-?\\d+)")
private val LOGICAL_Y = Regex("logical_y:\\s*(-?\\d+)")
private val LOGICAL_WIDTH = Regex("logical_width:\\s*(\\d+)")
private val LOGICAL_HEIGHT = Regex("logical_height:\\s*(\\d+)")
private val XFT_DPI = Regex("(?m)^\\s*Xft\\.dpi:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*$")
private val GNOME_SCALE = Regex("<scale>\\s*([0-9]+(?:\\.[0-9]+)?)\\s*</scale>")

@Serializable
private data class KwinConfigSet(val data: List<KwinOutput> = emptyList())

@Serializable
private data class KwinOutput(
    val connectorName: String? = null,
    val scale: Double? = null
)
