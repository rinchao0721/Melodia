package com.lin0721.linmusic.core.plugin

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException

// 布局节点类型；清单里的 type 取值，改名会让已发布的插件失效
enum class LayoutNodeType(val key: String, val isContainer: Boolean = false) {
    COLUMN("column", true),
    ROW("row", true),
    BOX("box", true),
    COVER("cover"),
    SONG_TITLE("songTitle"),
    ARTIST("artist"),
    SONG_INFO("songInfo"),
    LIKE_BUTTON("likeButton"),
    MINI_LYRIC("miniLyric"),
    PROGRESS("progress"),
    CONTROLS("controls"),
    ACTIONS("actions"),
    LYRICS_CARD("lyricsCard"),
    SPACER("spacer"),
    TEXT("text"),
    PAGER("pager", true),
    COLLAPSE_BUTTON("collapseButton"),
    MORE_BUTTON("moreButton"),
    SOURCE_LABEL("sourceLabel"),
    TIME("time"),
    LYRICS_VIEW("lyricsView"),

    // 以下只用于插件页面
    HEADING("heading"),
    IMAGE("image"),
    LIST("list"),
    TRACK_LIST("trackList"),
    BUTTON("button"),
    TOGGLE("toggle"),
    INPUT("input"),
    LOADING("loading"),
    EMPTY("empty"),
    SELECT("select"),
    SLIDER("slider"),
    PROGRESS_BAR("progressBar"),
    BADGE("badge"),
    TABS("tabs");

    companion object {
        fun fromKey(key: String): LayoutNodeType? = entries.firstOrNull { it.key == key }
    }
}

enum class LayoutAlign(val key: String) {
    START("start"), CENTER("center"), END("end");

    companion object {
        fun fromKey(key: String): LayoutAlign? = entries.firstOrNull { it.key == key }
    }
}

enum class LayoutSize(val key: String, val scale: Float) {
    SMALL("small", 0.8f), MEDIUM("medium", 1f), LARGE("large", 1.25f);

    companion object {
        fun fromKey(key: String): LayoutSize? = entries.firstOrNull { it.key == key }
    }
}

enum class LayoutShape(val key: String) {
    RECT("rect"), ROUNDED("rounded"), CIRCLE("circle");

    companion object {
        fun fromKey(key: String): LayoutShape? = entries.firstOrNull { it.key == key }
    }
}

enum class LayoutTextStyle(val key: String) {
    TITLE("title"), BODY("body"), CAPTION("caption");

    companion object {
        fun fromKey(key: String): LayoutTextStyle? = entries.firstOrNull { it.key == key }
    }
}

// row / column 主轴排列
enum class LayoutArrange(val key: String) {
    START("start"), CENTER("center"), END("end"),
    SPACE_BETWEEN("spaceBetween"), SPACE_AROUND("spaceAround"), SPACE_EVENLY("spaceEvenly");

    companion object {
        fun fromKey(key: String): LayoutArrange? = entries.firstOrNull { it.key == key }
    }
}

// 子节点在父容器里的位置；row / column 里只取交叉轴方向
enum class LayoutPosition(val key: String, val horizontal: LayoutAlign, val vertical: LayoutAlign) {
    TOP_START("topStart", LayoutAlign.START, LayoutAlign.START),
    TOP("top", LayoutAlign.CENTER, LayoutAlign.START),
    TOP_END("topEnd", LayoutAlign.END, LayoutAlign.START),
    START("start", LayoutAlign.START, LayoutAlign.CENTER),
    CENTER("center", LayoutAlign.CENTER, LayoutAlign.CENTER),
    END("end", LayoutAlign.END, LayoutAlign.CENTER),
    BOTTOM_START("bottomStart", LayoutAlign.START, LayoutAlign.END),
    BOTTOM("bottom", LayoutAlign.CENTER, LayoutAlign.END),
    BOTTOM_END("bottomEnd", LayoutAlign.END, LayoutAlign.END);

    companion object {
        fun fromKey(key: String): LayoutPosition? = entries.firstOrNull { it.key == key }
    }
}

enum class ControlVariant(val key: String) {
    DEFAULT("default"), PLAIN("plain"), OUTLINED("outlined");

    companion object {
        fun fromKey(key: String): ControlVariant? = entries.firstOrNull { it.key == key }
    }
}

enum class TimeFormat(val key: String) {
    POSITION("position"), DURATION("duration"), REMAINING("remaining"), BOTH("both");

    companion object {
        fun fromKey(key: String): TimeFormat? = entries.firstOrNull { it.key == key }
    }
}

enum class PagerIndicator(val key: String) {
    NONE("none"), DOTS("dots"), BAR("bar");

    companion object {
        fun fromKey(key: String): PagerIndicator? = entries.firstOrNull { it.key == key }
    }
}

data class LayoutPadding(val top: Float = 0f, val end: Float = 0f, val bottom: Float = 0f, val start: Float = 0f) {
    companion object {
        val Zero = LayoutPadding()
    }
}

