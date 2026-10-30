package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.material.icons.rounded.Extension
import com.lin0721.linmusic.core.plugin.LocalPluginCommands
import com.lin0721.linmusic.core.plugin.PluginCommandItem
import com.lin0721.linmusic.core.plugin.PluginMenuLocation
import com.lin0721.linmusic.core.plugin.rememberPluginCommands
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.desktop.data.download.DownloadTask
import com.lin0721.linmusic.desktop.data.download.DownloadTaskStatus
import com.lin0721.linmusic.desktop.ui.cloud.CloudMusicActions
import com.lin0721.linmusic.desktop.ui.cloud.CloudUploadsButton
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.MenuScope
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.components.trackTextInputFocus
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.download.DownloadRoundTracker
import com.lin0721.linmusic.desktop.ui.icons.DownloadIcons
import com.lin0721.linmusic.desktop.ui.navigation.BackStack
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

private val CloseHover = Color(0xFFE81123)

private const val CHECK_SHOW_MS = 1_500L
private const val FILL_ANIMATION_MS = 200

// 与底栏侧边图标按钮（PlayerBar）同尺寸、同悬停放大系数
private val DOWNLOAD_BUTTON_SIZE = 32.dp
private val DOWNLOAD_ICON_SIZE = 20.dp
private const val DOWNLOAD_HOVER_SCALE = 1.1f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.TitleBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    isHomeAll: Boolean,
    onHomeClick: () -> Unit,
    isMaximized: Boolean,
    userProfile: UserProfile?,
    searchQuery: String,
    searchPlaceholder: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onSearchBoundsChange: (Rect) -> Unit,
    onSearchMove: (Int) -> Boolean,
    onSearchSubmit: () -> Unit,
    searchFocusRequests: Flow<Unit>,
    isBrowseActive: Boolean,
    onBrowseClick: () -> Unit,
    recognitionOpen: Boolean,
    onRecognitionClick: () -> Unit,
    cloudMusic: CloudMusicActions,
    downloadTasks: List<DownloadTask>,
    downloadsOpen: Boolean,
    onDownloadsClick: (() -> Unit)?,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    onRecentClick: () -> Unit,
    onCloudClick: () -> Unit,
    onSettingsClick: () -> Unit,
    hasUpdate: Boolean,
    onLogoutClick: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(DesktopDimens.TitleBarHeight)) {
        // 整条标题栏作拖动区，按钮叠在上层自行消费点击；
        // 双击取 AWT 自带的 clickCount 判断，不消费事件；
        // 须在松开时切换：按下时切换会被拖动逻辑按旧位置把窗口挪回去，最大化时也不启用拖动
        val doubleClickToggle = Modifier.fillMaxSize().onPointerEvent(PointerEventType.Release) { event ->
            if (event.awtEventOrNull?.clickCount == 2) onToggleMaximize()
        }
        if (isMaximized) {
            Box(doubleClickToggle)
        } else {
            WindowDragArea(doubleClickToggle) {
                Box(Modifier.fillMaxSize())
            }
        }
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavArrow(Icons.AutoMirrored.Rounded.ArrowBackIos, "后退", canGoBack, onBack)
                NavArrow(Icons.AutoMirrored.Rounded.ArrowForwardIos, "前进", canGoForward, onForward)
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeButton(
                        isHomeAll = isHomeAll,
                        onClick = onHomeClick
                    )
                    SearchBox(
                        query = searchQuery,
                        placeholder = searchPlaceholder,
                        onQueryChange = onSearchQueryChange,
                        onFocusChange = onSearchFocusChange,
                        onBoundsChange = onSearchBoundsChange,
                        onMove = onSearchMove,
                        onSubmit = onSearchSubmit,
                        focusRequests = searchFocusRequests,
                        isBrowseActive = isBrowseActive,
                        onBrowseClick = onBrowseClick,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    RecognitionButton(active = recognitionOpen, onClick = onRecognitionClick)
                }
            }
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                rememberPluginCommands(PluginMenuLocation.TOOLBAR).forEach { item -> PluginToolbarButton(item) }
                CloudUploadsButton(cloudMusic)
                if (onDownloadsClick != null) DownloadsButton(downloadTasks, downloadsOpen, onDownloadsClick)
                AvatarMenu(userProfile, onLoginClick, onProfileClick, onRecentClick, onCloudClick, onSettingsClick, hasUpdate, onLogoutClick)
                WindowButton(Icons.Rounded.Remove, "最小化", onClick = onMinimize)
                WindowButton(
                    if (isMaximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare,
                    if (isMaximized) "还原" else "最大化",
                    onClick = onToggleMaximize
                )
                WindowButton(Icons.Rounded.Close, "关闭", hoverColor = CloseHover, onClick = onClose)
            }
        }
    }
}

