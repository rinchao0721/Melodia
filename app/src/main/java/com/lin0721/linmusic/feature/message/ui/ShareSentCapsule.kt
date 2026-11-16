package com.lin0721.linmusic.feature.message.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.contentColorOn
import com.lin0721.linmusic.feature.message.domain.MessageUser

private val PillShape = RoundedCornerShape(PillRadius)

@Composable
fun FriendAvatarCircle(
    user: MessageUser,
    size: Dp,
    fallbackIconSize: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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

// 底部操作成功胶囊：供分享卡片发送、一起听邀请发送与加入房间共用
@Composable
fun ShareSentCapsule(
    prefixText: String,
    highlightText: String,
    suffixText: String = "",
    user: MessageUser? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ambientShadow = if (MelodiaColors.IsDark) MelodiaColors.ShadowAmbient.copy(alpha = 0.45f) else MelodiaColors.ShadowAmbient
    val spotShadow = if (MelodiaColors.IsDark) MelodiaColors.ShadowSpot.copy(alpha = 0.45f) else MelodiaColors.ShadowSpot
    Row(
        modifier = modifier
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
            // 吞掉胶囊本体触摸，防止点到背后被盖住的控件
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 10.dp, end = if (actionText != null && onAction != null) 10.dp else 14.dp, top = 8.dp, bottom = 8.dp),
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
        if (user != null) {
            FriendAvatarCircle(
                user = user,
                size = 22.dp,
                fallbackIconSize = 13.dp
            )
        }
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (prefixText.isNotEmpty()) {
                Text(
                    text = prefixText,
                    color = MelodiaColors.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            Text(
                text = highlightText,
                color = MelodiaColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (suffixText.isNotEmpty()) {
                Text(
                    text = suffixText,
                    color = MelodiaColors.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
        if (actionText != null && onAction != null) {
            Box(
                modifier = Modifier
                    .pressable(
                        style = MelodiaPress.Pill,
                        shape = PillShape,
                        role = Role.Button,
                        onClick = onAction
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionText,
                    color = MelodiaColors.AccentText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
