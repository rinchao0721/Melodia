package com.lin0721.linmusic.feature.search.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius

private const val BAR_ANIM_DURATION = 250

// 进入搜索后标题行收起，搜索框亮底转暗底并出现取消
@Composable
internal fun SearchTopBar(
    mode: SearchMode,
    query: String,
    placeholder: String,
    avatarUrl: String?,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onActivate: () -> Unit,
    onFieldFocused: () -> Unit,
    onCancel: () -> Unit,
    onOpenSidebar: () -> Unit,
    onAvatarClickLoggedOut: () -> Unit,
    onOpenRecognition: () -> Unit
) {
    val isDiscovery = mode == SearchMode.Discovery
    val barTween = tween<Float>(BAR_ANIM_DURATION, easing = FastOutSlowInEasing)

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = isDiscovery,
            enter = expandVertically(tween(BAR_ANIM_DURATION, easing = FastOutSlowInEasing)) + fadeIn(barTween),
            exit = shrinkVertically(tween(BAR_ANIM_DURATION, easing = FastOutSlowInEasing)) + fadeOut(barTween)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = "$avatarUrl?param=200y200",
                        contentDescription = "打开侧边栏",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .pressable(MelodiaPress.Icon) { onOpenSidebar() }
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        Icons.Rounded.AccountCircle,
                        contentDescription = "打开侧边栏",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .pressable(MelodiaPress.Icon) { onAvatarClickLoggedOut() }
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "搜索",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                MelodiaIconButton(onClick = onOpenRecognition) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = "听歌识曲",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MelodiaSpacing.md, end = if (isDiscovery) MelodiaSpacing.md else MelodiaSpacing.xs)
                .padding(vertical = MelodiaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                isDiscovery = isDiscovery,
                query = query,
                placeholder = placeholder,
                focusRequester = focusRequester,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onActivate = onActivate,
                onFieldFocused = onFieldFocused,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = !isDiscovery,
                enter = expandHorizontally(tween(BAR_ANIM_DURATION, easing = FastOutSlowInEasing)) + fadeIn(barTween),
                exit = shrinkHorizontally(tween(BAR_ANIM_DURATION, easing = FastOutSlowInEasing)) + fadeOut(barTween)
            ) {
                MelodiaTextButton(onClick = onCancel) {
                    Text(text = "取消", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    isDiscovery: Boolean,
    query: String,
    placeholder: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onActivate: () -> Unit,
    onFieldFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorSpec = tween<Color>(BAR_ANIM_DURATION, easing = FastOutSlowInEasing)
    val containerColor by animateColorAsState(
        targetValue = if (isDiscovery) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
        animationSpec = colorSpec,
        label = "search_field_container"
    )
    val hintColor by animateColorAsState(
        targetValue = if (isDiscovery) MaterialTheme.colorScheme.background.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = colorSpec,
        label = "search_field_hint"
    )

    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(PillRadius))
            .background(containerColor)
            .then(
                if (isDiscovery) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onActivate
                    )
                } else {
                    Modifier
                }
            )
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = hintColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))

        if (isDiscovery) {
            Text(
                text = placeholder,
                color = hintColor,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = hintColor,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFieldFocused() }
            )
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150))
            ) {
                MelodiaIconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "清空搜索框",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
