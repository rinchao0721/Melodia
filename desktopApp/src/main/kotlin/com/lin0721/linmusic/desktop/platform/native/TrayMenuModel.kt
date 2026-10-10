package com.lin0721.linmusic.desktop.platform.native

// 托盘菜单的平台无关模型。
//
// Linux 会把它发布成 com.canonical.dbusmenu，由托盘宿主用自己的 QMenu 渲染
// （外观/缩放/键盘导航都是原生样式）；Windows 仍由 Compose 绘制菜单，
// 模型不参与渲染，仅保持接口一致。
data class TrayMenuModel(
    val header: String?,
    val nodes: List<TrayMenuNode>,
)

sealed interface TrayMenuNode {
    data class Action(val label: String, val enabled: Boolean = true, val onSelect: () -> Unit) : TrayMenuNode

    data class Toggle(val label: String, val checked: Boolean, val onSelect: () -> Unit) : TrayMenuNode

    data object Separator : TrayMenuNode
}
