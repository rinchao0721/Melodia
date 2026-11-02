package com.lin0721.linmusic.core.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlin.math.roundToInt

private const val MAX_SETTING_TEXT = 500

// 把滑块取值落到 min 起算的步长上
fun snapToStep(value: Float, min: Float, max: Float, step: Float): Float {
    if (step <= 0f) return value.coerceIn(min, max)
    return (min + ((value - min) / step).roundToInt() * step).coerceIn(min, max)
}

// 类型不符时回到默认值，读取与写入都经过这里
fun PluginSettingField.normalize(value: JsonElement?): JsonElement {
    val primitive = value as? JsonPrimitive ?: return default
    return when (type) {
        PluginSettingType.TOGGLE -> primitive.booleanOrNull?.let(::JsonPrimitive) ?: default
        PluginSettingType.TEXT -> if (primitive.isString) JsonPrimitive(primitive.content.take(MAX_SETTING_TEXT)) else default
        PluginSettingType.SELECT -> primitive.takeIf { it.isString && options.any { option -> option.value == it.content } } ?: default
        PluginSettingType.NUMBER -> primitive.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }
            ?.let { JsonPrimitive(snapToStep(it, min, max, step)) } ?: default
    }
}

// 插件设置页：字段来自清单，复用页面组件渲染
@Composable
fun PluginSettingsView(manifest: PluginManifest, host: PluginPageHost, ui: PluginPageUi, modifier: Modifier = Modifier) {
    val coroutine = rememberCoroutineScope()
    var values by remember(manifest.id) { mutableStateOf<JsonObject?>(null) }
    LaunchedEffect(manifest.id) { values = host.settings(manifest) }
    val current = values ?: return
    val save: (PluginSettingField, JsonElement) -> Unit = { field, value ->
        val normalized = field.normalize(value)
        values = JsonObject(current + (field.id to normalized))
        coroutine.launch { host.setSetting(manifest, field.id, normalized) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        manifest.contributes.settings.forEach { field ->
            val value = current[field.id]
            val rowModifier = Modifier.fillMaxWidth()
            when (field.type) {
                PluginSettingType.TOGGLE -> ui.Toggle(
                    field.title,
                    (value as? JsonPrimitive)?.booleanOrNull == true,
                    { save(field, JsonPrimitive(it)) },
                    rowModifier
                )
                PluginSettingType.TEXT -> ui.Input(
                    field.title,
                    (value as? JsonPrimitive)?.content.orEmpty(),
                    { save(field, JsonPrimitive(it)) },
                    {},
                    rowModifier
                )
                PluginSettingType.SELECT -> ui.Select(
                    field.title,
                    field.options,
                    (value as? JsonPrimitive)?.content.orEmpty(),
                    { save(field, JsonPrimitive(it)) },
                    rowModifier
                )
                PluginSettingType.NUMBER -> ui.Slider(
                    field.title,
                    (value as? JsonPrimitive)?.floatOrNull ?: field.min,
                    field.min,
                    field.max,
                    field.step,
                    { save(field, JsonPrimitive(it)) },
                    {},
                    rowModifier
                )
            }
        }
    }
}