// 校验过的布局树；渲染端只读这里的取值，全部已收在合理范围内
data class PlayerLayoutNode(
    val type: LayoutNodeType,
    val children: List<PlayerLayoutNode> = emptyList(),
    val padding: LayoutPadding = LayoutPadding.Zero,
    val spacing: Float = 0f,
    val align: LayoutAlign = LayoutAlign.START,
    val weight: Float? = null,
    val width: Float? = null,
    val height: Float? = null,
    // 百分比尺寸，按父容器剩余空间计算，与固定尺寸互斥
    val widthFraction: Float? = null,
    val heightFraction: Float? = null,
    val minWidth: Float? = null,
    val maxWidth: Float? = null,
    val minHeight: Float? = null,
    val maxHeight: Float? = null,
    val arrange: LayoutArrange? = null,
    val alignSelf: LayoutPosition? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val background: LayoutFill? = null,
    // 子树文字与图标的颜色
    val contentColor: LayoutColor? = null,
    val opacity: Float? = null,
    val elevation: Float = 0f,
    val border: LayoutBorder? = null,
    val textAlign: LayoutAlign? = null,
    val variant: ControlVariant = ControlVariant.DEFAULT,
    val animation: LayoutAnimation? = null,
    val onTap: LayoutAction? = null,
    val onDoubleTap: LayoutAction? = null,
    val onLongPress: LayoutAction? = null,
    val autoHide: Boolean = false,
    // 单行文字放不下时横向滚动
    val marquee: Boolean = false,
    val shape: LayoutShape = LayoutShape.RECT,
    val cornerRadius: Float = DEFAULT_CORNER_RADIUS,
    // 分角圆角，写了时优先于 cornerRadius
    val corners: LayoutCorners? = null,
    val indicator: PagerIndicator = PagerIndicator.NONE,
    val initialPage: Int = 0,
    val timeFormat: TimeFormat = TimeFormat.POSITION,
    // controls / actions 的按钮及顺序
    val buttons: List<String> = emptyList(),
    val size: LayoutSize = LayoutSize.MEDIUM,
    val text: String = "",
    val textStyle: LayoutTextStyle = LayoutTextStyle.BODY,
    val maxLines: Int = 1,
    val showTime: Boolean = true,
    // 封面宽高比与最大边长
    val aspect: Float = 1f,
    val maxSize: Float? = null,
    // 页面专用：list / trackList 绑定的数组、image 的地址、toggle / input 写回的状态字段
    val bind: String = "",
    val action: String? = null,
    // 绑定值为假时不显示
    val visible: String? = null,
    // select 的选项
    val options: List<PluginOption> = emptyList(),
    // slider 的取值范围
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 0f,
    // list 每行的列数
    val columns: Int = 1,
    // input 遮挡显示
    val secret: Boolean = false
) {
    fun contains(type: LayoutNodeType): Boolean = this.type == type || children.any { it.contains(type) }

    fun hasButton(type: LayoutNodeType, button: String): Boolean =
        (this.type == type && button in buttons) || children.any { it.hasButton(type, button) }

    // 宿主保底用：组件一定看得见才算，pager 里、visible 条件下、近乎透明或尺寸太小的都不算；autoHide 由宿主控制，照常算
    fun reliablyShows(match: (PlayerLayoutNode) -> Boolean): Boolean {
        if (hiddenByPlugin) return false
        if (match(this)) return true
        return type != LayoutNodeType.PAGER && children.any { it.reliablyShows(match) }
    }

    fun showsButton(type: LayoutNodeType, button: String): Boolean = reliablyShows { it.type == type && button in it.buttons }

    private val hiddenByPlugin: Boolean
        get() = visible != null ||
            (opacity ?: 1f) < MIN_VISIBLE_ALPHA ||
            listOf(width, height, maxWidth, maxHeight).any { it != null && it < MIN_VISIBLE_SIZE } ||
            listOf(widthFraction, heightFraction).any { it != null && it < MIN_VISIBLE_FRACTION } ||
            (contentColor as? LayoutColor.Fixed)?.let { minOf(it.light.alpha, it.dark.alpha) < MIN_VISIBLE_ALPHA } == true ||
            (animation as? LayoutAnimation.Keyframes)?.restFrames?.any { it.alpha < MIN_VISIBLE_ALPHA || it.scale < MIN_VISIBLE_SCALE } == true

    companion object {
        const val DEFAULT_CORNER_RADIUS = 8f
        private const val MIN_VISIBLE_ALPHA = 0.5f
        private const val MIN_VISIBLE_SCALE = 0.5f
        private const val MIN_VISIBLE_SIZE = 16f
        private const val MIN_VISIBLE_FRACTION = 0.05f
    }
}

