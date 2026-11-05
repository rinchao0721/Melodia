package com.melodia.sample.hello

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.lin0721.linmusic.plugin.api.PluginApiException
import com.lin0721.linmusic.plugin.api.SpectrumApi
import com.lin0721.linmusic.plugin.api.SpectrumFrame

private const val BAR_GAP_RATIO = 0.3f

// 页面可见时才收集，离开后采集随之停止
@Composable
fun SpectrumPage(spectrum: SpectrumApi) {
    var frame by remember { mutableStateOf<SpectrumFrame?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(spectrum) {
        try {
            spectrum.frames().collect { frame = it }
        } catch (e: PluginApiException) {
            failure = e.message
        }
    }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val message = failure
            if (message != null) {
                Text(message)
            } else {
                val color = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bands = frame?.bands ?: return@Canvas
                    val slot = size.width / bands.size
                    bands.forEachIndexed { index, level ->
                        val height = level * size.height
                        drawRect(
                            color = color,
                            topLeft = Offset(index * slot, size.height - height),
                            size = Size(slot * (1 - BAR_GAP_RATIO), height)
                        )
                    }
                }
            }
        }
    }
}
