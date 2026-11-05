package com.lin0721.linmusic.core.player.usb

private const val DESC_INTERFACE = 0x04
private const val DESC_ENDPOINT = 0x05
private const val DESC_CS_INTERFACE = 0x24

private const val CLASS_AUDIO = 0x01
private const val SUBCLASS_CONTROL = 0x01
private const val SUBCLASS_STREAMING = 0x02
private const val PROTOCOL_UAC2 = 0x20

private const val AC_INPUT_TERMINAL = 0x02
private const val AC_OUTPUT_TERMINAL = 0x03
private const val AC_FEATURE_UNIT = 0x06
private const val AC_CLOCK_SOURCE = 0x0A
private const val AC_CLOCK_SELECTOR = 0x0B
private const val AS_GENERAL = 0x01
private const val AS_FORMAT_TYPE = 0x02

private const val TERMINAL_USB_STREAMING = 0x0101
private const val FORMAT_TYPE_I = 0x01

// 端点属性：bit0-1 传输类型，bit2-3 同步类型，bit4-5 用途
private const val TRANSFER_ISOCHRONOUS = 0x01
private const val USAGE_DATA = 0x00
private const val USAGE_FEEDBACK = 0x01

internal const val SYNC_NONE = 0
internal const val SYNC_ASYNC = 1
internal const val SYNC_ADAPTIVE = 2
internal const val SYNC_SYNCHRONOUS = 3

// 一个 UAC2 输出备用设置：接口号与备用号用于切换，其余参数交给原生层发包
internal data class Uac2OutputFormat(
    val interfaceNumber: Int,
    val alternateSetting: Int,
    val channels: Int,
    val subslotBytes: Int,
    val bitResolution: Int,
    val dataEndpoint: Int,
    val dataMaxPacket: Int,
    val dataInterval: Int,
    val syncType: Int,
    // 0 表示没有显式反馈端点
    val feedbackEndpoint: Int,
    val feedbackMaxPacket: Int,
    // 供设置采样率的时钟源，找不到时为 null
    val clockSourceId: Int?,
    // 带音量控制的 Feature Unit，没有时为 null
    val volumeUnitId: Int?,
    // 需要设置音量的通道号：0 为主通道，否则为各逻辑声道
    val volumeChannels: List<Int> = emptyList()
)

internal data class Uac2Device(
    val controlInterface: Int,
    val outputs: List<Uac2OutputFormat>
)

private class Entity(
    val id: Int,
    val subtype: Int,
    val sources: List<Int>,
    val terminalType: Int = 0,
    val clockId: Int? = null,
    val volumeChannels: List<Int> = emptyList()
)

private class AltBuilder(val interfaceNumber: Int, val alternateSetting: Int) {
    var terminalLink = -1
    var channels = 0
    var formatType = 0
    var subslotBytes = 0
    var bitResolution = 0
    var dataEndpoint = 0
    var dataMaxPacket = 0
    var dataInterval = 1
    var syncType = SYNC_NONE
    var feedbackEndpoint = 0
    var feedbackMaxPacket = 0
}

internal object Uac2Descriptors {

    // 解析 getRawDescriptors 的完整配置描述符；不是 UAC2 设备或没有输出流时返回 null
    fun parse(raw: ByteArray): Uac2Device? {
        val entities = HashMap<Int, Entity>()
        val alts = ArrayList<AltBuilder>()
        var controlInterface: Int? = null
        var current: AltBuilder? = null
        var currentClass = -1
        var currentSubclass = -1
        var currentProtocol = -1

        var offset = 0
        while (offset + 2 <= raw.size) {
            val length = raw.u8(offset)
            if (length < 2 || offset + length > raw.size) break
            val type = raw.u8(offset + 1)
            when (type) {
                DESC_INTERFACE -> if (length >= 9) {
                    currentClass = raw.u8(offset + 5)
                    currentSubclass = raw.u8(offset + 6)
                    currentProtocol = raw.u8(offset + 7)
                    val number = raw.u8(offset + 2)
                    current = null
                    if (currentClass == CLASS_AUDIO && currentProtocol == PROTOCOL_UAC2) {
                        when (currentSubclass) {
                            SUBCLASS_CONTROL -> if (controlInterface == null) controlInterface = number
                            SUBCLASS_STREAMING -> {
                                val alt = raw.u8(offset + 3)
                                if (alt > 0) current = AltBuilder(number, alt).also { alts += it }
                            }
                        }
                    }
                }
                DESC_CS_INTERFACE -> if (currentClass == CLASS_AUDIO && currentProtocol == PROTOCOL_UAC2 && length >= 3) {
                    if (currentSubclass == SUBCLASS_CONTROL) {
                        parseControlEntity(raw, offset, length)?.let { entities[it.id] = it }
                    } else if (currentSubclass == SUBCLASS_STREAMING) {
                        current?.let { parseStreamingEntity(raw, offset, length, it) }
                    }
                }
                DESC_ENDPOINT -> if (length >= 7) current?.let { parseEndpoint(raw, offset, it) }
            }
            offset += length
        }

        val control = controlInterface ?: return null
        val outputs = alts.mapNotNull { alt -> buildOutput(alt, entities) }
        if (outputs.isEmpty()) return null
        return Uac2Device(control, outputs)
    }

