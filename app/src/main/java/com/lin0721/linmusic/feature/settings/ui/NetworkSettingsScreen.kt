package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.PlaceholderTextField
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@Composable
fun NetworkSettingsView(viewModel: SettingsViewModel) {
    val useRealIp by viewModel.useRealIp.collectAsStateWithLifecycle()
    val realIpValue by viewModel.realIpValue.collectAsStateWithLifecycle()
    val wifiOnlyPlay by viewModel.wifiOnlyPlay.collectAsStateWithLifecycle()
    val mobileAlert by viewModel.mobileAlert.collectAsStateWithLifecycle()
    val useProxy by viewModel.useProxy.collectAsStateWithLifecycle()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        item {
            SettingsGroupCard("网络连接") {
                SettingsSwitchRow(
                    title = "仅 Wi-Fi 网络下联网播放",
                    subtitle = "开启后，在移动网络环境将无法播放在线曲目",
                    checked = wifiOnlyPlay,
                    onCheckedChange = { viewModel.updateWifiOnlyPlay(it) }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                SettingsSwitchRow(
                    title = "流量播放警告提示",
                    subtitle = "从 Wi-Fi 切换为移动数据时弹出提醒",
                    checked = mobileAlert,
                    onCheckedChange = { viewModel.updateMobileAlert(it) }
                )
            }
        }

        item {
            SettingsGroupCard("网络代理") {
                SettingsSwitchRow(
                    title = "使用国内 IP 地址",
                    subtitle = "在海外IP可能会受到限制，可开启此处尝试解决",
                    checked = useRealIp,
                    onCheckedChange = { viewModel.updateUseRealIp(it) }
                )

                if (useRealIp) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.md)) {
                            Text("国内 IP 地址", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("可在此处输入国内 IP，不填写则为随机", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }

                        PlaceholderTextField(
                            value = realIpValue,
                            onValueChange = { viewModel.updateRealIpValue(it) },
                            placeholder = "IP 127.0.0.1",
                            modifier = Modifier.width(150.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                SettingsSwitchRow(
                    title = "使用代理服务器",
                    subtitle = "配置自定义网络代理进行数据解析",
                    checked = useProxy,
                    onCheckedChange = { viewModel.updateUseProxy(it) }
                )
            }
        }
    }
}