@Composable
private fun PluginToolbarButton(item: PluginCommandItem) {
    val commands = LocalPluginCommands.current
    DesktopTooltip(item.title, side = TooltipSide.Bottom, modifier = Modifier.padding(end = 8.dp)) {
        IconButton(onClick = { commands?.run(item) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Extension, item.title, tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
        }
    }
}

// 首页圆形按钮，尺寸与搜索栏 48.dp 统一，具备悬停提亮与缩放动效
// 与左侧首页按钮对称的圆形按钮，弹窗打开期间保持高亮底色
@Composable
private fun RecognitionButton(active: Boolean, onClick: () -> Unit) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (isHovered) 1.06f else 1f, uiTween(150))
    val bgColor by animateColorAsState(if (isHovered || active) DesktopColors.FieldOnCanvasHover else DesktopColors.FieldOnCanvas, uiTween(150))
    DesktopTooltip("听歌识曲", side = TooltipSide.Bottom) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(48.dp)
                .hoverable(hoverSource)
                .clip(CircleShape)
                .floatingOutline(CircleShape)
                .background(bgColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.GraphicEq, "听歌识曲", tint = DesktopColors.TextPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HomeButton(
    isHomeAll: Boolean,
    onClick: () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(if (isHovered) 1.06f else 1f, uiTween(150))
    val bgColor by animateColorAsState(if (isHovered) DesktopColors.FieldOnCanvasHover else DesktopColors.FieldOnCanvas, uiTween(150))

    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(48.dp)
            .hoverable(hoverSource)
            .clip(CircleShape)
            .floatingOutline(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 已在首页“全部”时是空心，其余位置是实心
        Icon(
            if (isHomeAll) Icons.Outlined.Home else Icons.Rounded.Home,
            "首页",
            tint = DesktopColors.TextPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

// 头像左侧的下载入口，样式同底栏图标按钮
// 图标按整轮进度染色，全部成功短暂变对勾；有暂停或失败的任务时亮红点
@Composable
private fun DownloadsButton(tasks: List<DownloadTask>, open: Boolean, onClick: () -> Unit) {
    val tracker = remember { DownloadRoundTracker() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var showCheck by remember { mutableStateOf(false) }
    LaunchedEffect(tasks) {
        val summary = tracker.update(tasks)
        progress = summary.progress
        if (summary.progress != null) showCheck = false
        if (summary.finished && summary.allSucceeded) showCheck = true
    }
    LaunchedEffect(showCheck) {
        if (showCheck) {
            delay(CHECK_SHOW_MS)
            showCheck = false
        }
    }
    val fill by animateFloatAsState(progress ?: 0f, uiTween(FILL_ANIMATION_MS), label = "downloadFill")
    val needsAttention = tasks.any { it.status == DownloadTaskStatus.PAUSED || it.status == DownloadTaskStatus.FAILED }

    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) DOWNLOAD_HOVER_SCALE else 1f, uiTween(FILL_ANIMATION_MS), label = "downloadHover")
    val tint = if (hovered || open) DesktopColors.TextPrimary else DesktopColors.TextGray
    val tooltip = progress?.let { "下载中 ${(it * 100).roundToInt()}%" } ?: "下载管理"
    DesktopTooltip(tooltip, side = TooltipSide.Bottom, modifier = Modifier.padding(end = 8.dp)) {
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onClick,
                interactionSource = hoverSource,
                modifier = Modifier.size(DOWNLOAD_BUTTON_SIZE).graphicsLayer { scaleX = scale; scaleY = scale }
            ) {
                Crossfade(showCheck, animationSpec = uiTween(FILL_ANIMATION_MS), label = "downloadIcon") { check ->
                    Box(Modifier.size(DOWNLOAD_ICON_SIZE)) {
                        val icon = if (check) DownloadIcons.Check else DownloadIcons.Download
                        Icon(icon, "下载管理", tint = if (check) DesktopColors.TextPrimary else tint, modifier = Modifier.fillMaxSize())
                        if (!check && fill > 0f) {
                            Icon(
                                icon,
                                null,
                                tint = DesktopColors.Accent,
                                modifier = Modifier.fillMaxSize().drawWithContent {
                                    clipRect(top = size.height * (1f - fill)) { this@drawWithContent.drawContent() }
                                }
                            )
                        }
                    }
                }
            }
            if (needsAttention && !showCheck) {
                AttentionDot(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp))
            }
        }
    }
}

