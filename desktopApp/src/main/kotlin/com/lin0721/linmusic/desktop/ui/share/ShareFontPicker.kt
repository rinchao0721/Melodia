package com.lin0721.linmusic.desktop.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.share.ui.ShareCardTypographyOptions
import com.lin0721.linmusic.core.share.ui.ShareFontOption
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FontMgr
import java.text.Collator
import java.util.Locale

private const val TAG = "ShareFontPicker"

// 预设字形在前，其后是电脑上已安装的字体；支持按名称搜索
@OptIn(ExperimentalTextApi::class)
@Composable
internal fun ShareFontPicker(current: ShareFontOption, onSelect: (ShareFontOption) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val systemFonts by produceState(emptyList<String>()) { value = withContext(Dispatchers.IO) { installedFontFamilies() } }
    val close = {
        open = false
        query = ""
    }

    Box {
        Row(
            Modifier.clip(CircleShape)
                .background(DesktopColors.Overlay.copy(alpha = 0.1f))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { open = true }
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                current.label,
                color = DesktopColors.TextPrimary,
                fontSize = 13.sp,
                fontFamily = current.family,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
        }
        DesktopMenu(expanded = open, onDismiss = close, width = 280.dp) {
            DialogTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索字体",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
            )
            val keyword = query.trim()
            val presets = ShareCardTypographyOptions.presetFonts.filter { keyword.isEmpty() || it.label.contains(keyword, ignoreCase = true) }
            val installed = systemFonts.filter { keyword.isEmpty() || it.contains(keyword, ignoreCase = true) }
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(presets, key = { "preset:${it.label}" }) { option ->
                    SimpleMenuItem(option.label, onClick = {
                        onSelect(option)
                        close()
                    }, selected = option.label == current.label)
                }
                items(installed, key = { "system:$it" }) { name ->
                    val family = remember(name) { FontFamily(SystemFont(name)) }
                    SimpleMenuItem(
                        name,
                        onClick = {
                            onSelect(ShareFontOption(name, family))
                            close()
                        },
                        selected = name == current.label,
                        leading = { Text("Aa", color = DesktopColors.TextGray, fontSize = 13.sp, fontFamily = family) }
                    )
                }
            }
            if (presets.isEmpty() && installed.isEmpty()) {
                Text("没有匹配的字体", color = DesktopColors.TextGray, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

// Windows 的竖排字体以 @ 开头，不适合横排展示
private fun installedFontFamilies(): List<String> = try {
    val manager = FontMgr.default
    val collator = Collator.getInstance(Locale.CHINA)
    (0 until manager.familiesCount)
        .mapNotNull { manager.getFamilyName(it).takeIf { name -> name.isNotBlank() && !name.startsWith("@") } }
        .distinct()
        .sortedWith(collator)
} catch (e: Exception) {
    AppLogger.w(TAG, "读取系统字体失败", e)
    emptyList()
}
