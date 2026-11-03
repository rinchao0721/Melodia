package com.lin0721.linmusic.core.player.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.lin0721.linmusic.core.log.AppLogger
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private const val TAG = "UsbAudioDeviceManager"
private const val ACTION_USB_PERMISSION = "com.lin0721.linmusic.USB_AUDIO_PERMISSION"
private const val PREFS_NAME = "usb_audio"
private const val KEY_VOLUME_LEVEL = "volume_level"
private const val MAX_VOLUME_LEVEL = 30

// 最低一档到满音量跨 60dB，按 dB 等分，听感上每档变化接近
private const val VOLUME_RANGE_DB = 60.0

internal enum class UsbFallbackReason { NO_PERMISSION, NOT_SUPPORTED, OPEN_FAILED, UNSUPPORTED_FORMAT, START_FAILED }

// 独占输出的设备状态：授权、接管与释放、插拔；acquireSession 可在播放线程调用
class UsbAudioDeviceManager(context: Context) : ExclusiveVolumeControl {

    private val appContext = context.applicationContext
    private val usb = appContext.getSystemService(UsbManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val availabilityListeners = CopyOnWriteArraySet<() -> Unit>()
    private val volumeListeners = CopyOnWriteArraySet<() -> Unit>()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -1 表示还没接管过，首次接管时按系统媒体音量换算
    @Volatile private var volumeLevel = prefs.getInt(KEY_VOLUME_LEVEL, -1)
    @Volatile private var playerGain = 1f
    @Volatile private var active = false

    @Volatile var enabled = false
        private set

    // 播放中拔出 DAC 时在主线程回调
    var onDeviceDetached: (() -> Unit)? = null

    private var session: UsbAudioSession? = null
    private var capabilities: Pair<String, UsbAudioCapabilities>? = null
    private var started = false

    // 以下按设备名记录，设备拔出后清空，重新插入时重新尝试
    private var failedDevice: String? = null
    private var permissionRequested: String? = null
    private val reported = HashSet<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> if (device != null && isAudioDevice(device)) {
                    if (enabled) requestPermissionIfNeeded(device)
                    notifyAvailability()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> if (device != null) onDetached(device)
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (!granted) reportFallback(UsbFallbackReason.NO_PERMISSION)
                    notifyAvailability()
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { appContext.unregisterReceiver(receiver) }
        closeSession()
    }

    // 关闭时立即释放接口，系统驱动随即重新接管 DAC
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) findDevice()?.let(::requestPermissionIfNeeded) else closeSession()
        notifyAvailability()
    }

    fun addAvailabilityListener(listener: () -> Unit) {
        availabilityListeners += listener
    }

    fun removeAvailabilityListener(listener: () -> Unit) {
        availabilityListeners -= listener
    }

    internal fun acquireSession(): UsbAudioSession? = synchronized(lock) {
        if (!enabled) return null
        session?.takeIf { it.isOpen }?.let { return it }
        val device = findDevice() ?: return null
        if (!usb.hasPermission(device)) {
            requestPermissionIfNeeded(device)
            return null
        }
        if (failedDevice == device.deviceName) return null
        // 接管后系统认为 DAC 已拔出，要在接管前读系统给它的媒体音量
        if (volumeLevel < 0) setLevelInternal(systemVolumeLevel())
        when (val result = UsbAudioSession.open(usb, device)) {
            is UsbAudioSession.Companion.Result.Opened -> result.session.also {
                session = it
                capabilities = device.deviceName to it.capabilities
                active = true
                applyVolume()
                notifyVolume()
            }
            is UsbAudioSession.Companion.Result.Failed -> {
                AppLogger.w(TAG, "接管 ${device.productName} 失败：${result.reason}")
                failedDevice = device.deviceName
                reportFallback(if (result.reason == UsbOpenFailure.NOT_UAC2) UsbFallbackReason.NOT_SUPPORTED else UsbFallbackReason.OPEN_FAILED)
                null
            }
        }
    }

    // 已知能力时不接管设备就能判断；还不知道时接管一次读取能力
    internal fun supports(sampleRate: Int, channels: Int): Boolean {
        if (!enabled) return false
        val device = findDevice() ?: return false
        val known = synchronized(lock) { capabilities?.takeIf { it.first == device.deviceName }?.second }
        val caps = known ?: acquireSession()?.capabilities ?: return false
        return caps.formatFor(sampleRate, channels) != null
    }

    // 已读到当前 DAC 的能力，即“不支持”是格式问题而非未授权、未插入
    internal fun knowsDevice(): Boolean {
        val device = findDevice() ?: return false
        return synchronized(lock) { capabilities?.first == device.deviceName }
    }

    // DAC 不支持当前格式时交还给系统驱动，否则系统输出会落到手机扬声器
    internal fun releaseForFallback() {
        closeSession()
    }

    // 同一原因在同一次插入期间只提示一次
    internal fun reportFallback(reason: UsbFallbackReason, sampleRate: Int = 0) {
        val key = "$reason:$sampleRate"
        synchronized(lock) { if (!reported.add(key)) return }
        val message = when (reason) {
            UsbFallbackReason.NO_PERMISSION -> "未获得 USB 授权，使用系统输出"
            UsbFallbackReason.NOT_SUPPORTED -> "该 USB 设备不支持独占输出，使用系统输出"
            UsbFallbackReason.OPEN_FAILED, UsbFallbackReason.START_FAILED -> "无法接管 USB 设备，使用系统输出"
            UsbFallbackReason.UNSUPPORTED_FORMAT -> "USB 设备不支持 ${formatRate(sampleRate)}，这首歌使用系统输出"
        }
        main.post { Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show() }
    }

    private fun onDetached(device: UsbDevice) {
        val wasActive = synchronized(lock) {
            if (failedDevice == device.deviceName) failedDevice = null
            if (permissionRequested == device.deviceName) permissionRequested = null
            if (capabilities?.first == device.deviceName) capabilities = null
            reported.clear()
            val current = session
            if (current != null && current.device.deviceName == device.deviceName) {
                current.close()
                session = null
                active = false
                true
            } else {
                false
            }
        }
        if (wasActive) {
            main.post { onDeviceDetached?.invoke() }
            notifyVolume()
        }
        notifyAvailability()
    }

    private fun closeSession() {
        val wasActive = synchronized(lock) {
            val current = session ?: return@synchronized false
            current.close()
            session = null
            active = false
            true
        }
        if (wasActive) notifyVolume()
    }

    override val isActive: Boolean get() = active

    override val maxLevel: Int get() = MAX_VOLUME_LEVEL

    override val level: Int get() = volumeLevel.coerceIn(0, MAX_VOLUME_LEVEL)

    override fun setLevel(level: Int) {
        val target = level.coerceIn(0, MAX_VOLUME_LEVEL)
        if (target == volumeLevel) return
        setLevelInternal(target)
        applyVolume()
        notifyVolume()
    }

    override fun addVolumeListener(listener: () -> Unit) {
        volumeListeners += listener
    }

    override fun removeVolumeListener(listener: () -> Unit) {
        volumeListeners -= listener
    }

    internal fun setPlayerGain(gain: Float) {
        playerGain = gain.coerceIn(0f, 1f)
        applyVolume()
    }

    // 有硬件音量时独占音量与播放器增益合成为 dB 交给 DAC，数据原样输出；否则合成为软件增益
    internal fun applyVolume() {
        val current = synchronized(lock) { session } ?: return
        val userDb = levelToDecibels(level)
        val gain = playerGain
        val hardware = current.hardwareVolume
        if (hardware != null) {
            val total = if (gain <= 0f) Double.NEGATIVE_INFINITY else userDb + 20 * log10(gain.toDouble())
            current.setHardwareDecibels(total)
            current.setSoftwareGain(1f)
        } else {
            val linear = if (userDb.isInfinite()) 0.0 else 10.0.pow(userDb / 20)
            current.setSoftwareGain((linear * gain).toFloat())
        }
    }

    private fun setLevelInternal(level: Int) {
        volumeLevel = level
        prefs.edit().putInt(KEY_VOLUME_LEVEL, level).apply()
    }

    private fun systemVolumeLevel(): Int {
        val audio = appContext.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current.toDouble() / max * MAX_VOLUME_LEVEL).roundToInt()
    }

    private fun levelToDecibels(level: Int): Double =
        if (level <= 0) Double.NEGATIVE_INFINITY else -VOLUME_RANGE_DB * (1 - level.toDouble() / MAX_VOLUME_LEVEL)

    private fun notifyVolume() {
        volumeListeners.forEach { it() }
    }

    private fun requestPermissionIfNeeded(device: UsbDevice) {
        if (usb.hasPermission(device)) return
        synchronized(lock) {
            if (permissionRequested == device.deviceName) return
            permissionRequested = device.deviceName
        }
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
        // 系统要往 Intent 里填授权结果，必须可变
        val pending = PendingIntent.getBroadcast(appContext, 0, intent, PendingIntent.FLAG_MUTABLE)
        usb.requestPermission(device, pending)
    }

    private fun findDevice(): UsbDevice? = usb.deviceList.values.firstOrNull(::isAudioDevice)

    private fun isAudioDevice(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_AUDIO }

    private fun notifyAvailability() {
        availabilityListeners.forEach { it() }
    }

    private fun formatRate(sampleRate: Int): String =
        if (sampleRate % 1000 == 0) "${sampleRate / 1000}kHz" else "%.1fkHz".format(sampleRate / 1000.0)
}
