package com.lin0721.linmusic.desktop.platform.native.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinuxUiScaleTest {

    // 与本机 wayland-info 实际输出同格式：两块 4K 屏、逻辑 2560（缩放 1.5）
    private val waylandInfoSample = """
        interface: 'wl_output',                                  version:  4, name: 66
        	name: DP-2
        	x: 2560, y: 0, scale: 2,
        	physical_width: 596 mm, physical_height: 335 mm,
        	mode:
        		width: 3840 px, height: 2160 px, refresh: 160.000 Hz,
        		flags: current
        interface: 'wl_output',                                  version:  4, name: 67
        	name: DP-3
        	x: 0, y: 0, scale: 2,
        	mode:
        		width: 3840 px, height: 2160 px, refresh: 60.000 Hz,
        		flags: current
        interface: 'zxdg_output_manager_v1',                     version:  3, name: 33
        	xdg_output_v1
        		output: 67
        		name: 'DP-3'
        		logical_x: 0, logical_y: 0
        		logical_width: 2560, logical_height: 1440
        	xdg_output_v1
        		output: 66
        		name: 'DP-2'
        		logical_x: 2560, logical_y: 0
        		logical_width: 2560, logical_height: 1440
    """.trimIndent()

    @Test
    fun `解析 wayland-info 的物理与逻辑尺寸`() {
        val outputs = parseWaylandInfo(waylandInfoSample)
        assertEquals(2, outputs.size)
        assertEquals(listOf(3840, 3840), outputs.map { it.physicalWidth })
        assertEquals(listOf(2560, 2560), outputs.map { it.logicalWidth })
    }

    @Test
    fun `解析物理高度与逻辑原点`() {
        val byName = parseWaylandInfo(waylandInfoSample)
        assertEquals(listOf(2160, 2160), byName.map { it.physicalHeight })
        assertEquals(listOf(1440, 1440), byName.map { it.logicalHeight })
        // 样例中 DP-3 的逻辑原点为 (0,0)、DP-2 为 (2560,0)（解析按 wl_output 出现顺序）
        assertEquals(listOf(2560, 0), byName.map { it.logicalX })
        assertEquals(listOf(0, 0), byName.map { it.logicalY })
    }

    @Test
    fun `非当前模式不会被当成物理尺寸`() {
        val text = """
            interface: 'wl_output',                                  version:  4, name: 5
            	name: DP-1
            	mode:
            		width: 1920 px, height: 1080 px, refresh: 60.000 Hz,
            	mode:
            		width: 3840 px, height: 2160 px, refresh: 60.000 Hz,
            		flags: current
            interface: 'zxdg_output_manager_v1',                     version:  3, name: 6
            	xdg_output_v1
            		output: 5
            		logical_width: 2560, logical_height: 1440
        """.trimIndent()
        assertEquals(listOf(3840), parseWaylandInfo(text).map { it.physicalWidth })
    }

    @Test
    fun `X11 屏幕为物理尺寸时按 物理比逻辑 得到缩放`() {
        val outputs = parseWaylandInfo(waylandInfoSample)
        assertEquals(1.5f, waylandScaleFrom(outputs, awtScreenWidth = 3840))
    }

    @Test
    fun `X11 屏幕为逻辑尺寸时判定合成器已缩放不补偿`() {
        val outputs = parseWaylandInfo(waylandInfoSample)
        assertEquals(1f, waylandScaleFrom(outputs, awtScreenWidth = 2560))
    }

    @Test
    fun `两种尺寸都不匹配时取最大比值兜底`() {
        val outputs = parseWaylandInfo(waylandInfoSample)
        assertEquals(1.5f, waylandScaleFrom(outputs, awtScreenWidth = 1920))
    }

    @Test
    fun `空输出不产生缩放结论`() {
        assertNull(waylandScaleFrom(emptyList(), awtScreenWidth = 3840))
    }

    @Test
    fun `Xft dpi 换算缩放`() {
        assertEquals(1.5f, scaleFromXftDpi("Xft.dpi:\t144"))
        assertEquals(1.25f, scaleFromXftDpi("Xft.dpi: 120"))
        assertNull(scaleFromXftDpi("Xft.dpi:\t96"))
        assertNull(scaleFromXftDpi(""))
    }

    @Test
    fun `GNOME monitors 取最大 scale`() {
        assertEquals(1.5f, scaleFromGnomeMonitors("<scale>1</scale><scale>1.5</scale>"))
        assertEquals(1.25f, scaleFromGnomeMonitors("<logicalmonitor><scale>1.25</scale></logicalmonitor>"))
        assertNull(scaleFromGnomeMonitors("<scale>1</scale>"))
    }

    @Test
    fun `工具包环境变量取较大值`() {
        assertEquals(2f, scaleFromToolkitEnv(gdkScale = "2", qtScaleFactor = null))
        assertEquals(1.5f, scaleFromToolkitEnv(gdkScale = null, qtScaleFactor = "1.5"))
        assertEquals(2f, scaleFromToolkitEnv(gdkScale = "2", qtScaleFactor = "1.25"))
        assertNull(scaleFromToolkitEnv(gdkScale = "1", qtScaleFactor = null))
        assertNull(scaleFromToolkitEnv(gdkScale = null, qtScaleFactor = null))
    }
}
