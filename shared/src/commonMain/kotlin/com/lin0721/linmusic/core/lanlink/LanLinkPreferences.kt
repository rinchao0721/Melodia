package com.lin0721.linmusic.core.lanlink

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.util.UUID

private const val TAG = "LanLinkPreferences"

// 持久化本机 deviceId 与已配对设备列表（含 deviceKey Base64）；项目暂未引入平台密钥库，跟随既有凭证存 DataStore
class LanLinkPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_LOCAL_DEVICE_ID = stringPreferencesKey("lan_link_local_device_id")
        private val KEY_PAIRED_DEVICES = stringPreferencesKey("lan_link_paired_devices")
    }

    private val deviceIdMutex = Mutex()

    val pairedDevices: Flow<List<PairedLanDevice>> = dataStore.data
        .map { prefs -> decodeDevices(prefs[KEY_PAIRED_DEVICES]) }
        .distinctUntilChanged()

    suspend fun ensureLocalDeviceId(): String {
        val cached = dataStore.data.first()[KEY_LOCAL_DEVICE_ID]
        if (!cached.isNullOrBlank()) return cached
        return deviceIdMutex.withLock {
            val again = dataStore.data.first()[KEY_LOCAL_DEVICE_ID]
            if (!again.isNullOrBlank()) return@withLock again
            val generated = UUID.randomUUID().toString()
            dataStore.edit { prefs -> prefs[KEY_LOCAL_DEVICE_ID] = generated }
            generated
        }
    }

    suspend fun getPairedDevice(deviceId: String): PairedLanDevice? =
        pairedDevices.first().firstOrNull { it.deviceId == deviceId }

    suspend fun savePairedDevice(device: PairedLanDevice) {
        dataStore.edit { prefs ->
            val current = decodeDevices(prefs[KEY_PAIRED_DEVICES]).filterNot { it.deviceId == device.deviceId }
            prefs[KEY_PAIRED_DEVICES] = LanLinkJson.encodeToString(listOf(device) + current)
        }
    }

    suspend fun removePairedDevice(deviceId: String) {
        dataStore.edit { prefs ->
            val current = decodeDevices(prefs[KEY_PAIRED_DEVICES])
            val updated = current.filterNot { it.deviceId == deviceId }
            if (updated.size != current.size) {
                if (updated.isEmpty()) {
                    prefs.remove(KEY_PAIRED_DEVICES)
                } else {
                    prefs[KEY_PAIRED_DEVICES] = LanLinkJson.encodeToString(updated)
                }
            }
        }
    }

    private fun decodeDevices(raw: String?): List<PairedLanDevice> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { LanLinkJson.decodeFromString<List<PairedLanDevice>>(raw) }
            .onFailure { AppLogger.w(TAG, "已配对设备列表反序列化失败", it) }
            .getOrDefault(emptyList())
    }
}
