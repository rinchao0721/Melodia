package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

private val LocalSettingsEmbedded = compositionLocalOf { false }

class SettingsPageScope {
    internal val blocks = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        blocks += content
    }
}

// 子页内容列；嵌入合并页时不自带滚动与边距，由 SettingsMergedPage 统一承载
@Composable
fun SettingsPageColumn(content: SettingsPageScope.() -> Unit) {
    val scope = SettingsPageScope().apply(content)
    if (LocalSettingsEmbedded.current) {
        Column(verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)) {
            scope.blocks.forEach { it() }
        }
    } else {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
        ) {
            scope.blocks.forEach { it() }
        }
    }
}

// 把多个子页内容纵向合并为一页
@Composable
fun SettingsMergedPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
    ) {
        CompositionLocalProvider(LocalSettingsEmbedded provides true) { content() }
    }
}