// 各区域允许的组件与按钮
enum class PlayerLayoutArea(
    val key: String,
    val label: String,
    val components: Set<LayoutNodeType>,
    val actionButtons: List<String> = emptyList(),
    val controlButtons: List<String> = CONTROL_BUTTONS,
    // frame 里本区域支持的字段
    val frameFields: Set<String> = emptySet(),
    val hostActions: Set<HostAction> = HostAction.COMMON
) {
    ANDROID_FULL_PLAYER(
        "android.fullPlayer", "Android 全屏播放页",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.MINI_LYRIC, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS,
            LayoutNodeType.ACTIONS, LayoutNodeType.LYRICS_CARD, LayoutNodeType.SPACER, LayoutNodeType.TEXT,
            LayoutNodeType.PAGER, LayoutNodeType.COLLAPSE_BUTTON, LayoutNodeType.MORE_BUTTON, LayoutNodeType.SOURCE_LABEL,
            LayoutNodeType.TIME, LayoutNodeType.LYRICS_VIEW
        ),
        ANDROID_ACTIONS,
        ALL_CONTROLS,
        frameFields = setOf("header", "cards"),
        hostActions = HostAction.ANDROID_PLAYER + HostAction.COLLAPSE
    ),
    ANDROID_FULL_PLAYER_WIDE(
        "android.fullPlayerWide", "Android 宽屏播放页",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.MINI_LYRIC, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS,
            LayoutNodeType.ACTIONS, LayoutNodeType.SPACER, LayoutNodeType.TEXT, LayoutNodeType.PAGER, LayoutNodeType.TIME
        ),
        ANDROID_ACTIONS,
        ALL_CONTROLS,
        frameFields = setOf("split"),
        hostActions = HostAction.ANDROID_PLAYER
    ),
    ANDROID_MINI_PLAYER(
        "android.miniPlayer", "Android 迷你播放器",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS,
            LayoutNodeType.SPACER, LayoutNodeType.TEXT, LayoutNodeType.TIME, LayoutNodeType.MINI_LYRIC
        ),
        controlButtons = listOf("previous", CONTROL_PLAY_PAUSE, "next"),
        frameFields = setOf("background", "radius", "shadow", "border")
    ),
    DESKTOP_PLAYER_BAR(
        "desktop.playerBar", "桌面播放栏",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS, LayoutNodeType.ACTIONS,
            LayoutNodeType.SPACER, LayoutNodeType.TEXT, LayoutNodeType.TIME
        ),
        listOf("desktopLyric", "lyricsView", "queue", "device", "volume", "miniPlayer", "fullscreen"),
        ALL_CONTROLS,
        frameFields = setOf("background"),
        hostActions = HostAction.DESKTOP
    ),
    DESKTOP_NOW_PLAYING(
        "desktop.nowPlaying", "桌面「正在播放」顶部",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS, LayoutNodeType.SPACER,
            LayoutNodeType.TEXT, LayoutNodeType.PAGER, LayoutNodeType.TIME
        ),
        controlButtons = ALL_CONTROLS,
        hostActions = HostAction.DESKTOP
    ),
    ANDROID_LYRICS(
        "android.lyrics", "Android 全屏歌词页",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.MINI_LYRIC, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS,
            LayoutNodeType.ACTIONS, LayoutNodeType.SPACER, LayoutNodeType.TEXT, LayoutNodeType.PAGER,
            LayoutNodeType.COLLAPSE_BUTTON, LayoutNodeType.MORE_BUTTON, LayoutNodeType.TIME, LayoutNodeType.LYRICS_VIEW
        ),
        ANDROID_ACTIONS + LYRICS_SETTINGS,
        ALL_CONTROLS,
        hostActions = HostAction.ANDROID_LYRICS
    ),
    DESKTOP_LYRICS_VIEW(
        "desktop.lyricsView", "桌面歌词页",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS, LayoutNodeType.ACTIONS,
            LayoutNodeType.SPACER, LayoutNodeType.TEXT, LayoutNodeType.PAGER, LayoutNodeType.COLLAPSE_BUTTON,
            LayoutNodeType.TIME, LayoutNodeType.LYRICS_VIEW
        ),
        listOf(LYRICS_SETTINGS, "fullscreen", "share"),
        ALL_CONTROLS,
        hostActions = HostAction.DESKTOP_LYRICS
    );

    // 没写 buttons 时用的按钮；后来新增的按钮需要显式写出，避免改变已发布插件的样子
    val defaultControlButtons: List<String> get() = controlButtons - OPT_IN_BUTTONS
    val defaultActionButtons: List<String> get() = actionButtons - OPT_IN_BUTTONS

    // 缺少必需组件时返回原因
    fun missingRequired(root: PlayerLayoutNode): String? = when (this) {
        DESKTOP_PLAYER_BAR -> when {
            !root.showsButton(LayoutNodeType.CONTROLS, CONTROL_PLAY_PAUSE) -> "播放栏必须包含播放/暂停按钮$HIDDEN_HINT"
            !root.reliablyShows { it.type == LayoutNodeType.PROGRESS } -> "播放栏必须包含进度条$HIDDEN_HINT"
            else -> null
        }
        ANDROID_FULL_PLAYER, ANDROID_FULL_PLAYER_WIDE, ANDROID_MINI_PLAYER ->
            if (root.showsButton(LayoutNodeType.CONTROLS, CONTROL_PLAY_PAUSE)) null else "必须包含播放/暂停按钮$HIDDEN_HINT"
        DESKTOP_NOW_PLAYING -> null
        ANDROID_LYRICS, DESKTOP_LYRICS_VIEW ->
            if (root.contains(LayoutNodeType.LYRICS_VIEW)) null else "歌词页必须包含 lyricsView"
    }

    companion object {
        fun fromKey(key: String): PlayerLayoutArea? = entries.firstOrNull { it.key == key }
    }
}