// 红色提醒圆点，外圈描边与标题栏背景分隔
@Composable
private fun AttentionDot(modifier: Modifier = Modifier) {
    Box(
        modifier.size(8.dp)
            .border(1.dp, DesktopColors.WindowBackground, CircleShape)
            .padding(1.dp)
            .clip(CircleShape)
            .background(DesktopColors.Accent)
    )
}

// 未登录时菜单提供登录入口，设置页始终可达；有新版本时头像与设置项亮红点
@Composable
private fun AvatarMenu(
    userProfile: UserProfile?,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    onRecentClick: () -> Unit,
    onCloudClick: () -> Unit,
    onSettingsClick: () -> Unit,
    hasUpdate: Boolean,
    onLogoutClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = 12.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(DesktopColors.Surface).clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            if (userProfile != null) {
                Cover(userProfile.avatarUrl, 32.dp, shape = CircleShape)
            } else {
                Icon(Icons.Rounded.Person, "账户", tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
            }
        }
        if (hasUpdate) AttentionDot(Modifier.align(Alignment.TopEnd))
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }) {
            if (userProfile != null) {
                Text(
                    userProfile.nickname,
                    color = DesktopColors.TextGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
                MenuDivider()
            } else {
                AvatarMenuItem(Icons.AutoMirrored.Rounded.Login, "登录") {
                    expanded = false
                    onLoginClick()
                }
            }
            if (userProfile != null) {
                AvatarMenuItem(Icons.Rounded.Person, "个人主页") {
                    expanded = false
                    onProfileClick()
                }
                AvatarMenuItem(Icons.Rounded.History, "最近播放") {
                    expanded = false
                    onRecentClick()
                }
                AvatarMenuItem(Icons.Rounded.Cloud, "我的云盘") {
                    expanded = false
                    onCloudClick()
                }
            }
            MenuItem(
                "设置",
                onClick = {
                    expanded = false
                    onSettingsClick()
                },
                icon = Icons.Rounded.Settings,
                trailing = if (hasUpdate) {
                    { Box(Modifier.size(6.dp).clip(CircleShape).background(DesktopColors.Accent)) }
                } else {
                    null
                }
            )
            if (userProfile != null) {
                AvatarMenuItem(Icons.AutoMirrored.Rounded.Logout, "退出登录") {
                    expanded = false
                    onLogoutClick()
                }
            }
        }
    }
}

