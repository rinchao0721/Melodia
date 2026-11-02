package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import com.lin0721.linmusic.desktop.ui.components.HoldTextInputFocus
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import java.awt.event.KeyEvent as AwtKeyEvent

// 录制期间暂停全局热键，避免按下的组合被已注册的热键拦截
@Composable
internal fun HotkeyEditor(
    hotkeyMap: Map<HotkeyAction, HotkeyCombo?>,
    failed: Set<HotkeyAction>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSave: (Map<HotkeyAction, HotkeyCombo?>) -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    var recording by remember { mutableStateOf<HotkeyAction?>(null) }

    // 离开页面时若仍在录制，恢复热键
    DisposableEffect(Unit) {
        onDispose { if (recording != null) onResume() }
    }

    val stopRecording = {
        if (recording != null) {
            recording = null
            onResume()
        }
    }

    HotkeyAction.entries.forEach { action ->
        val combo = hotkeyMap[action]
        val isFailed = combo != null && action in failed
        SettingRow(
            title = action.label,
            subtitle = if (isFailed) "已被其他程序占用，未生效" else null,
            subtitleColor = DesktopColors.Accent
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HotkeyRecorder(
                    combo = combo,
                    isRecording = recording == action,
                    onStart = {
                        if (recording == null) onPause()
                        recording = action
                    },
                    onCancel = stopRecording,
                    onRecorded = { newCombo ->
                        val updated = hotkeyMap.toMutableMap()
                        // 与其他动作重复时，从原动作上移除
                        val conflict = updated.entries.firstOrNull { it.key != action && it.value == newCombo }?.key
                        if (conflict != null) {
                            updated[conflict] = null
                            navigator.showMessage("${newCombo.label} 已从「${conflict.label}」移除")
                        }
                        updated[action] = newCombo
                        onSave(updated)
                        stopRecording()
                    },
                    onInvalid = navigator.showMessage
                )
                IconButton(
                    onClick = { onSave(hotkeyMap + (action to null)) },
                    enabled = combo != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        "清除",
                        tint = if (combo != null) DesktopColors.TextGray else DesktopColors.SurfaceLight,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
        SettingsActionButton("恢复默认", onClick = { onSave(HotkeyCombo.defaults) })
    }
}

@Composable
private fun HotkeyRecorder(
    combo: HotkeyCombo?,
    isRecording: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRecorded: (HotkeyCombo) -> Unit,
    onInvalid: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    HoldTextInputFocus(isRecording)
    LaunchedEffect(isRecording) {
        if (isRecording) focusRequester.requestFocus()
    }
    Box(
        Modifier.widthIn(min = 160.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(DesktopColors.Pane)
            .border(1.dp, if (isRecording) DesktopColors.Accent else DesktopColors.SurfaceLight, RoundedCornerShape(4.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { if (!it.isFocused && isRecording) onCancel() }
            .onPreviewKeyEvent { event ->
                if (!isRecording || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val awtCode = (event.nativeKeyEvent as? AwtKeyEvent)?.keyCode ?: return@onPreviewKeyEvent true
                when (awtCode) {
                    AwtKeyEvent.VK_ESCAPE -> onCancel()
                    AwtKeyEvent.VK_CONTROL, AwtKeyEvent.VK_ALT, AwtKeyEvent.VK_SHIFT, AwtKeyEvent.VK_WINDOWS,
                    AwtKeyEvent.VK_META, AwtKeyEvent.VK_ALT_GRAPH -> Unit
                    else -> {
                        val vk = HotkeyCombo.keyCodeFromAwt(awtCode)
                        val modifiers = HotkeyCombo.modifiersOf(event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
                        when {
                            !HotkeyCombo.isSupportedKey(vk) -> onInvalid("不支持该按键")
                            !HotkeyCombo.isValid(modifiers, vk) -> onInvalid("快捷键需要包含 Ctrl 或 Alt")
                            else -> onRecorded(HotkeyCombo(modifiers, vk))
                        }
                    }
                }
                true
            }
            .focusable()
            .clickable(onClick = onStart)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            when {
                isRecording -> "请按下组合键…"
                combo != null -> combo.label
                else -> "未设置"
            },
            color = when {
                isRecording -> DesktopColors.Accent
                combo != null -> DesktopColors.TextPrimary
                else -> DesktopColors.TextGray
            },
            fontSize = 14.sp
        )
    }
}