private val CONTAINERS = setOf(LayoutNodeType.COLUMN, LayoutNodeType.ROW, LayoutNodeType.BOX)
private val ANDROID_ACTIONS = listOf("device", "queue", "share", "quality", "more", "sleepTimer", "comments", "lyrics")

private const val HIDDEN_HINT = "，且不能放在 pager 里、不能用 visible 条件、透明度、过小尺寸或动效隐藏"

const val CONTROL_PLAY_PAUSE = "playPause"
const val CONTROL_PLAY_MODE = "playMode"
const val LYRICS_SETTINGS = "lyricsSettings"
val CONTROL_BUTTONS = listOf("shuffle", "previous", CONTROL_PLAY_PAUSE, "next", "repeat")
private val ALL_CONTROLS = CONTROL_BUTTONS + CONTROL_PLAY_MODE
private val OPT_IN_BUTTONS = setOf(CONTROL_PLAY_MODE, "sleepTimer", "comments", "lyrics", LYRICS_SETTINGS)

sealed interface PlayerLayoutParseResult {
    data class Success(
        val root: PlayerLayoutNode,
        val warnings: List<String>,
        val frame: PlayerLayoutFrame = PlayerLayoutFrame.Default,
        val variants: List<PlayerLayoutVariant> = emptyList()
    ) : PlayerLayoutParseResult {
        val spec: PlayerLayoutSpec get() = PlayerLayoutSpec(root, frame, variants)
    }
    data class Failure(val reason: String) : PlayerLayoutParseResult
}

// 布局描述校验：结构超限或缺少必需组件时整个区域无效；单个节点或参数不合法时忽略并提示
object PlayerLayoutParser {

    const val MAX_DEPTH = 8
    const val MAX_NODES = 128
    private const val MAX_PADDING = 64f
    private const val MAX_SPACING = 64f
    private const val MAX_DIMENSION = 1000f
    private const val MAX_WEIGHT = 10f
    private const val MIN_WEIGHT = 0.1f
    private const val MAX_RADIUS = 999f
    private const val MAX_TEXT = 40
    private const val MAX_LINES = 4
    private const val MIN_ASPECT = 0.25f
    private const val MAX_ASPECT = 4f
    private const val MIN_COVER = 24f
    private const val MAX_COLUMNS = 8
    private val ACTION_NAME = Regex("^[A-Za-z0-9_:-]{1,40}$")
    private val PAGE_COMPONENTS = setOf(
        LayoutNodeType.COLUMN, LayoutNodeType.ROW, LayoutNodeType.BOX, LayoutNodeType.SPACER, LayoutNodeType.TEXT,
        LayoutNodeType.HEADING, LayoutNodeType.IMAGE, LayoutNodeType.LIST, LayoutNodeType.TRACK_LIST,
        LayoutNodeType.BUTTON, LayoutNodeType.TOGGLE, LayoutNodeType.INPUT, LayoutNodeType.LOADING, LayoutNodeType.EMPTY,
        LayoutNodeType.SELECT, LayoutNodeType.SLIDER, LayoutNodeType.PROGRESS_BAR, LayoutNodeType.BADGE, LayoutNodeType.TABS
    )

    private const val MAX_PAGES = 5
    private const val MAX_VARIANTS = 4
    private const val MAX_OFFSET = 200f
    private val WRAPPER_KEYS = setOf("layout", "frame", "variants", "styles")
    private const val MAX_ELEVATION = 24f
    private const val MAX_BORDER = 8f
    private val MOTION_KEYS = listOf("animation", "onTap", "onDoubleTap", "onLongPress")
    const val MAX_ANIMATED = 16

    private class TooLarge(message: String) : Exception(message)

    // 值可以直接是布局树，也可以是带 layout 字段的外壳对象
    fun parse(area: PlayerLayoutArea, root: JsonObject): PlayerLayoutParseResult {
        if ("layout" !in root || "type" in root) return parseAreaTree(area, root, emptyMap())
        val layout = root["layout"] as? JsonObject ?: return PlayerLayoutParseResult.Failure("layout 不是有效的布局对象")
        val styleWarnings = mutableListOf<String>()
        val styles = LayoutStyleParser.styles(root["styles"], styleWarnings)
        val main = parseAreaTree(area, layout, styles)
        if (main !is PlayerLayoutParseResult.Success) return main
        val warnings = (styleWarnings + main.warnings).toMutableList()
        root.keys.filter { it !in WRAPPER_KEYS }.forEach { warnings += "布局不支持字段 $it，已忽略" }
        val frame = PlayerLayoutFrameParser.parse(area, root["frame"], warnings)
        return main.copy(warnings = warnings, frame = frame, variants = variants(area, root["variants"], styles, warnings))
    }

