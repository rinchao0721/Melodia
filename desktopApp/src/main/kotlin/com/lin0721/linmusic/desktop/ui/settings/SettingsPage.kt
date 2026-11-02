package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.update.UpdateChecker
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.library.LibrarySearchInput
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.settings.ui.CommonSettingsViewModel
import com.lin0721.linmusic.feature.settings.ui.LxPluginSettingsViewModel
import com.lin0721.linmusic.core.plugin.PluginRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ContentMaxWidth = 944.dp
private val ContentHorizontalPadding = 32.dp
private val SpyOffset = 40.dp
private val BottomPadding = 24.dp

@Composable
fun SettingsPage(
    common: CommonSettingsViewModel,
    desktop: DesktopSettingsViewModel,
    lxPlugins: LxPluginSettingsViewModel,
    plugins: PluginRepository,
    updateChecker: UpdateChecker,
    localLibrary: LocalLibraryRepository,
    modifier: Modifier = Modifier,
    // SettingsCategory.name，从外部入口打开时定位到该分区
    focusCategory: String? = null,
    onFocusConsumed: () -> Unit = {}
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    val motion = LocalUiMotion.current
    val density = LocalDensity.current

    LaunchedEffect(desktop) { desktop.refresh() }
    LaunchedEffect(desktop) { desktop.messages.collect { navigator.showMessage(it) } }

    val entries = settingsEntries(common, desktop, lxPlugins, plugins, updateChecker, localLibrary)
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    val sections = SettingsCategory.entries.mapNotNull { category ->
        val matched = entries.filter { it.category == category && matchesSettingsQuery(query, it.searchText) }
        if (matched.isEmpty()) null else category to matched
    }
    val categories = SettingsCategory.entries.filter { category -> entries.any { it.category == category } }

    val scrollState = rememberScrollState()
    val sectionTops = remember { mutableStateMapOf<SettingsCategory, Int>() }
    val sectionHeights = remember { mutableStateMapOf<SettingsCategory, Int>() }
    var viewportHeight by remember { mutableStateOf(0) }

    // 查询变化后结果集已换，回到顶部
    LaunchedEffect(query) { scrollState.scrollTo(0) }

    val spyOffsetPx = with(density) { SpyOffset.roundToPx() }
    val currentSections by rememberUpdatedState(sections)
    val activeCategory by remember {
        derivedStateOf {
            val position = scrollState.value + spyOffsetPx
            currentSections.map { it.first }.lastOrNull { (sectionTops[it] ?: Int.MAX_VALUE) <= position }
                ?: currentSections.firstOrNull()?.first
        }
    }

    // 分区位置测量出来后才能滚动
    val focusTarget = focusCategory?.let { name -> SettingsCategory.entries.firstOrNull { it.name == name } }
    val focusTop = focusTarget?.let { sectionTops[it] }
    LaunchedEffect(focusCategory, focusTop) {
        if (focusCategory == null) return@LaunchedEffect
        if (focusTarget == null) {
            onFocusConsumed()
        } else if (focusTop != null) {
            query = ""
            scrollState.scrollTo(focusTop)
            onFocusConsumed()
        }
    }

    // 末尾分区较短时补足高度，保证点击对应标签后它能滚到顶部
    val lastSectionHeight = sections.lastOrNull()?.let { sectionHeights[it.first] } ?: 0
    val tailHeight = if (searching) BottomPadding else with(density) { (viewportHeight - lastSectionHeight).coerceAtLeast(0).toDp() } + BottomPadding

    Column(modifier.fillMaxSize()) {
        SettingsHeader(
            query = query,
            onQueryChange = { query = it },
            categories = categories,
            active = activeCategory,
            searching = searching,
            resultCount = sections.sumOf { it.second.size },
            onSelectCategory = { category ->
                sectionTops[category]?.let { top ->
                    scope.launch { if (motion.enabled) scrollState.animateScrollTo(top) else scrollState.scrollTo(top) }
                }
            }
        )
        HoverScrollbarBox(scrollState, Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().onSizeChanged { viewportHeight = it.height }.verticalScroll(scrollState)) {
                Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = ContentHorizontalPadding)) {
                    if (sections.isEmpty()) EmptySearchResult()
                    sections.forEach { (category, items) ->
                        SettingsSection(
                            category = category,
                            entries = items,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val top = coordinates.positionInParent().y.roundToInt()
                                if (sectionTops[category] != top) sectionTops[category] = top
                                if (sectionHeights[category] != coordinates.size.height) sectionHeights[category] = coordinates.size.height
                            }
                        )
                    }
                    Spacer(Modifier.height(tailHeight))
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    categories: List<SettingsCategory>,
    active: SettingsCategory?,
    searching: Boolean,
    resultCount: Int,
    onSelectCategory: (SettingsCategory) -> Unit
) {
    Column(
        Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
            .padding(horizontal = ContentHorizontalPadding)
            .padding(top = 24.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("设置", color = DesktopColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(24.dp))
            SettingsSearchField(query, onQueryChange, Modifier.widthIn(max = 420.dp).weight(1f))
        }
        // 搜索中改为显示结果数，保持头部高度不变
        Box(Modifier.padding(top = 12.dp).height(32.dp), contentAlignment = Alignment.CenterStart) {
            if (searching) {
                Text("找到 $resultCount 项设置", color = DesktopColors.TextGray, fontSize = 13.sp)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = category == active,
                            onClick = { onSelectCategory(category) },
                            label = { Text(category.label, fontSize = 13.sp) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DesktopColors.Surface,
                                labelColor = DesktopColors.TextPrimary,
                                selectedContainerColor = DesktopColors.TextPrimary,
                                selectedLabelColor = DesktopColors.Pane
                            ),
                            border = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.height(40.dp).clip(shape).background(DesktopColors.Surface)
            .border(3.dp, if (focused) DesktopColors.TextPrimary else Color.Transparent, shape)
    ) {
        LibrarySearchInput(
            query = query,
            startPadding = 14.dp,
            showClear = query.isNotEmpty(),
            onQueryChange = onQueryChange,
            onClear = { onQueryChange("") },
            onFocusChange = { focused = it },
            placeholder = "搜索设置项"
        )
    }
}

@Composable
private fun SettingsSection(category: SettingsCategory, entries: List<SettingsEntry>, modifier: Modifier = Modifier) {
    Column(modifier) {
        SettingsSectionTitle(category.label)
        entries.forEachIndexed { index, entry ->
            val group = entry.group
            if (group != null && group != entries.getOrNull(index - 1)?.group) SettingsGroupTitle(group)
            key(entry.key) { entry.content() }
        }
    }
}

@Composable
private fun EmptySearchResult() {
    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("没有找到相关设置", color = DesktopColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("换个关键词试试，或清除搜索查看全部设置", color = DesktopColors.TextGray, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