    private fun parseControlEntity(raw: ByteArray, offset: Int, length: Int): Entity? {
        val subtype = raw.u8(offset + 2)
        return when (subtype) {
            AC_INPUT_TERMINAL -> if (length >= 8) {
                Entity(raw.u8(offset + 3), subtype, emptyList(), raw.u16(offset + 4), raw.u8(offset + 7))
            } else null
            AC_OUTPUT_TERMINAL -> if (length >= 9) {
                Entity(raw.u8(offset + 3), subtype, listOf(raw.u8(offset + 7)), raw.u16(offset + 4), raw.u8(offset + 8))
            } else null
            AC_FEATURE_UNIT -> if (length >= 10) {
                // bmaControls 每通道 4 字节，bit2-3 为音量控制；主通道有音量就只调主通道，否则逐声道调
                val controls = (length - 6) / 4
                val withVolume = (0 until controls).filter { (raw.u32(offset + 5 + it * 4) shr 2) and 0x3 != 0 }
                val channels = if (0 in withVolume) listOf(0) else withVolume
                Entity(raw.u8(offset + 3), subtype, listOf(raw.u8(offset + 4)), volumeChannels = channels)
            } else null
            AC_CLOCK_SOURCE -> if (length >= 4) Entity(raw.u8(offset + 3), subtype, emptyList()) else null
            AC_CLOCK_SELECTOR -> if (length >= 5) {
                val pins = raw.u8(offset + 4).coerceAtMost(length - 5)
                val sources = (0 until pins).map { raw.u8(offset + 5 + it) }
                Entity(raw.u8(offset + 3), subtype, sources)
            } else null
            else -> null
        }
    }

    private fun parseStreamingEntity(raw: ByteArray, offset: Int, length: Int, alt: AltBuilder) {
        when (raw.u8(offset + 2)) {
            AS_GENERAL -> if (length >= 11) {
                alt.terminalLink = raw.u8(offset + 3)
                alt.formatType = raw.u8(offset + 5)
                alt.channels = raw.u8(offset + 10)
            }
            AS_FORMAT_TYPE -> if (length >= 6 && raw.u8(offset + 3) == FORMAT_TYPE_I) {
                alt.subslotBytes = raw.u8(offset + 4)
                alt.bitResolution = raw.u8(offset + 5)
            }
        }
    }

    private fun parseEndpoint(raw: ByteArray, offset: Int, alt: AltBuilder) {
        val address = raw.u8(offset + 2)
        val attributes = raw.u8(offset + 3)
        if (attributes and 0x3 != TRANSFER_ISOCHRONOUS) return
        val sizeField = raw.u16(offset + 4)
        // 高速高带宽端点：bit11-12 为每微帧附加事务数
        val maxPacket = (sizeField and 0x7FF) * (1 + ((sizeField shr 11) and 0x3))
        val interval = raw.u8(offset + 6).coerceAtLeast(1)
        val isIn = address and 0x80 != 0
        when ((attributes shr 4) and 0x3) {
            USAGE_DATA -> if (!isIn) {
                alt.dataEndpoint = address
                alt.dataMaxPacket = maxPacket
                alt.dataInterval = interval
                alt.syncType = (attributes shr 2) and 0x3
            }
            USAGE_FEEDBACK -> if (isIn) {
                alt.feedbackEndpoint = address
                alt.feedbackMaxPacket = maxPacket
            }
        }
    }

    private fun buildOutput(alt: AltBuilder, entities: Map<Int, Entity>): Uac2OutputFormat? {
        if (alt.dataEndpoint == 0 || alt.formatType != FORMAT_TYPE_I) return null
        if (alt.subslotBytes !in 2..4 || alt.channels <= 0 || alt.bitResolution <= 0) return null
        // 输出流链接到 USB 流类型的输入终端
        val terminal = entities[alt.terminalLink]
            ?.takeIf { it.subtype == AC_INPUT_TERMINAL && it.terminalType == TERMINAL_USB_STREAMING }
            ?: return null
        val volumeUnit = findVolumeUnit(terminal.id, entities)
        return Uac2OutputFormat(
            interfaceNumber = alt.interfaceNumber,
            alternateSetting = alt.alternateSetting,
            channels = alt.channels,
            subslotBytes = alt.subslotBytes,
            bitResolution = alt.bitResolution,
            dataEndpoint = alt.dataEndpoint,
            dataMaxPacket = alt.dataMaxPacket,
            dataInterval = alt.dataInterval,
            syncType = alt.syncType,
            feedbackEndpoint = alt.feedbackEndpoint,
            feedbackMaxPacket = alt.feedbackMaxPacket,
            clockSourceId = resolveClockSource(terminal.clockId, entities),
            volumeUnitId = volumeUnit?.id,
            volumeChannels = volumeUnit?.volumeChannels.orEmpty()
        )
    }

    // 时钟选择器取第一个输入，直到落到时钟源
    private fun resolveClockSource(start: Int?, entities: Map<Int, Entity>): Int? {
        var id = start ?: return null
        repeat(entities.size + 1) {
            val entity = entities[id] ?: return null
            when (entity.subtype) {
                AC_CLOCK_SOURCE -> return entity.id
                AC_CLOCK_SELECTOR -> id = entity.sources.firstOrNull() ?: return null
                else -> return null
            }
        }
        return null
    }

    // 从输入终端顺着下游找第一个带音量控制的 Feature Unit
    private fun findVolumeUnit(terminalId: Int, entities: Map<Int, Entity>): Entity? {
        var source = terminalId
        val visited = HashSet<Int>()
        while (visited.add(source)) {
            val next = entities.values.firstOrNull { it.subtype != AC_INPUT_TERMINAL && source in it.sources } ?: return null
            if (next.subtype == AC_FEATURE_UNIT && next.volumeChannels.isNotEmpty()) return next
            if (next.subtype == AC_OUTPUT_TERMINAL) return null
            source = next.id
        }
        return null
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

    private fun ByteArray.u16(index: Int): Int = u8(index) or (u8(index + 1) shl 8)

    private fun ByteArray.u32(index: Int): Int =
        u8(index) or (u8(index + 1) shl 8) or (u8(index + 2) shl 16) or (u8(index + 3) shl 24)
}
