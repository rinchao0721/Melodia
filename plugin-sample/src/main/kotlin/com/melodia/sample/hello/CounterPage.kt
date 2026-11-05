package com.melodia.sample.hello

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// 页面在独立的窗口层渲染，不继承应用主题
@Composable
fun CounterPage() {
    var count by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface {
            Column {
                Text("已点击 $count 次")
                Button(onClick = { count++ }) { Text("点我") }
            }
        }
    }
}