@Composable
private fun MenuScope.AvatarMenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    MenuItem(text, onClick, icon = icon)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SearchBox(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onBoundsChange: (Rect) -> Unit,
    onMove: (Int) -> Boolean,
    onSubmit: () -> Unit,
    focusRequests: Flow<Unit>,
    isBrowseActive: Boolean,
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    LaunchedEffect(focusRequests) {
        focusRequests.collect { runCatching { focusRequester.requestFocus() } }
    }

    val targetBg = when {
        isFocused || isHovered -> DesktopColors.FieldOnCanvasHover
        else -> DesktopColors.FieldOnCanvas
    }
    val animatedBg by animateColorAsState(targetBg, uiTween(180))

    val borderColor = when {
        isFocused -> DesktopColors.TextPrimary
        isHovered -> DesktopColors.TextPrimary.copy(alpha = 0.55f)
        DesktopColors.IsDark -> Color.Transparent
        else -> DesktopColors.Outline
    }
    val animatedBorderColor by animateColorAsState(borderColor, uiTween(180))

    val scale by animateFloatAsState(
        targetValue = if (isHovered || isFocused) 1.015f else 1f,
        animationSpec = uiTween(180)
    )

    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .widthIn(min = 200.dp, max = 490.dp)
            .height(48.dp)
            .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
            .hoverable(hoverInteraction)
            .clip(RoundedCornerShape(24.dp))
            .border(2.dp, animatedBorderColor, RoundedCornerShape(24.dp))
            .background(animatedBg)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Search,
            null,
            tint = if (isFocused || isHovered) DesktopColors.TextPrimary else DesktopColors.TextGray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        placeholder,
                        color = DesktopColors.TextGray,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isHovered && !isFocused) {
                        Spacer(Modifier.width(8.dp))
                        SearchShortcutKey("Ctrl")
                        Spacer(Modifier.width(3.dp))
                        SearchShortcutKey("F")
                    }
                }
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = DesktopColors.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(DesktopColors.TextPrimary),
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(focusRequester)
                    .trackTextInputFocus()
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusChange(it.isFocused)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> onMove(1)
                            Key.DirectionUp -> onMove(-1)
                            Key.Enter -> {
                                onSubmit()
                                // 提交后释放焦点，空格等窗口快捷键才不会被输入框吃掉
                                focusManager.clearFocus()
                                true
                            }
                            else -> false
                        }
                    }
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                "清空",
                tint = DesktopColors.TextGray,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") }
                    .padding(2.dp)
            )
        }
        Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(24.dp).background(DesktopColors.SurfaceLight))
        DesktopTooltip("浏览", side = TooltipSide.Bottom) {
            BrowseCabinetButton(
                isActive = isBrowseActive,
                onClick = onBrowseClick
            )
        }
    }
}

// 快捷键按键徽标
@Composable
private fun SearchShortcutKey(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, DesktopColors.Overlay.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
            .background(DesktopColors.Overlay.copy(alpha = 0.05f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = DesktopColors.TextGray,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

// 包含独立悬停动效与自绘柜子/抽屉图标的浏览按钮
@Composable
private fun BrowseCabinetButton(
    isActive: Boolean,
    onClick: () -> Unit
) {
    val buttonHover = remember { MutableInteractionSource() }
    val isButtonHovered by buttonHover.collectIsHoveredAsState()

    val buttonScale by animateFloatAsState(
        targetValue = if (isButtonHovered) 1.15f else 1f,
        animationSpec = uiTween(150)
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            isActive || isButtonHovered -> DesktopColors.TextPrimary
            else -> DesktopColors.TextGray
        },
        animationSpec = uiTween(150)
    )

    Box(
        Modifier
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .hoverable(buttonHover)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        BrowseCabinetIcon(
            color = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

// 自绘热搜榜与歌单分类“收纳箱/抽屉”图标
@Composable
private fun BrowseCabinetIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6f * density

        // 顶部小把手/弧线盖
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.28f, h * 0.12f),
            size = Size(w * 0.44f, h * 0.16f),
            cornerRadius = CornerRadius(2f * density, 2f * density),
            style = Stroke(width = strokeWidth)
        )

        // 主体箱体外框
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.28f),
            size = Size(w * 0.76f, h * 0.60f),
            cornerRadius = CornerRadius(3f * density, 3f * density),
            style = Stroke(width = strokeWidth)
        )

        // 抽屉把手/中间扣手
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.38f, h * 0.52f),
            size = Size(w * 0.24f, h * 0.12f),
            cornerRadius = CornerRadius(2f * density, 2f * density),
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
private fun NavArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            description,
            tint = if (enabled) DesktopColors.TextPrimary else DesktopColors.SurfaceLight,
            modifier = Modifier.size(16.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WindowButton(
    icon: ImageVector,
    description: String,
    hoverColor: Color = DesktopColors.Hover,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        Modifier.width(46.dp).fillMaxHeight()
            .background(if (hovered) hoverColor else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = DesktopColors.TextPrimary, modifier = Modifier.size(16.dp))
    }
}