    private fun parseAreaTree(area: PlayerLayoutArea, root: JsonObject, styles: Map<String, JsonObject>): PlayerLayoutParseResult {
        val node = parseTree(
            root, area.components, area.label, area.controlButtons, area.actionButtons,
            area.defaultControlButtons, area.defaultActionButtons, playerBindings = true, styles = styles,
            hostActions = area.hostActions
        )
        if (node !is PlayerLayoutParseResult.Success) return node
        area.missingRequired(node.root)?.let { return PlayerLayoutParseResult.Failure(it) }
        return node
    }

    // 每条变体单独校验，无效的只跳过这一条
    private fun variants(
        area: PlayerLayoutArea,
        value: JsonElement?,
        styles: Map<String, JsonObject>,
        warnings: MutableList<String>
    ): List<PlayerLayoutVariant> {
        if (value == null) return emptyList()
        val array = value as? JsonArray ?: return emptyList<PlayerLayoutVariant>().also { warnings += "variants 不是数组，已忽略" }
        if (array.size > MAX_VARIANTS) warnings += "variants 最多 $MAX_VARIANTS 条，多出的已忽略"
        return array.take(MAX_VARIANTS).mapIndexedNotNull { index, item ->
            val label = "variants 第 ${index + 1} 条"
            val obj = item as? JsonObject ?: return@mapIndexedNotNull null.also { warnings += "$label 不是对象，已忽略" }
            val condition = PlayerLayoutFrameParser.parseCondition(obj["when"], warnings, label)
                ?: return@mapIndexedNotNull null.also { warnings += "$label 没有有效的 when 条件，已忽略" }
            val layout = obj["layout"] as? JsonObject ?: return@mapIndexedNotNull null.also { warnings += "$label 缺少 layout，已忽略" }
            when (val parsed = parseAreaTree(area, layout, styles)) {
                is PlayerLayoutParseResult.Failure -> null.also { warnings += "$label 无效（${parsed.reason}），已忽略" }
                is PlayerLayoutParseResult.Success -> {
                    parsed.warnings.forEach { warnings += "$label：$it" }
                    PlayerLayoutVariant(condition, parsed.root)
                }
            }
        }
    }

    // 插件页面：组件集合不同，节点的绑定字段需要额外校验
    fun parsePage(root: JsonObject): PlayerLayoutParseResult =
        parseTree(root, PAGE_COMPONENTS, "页面", emptyList(), emptyList())

