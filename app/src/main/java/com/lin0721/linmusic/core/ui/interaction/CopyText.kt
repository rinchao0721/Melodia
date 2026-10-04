package com.lin0721.linmusic.core.ui.interaction

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import com.lin0721.linmusic.core.ui.components.ToastManager

fun copyText(context: Context, text: String, clipboardLabel: String) {
    val normalizedText = text.trim()
    if (normalizedText.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, normalizedText))
    ToastManager.showToast("已经复制「$normalizedText」")
}

// 短按期间不消费指针事件，让外层 clickable 正常播放；只有确认长按后才消费事件并复制。
// 移动后 awaitLongPressOrCancellation 会取消识别，让出手势给列表滚动/顶栏拖动。
@Composable
fun Modifier.copyTextOnLongPress(
    text: String,
    clipboardLabel: String
): Modifier {
    if (text.isBlank()) return this
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentText by rememberUpdatedState(text)

    fun copy() = copyText(context, currentText, clipboardLabel)

    return this
        .pointerInput(text, clipboardLabel) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                awaitLongPressOrCancellation(down.id)?.let { longPress ->
                    longPress.consume()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    copy()

                    // 长按已经处理后持续消费到抬手，取消外层 clickable，避免复制后又播放歌曲。
                    do {
                        val event = awaitPointerEvent()
                        event.changes
                            .filter { it.id == down.id }
                            .forEach { it.consume() }
                    } while (event.changes.any { it.id == down.id && it.pressed })
                }
            }
        }
        .semantics {
            onLongClick(label = "复制$clipboardLabel") {
                copy()
                true
            }
        }
}

@Composable
fun Modifier.copySongTitleOnLongPress(title: String): Modifier =
    copyTextOnLongPress(title, clipboardLabel = "歌名")
