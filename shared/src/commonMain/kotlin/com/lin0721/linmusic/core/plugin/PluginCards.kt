package com.lin0721.linmusic.core.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.preferences.PlayerCardEntries
import com.lin0721.linmusic.core.preferences.PlayerCardEntry
import com.lin0721.linmusic.core.preferences.PluginCardRef
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaPalette
import com.lin0721.linmusic.core.ui.theme.styledCardRadius
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

// 信息卡片列表的完整顺序：内置卡片与已启用插件的卡片
class PlayerCardSource(
    private val settings: SettingsPreferences,
    val pages: StateFlow<List<ActivePluginPage>>
) {
    val entries: Flow<List<PlayerCardEntry>> = combine(settings.fullPlayerCardRaw, pages) { raw, active ->
        PlayerCardEntries.merge(raw, active.filter { it.isCard }.map { PluginCardRef(it.cardKey, it.page.title) })
    }

    suspend fun save(entries: List<PlayerCardEntry>) = settings.saveFullPlayerCardEntries(entries)
}

val ActivePluginPage.isCard: Boolean get() = page.placement == PluginPagePlacement.NOW_PLAYING_CARD

val ActivePluginPage.cardKey: String get() = PlayerCardEntries.pluginKey(manifest.id, page.id)

// 插件卡片渲染需要的依赖，由各端在根部提供
class PluginCardContext(
    val source: PlayerCardSource,
    val host: PluginPageHost,
    val ui: PluginPageUi
)

val LocalPluginCards = staticCompositionLocalOf<PluginCardContext?> { null }

// 插件卡片：标题加页面内容，页面描述无效时不显示
@Composable
fun PluginCardView(key: String, modifier: Modifier = Modifier) {
    val context = LocalPluginCards.current ?: return
    val pages by context.source.pages.collectAsState()
    val active = pages.firstOrNull { it.isCard && it.cardKey == key }?.takeIf { it.root != null } ?: return
    val palette = LocalMelodiaPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(styledCardRadius(16.dp)))
            .background(palette.cardSurface)
            .padding(16.dp)
    ) {
        BasicText(
            active.page.title,
            modifier = Modifier.padding(bottom = 12.dp),
            style = TextStyle(color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
        PluginPageView(active, context.host, context.ui, Modifier.fillMaxWidth())
    }
}