    private fun parseTree(
        root: JsonObject,
        components: Set<LayoutNodeType>,
        label: String,
        controlButtons: List<String>,
        actionButtons: List<String>,
        defaultControls: List<String> = controlButtons,
        defaultActions: List<String> = actionButtons,
        // 播放器区域用 $player 绑定，页面用 $state / $item
        playerBindings: Boolean = false,
        styles: Map<String, JsonObject> = emptyMap(),
        hostActions: Set<HostAction> = emptySet()
    ): PlayerLayoutParseResult {
        val warnings = mutableListOf<String>()
        var count = 0
        var animated = 0
        var lyricsViews = 0
        fun build(json: JsonElement, depth: Int, inPager: Boolean): PlayerLayoutNode? {
            if (depth > MAX_DEPTH) throw TooLarge("布局层级超过 $MAX_DEPTH 层")
            count++
            if (count > MAX_NODES) throw TooLarge("布局节点超过 $MAX_NODES 个")
            val raw = json as? JsonObject ?: return null.also { warnings += "布局里有无效节点，已忽略" }
            // 样式先合并进节点，节点自己写的属性优先
            val obj = raw.string("class")?.let { name ->
                styles[name]?.let { JsonObject(it + raw - "class") } ?: raw.also { warnings += "样式 $name 不存在，已忽略" }
            } ?: raw
            val typeKey = obj.string("type")
            val type = typeKey?.let(LayoutNodeType::fromKey) ?: return null.also { warnings += "未知组件 ${typeKey ?: "（缺少 type）"}，已忽略" }
            if (type !in components) return null.also { warnings += "${label}不支持组件 ${type.key}，已忽略" }
            if (type == LayoutNodeType.PAGER && inPager) return null.also { warnings += "pager 不能嵌套，已忽略" }
            // 歌词列表共用宿主的滚动状态，同一棵树里只能有一个
            if (type == LayoutNodeType.LYRICS_VIEW && lyricsViews++ > 0) return null.also { warnings += "lyricsView 只能有一个，多出的已忽略" }
            if (!playerBindings) MOTION_KEYS.filter { it in obj }.forEach { warnings += "${label}不支持 $it，已忽略" }
            val childInPager = inPager || type == LayoutNodeType.PAGER
            var children = if (type.isContainer || type == LayoutNodeType.LIST || type == LayoutNodeType.TABS) {
                (obj["children"] as? JsonArray)?.mapNotNull { build(it, depth + 1, childInPager) }.orEmpty()
            } else {
                emptyList()
            }
            if (type == LayoutNodeType.PAGER) {
                if (children.isEmpty()) return null.also { warnings += "pager 没有子节点，已忽略" }
                if (children.size > MAX_PAGES) {
                    warnings += "pager 最多 $MAX_PAGES 页，多出的已忽略"
                    children = children.take(MAX_PAGES)
                }
            }
            val buttons = when (type) {
                LayoutNodeType.CONTROLS -> buttons(obj, controlButtons, "播放控制", warnings) ?: defaultControls
                LayoutNodeType.ACTIONS -> buttons(obj, actionButtons, "操作按钮", warnings) ?: defaultActions
                else -> emptyList()
            }
            if (type == LayoutNodeType.CONTROLS || type == LayoutNodeType.ACTIONS) {
                if (buttons.isEmpty()) return null.also { warnings += "${type.key} 没有可用的按钮，已忽略" }
            }
            val text = obj.string("text")?.take(MAX_TEXT).orEmpty()
            if (playerBindings && type == LayoutNodeType.TEXT && PlayerBinding.looksLikeBinding(text) && !PlayerBinding.isBinding(text)) {
                return null.also { warnings += "text 绑定 $text 无效，已忽略" }
            }
            val bind = obj.string("bind")?.trim().orEmpty()
            val action = obj.string("action")?.trim()?.takeIf { it.isNotEmpty() }
            pageNodeProblem(type, bind, action)?.let { return null.also { _ -> warnings += "${type.key} $it，已忽略" } }
            val options = if (type == LayoutNodeType.SELECT) PluginParser.parseOptions(obj["options"]) else emptyList()
            if (type == LayoutNodeType.SELECT && options.isEmpty()) return null.also { warnings += "select 没有可选项，已忽略" }
            val min = obj.float("min") ?: 0f
            val max = obj.float("max") ?: 1f
            if (type == LayoutNodeType.SLIDER && min >= max) return null.also { warnings += "slider 的 min、max 无效，已忽略" }
            if (type == LayoutNodeType.TABS && children.isEmpty()) return null.also { warnings += "tabs 没有子节点，已忽略" }
            val offset = offset(obj, warnings)
            val animation = obj["animation"]?.takeIf { playerBindings }?.let { LayoutMotionParser.parse(it, warnings) }?.let {
                if (animated < MAX_ANIMATED) it.also { animated++ } else null.also { warnings += "带动效的节点超过 $MAX_ANIMATED 个，多出的已忽略" }
            }
            fun gesture(key: String) = obj.string(key)?.takeIf { playerBindings }
                ?.let { LayoutGestureParser.parse(it, key, hostActions, allowScript = true, warnings) }
            return PlayerLayoutNode(
                type = type,
                children = children,
                padding = padding(obj["padding"], warnings),
                spacing = obj.float("spacing")?.coerceIn(0f, MAX_SPACING) ?: 0f,
                align = enumValue(obj, "align", LayoutAlign::fromKey, warnings) ?: LayoutAlign.START,
                weight = obj.float("weight")?.coerceIn(MIN_WEIGHT, MAX_WEIGHT),
                width = obj.float("width")?.coerceIn(0f, MAX_DIMENSION),
                height = obj.float("height")?.coerceIn(0f, MAX_DIMENSION),
                widthFraction = fraction(obj, "width", warnings),
                heightFraction = fraction(obj, "height", warnings),
                minWidth = obj.float("minWidth")?.coerceIn(0f, MAX_DIMENSION),
                maxWidth = obj.float("maxWidth")?.coerceIn(0f, MAX_DIMENSION),
                minHeight = obj.float("minHeight")?.coerceIn(0f, MAX_DIMENSION),
                maxHeight = obj.float("maxHeight")?.coerceIn(0f, MAX_DIMENSION),
                arrange = enumValue(obj, "arrange", LayoutArrange::fromKey, warnings),
                alignSelf = enumValue(obj, "alignSelf", LayoutPosition::fromKey, warnings),
                offsetX = offset?.first ?: 0f,
                offsetY = offset?.second ?: 0f,
                background = obj["background"]?.let { value ->
                    LayoutStyleParser.fill(value) ?: null.also { warnings += "background 取值无效，已忽略" }
                },
                contentColor = obj["contentColor"]?.let { value ->
                    LayoutStyleParser.color(value) ?: null.also { warnings += "contentColor 取值无效，已忽略" }
                },
                opacity = obj.float("opacity")?.coerceIn(0f, 1f),
                elevation = obj.float("elevation")?.coerceIn(0f, MAX_ELEVATION) ?: 0f,
                border = obj["border"]?.let { value ->
                    LayoutStyleParser.border(value, MAX_BORDER) ?: null.also { warnings += "border 取值无效，已忽略" }
                },
                textAlign = enumValue(obj, "textAlign", LayoutAlign::fromKey, warnings),
                animation = animation,
                onTap = gesture("onTap"),
                onDoubleTap = gesture("onDoubleTap"),
                onLongPress = gesture("onLongPress"),
                marquee = (type == LayoutNodeType.TEXT || type == LayoutNodeType.MINI_LYRIC) &&
                    (obj["marquee"] as? JsonPrimitive)?.booleanOrNull == true,
                autoHide = playerBindings && (obj["autoHide"] as? JsonPrimitive)?.booleanOrNull == true &&
                    (type != LayoutNodeType.LYRICS_VIEW).also { ok -> if (!ok) warnings += "lyricsView 不能写 autoHide，已忽略" },
                variant = if (type == LayoutNodeType.CONTROLS) {
                    enumValue(obj, "variant", ControlVariant::fromKey, warnings) ?: ControlVariant.DEFAULT
                } else {
                    ControlVariant.DEFAULT
                },
                shape = enumValue(obj, "shape", LayoutShape::fromKey, warnings) ?: LayoutShape.RECT,
                cornerRadius = obj.float("radius")?.coerceIn(0f, MAX_RADIUS) ?: PlayerLayoutNode.DEFAULT_CORNER_RADIUS,
                corners = (obj["radius"] as? JsonArray)?.let { array ->
                    parseCorners(array, MAX_RADIUS) ?: null.also { warnings += "radius 无效，已忽略" }
                },
                indicator = if (type == LayoutNodeType.PAGER) {
                    enumValue(obj, "indicator", PagerIndicator::fromKey, warnings) ?: PagerIndicator.NONE
                } else {
                    PagerIndicator.NONE
                },
                initialPage = obj.int("initialPage")?.coerceIn(0, (children.size - 1).coerceAtLeast(0)) ?: 0,
                timeFormat = if (type == LayoutNodeType.TIME) {
                    enumValue(obj, "format", TimeFormat::fromKey, warnings) ?: TimeFormat.POSITION
                } else {
                    TimeFormat.POSITION
                },
                buttons = buttons,
                size = enumValue(obj, "size", LayoutSize::fromKey, warnings) ?: LayoutSize.MEDIUM,
                text = text,
                textStyle = enumValue(obj, "style", LayoutTextStyle::fromKey, warnings) ?: LayoutTextStyle.BODY,
                maxLines = obj.int("lines")?.coerceIn(1, MAX_LINES) ?: 1,
                showTime = (obj["showTime"] as? JsonPrimitive)?.booleanOrNull ?: true,
                aspect = obj.float("aspect")?.coerceIn(MIN_ASPECT, MAX_ASPECT) ?: 1f,
                maxSize = obj.float("maxSize")?.coerceIn(MIN_COVER, MAX_DIMENSION),
                bind = bind,
                action = action,
                visible = obj.string("visible")?.trim()?.takeIf { value ->
                    val ok = if (playerBindings) PlayerBinding.isBinding(value) else PluginBinding.isBinding(value)
                    ok.also { if (!it) warnings += "visible 取值 $value 不是有效的绑定，已忽略" }
                },
                options = options,
                min = min,
                max = max,
                step = obj.float("step")?.takeIf { it > 0f } ?: 0f,
                columns = obj.int("columns")?.coerceIn(1, MAX_COLUMNS) ?: 1,
                secret = type == LayoutNodeType.INPUT && (obj["secret"] as? JsonPrimitive)?.booleanOrNull == true
            )
        }
        val node = try {
            build(root, 1, inPager = false)
        } catch (e: TooLarge) {
            return PlayerLayoutParseResult.Failure(e.message ?: "布局过大")
        } ?: return PlayerLayoutParseResult.Failure("布局根节点无效")
        return PlayerLayoutParseResult.Success(node, warnings)
    }

