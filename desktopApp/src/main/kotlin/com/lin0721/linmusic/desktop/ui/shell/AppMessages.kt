package com.lin0721.linmusic.desktop.ui.shell

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

// 汇总常驻 ViewModel、播放器、下载器、更新检查与应用壳自身的提示，统一交给 Snackbar；页面级 ViewModel 的提示由 DesktopFrameHost 转发
internal fun appMessages(deps: DesktopAppDependencies, state: DesktopAppState): Flow<String> = merge(
    deps.homeViewModel.toastEvent,
    deps.libraryViewModel.toastEvent,
    deps.searchViewModel.toastEvent,
    deps.playerViewModel.toastEvent,
    deps.newWorksViewModel.toastEvent,
    state.messages,
    deps.playbackController.messages,
    deps.downloader.messages,
    deps.updateChecker.messages
)
