package com.lin0721.linmusic.core.player.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.lin0721.linmusic.core.log.AppLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "UsbAudioSession"

private const val REQUEST_CUR = 0x01
private const val REQUEST_RANGE = 0x02
private const val CS_SAM_FREQ_CONTROL = 0x01
private const val FU_MUTE_CONTROL = 0x01
private const val FU_VOLUME_CONTROL = 0x02
private const val REQUEST_TYPE_IN = 0xA1
private const val REQUEST_TYPE_OUT = 0x21
private const val CONTROL_TIMEOUT_MS = 1000
private const val RANGE_BUFFER_BYTES = 2 + 12 * 32

private const val HIGH_SPEED_PACKETS_PER_SECOND = 8000
private const val FULL_SPEED_PACKETS_PER_SECOND = 1000

internal enum class UsbOpenFailure { NO_LIBRARY, OPEN_FAILED, NOT_UAC2, CLAIM_FAILED, NATIVE_FAILED }

// Feature Unit 音量范围，单位 1/256 dB
internal data class HardwareVolume(val unitId: Int, val channels: List<Int>, val minRaw: Int, val maxRaw: Int)

// 已接管的 DAC：持有接口占用与原生流句柄，所有原生调用在 lock 内进行，关闭后句柄置 0
internal class UsbAudioSession private constructor(
    val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    val uac: Uac2Device,
    val rates: List<SampleRateRange>,
    private val interfaces: List<UsbInterface>,
    private val claimed: List<UsbInterface>,
    private var handle: Long,
    // DAC 没有可用的硬件音量时为 null，改用软件增益
    val hardwareVolume: HardwareVolume?
) {

    private val lock = Any()
    val capabilities = UsbAudioCapabilities(
        uac.outputs,
        rates,
        if (UsbAudioNative.nativeIsHighSpeed(handle)) HIGH_SPEED_PACKETS_PER_SECOND else FULL_SPEED_PACKETS_PER_SECOND
    )
    private var activeFormat: Uac2OutputFormat? = null
    private var activeRate = 0

    // 当前占用原生流的输出，同一时间只允许一个
    private var owner: Any? = null

    val isOpen: Boolean get() = synchronized(lock) { handle != 0L }

    fun formatFor(sampleRate: Int, channels: Int): Uac2OutputFormat? = capabilities.formatFor(sampleRate, channels)

    // 采样率或备用设置变化时先回到零带宽设置，设好时钟再切过去；失败返回 false
    fun start(owner: Any, format: Uac2OutputFormat, sampleRate: Int, inputEncoding: Int, bufferMs: Int): Boolean = synchronized(lock) {
        if (handle == 0L) return false
        val current = this.owner
        if (current != null && current !== owner) return false
        UsbAudioNative.nativeStop(handle)
        if (activeFormat != format || activeRate != sampleRate) {
            altSetting(format.interfaceNumber, 0)?.let { connection.setInterface(it) }
            format.clockSourceId?.let { clock ->
                if (!setSampleRate(clock, sampleRate)) AppLogger.w(TAG, "设采样率 $sampleRate 未确认，按名义值继续")
            }
            val alt = altSetting(format.interfaceNumber, format.alternateSetting)
            if (alt == null || !connection.setInterface(alt)) {
                AppLogger.w(TAG, "切换备用设置 ${format.alternateSetting} 失败")
                activeFormat = null
                return false
            }
            activeFormat = format
            activeRate = sampleRate
        }
        val config = IntArray(UsbAudioNative.CONFIG_SIZE).apply {
            this[UsbAudioNative.CONFIG_DATA_ENDPOINT] = format.dataEndpoint
            this[UsbAudioNative.CONFIG_DATA_MAX_PACKET] = format.dataMaxPacket
            this[UsbAudioNative.CONFIG_FEEDBACK_ENDPOINT] = format.feedbackEndpoint
            this[UsbAudioNative.CONFIG_FEEDBACK_MAX_PACKET] = format.feedbackMaxPacket
            this[UsbAudioNative.CONFIG_DATA_INTERVAL] = format.dataInterval
            this[UsbAudioNative.CONFIG_SAMPLE_RATE] = sampleRate
            this[UsbAudioNative.CONFIG_CHANNELS] = format.channels
            this[UsbAudioNative.CONFIG_SUBSLOT_BYTES] = format.subslotBytes
            this[UsbAudioNative.CONFIG_BIT_RESOLUTION] = format.bitResolution
            this[UsbAudioNative.CONFIG_INPUT_ENCODING] = inputEncoding
            this[UsbAudioNative.CONFIG_BUFFER_MS] = bufferMs
        }
        val result = UsbAudioNative.nativeStart(handle, config)
        if (result != 0) {
            AppLogger.w(TAG, "启动输出失败 ${UsbAudioNative.nativeErrorName(result)}")
            return false
        }
        this.owner = owner
        AppLogger.i(TAG, "独占输出 ${sampleRate}Hz ${format.bitResolution}bit 备用设置 ${format.alternateSetting}")
        true
    }

    fun stop(owner: Any): Unit = synchronized(lock) {
        if (this.owner !== owner) return
        if (handle != 0L) UsbAudioNative.nativeStop(handle)
        this.owner = null
    }

    fun write(owner: Any, buffer: ByteBuffer, offset: Int, size: Int): Int = synchronized(lock) {
        if (handle == 0L || this.owner !== owner) return 0
        UsbAudioNative.nativeWriteDirect(handle, buffer, offset, size)
    }

    fun setPaused(owner: Any, paused: Boolean) = withOwner(owner) { UsbAudioNative.nativeSetPaused(it, paused) }

    fun flush(owner: Any) = withOwner(owner) { UsbAudioNative.nativeFlush(it) }

    // 软件增益对当前占用流的输出生效，没有占用时等下次启动由调用方重新设置
    fun setSoftwareGain(gain: Float): Unit = synchronized(lock) {
        if (handle != 0L && owner != null) UsbAudioNative.nativeSetGain(handle, gain)
    }

    // decibels 为负无穷时静音；超出 DAC 范围的值钳到端点
    fun setHardwareDecibels(decibels: Double): Unit = synchronized(lock) {
        val volume = hardwareVolume ?: return
        if (handle == 0L) return
        val muted = decibels.isInfinite() && decibels < 0
        val raw = if (muted) volume.minRaw else (decibels * 256).toInt().coerceIn(volume.minRaw, volume.maxRaw)
        val data = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(raw.toShort()).array()
        val mute = byteArrayOf(if (muted) 1 else 0)
        val index = (volume.unitId shl 8) or uac.controlInterface
        for (channel in volume.channels) {
            connection.controlTransfer(REQUEST_TYPE_OUT, REQUEST_CUR, (FU_VOLUME_CONTROL shl 8) or channel, index, data, 2, CONTROL_TIMEOUT_MS)
        }
        // 静音控制多在主通道上，失败不影响音量
        connection.controlTransfer(REQUEST_TYPE_OUT, REQUEST_CUR, FU_MUTE_CONTROL shl 8, index, mute, 1, CONTROL_TIMEOUT_MS)
    }

    fun framesPlayed(owner: Any): Long = synchronized(lock) {
        if (handle == 0L || this.owner !== owner) 0L else UsbAudioNative.nativeFramesPlayed(handle)
    }

    fun bufferedFrames(owner: Any): Long = synchronized(lock) {
        if (handle == 0L || this.owner !== owner) 0L else UsbAudioNative.nativeBufferedFrames(handle)
    }

    // 非 0 表示流已中断（拔出、传输错误）或会话已关闭
    fun lastError(owner: Any): Int = synchronized(lock) {
        if (handle == 0L || this.owner !== owner) -1 else UsbAudioNative.nativeLastError(handle)
    }

    // 系统驱动在释放接口后会自动重新接管
    fun close(): Unit = synchronized(lock) {
        if (handle == 0L) return
        UsbAudioNative.nativeStop(handle)
        activeFormat?.let { format -> altSetting(format.interfaceNumber, 0)?.let { connection.setInterface(it) } }
        claimed.forEach { connection.releaseInterface(it) }
        UsbAudioNative.nativeClose(handle)
        handle = 0L
        owner = null
        activeFormat = null
        connection.close()
        AppLogger.i(TAG, "已释放 ${device.productName}")
    }

    private inline fun withOwner(owner: Any, block: (Long) -> Unit) = synchronized(lock) {
        if (handle != 0L && this.owner === owner) block(handle)
    }

    private fun altSetting(number: Int, alternate: Int): UsbInterface? =
        interfaces.firstOrNull { it.id == number && it.alternateSetting == alternate }

    private fun setSampleRate(clockId: Int, sampleRate: Int): Boolean {
        val index = (clockId shl 8) or uac.controlInterface
        val data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate).array()
        connection.controlTransfer(REQUEST_TYPE_OUT, REQUEST_CUR, CS_SAM_FREQ_CONTROL shl 8, index, data, data.size, CONTROL_TIMEOUT_MS)
        val readBack = ByteArray(4)
        val read = connection.controlTransfer(REQUEST_TYPE_IN, REQUEST_CUR, CS_SAM_FREQ_CONTROL shl 8, index, readBack, 4, CONTROL_TIMEOUT_MS)
        return read == 4 && ByteBuffer.wrap(readBack).order(ByteOrder.LITTLE_ENDIAN).int == sampleRate
    }

    companion object {

        sealed interface Result {
            class Opened(val session: UsbAudioSession) : Result
            class Failed(val reason: UsbOpenFailure) : Result
        }

        // 调用方须已获得该设备的 USB 授权；接管期间系统声卡驱动被卸下
        fun open(usb: UsbManager, device: UsbDevice): Result {
            if (!UsbAudioNative.isAvailable) return Result.Failed(UsbOpenFailure.NO_LIBRARY)
            val connection = usb.openDevice(device) ?: return Result.Failed(UsbOpenFailure.OPEN_FAILED)
            val uac = connection.rawDescriptors?.let(Uac2Descriptors::parse)
            if (uac == null) {
                connection.close()
                return Result.Failed(UsbOpenFailure.NOT_UAC2)
            }
            val interfaces = (0 until device.interfaceCount).map(device::getInterface)
            val numbers = (listOf(uac.controlInterface) + uac.outputs.map { it.interfaceNumber }).distinct()
            val claimed = ArrayList<UsbInterface>()
            for (number in numbers) {
                val intf = interfaces.firstOrNull { it.id == number && it.alternateSetting == 0 }
                if (intf == null || !connection.claimInterface(intf, true)) {
                    claimed.forEach(connection::releaseInterface)
                    connection.close()
                    return Result.Failed(UsbOpenFailure.CLAIM_FAILED)
                }
                claimed += intf
            }
            val clock = uac.outputs.firstNotNullOfOrNull { it.clockSourceId }
            val rates = clock?.let { readRates(connection, it, uac.controlInterface) }.orEmpty()
            val error = IntArray(1)
            val handle = UsbAudioNative.nativeOpen(connection.fileDescriptor, error)
            if (handle == 0L) {
                AppLogger.w(TAG, "原生打开失败 ${UsbAudioNative.nativeErrorName(error[0])}")
                claimed.forEach(connection::releaseInterface)
                connection.close()
                return Result.Failed(UsbOpenFailure.NATIVE_FAILED)
            }
            val volume = readHardwareVolume(connection, uac)
            AppLogger.i(
                TAG,
                "已接管 ${device.productName}，采样率 ${rates.joinToString { if (it.min == it.max) "${it.min}" else "${it.min}-${it.max}" }}，" +
                    "硬件音量 ${volume?.let { "${it.minRaw / 256.0}~${it.maxRaw / 256.0}dB 通道${it.channels}" } ?: "无"}"
            )
            return Result.Opened(UsbAudioSession(device, connection, uac, rates, interfaces, claimed, handle, volume))
        }

        // 各声道范围取交集，读不到范围时视为没有硬件音量
        private fun readHardwareVolume(connection: UsbDeviceConnection, uac: Uac2Device): HardwareVolume? {
            val format = uac.outputs.firstOrNull { it.volumeUnitId != null } ?: return null
            val unit = format.volumeUnitId ?: return null
            var min = Int.MIN_VALUE
            var max = Int.MAX_VALUE
            for (channel in format.volumeChannels) {
                val buffer = ByteArray(2 + 6 * 8)
                val read = connection.controlTransfer(
                    REQUEST_TYPE_IN, REQUEST_RANGE, (FU_VOLUME_CONTROL shl 8) or channel, (unit shl 8) or uac.controlInterface,
                    buffer, buffer.size, CONTROL_TIMEOUT_MS
                )
                if (read < 8) return null
                val bb = ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                val count = bb.short.toInt()
                if (count <= 0 || read < 2 + count * 6) return null
                val channelMin = bb.short.toInt()
                // 总范围取首个子区间的下限与末个子区间的上限
                bb.position(2 + (count - 1) * 6 + 2)
                val channelMax = bb.short.toInt()
                min = maxOf(min, channelMin)
                max = minOf(max, channelMax)
            }
            return if (format.volumeChannels.isNotEmpty() && max > min) HardwareVolume(unit, format.volumeChannels, min, max) else null
        }

        private fun readRates(connection: UsbDeviceConnection, clockId: Int, controlInterface: Int): List<SampleRateRange> {
            val buffer = ByteArray(RANGE_BUFFER_BYTES)
            val read = connection.controlTransfer(
                REQUEST_TYPE_IN, REQUEST_RANGE, CS_SAM_FREQ_CONTROL shl 8, (clockId shl 8) or controlInterface,
                buffer, buffer.size, CONTROL_TIMEOUT_MS
            )
            return if (read > 0) Uac2SampleRates.parse(buffer.copyOf(read)) else emptyList()
        }
    }
}