    private fun pageNodeProblem(type: LayoutNodeType, bind: String, action: String?): String? = when (type) {
        LayoutNodeType.LIST, LayoutNodeType.TRACK_LIST ->
            if (PluginBinding.isBinding(bind)) null else "的 bind 需要写成 \$state.xxx 或 \$item.xxx"
        // 写回只支持第一层字段
        LayoutNodeType.TOGGLE, LayoutNodeType.INPUT, LayoutNodeType.SELECT, LayoutNodeType.SLIDER ->
            if (PluginBinding.isBinding(bind) && bind.startsWith("\$state.") && '.' !in bind.removePrefix("\$state.")) null else "的 bind 需要写成 \$state.xxx"
        LayoutNodeType.PROGRESS_BAR ->
            if (PluginBinding.isBinding(bind)) null else "的 bind 需要写成 \$state.xxx 或 \$item.xxx"
        LayoutNodeType.IMAGE -> if (bind.isEmpty()) "缺少 bind" else null
        LayoutNodeType.BUTTON -> if (action != null && ACTION_NAME.matches(action)) null else "缺少有效的 action"
        else -> if (action != null && !ACTION_NAME.matches(action)) "的 action 无效" else null
    }

    // 读取清单里某个页面的描述；缺少 playback.control 时去掉歌曲列表
    fun loadPage(manifest: PluginManifest, page: PluginPage): PlayerLayoutParseResult {
        val json = when (val ref = page.ui) {
            is PluginUiRef.Inline -> ref.tree
            is PluginUiRef.File -> readJson(manifest, ref.path) ?: return PlayerLayoutParseResult.Failure("${ref.path} 缺失或不是有效的 JSON 对象")
        }
        val parsed = parsePage(json)
        if (parsed !is PlayerLayoutParseResult.Success || PluginPermission.PLAYBACK_CONTROL in manifest.permissions) return parsed
        if (!parsed.root.contains(LayoutNodeType.TRACK_LIST)) return parsed
        fun strip(node: PlayerLayoutNode): PlayerLayoutNode =
            node.copy(
                children = node.children.filter { it.type != LayoutNodeType.TRACK_LIST }.map(::strip)
                    .filterNot { it.type == LayoutNodeType.TABS && it.children.isEmpty() }
            )
        return parsed.copy(root = strip(parsed.root), warnings = parsed.warnings + "trackList 需要 playback.control 权限，已忽略")
    }

