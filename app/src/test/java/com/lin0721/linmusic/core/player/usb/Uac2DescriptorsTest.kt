package com.lin0721.linmusic.core.player.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Uac2DescriptorsTest {

    private fun bytes(hex: String): ByteArray =
        hex.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()

    // MOONDROP DAWN PRO 2的配置描述符：类描述符取自真机，端点与配置头按其解析结果还原
    private val cx31993 = bytes(
        """
        09 02 20 01 03 01 00 80 fa
        08 0b 00 02 01 00 20 00
        09 04 00 00 00 01 01 20 00
        09 24 01 00 02 04 40 00 00
        08 24 0a 09 03 07 00 00
        11 24 02 01 01 01 00 09 02 03 00 00 00 00 00 00 00
        12 24 06 02 01 03 00 00 00 0c 00 00 00 0c 00 00 00 00
        0c 24 03 03 02 03 00 02 09 00 00 00
        09 04 01 00 00 01 02 20 00
        09 04 01 01 01 01 02 20 00
        10 24 01 01 00 01 01 00 00 00 02 03 00 00 00 00
        06 24 02 01 02 10
        07 05 01 0d c0 00 01
        08 25 01 00 00 00 00 00
        09 04 01 02 01 01 02 20 00
        10 24 01 01 00 01 01 00 00 00 02 03 00 00 00 00
        06 24 02 01 03 18
        07 05 01 0d 20 01 01
        08 25 01 00 00 00 00 00
        09 04 01 03 01 01 02 20 00
        10 24 01 01 00 01 01 00 00 00 02 03 00 00 00 00
        06 24 02 01 04 20
        07 05 01 0d 80 01 01
        08 25 01 00 00 00 00 00
        09 04 02 00 01 03 00 00 00
        09 21 11 01 00 01 22 21 00
        07 05 83 03 40 00 04
        """
    )

    @Test
    fun `解析出三种位深的输出格式`() {
        val device = Uac2Descriptors.parse(cx31993)!!
        assertEquals(0, device.controlInterface)
        assertEquals(listOf(16, 24, 32), device.outputs.map { it.bitResolution })
        assertEquals(listOf(2, 3, 4), device.outputs.map { it.subslotBytes })
        assertEquals(listOf(192, 288, 384), device.outputs.map { it.dataMaxPacket })
        device.outputs.forEach { format ->
            assertEquals(1, format.interfaceNumber)
            assertEquals(2, format.channels)
            assertEquals(0x01, format.dataEndpoint)
            assertEquals(SYNC_SYNCHRONOUS, format.syncType)
            assertEquals(0, format.feedbackEndpoint)
            assertEquals(9, format.clockSourceId)
        }
    }

    @Test
    fun `音量控制在逐声道上时按声道返回`() {
        val format = Uac2Descriptors.parse(cx31993)!!.outputs.first()
        assertEquals(2, format.volumeUnitId)
        assertEquals(listOf(1, 2), format.volumeChannels)
    }

    @Test
    fun `主通道有音量时只调主通道`() {
        val raw = cx31993.copyOf()
        // Feature Unit 主通道 bmaControls 第一字节改为 0x0f（静音+音量）
        val index = indexOf(raw, bytes("12 24 06 02 01")) + 5
        raw[index] = 0x0f
        val format = Uac2Descriptors.parse(raw)!!.outputs.first()
        assertEquals(listOf(0), format.volumeChannels)
    }

    @Test
    fun `时钟选择器解析到第一个时钟源`() {
        val raw = bytes(
            """
            09 04 00 00 00 01 01 20 00
            08 24 0a 05 03 07 00 00
            08 24 0b 06 01 05 03 00
            11 24 02 01 01 01 00 06 02 03 00 00 00 00 00 00 00
            09 04 01 01 01 01 02 20 00
            10 24 01 01 00 01 01 00 00 00 02 03 00 00 00 00
            06 24 02 01 03 18
            07 05 01 05 20 01 01
            07 05 81 11 04 00 04
            """
        )
        val format = Uac2Descriptors.parse(raw)!!.outputs.single()
        assertEquals(5, format.clockSourceId)
        assertEquals(SYNC_ASYNC, format.syncType)
        assertEquals(0x81, format.feedbackEndpoint)
        assertEquals(4, format.feedbackMaxPacket)
        assertNull(format.volumeUnitId)
    }

    @Test
    fun `UAC1 或残缺描述符返回空`() {
        assertNull(Uac2Descriptors.parse(bytes("09 04 00 00 00 01 01 00 00 09 04 01 01 01 01 02 00 00")))
        assertNull(Uac2Descriptors.parse(ByteArray(0)))
        assertNull(Uac2Descriptors.parse(bytes("09 04 00")))
        assertNull(Uac2Descriptors.parse(bytes("00 00 00 00")))
    }

    @Test
    fun `截断的描述符不越界`() {
        for (size in 0 until cx31993.size) Uac2Descriptors.parse(cx31993.copyOf(size))
    }

    @Test
    fun `采样率区间解析与判断`() {
        val ranges = Uac2SampleRates.parse(
            bytes(
                """
                03 00
                44 ac 00 00 44 ac 00 00 00 00 00 00
                00 77 01 00 00 77 01 00 00 00 00 00
                40 1f 00 00 00 ee 02 00 40 1f 00 00
                """
            )
        )
        assertEquals(3, ranges.size)
        assertTrue(ranges.any { it.contains(44100) })
        assertTrue(ranges.any { it.contains(96000) })
        assertTrue(ranges.any { it.contains(16000) })
        assertFalse(ranges.any { it.contains(22050) })
        assertTrue(Uac2SampleRates.parse(bytes("05 00 44 ac 00 00")).isEmpty())
        assertTrue(Uac2SampleRates.parse(ByteArray(1)).isEmpty())
    }

    @Test
    fun `按采样率与包长选最高位深`() {
        val outputs = Uac2Descriptors.parse(cx31993)!!.outputs
        val rates = listOf(44100, 48000, 96000, 192000, 384000).map { SampleRateRange(it, it, 0) }
        assertEquals(32, Uac2FormatSelector.select(outputs, rates, 44100, 2, 8000)?.bitResolution)
        assertEquals(32, Uac2FormatSelector.select(outputs, rates, 384000, 2, 8000)?.bitResolution)
        assertNull("DAC 不支持的采样率交给系统输出", Uac2FormatSelector.select(outputs, rates, 22050, 2, 8000))
        assertNull("声道数不一致交给系统输出", Uac2FormatSelector.select(outputs, rates, 44100, 1, 8000))
        assertNull("全速总线下 192k 塞不进 384 字节的包", Uac2FormatSelector.select(outputs, rates, 192000, 2, 1000))
    }

    @Test
    fun `有反馈端点时多留一帧`() {
        val format = Uac2Descriptors.parse(cx31993)!!.outputs.last()
        assertTrue(Uac2FormatSelector.fitsPacket(format, 384000, 8000))
        assertFalse(Uac2FormatSelector.fitsPacket(format.copy(syncType = SYNC_ASYNC), 384000, 8000))
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int =
        (0..haystack.size - needle.size).first { start -> needle.indices.all { haystack[start + it] == needle[it] } }
}
