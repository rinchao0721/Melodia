package com.lin0721.linmusic.feature.player.share

import androidx.compose.runtime.rememberCoroutineScope
import com.lin0721.linmusic.core.deeplink.SongLinkRequests
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.share.ShareCardBackground
import com.lin0721.linmusic.core.share.ShareCardContent
import com.lin0721.linmusic.core.share.ShareCardKind
import com.lin0721.linmusic.core.share.ShareCardRatio
import com.lin0721.linmusic.core.share.ShareCardStyle
import com.lin0721.linmusic.core.share.ui.ShareCard
import com.lin0721.linmusic.core.share.ui.ShareCardArt
import com.lin0721.linmusic.core.share.ui.ShareCardTypography
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.contentColorOn
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.uiEnter
import com.lin0721.linmusic.core.ui.theme.uiExit
import com.lin0721.linmusic.core.ui.theme.uiTween
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.message.ui.ShareToFriendUiState
import kotlin.math.absoluteValue

private val PillShape = RoundedCornerShape(PillRadius)

@Composable
internal fun ShareCardPreviewPage(
    content: ShareCardContent,
    art: ShareCardArt,
    kind: ShareCardKind,
    hasLyrics: Boolean,
    styleIndex: Int,
    background: ShareCardBackground,
    ratio: ShareCardRatio,
    typography: ShareCardTypography,
    blurSupported: Boolean,
    exporting: Boolean,
    songLink: String?,
    showFriendBar: Boolean,
    friendUiState: ShareToFriendUiState,
    sentNotice: ShareSentNotice?,
    onNavigateToPrivateChat: (Long) -> Unit,
    onSendToFriend: (Long) -> Unit,
    onOpenMoreFriends: () -> Unit,
    onKindChange: (ShareCardKind) -> Unit,
    onStyleIndexChange: (Int) -> Unit,
    onBackgroundChange: (ShareCardBackground) -> Unit,
    onRatioChange: (ShareCardRatio) -> Unit,
    onTypographyChange: (ShareCardTypography) -> Unit,
    onEditLyrics: (() -> Unit)?,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val styles = ShareCardStyle.of(kind)
    var fontSheetOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val settingsPreferences: SettingsPreferences = koinInject()
    val songLinkRequests: SongLinkRequests = koinInject()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val motion = LocalUiMotion.current
    var bottomActionsHeightPx by remember { mutableIntStateOf(0) }
    // 退场淡出期间保留最后一次收件人，避免文本头像提前消失
    var heldSentNotice by remember { mutableStateOf(sentNotice) }
    if (sentNotice != null) {
        heldSentNotice = sentNotice
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (showFriendBar) {
                ShareToFriendBar(
                    state = friendUiState,
                    onUserClick = onSendToFriend,
                    onMoreClick = onOpenMoreFriends
                )
            }
            key(kind) {
                val pagerState = rememberPagerState(initialPage = styleIndex.coerceIn(0, styles.lastIndex)) { styles.size }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect(onStyleIndexChange)
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 44.dp),
                    pageSpacing = 14.dp
                ) { page ->
                    val offset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction).absoluteValue
                    Column(
                        Modifier.fillMaxSize().graphicsLayer {
                            val scale = 1f - 0.08f * offset.coerceIn(0f, 1f)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - 0.4f * offset.coerceIn(0f, 1f)
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ShareCard(
                                content = content,
                                style = styles[page],
                                background = background,
                                ratio = ratio,
                                art = art,
                                modifier = Modifier.aspectRatio(ratio.aspect).clip(RoundedCornerShape(16.dp)),
                                typography = typography
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(styles[page].label, color = MelodiaColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(MelodiaSpacing.md))
            if (hasLyrics) {
                KindToggle(kind, onKindChange, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(MelodiaSpacing.md))
            }
            BackgroundRow(art, background, blurSupported, onBackgroundChange)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = MelodiaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                songLink?.let { link ->
                    OptionChip("复制链接", Icons.Rounded.Link, false) {
                        clipboard.setText(AnnotatedString(link))
                        // 自己复制出去的链接不要下次开应用再弹一次
                        songLinkRequests.markSelfHandled(link)
                        scope.launch { settingsPreferences.setLastHandledClipboardLink(link) }
                        ToastManager.showToast("已复制链接")
                    }
                }
                ShareCardRatio.entries.forEach { option ->
                    OptionChip(option.label, null, option == ratio) { onRatioChange(option) }
                }
                if (kind == ShareCardKind.LYRIC) {
                    OptionChip("歌词字体", Icons.Rounded.TextFields, typography != ShareCardTypography()) { fontSheetOpen = true }
                    onEditLyrics?.let { OptionChip("编辑歌词", Icons.Rounded.Edit, false, it) }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { bottomActionsHeightPx = it.height }
                    .navigationBarsPadding()
                    .padding(MelodiaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MelodiaOutlinedButton(
                    onClick = onSave,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("保存图片", fontWeight = FontWeight.Bold)
                }
                MelodiaButton(
                    onClick = onShare,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f).height(48.dp),
                    // 选中/主操作用主文字色反白，深浅主题都能看清
                    colors = ButtonDefaults.buttonColors(containerColor = MelodiaColors.TextPrimary, contentColor = MelodiaColors.InverseText)
                ) {
                    if (exporting) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = MelodiaColors.InverseText, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("分享", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val bottomClearance = if (bottomActionsHeightPx > 0) {
            with(density) { bottomActionsHeightPx.toDp() }
        } else {
            88.dp
        }
        val slideOffsetPx = with(density) { 12.dp.roundToPx() }
        AnimatedVisibility(
            visible = sentNotice != null,
            enter = uiEnter(
                fadeIn(uiTween(220)) +
                    slideInVertically(uiTween(220)) { slideOffsetPx }
            ),
            exit = uiExit(fadeOut(uiTween(200))),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = MelodiaSpacing.md)
                .padding(bottom = bottomClearance)
        ) {
            heldSentNotice?.let { notice ->
                AnimatedContent(
                    targetState = notice,
                    transitionSpec = {
                        motion.transform(fadeIn(motion.tween(150)) togetherWith fadeOut(motion.tween(120)))
                    },
                    contentKey = { it.id },
                    label = "shareSentNotice"
                ) { current ->
                    ShareSentCapsule(
                        user = current.user,
                        onGoToChat = { onNavigateToPrivateChat(current.user.uid) }
                    )
                }
            }
        }
    }
    if (fontSheetOpen) {
        ShareFontSheet(typography, onTypographyChange) { fontSheetOpen = false }
    }
}

@Composable
private fun KindToggle(kind: ShareCardKind, onChange: (ShareCardKind) -> Unit, modifier: Modifier) {
    Row(
        modifier.width(260.dp).height(40.dp).clip(PillShape).background(MelodiaColors.Fill)
    ) {
        ShareCardKind.entries.forEach { option ->
            val selected = option == kind
            val bg by animateColorAsState(if (selected) MelodiaColors.TextPrimary else Color.Transparent, label = "kindBg")
            val fg by animateColorAsState(if (selected) MelodiaColors.InverseText else MelodiaColors.TextPrimary, label = "kindFg")
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .pressable(MelodiaPress.Pill, PillShape) { onChange(option) }
                    .background(bg, PillShape),
                contentAlignment = Alignment.Center
            ) {
                Text(option.label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BackgroundRow(
    art: ShareCardArt,
    current: ShareCardBackground,
    blurSupported: Boolean,
    onChange: (ShareCardBackground) -> Unit
) {
    val base = art.palette.firstOrNull() ?: Color(0xFF2F5566)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = MelodiaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Swatch(current == ShareCardBackground.Auto, { onChange(ShareCardBackground.Auto) }) {
            Box(Modifier.fillMaxSize().background(lerp(base, Color.Black, 0.25f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "自动", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        repeat(ShareCardBackground.PALETTE_SIZE) { i ->
            val option = ShareCardBackground.Palette(i)
            val color = art.palette.getOrNull(i) ?: return@repeat
            Swatch(current == option, { onChange(option) }) {
                Box(Modifier.fillMaxSize().background(lerp(color, Color.Black, 0.15f)))
            }
        }
        ShareCardBackground.presets.forEach { option ->
            Swatch(current == option, { onChange(option) }) {
                Box(Modifier.fillMaxSize().background(Color(option.argb)))
            }
        }
        val cover = art.cover
        if (blurSupported && cover != null) {
            Swatch(current == ShareCardBackground.BlurCover, { onChange(ShareCardBackground.BlurCover) }) {
                Image(
                    painter = cover,
                    contentDescription = "模糊封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(4.dp)
                )
            }
        }
    }
}

@Composable
private fun Swatch(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ring by animateColorAsState(if (selected) MelodiaColors.TextPrimary else MelodiaColors.Outline, label = "swatchRing")
    Box(
        Modifier.size(40.dp)
            .pressable(MelodiaPress.Pill, CircleShape, onClick = onClick)
            .border(if (selected) 2.5.dp else 1.dp, ring, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
    ) {
        content()
    }
}

@Composable
internal fun OptionChip(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) MelodiaColors.TextPrimary else MelodiaColors.Fill, label = "chipBg")
    val fg by animateColorAsState(if (selected) MelodiaColors.InverseText else MelodiaColors.TextPrimary, label = "chipFg")
    Row(
        Modifier.height(36.dp)
            .pressable(MelodiaPress.Pill, PillShape, onClick = onClick)
            .background(bg, PillShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ShareToFriendBar(
    state: ShareToFriendUiState,
    onUserClick: (Long) -> Unit,
    onMoreClick: () -> Unit
) {
    when (state) {
        ShareToFriendUiState.Idle, ShareToFriendUiState.Loading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MelodiaSpacing.sm)
            ) {
                Text(
                    text = "发给好友",
                    color = MelodiaColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = MelodiaSpacing.md)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = MelodiaSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(5) {
                        FriendPlaceholderItem()
                    }
                }
            }
        }

        is ShareToFriendUiState.Error -> Unit

        // 没有最近联系人时仍保留「更多」，可以从关注列表里选人
        is ShareToFriendUiState.Ready -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MelodiaSpacing.sm)
            ) {
                Text(
                    text = "发给好友",
                    color = MelodiaColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = MelodiaSpacing.md)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MelodiaSpacing.md),
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.recentUsers.forEach { user ->
                            FriendAvatarItem(
                                user = user,
                                isSending = state.sendingToUid == user.uid,
                                enabled = state.sendingToUid == null,
                                onClick = { onUserClick(user.uid) }
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    FriendMoreItem(
                        enabled = state.sendingToUid == null,
                        onClick = onMoreClick
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendAvatarItem(
    user: MessageUser,
    isSending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .alpha(if (enabled || isSending) 1f else 0.55f)
            .pressable(
                style = MelodiaPress.Pill,
                shape = RoundedCornerShape(10.dp),
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            FriendAvatarCircle(
                user = user,
                size = 40.dp,
                fallbackIconSize = 20.dp
            )
            if (isSending) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MelodiaColors.Background.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MelodiaColors.Accent,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = user.nickname.takeIf { it.isNotBlank() } ?: "网易云用户",
            color = MelodiaColors.TextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FriendAvatarCircle(
    user: MessageUser,
    size: Dp,
    fallbackIconSize: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MelodiaColors.Fill)
            .border(0.5.dp, MelodiaColors.Outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val avatarModel = user.avatarUrl.takeIf { it.isNotBlank() }?.let { "$it?param=120y120" }
        if (avatarModel != null) {
            SubcomposeAsyncImage(
                model = avatarModel,
                contentDescription = user.nickname,
                contentScale = ContentScale.Crop,
                error = {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MelodiaColors.TextSecondary,
                        modifier = Modifier.size(fallbackIconSize)
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MelodiaColors.TextSecondary,
                modifier = Modifier.size(fallbackIconSize)
            )
        }
    }
}

@Composable
private fun ShareSentCapsule(
    user: MessageUser,
    onGoToChat: () -> Unit
) {
    val displayName = user.nickname.trim().ifEmpty { "对方" }
    val ambientShadow = if (MelodiaColors.IsDark) MelodiaColors.ShadowAmbient.copy(alpha = 0.45f) else MelodiaColors.ShadowAmbient
    val spotShadow = if (MelodiaColors.IsDark) MelodiaColors.ShadowSpot.copy(alpha = 0.45f) else MelodiaColors.ShadowSpot
    Row(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .shadow(
                elevation = 12.dp,
                shape = PillShape,
                ambientColor = ambientShadow,
                spotColor = spotShadow
            )
            .clip(PillShape)
            .background(MelodiaColors.PopupSurface)
            .border(1.dp, MelodiaColors.Outline, PillShape)
            // 吞掉胶囊本体触摸，防止点到背后被盖住的选项芯片
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MelodiaColors.Success),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = contentColorOn(MelodiaColors.Success),
                modifier = Modifier.size(14.dp)
            )
        }
        FriendAvatarCircle(
            user = user,
            size = 22.dp,
            fallbackIconSize = 13.dp
        )
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已发送给 ",
                color = MelodiaColors.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(
                text = displayName,
                color = MelodiaColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        Box(
            modifier = Modifier
                .pressable(
                    style = MelodiaPress.Pill,
                    shape = PillShape,
                    role = Role.Button,
                    onClick = onGoToChat
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "去聊天 ›",
                color = MelodiaColors.AccentText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FriendMoreItem(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .pressable(
                style = MelodiaPress.Pill,
                shape = RoundedCornerShape(10.dp),
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MelodiaColors.Fill)
                .border(1.dp, MelodiaColors.Outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = "更多好友",
                tint = MelodiaColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "更多",
            color = MelodiaColors.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FriendPlaceholderItem() {
    Column(
        modifier = Modifier
            .width(52.dp)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MelodiaColors.Fill)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MelodiaColors.Fill)
        )
    }
}
