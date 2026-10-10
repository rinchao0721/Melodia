package com.lin0721.linmusic.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Test

// 输出设备筛选的单元测试。
// 数据取自本机 mpv 实际输出：pipewire 与 pulse 各列一遍同一批设备，外加 alsa 的大量
// 虚拟条目（surround 变体、插件别名）与 jack/sdl 等后端占位项，共 57 条。
class AudioDeviceSelectionTest {

    private val mpvJson = """
        [
          {"name":"auto","description":"Autoselect device"},
          {"name":"pipewire","description":"Default (pipewire)"},
          {"name":"pipewire/alsa_output.pci-0000_01_00.1.hdmi-stereo","description":"AD102 High Definition Audio Controller Digital Stereo (HDMI) [F27G10U]"},
          {"name":"pipewire/alsa_output.usb-Mi_REDMI______________2_Pro_20190808-00.analog-stereo","description":"REDMI 电脑音箱 2 Pro Analog Stereo"},
          {"name":"pulse","description":"Default (pulse)"},
          {"name":"pulse/alsa_output.pci-0000_01_00.1.hdmi-stereo","description":"AD102 High Definition Audio Controller Digital Stereo (HDMI) [F27G10U]"},
          {"name":"pulse/alsa_output.usb-Mi_REDMI______________2_Pro_20190808-00.analog-stereo","description":"REDMI 电脑音箱 2 Pro Analog Stereo"},
          {"name":"alsa","description":"Default (alsa)"},
          {"name":"alsa/lavrate","description":"Rate Converter Plugin Using Libav/FFmpeg Library"},
          {"name":"alsa/surround71:CARD=Pro,DEV=0","description":"REDMI 电脑音箱 2 Pro, USB Audio/7.1 Surround output"},
          {"name":"alsa/usbstream:CARD=Pro","description":"REDMI 电脑音箱 2 Pro/USB Stream Output"},
          {"name":"jack","description":"Default (jack)"},
          {"name":"sdl","description":"Default (sdl)"}
        ]
    """.trimIndent()

    @Test
    fun `有声服务时只保留 pipewire 的真实设备`() {
        val devices = selectAudioDevices(parseAudioDevices(mpvJson), currentName = AUTO_AUDIO_DEVICE)

        assertEquals(
            listOf(
                AUTO_AUDIO_DEVICE,
                "pipewire/alsa_output.pci-0000_01_00.1.hdmi-stereo",
                "pipewire/alsa_output.usb-Mi_REDMI______________2_Pro_20190808-00.analog-stereo",
            ),
            devices.map { it.name },
        )
    }

    @Test
    fun `没有 pipewire 时退回 pulse 设备`() {
        val json = mpvJson.replace("\"name\":\"pipewire", "\"name\":\"__gone")
        val devices = selectAudioDevices(parseAudioDevices(json), currentName = AUTO_AUDIO_DEVICE)

        assertEquals(
            listOf(
                AUTO_AUDIO_DEVICE,
                "pulse/alsa_output.pci-0000_01_00.1.hdmi-stereo",
                "pulse/alsa_output.usb-Mi_REDMI______________2_Pro_20190808-00.analog-stereo",
            ),
            devices.map { it.name },
        )
    }

    @Test
    fun `当前设备不属于所选后端时仍保留`() {
        val devices = selectAudioDevices(parseAudioDevices(mpvJson), currentName = "alsa/surround71:CARD=Pro,DEV=0")

        assertEquals(
            listOf(
                AUTO_AUDIO_DEVICE,
                "pipewire/alsa_output.pci-0000_01_00.1.hdmi-stereo",
                "pipewire/alsa_output.usb-Mi_REDMI______________2_Pro_20190808-00.analog-stereo",
                "alsa/surround71:CARD=Pro,DEV=0",
            ),
            devices.map { it.name },
        )
    }

    @Test
    fun `Windows 只保留 WASAPI 设备`() {
        val json = """
            [
              {"name":"auto","description":"Autoselect device"},
              {"name":"wasapi/{c2e4f0d0-1111-4444-9999-abcdefabcdef}","description":"扬声器 (Realtek High Definition Audio)"},
              {"name":"openal","description":"Default (openal)"}
            ]
        """.trimIndent()

        val devices = selectAudioDevices(parseAudioDevices(json), currentName = AUTO_AUDIO_DEVICE)

        assertEquals(listOf(AUTO_AUDIO_DEVICE, "wasapi/{c2e4f0d0-1111-4444-9999-abcdefabcdef}"), devices.map { it.name })
    }

    @Test
    fun `没有已知后端时原样返回`() {
        val json = """
            [
              {"name":"auto","description":"Autoselect device"},
              {"name":"coreaudio/AppleHDAEngineOutput:1B,0,1,2:0","description":"MacBook 扬声器"}
            ]
        """.trimIndent()

        val devices = selectAudioDevices(parseAudioDevices(json), currentName = AUTO_AUDIO_DEVICE)

        assertEquals(listOf(AUTO_AUDIO_DEVICE, "coreaudio/AppleHDAEngineOutput:1B,0,1,2:0"), devices.map { it.name })
    }
}
