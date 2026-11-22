package com.lin0721.linmusic.core.kugou

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.UUID

// 酷狗设备指纹持久化模型
data class KugouDeviceIdentity(
    val guid: String,
    val mid: String,
    val dfid: String = "-",
    val dev: String,
    val mac: String = DEFAULT_MAC,
    val webglHash: String
) {
    val effectiveDfid: String
        get() = dfid.trim().ifEmpty { "-" }

    companion object {
        const val DEFAULT_MAC = "02:00:00:00:00:00"
    }
}

// 复用现有用户偏好 DataStore，以 kugou_device_* 前缀键持久化设备标识，不新建额外偏好文件
class KugouDeviceStore(
    private val dataStore: DataStore<Preferences>
) {
    private val mutex = Mutex()
    private val random = SecureRandom()

    suspend fun getOrCreateDeviceIdentity(): KugouDeviceIdentity {
        val current = dataStore.data.first()
        readCompleteOrNull(current)?.let { return it }

        return mutex.withLock {
            val latest = dataStore.data.first()
            readCompleteOrNull(latest)?.let { return@withLock it }

            val guid = latest[KEY_GUID]?.takeIf { it.isNotBlank() }
                ?: KugouCrypto.md5Hex(UUID.randomUUID().toString())
            val mid = latest[KEY_MID]?.takeIf { it.isNotBlank() }
                ?: KugouCrypto.calculateMid(guid)
            val dfid = latest[KEY_DFID]?.takeIf { it.isNotBlank() } ?: "-"
            val dev = latest[KEY_DEV]?.takeIf { it.isNotBlank() }
                ?: KugouCrypto.randomString(10).uppercase()
            val mac = latest[KEY_MAC]?.takeIf { it.isNotBlank() }
                ?: KugouDeviceIdentity.DEFAULT_MAC
            val webglHash = latest[KEY_WEBGL_HASH]?.takeIf { it.isNotBlank() }
                ?: generateRandomUint64String()

            val identity = KugouDeviceIdentity(
                guid = guid,
                mid = mid,
                dfid = dfid,
                dev = dev,
                mac = mac,
                webglHash = webglHash
            )
            dataStore.edit { prefs ->
                prefs[KEY_GUID] = identity.guid
                prefs[KEY_MID] = identity.mid
                prefs[KEY_DFID] = identity.dfid
                prefs[KEY_DEV] = identity.dev
                prefs[KEY_MAC] = identity.mac
                prefs[KEY_WEBGL_HASH] = identity.webglHash
            }
            identity
        }
    }

    suspend fun updateDfid(dfid: String) {
        val trimmed = dfid.trim()
        if (trimmed.isEmpty()) return
        mutex.withLock {
            dataStore.edit { prefs ->
                prefs[KEY_DFID] = trimmed
            }
        }
    }

    private fun readCompleteOrNull(prefs: Preferences): KugouDeviceIdentity? {
        val guid = prefs[KEY_GUID]?.takeIf { it.isNotBlank() } ?: return null
        val mid = prefs[KEY_MID]?.takeIf { it.isNotBlank() } ?: return null
        val dfid = prefs[KEY_DFID]?.takeIf { it.isNotBlank() } ?: "-"
        val dev = prefs[KEY_DEV]?.takeIf { it.isNotBlank() } ?: return null
        val mac = prefs[KEY_MAC]?.takeIf { it.isNotBlank() } ?: KugouDeviceIdentity.DEFAULT_MAC
        val webglHash = prefs[KEY_WEBGL_HASH]?.takeIf { it.isNotBlank() } ?: return null
        return KugouDeviceIdentity(
            guid = guid,
            mid = mid,
            dfid = dfid,
            dev = dev,
            mac = mac,
            webglHash = webglHash
        )
    }

    private fun generateRandomUint64String(): String {
        val hi = (random.nextLong() and 0xFFFFFFFFL)
        val lo = (random.nextLong() and 0xFFFFFFFFL)
        return ((hi shl 32) or lo).toULong().toString()
    }

    companion object {
        private val KEY_GUID = stringPreferencesKey("kugou_device_guid")
        private val KEY_MID = stringPreferencesKey("kugou_device_mid")
        private val KEY_DFID = stringPreferencesKey("kugou_device_dfid")
        private val KEY_DEV = stringPreferencesKey("kugou_device_dev")
        private val KEY_MAC = stringPreferencesKey("kugou_device_mac")
        private val KEY_WEBGL_HASH = stringPreferencesKey("kugou_device_webgl_hash")
    }
}
