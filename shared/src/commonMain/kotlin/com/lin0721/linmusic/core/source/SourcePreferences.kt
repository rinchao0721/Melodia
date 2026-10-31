package com.lin0721.linmusic.core.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 音源换源偏好配置
class SourcePreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_FALLBACK_ENABLED = booleanPreferencesKey("source_fallback_enabled")
        private val KEY_UNM_SERVER_URL = stringPreferencesKey("source_unm_server_url")
        private val KEY_UNM_REMOTE_FALLBACK_ENABLED = booleanPreferencesKey("source_unm_remote_fallback_enabled")
        private val KEY_UNM_AUTO_MATCH = booleanPreferencesKey("source_unm_auto_match")

        // LX 自定义插件偏好
        private val KEY_LX_PLUGIN_ENABLED = booleanPreferencesKey("source_lx_plugin_enabled")
        private val KEY_LX_SCRIPT_CONTENT = stringPreferencesKey("source_lx_script_content")
        private val KEY_LX_PLUGIN_NAME = stringPreferencesKey("source_lx_plugin_name")
        private val KEY_LX_PLUGIN_VERSION = stringPreferencesKey("source_lx_plugin_version")
        private val KEY_LX_PLUGIN_AUTHOR = stringPreferencesKey("source_lx_plugin_author")
        private val KEY_LX_PLUGIN_DESC = stringPreferencesKey("source_lx_plugin_desc")
        private val KEY_LX_PLUGIN_SOURCES = stringPreferencesKey("source_lx_plugin_sources")
        private val KEY_LX_PLUGINS_JSON = stringPreferencesKey("source_lx_plugins_json")
        private val KEY_LX_PLUGIN_LOOSE_TIMEOUT = booleanPreferencesKey("source_lx_plugin_loose_timeout")

        // 社区源优先偏好
        private val KEY_COMMUNITY_SOURCE_PRIORITY = booleanPreferencesKey("source_community_source_priority")

        // 多平台聚合搜索偏好
        private val KEY_SEARCH_AGGREGATION_ENABLED = booleanPreferencesKey("source_search_aggregation_enabled")
    }

    // 聚合搜索开关
    val searchAggregationEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SEARCH_AGGREGATION_ENABLED] ?: false
    }

    suspend fun saveSearchAggregationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SEARCH_AGGREGATION_ENABLED] = enabled }
    }

    // 换源总开关
    val fallbackEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FALLBACK_ENABLED] ?: false
    }

    suspend fun saveFallbackEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_FALLBACK_ENABLED] = enabled }
    }

    // UNM 服务端 Base URL（默认不填写）
    val unmServerUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_UNM_SERVER_URL]?.trim() ?: ""
    }

    suspend fun saveUnmServerUrl(url: String) {
        dataStore.edit { prefs ->
            val trimmed = url.trim().trimEnd('/')
            if (trimmed.isBlank()) {
                prefs.remove(KEY_UNM_SERVER_URL)
            } else {
                prefs[KEY_UNM_SERVER_URL] = trimmed
            }
        }
    }

    suspend fun resetUnmServerUrl() {
        dataStore.edit { prefs -> prefs.remove(KEY_UNM_SERVER_URL) }
    }

    // 是否在本地音源未命中时启用远程 UNM 服务器兜底（默认不启用）
    val unmRemoteFallbackEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_UNM_REMOTE_FALLBACK_ENABLED] ?: false
    }

    suspend fun saveUnmRemoteFallbackEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_UNM_REMOTE_FALLBACK_ENABLED] = enabled }
    }

    // 是否开启自动选择音源（不传 source，由服务端自选轮询）
    val unmAutoMatch: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_UNM_AUTO_MATCH] ?: true
    }

    suspend fun saveUnmAutoMatch(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_UNM_AUTO_MATCH] = enabled }
    }


    // 社区源优先开关
    val communityPriority: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_COMMUNITY_SOURCE_PRIORITY] ?: false
    }

    suspend fun saveCommunityPriority(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_COMMUNITY_SOURCE_PRIORITY] = enabled }
    }

    // ─── LX 插件相关偏好 ───

    val lxPluginEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LX_PLUGIN_ENABLED] ?: false
    }

    suspend fun saveLxPluginEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_LX_PLUGIN_ENABLED] = enabled }
    }

    // 宽松解析：把单次解析上限从 12s 放宽到 30s，换取慢速社区源（kw/kg/tx）不再被截断
    val lxPluginLooseTimeout: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LX_PLUGIN_LOOSE_TIMEOUT] ?: false
    }

    suspend fun saveLxPluginLooseTimeout(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_LX_PLUGIN_LOOSE_TIMEOUT] = enabled }
    }

    val lxScriptContent: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LX_SCRIPT_CONTENT] ?: ""
    }

    val lxPluginName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LX_PLUGIN_NAME] ?: ""
    }

    val lxPluginVersion: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LX_PLUGIN_VERSION] ?: ""
    }

    val lxPluginAuthor: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LX_PLUGIN_AUTHOR] ?: ""
    }

    val lxPluginDesc: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LX_PLUGIN_DESC] ?: ""
    }

    val lxPluginSources: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_LX_PLUGIN_SOURCES]
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    // 多插件持久化列表（平滑兼容旧版单插件配置）
    val lxPlugins: Flow<List<LxPluginItem>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_LX_PLUGINS_JSON]
        if (!raw.isNullOrBlank()) {
            runCatching { Json.decodeFromString<List<LxPluginItem>>(raw) }.getOrDefault(emptyList())
        } else {
            val oldScript = prefs[KEY_LX_SCRIPT_CONTENT] ?: ""
            if (oldScript.isNotBlank()) {
                val oldSources = prefs[KEY_LX_PLUGIN_SOURCES]?.let {
                    runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull()
                } ?: emptyList()
                listOf(
                    LxPluginItem(
                        id = "legacy_default",
                        name = prefs[KEY_LX_PLUGIN_NAME] ?: "未命名插件",
                        version = prefs[KEY_LX_PLUGIN_VERSION] ?: "1.0.0",
                        author = prefs[KEY_LX_PLUGIN_AUTHOR] ?: "未知",
                        description = prefs[KEY_LX_PLUGIN_DESC] ?: "",
                        sources = oldSources,
                        rawScript = oldScript,
                        isEnabled = prefs[KEY_LX_PLUGIN_ENABLED] ?: true
                    )
                )
            } else {
                emptyList()
            }
        }
    }

    suspend fun saveLxPlugins(plugins: List<LxPluginItem>) {
        dataStore.edit { prefs ->
            prefs[KEY_LX_PLUGINS_JSON] = Json.encodeToString(plugins)
        }
    }

    suspend fun addOrUpdateLxPlugin(item: LxPluginItem, overwriteId: String? = null) {
        val current = lxPlugins.first().toMutableList()
        if (overwriteId != null) {
            val index = current.indexOfFirst { it.id == overwriteId }
            if (index >= 0) {
                current[index] = item.copy(id = overwriteId)
            } else {
                current.add(item)
            }
        } else {
            val existingIndex = current.indexOfFirst { it.id == item.id }
            if (existingIndex >= 0) {
                current[existingIndex] = item
            } else {
                current.add(item)
            }
        }
        saveLxPlugins(current)
    }

    suspend fun removeLxPlugin(id: String) {
        val current = lxPlugins.first().filter { it.id != id }
        saveLxPlugins(current)
    }

    suspend fun toggleLxPlugin(id: String) {
        val current = lxPlugins.first().map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        saveLxPlugins(current)
    }

    suspend fun moveLxPluginUp(id: String) {
        val current = lxPlugins.first().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index > 0) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
            saveLxPlugins(current)
        }
    }

    suspend fun moveLxPluginDown(id: String) {
        val current = lxPlugins.first().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index in 0 until current.size - 1) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            saveLxPlugins(current)
        }
    }

    suspend fun saveLxPlugin(
        name: String,
        version: String,
        author: String,
        desc: String,
        sources: List<String>,
        script: String
    ) {
        val item = LxPluginItem(
            id = "default_${System.currentTimeMillis()}",
            name = name,
            version = version,
            author = author,
            description = desc,
            sources = sources,
            rawScript = script,
            isEnabled = true
        )
        addOrUpdateLxPlugin(item)
        dataStore.edit { prefs ->
            prefs[KEY_LX_PLUGIN_NAME] = name
            prefs[KEY_LX_PLUGIN_VERSION] = version
            prefs[KEY_LX_PLUGIN_AUTHOR] = author
            prefs[KEY_LX_PLUGIN_DESC] = desc
            prefs[KEY_LX_PLUGIN_SOURCES] = Json.encodeToString(sources)
            prefs[KEY_LX_SCRIPT_CONTENT] = script
            prefs[KEY_LX_PLUGIN_ENABLED] = true
        }
    }

    suspend fun clearLxPlugin() {
        saveLxPlugins(emptyList())
        dataStore.edit { prefs ->
            prefs.remove(KEY_LX_PLUGIN_NAME)
            prefs.remove(KEY_LX_PLUGIN_VERSION)
            prefs.remove(KEY_LX_PLUGIN_AUTHOR)
            prefs.remove(KEY_LX_PLUGIN_DESC)
            prefs.remove(KEY_LX_PLUGIN_SOURCES)
            prefs.remove(KEY_LX_SCRIPT_CONTENT)
            prefs[KEY_LX_PLUGIN_ENABLED] = false
        }
    }
}
