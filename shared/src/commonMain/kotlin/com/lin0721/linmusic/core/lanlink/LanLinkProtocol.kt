package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.QueueItem
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
    const val REMOTE_STATE_THROTTLE_MS = 1_000L
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

// 局域网联动播放模式
@Serializable
enum class LanLinkPlaybackMode(val key: String) {
    @SerialName("single_device")
    SINGLE_DEVICE("single_device"),

    @SerialName("independent")
    INDEPENDENT("independent");

    companion object {
        fun fromKey(key: String?): LanLinkPlaybackMode =
            entries.firstOrNull { it.key == key } ?: SINGLE_DEVICE
    }
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

    @Serializable
    @SerialName("bye")
    data class Bye(
        val reason: String = ""
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_playback_hello")
    data class RemotePlaybackHello(
        val deviceId: String,
        val deviceName: String,
        val isPlaying: Boolean,
        val playWhenReady: Boolean,
        val hasTrack: Boolean,
        val lastActiveAtMs: Long,
        val isHost: Boolean,
        val playbackMode: LanLinkPlaybackMode = LanLinkPlaybackMode.SINGLE_DEVICE,
        val playbackModeUpdatedAtMs: Long = 0L
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_playback_mode_sync")
    data class RemotePlaybackModeSync(
        val mode: LanLinkPlaybackMode,
        val updatedAtMs: Long
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_active_device")
    data class RemoteActiveDevice(
        val activeDeviceId: String,
        val activeDeviceName: String,
        val epoch: Long,
        val timestampMs: Long
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_handoff_play")
    data class RemoteHandoffPlay(
        val targetDeviceId: String,
        val item: QueueItem,
        val startPositionMs: Long = 0L,
        val epoch: Long,
        val timestampMs: Long
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_request_sync")
    data object RemoteRequestSync : LanLinkMessage

    @Serializable
    @SerialName("remote_command")
    data class RemoteCommand(
        val action: RemoteCommandAction,
        val index: Int = -1,
        val fromIndex: Int = -1,
        val toIndex: Int = -1,
        val positionMs: Long = 0L,
        val volumePercent: Int = -1,
        val personalFmMode: PersonalFmMode? = null
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_play_queue")
    data class RemotePlayQueue(
        val items: List<QueueItem>,
        val startIndex: Int,
        val playContext: String? = null,
        val source: PlaySource? = null,
        val startPositionMs: Long = 0L
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_play_audio")
    data class RemotePlayAudio(
        val songId: Long,
        val url: String,
        val title: String,
        val artist: String,
        val coverUrl: String,
        val startPosition: Long = 0L,
        val playContext: String? = null
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_add_to_play_next")
    data class RemoteAddToPlayNext(
        val items: List<QueueItem>
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_play_next_now")
    data class RemotePlayNextNow(
        val item: QueueItem,
        val startPositionMs: Long = 0L
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_append_to_queue")
    data class RemoteAppendToQueue(
        val items: List<QueueItem>
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_extend_queue")
    data class RemoteExtendQueue(
        val source: PlaySource,
        val items: List<QueueItem>
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_start_personal_fm")
    data class RemoteStartPersonalFm(
        val items: List<QueueItem>
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_playback_state")
    data class RemotePlaybackState(
        val isPlaying: Boolean,
        val playWhenReady: Boolean,
        val nowPlaying: RemoteNowPlaying? = null,
        val positionMs: Long = 0L,
        val sampledAtMs: Long = 0L,
        val durationMs: Long = 0L,
        val playMode: PlayMode = PlayMode.LIST_LOOP,
        val playContext: String? = null,
        val playSource: PlaySource? = null,
        val currentIndex: Int = -1,
        val volumePercent: Int = 100,
        val queueVersion: Long = 0L
    ) : LanLinkMessage

    @Serializable
    @SerialName("remote_queue_sync")
    data class RemoteQueueSync(
        val queueVersion: Long,
        val items: List<QueueItem>,
        val currentIndex: Int,
        val playContext: String? = null,
        val playSource: PlaySource? = null
    ) : LanLinkMessage
}

@Serializable
data class RemoteNowPlaying(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String? = null
) {
    fun toNowPlaying(): NowPlaying = NowPlaying(mediaId, title, artist, artworkUri)

    companion object {
        fun from(nowPlaying: NowPlaying): RemoteNowPlaying =
            RemoteNowPlaying(nowPlaying.mediaId, nowPlaying.title, nowPlaying.artist, nowPlaying.artworkUri)
    }
}

@Serializable
enum class RemoteCommandAction {
    PLAY,
    PAUSE,
    TOGGLE_PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    SKIP_TO_PREVIOUS,
    PLAY_AT_INDEX,
    REMOVE_FROM_QUEUE,
    MOVE_IN_QUEUE,
    CLEAR_QUEUE,
    STOP_AND_CLEAR_QUEUE,
    SEEK,
    SET_VOLUME,
    TOGGLE_SHUFFLE,
    TOGGLE_REPEAT,
    ROTATE_PLAY_MODE,
    RELOAD_CURRENT,
    CANCEL_PENDING_SKIP,
    DISABLE_ROAMING,
    DISABLE_INTELLIGENCE,
    DISABLE_PERSONAL_FM,
    TRASH_FM,
    SWITCH_FM_MODE
}

