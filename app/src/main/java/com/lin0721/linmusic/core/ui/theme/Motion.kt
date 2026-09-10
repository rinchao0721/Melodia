package com.lin0721.linmusic.core.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

// 按压反馈时长：按下跟手，抬起略缓收尾，全程不带回弹
const val PressDownDurationMs = 90
const val PressUpDurationMs = 130

val PressDownSpec = tween<Float>(PressDownDurationMs, easing = LinearEasing)
val PressUpSpec = tween<Float>(PressUpDurationMs, easing = FastOutSlowInEasing)

// 数据入场：图表从零生长、数值从零递增，一次走完不循环
const val DataEnterDurationMs = 700

// 同组内各项错开起始所占的进度比例，余下比例是单项自身的生长时长
const val DataEnterStaggerFraction = 0.4f

val DataEnterSpec = tween<Float>(DataEnterDurationMs, easing = FastOutSlowInEasing)

// 弹窗内容态横向切换（如登录方式选择 二维码/Cookie 子内容）
const val ContentSwitchDurationMs = 300

// 全屏页滑入滑出（如网页登录页覆盖/退出）
const val ScreenSlideDurationMs = 320

// 封面左右滑动切歌：确认切换滑出/未达阈值回弹共用同一档弹簧手感，与全屏播放器展开收起一致
val SwipeCoverSpringSpec: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

// scale 为按下时的缩放比；highlightAlpha 为垫在内容之下的纯黑层透明度（1f 即底色完全熄灭），
// 该层只在长按超过系统阈值后才出现，轻点不触发
@Immutable
data class PressStyle(val scale: Float, val highlightAlpha: Float)

// 按部件类型划分的按压档位，全项目只允许从这里取值
object MelodiaPress {
    // 封面卡片
    val Card = PressStyle(scale = 0.96f, highlightAlpha = 0f)

    // 药丸/Chip
    val Pill = PressStyle(scale = 0.94f, highlightAlpha = 0f)

    // 图标按钮
    val Icon = PressStyle(scale = 0.88f, highlightAlpha = 0f)

    // 主行动按钮
    val Action = PressStyle(scale = 0.97f, highlightAlpha = 0f)

    // 列表行/设置项：行宽满屏，缩放幅度必须压到几乎看不出两侧留白；长按再熄灭底色
    val Row = PressStyle(scale = 0.99f, highlightAlpha = 1f)

    // 底栏 tab
    val Tab = PressStyle(scale = 0.90f, highlightAlpha = 0f)

    // 播放/暂停/上下曲
    val Transport = PressStyle(scale = 0.90f, highlightAlpha = 0f)

    // 手势容器、点击拦截层
    val None = PressStyle(scale = 1f, highlightAlpha = 0f)
}
