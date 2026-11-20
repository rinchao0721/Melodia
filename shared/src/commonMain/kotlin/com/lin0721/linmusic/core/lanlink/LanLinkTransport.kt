package com.lin0721.linmusic.core.lanlink

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LanLinkIncomingMessage(
    val peer: ConnectedPeerInfo,
    val message: LanLinkMessage
)

private val DefaultSessionSequenceFlow: StateFlow<Long> = MutableStateFlow(0L)

// 局域网联动通用传输抽象：供遥控协调器解耦 Desktop Host 与 Android Client 实现
interface LanLinkTransport {
    val enabled: StateFlow<Boolean>
    val connectedDevice: StateFlow<ConnectedPeerInfo?>
    val sessionSequence: StateFlow<Long>
        get() = DefaultSessionSequenceFlow
    val localDeviceId: String
    val localDeviceName: String
    val isHostDevice: Boolean
    val pairedDevices: StateFlow<List<PairedLanDevice>>
    val incomingMessages: Flow<LanLinkIncomingMessage>

    suspend fun sendMessage(message: LanLinkMessage): Boolean
    fun emitUserMessage(message: String)
    suspend fun requestConnectDevice(deviceId: String): Boolean = false
}