    private fun readJson(manifest: PluginManifest, path: String): JsonObject? {
        val file = manifest.file(path) ?: return null
        return try {
            Json.parseToJsonElement(file.readText()) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    // 读取清单里某个区域的布局，文件缺失或不是 JSON 对象时失败
    fun load(manifest: PluginManifest, area: PlayerLayoutArea): PlayerLayoutParseResult {
        val json = when (val ref = manifest.contributes.playerLayouts[area.key]) {
            null -> return PlayerLayoutParseResult.Failure("插件没有提供这个区域的布局")
            is PluginUiRef.Inline -> ref.tree
            is PluginUiRef.File -> readJson(manifest, ref.path) ?: return PlayerLayoutParseResult.Failure("${ref.path} 缺失或不是有效的 JSON 对象")
        }
        val parsed = parse(area, json)
        if (parsed !is PlayerLayoutParseResult.Success || !parsed.spec.usesScript || manifest.runsLayoutScripts) return parsed
        // 沙盒插件没声明 layoutActions 时脚本不会运行，去掉脚本动作
        return parsed.copy(
            root = parsed.root.withoutScripts(),
            variants = parsed.variants.map { it.copy(root = it.root.withoutScripts()) },
            warnings = parsed.warnings + "用到脚本动作需要在 contributes 里写 \"layoutActions\": true 并带上 main.js，已忽略这些动作"
        )
    }

    // 未写 buttons 时返回 null 表示用全部按钮
    private fun buttons(obj: JsonObject, allowed: List<String>, label: String, warnings: MutableList<String>): List<String>? {
        val array = obj["buttons"] as? JsonArray ?: return null
        return array.mapNotNull { item ->
            val key = (item as? JsonPrimitive)?.takeIf { it.isString }?.content
            key?.takeIf { it in allowed } ?: null.also { warnings += "$label 不支持按钮 ${key ?: item}，已忽略" }
        }.distinct()
    }

    // 一个数字为四边相同；两个为上下、左右；四个为上、右、下、左
    private fun padding(value: JsonElement?, warnings: MutableList<String>): LayoutPadding {
        fun clamp(v: Float) = v.coerceIn(0f, MAX_PADDING)
        return when (value) {
            null -> LayoutPadding.Zero
            is JsonPrimitive -> value.floatOrNull?.let { clamp(it) }?.let { LayoutPadding(it, it, it, it) }
                ?: LayoutPadding.Zero.also { warnings += "padding 无效，已忽略" }
            is JsonArray -> {
                val numbers = value.mapNotNull { (it as? JsonPrimitive)?.floatOrNull?.let(::clamp) }
                when {
                    numbers.size != value.size -> LayoutPadding.Zero.also { warnings += "padding 无效，已忽略" }
                    numbers.size == 2 -> LayoutPadding(numbers[0], numbers[1], numbers[0], numbers[1])
                    numbers.size == 4 -> LayoutPadding(numbers[0], numbers[1], numbers[2], numbers[3])
                    else -> LayoutPadding.Zero.also { warnings += "padding 无效，已忽略" }
                }
            }
            else -> LayoutPadding.Zero.also { warnings += "padding 无效，已忽略" }
        }
    }

    // "45%" 形式的尺寸；数字由 width / height 处理
    private fun fraction(obj: JsonObject, key: String, warnings: MutableList<String>): Float? {
        val text = obj.string(key)?.trim() ?: return null
        val percent = text.removeSuffix("%").takeIf { text.endsWith("%") }?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
            ?: return null.also { warnings += "$key 取值 $text 无效，已忽略" }
        return (percent / 100f).coerceAtMost(1f)
    }

    private fun offset(obj: JsonObject, warnings: MutableList<String>): Pair<Float, Float>? {
        val value = obj["offset"] ?: return null
        val numbers = (value as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.floatOrNull?.takeIf { v -> v.isFinite() } }
        val x = numbers?.getOrNull(0)
        val y = numbers?.getOrNull(1)
        if (numbers?.size != 2 || x == null || y == null) return null.also { warnings += "offset 无效，已忽略" }
        return x.coerceIn(-MAX_OFFSET, MAX_OFFSET) to y.coerceIn(-MAX_OFFSET, MAX_OFFSET)
    }

    private fun <T> enumValue(obj: JsonObject, key: String, from: (String) -> T?, warnings: MutableList<String>): T? {
        val text = obj.string(key) ?: return null
        return from(text) ?: null.also { warnings += "$key 取值 $text 无效，已忽略" }
    }

    private fun JsonObject.float(key: String): Float? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
}

// 当前生效的播放器布局，key 为区域；没有插件布局的区域不在表里
val LocalPlayerLayouts = staticCompositionLocalOf<Map<PlayerLayoutArea, PlayerLayoutSpec>> { emptyMap() }
