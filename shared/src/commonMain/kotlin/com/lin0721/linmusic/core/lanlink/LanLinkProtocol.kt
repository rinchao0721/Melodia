package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalSnapshot
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

object LanLinkConstants {
    const val UDP_DISCOVERY_PORT = 45821
    const val PROTOCOL_VERSION = 1
    const val MAX_FRAME_BYTES = 4 * 1024 * 1024
    const val PAIRING_CODE_TTL_MS = 2 * 60 * 1000L
    const val PAIRING_MAX_ATTEMPTS = 5
    const val HEARTBEAT_INTERVAL_MS = 15_000L
    const val HEARTBEAT_TIMEOUT_MS = 45_000L
    const val SYNC_DEBOUNCE_MS = 1_000L
    const val DISCOVERY_LISTEN_MS = 2_200L
}

@OptIn(ExperimentalSerializationApi::class)
val LanLinkJson: Json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

@Serializable
data class LanDiscoveryProbe(
    val protocolVersion: Int = LanLinkConstants.PROTOCOL_VERSION,
    val clientDeviceId: String
)

@Serializable
data class LanDiscoveryResponse(
    val deviceId: String,
    val deviceName: String,
    val tcpPort: Int,
    val protocolVersion: Int = LanLinkConstants.PROTOCOL_VERSION
)

data class DiscoveredLanDevice(
    val deviceId: String,
    val deviceName: String,
    val hostAddress: String,
    val tcpPort: Int,
    val protocolVersion: Int,
    val lastSeenMs: Long
)

@Serializable
data class PairedLanDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceKeyBase64: String,
    val pairedAtMs: Long
)

data class ConnectedPeerInfo(
    val deviceId: String,
    val deviceName: String
)

enum class LanLinkConnectionState {
    DISABLED,
    IDLE,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    UNPAIRED
}

enum class LanLinkSyncState {
    IDLE,
    SYNCED,
    ACCOUNT_MISMATCH,
    NOT_LOGGED_IN
}

// 局域网联动 TCP 帧负载协议；预留 sealed 接口与 type 字段供二期遥控、三期接力扩展
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed interface LanLinkMessage {

    @Serializable
    @SerialName("pair_hello")
    data class PairHello(
        val clientDeviceId: String,
        val clientDeviceName: String,
        val clientNonceBase64: String
    ) : LanLinkMessage

    @Serializable
    @SerialName("pair_challenge")
    data class PairChallenge(
        val serverDeviceId: String,
        val serverDeviceName: String,
        val serverNonceBase64: String,
        val saltBase64: String
    ) : LanLinkMessage

    @Serializable
    @SerialName("pair_verify")
    data class PairVerify(
        val clientProofBase64: String
    ) : LanLinkMessage

    @Serializable
    @SerialName("pair_grant")
    data class PairGrant(
        val success: Boolean,
        val serverProofBase64: String = "",
        val encryptedDeviceKeyBase64: String = "",
        val error: String = ""
    ) : LanLinkMessage

    @Serializable
    @SerialName("auth_hello")
    data class AuthHello(
        val clientDeviceId: String,
        val clientDeviceName: String,
        val clientNonceBase64: String
    ) : LanLinkMessage

    @Serializable
    @SerialName("auth_challenge")
    data class AuthChallenge(
        val serverDeviceId: String,
        val serverDeviceName: String,
        val serverNonceBase64: String,
        val serverProofBase64: String
    ) : LanLinkMessage

    @Serializable
    @SerialName("auth_verify")
    data class AuthVerify(
        val clientProofBase64: String
    ) : LanLinkMessage

    @Serializable
    @SerialName("auth_result")
    data class AuthResult(
        val success: Boolean,
        val error: String = ""
    ) : LanLinkMessage

    @Serializable
    @SerialName("account_exchange")
    data class AccountExchange(
        val uid: Long?
    ) : LanLinkMessage

    @Serializable
    @SerialName("sync_snapshot")
    data class SyncSnapshot(
        val snapshot: PlaylistExternalSnapshot,
        val sentAtMs: Long
    ) : LanLinkMessage

    @Serializable
    @SerialName("ping")
    data class Ping(
        val timestampMs: Long
    ) : LanLinkMessage

    @Serializable
    @SerialName("pong")
    data class Pong(
        val timestampMs: Long
    ) : LanLinkMessage

    @Serializable
    @SerialName("unpaired_notice")
    data class UnpairedNotice(
        val reason: String = ""
    ) : LanLinkMessage

    @Serializable
    @SerialName("error")
    data class ErrorMessage(
        val code: String,
        val message: String
    ) : LanLinkMessage
}
